package com.poddersinthechat.app.data.repository

import android.content.Context
import com.poddersinthechat.app.data.SettingsStore
import com.poddersinthechat.app.data.db.DownloadDao
import com.poddersinthechat.app.data.model.DownloadedEpisode
import com.poddersinthechat.app.data.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import java.util.concurrent.TimeUnit

/**
 * Downloads episode audio into the app's private storage for offline playback.
 *
 * Design goals (per product requirement):
 * - Offline is an opt-in side channel: long-press an episode, tap download.
 *   Normal streaming is never affected.
 * - Downloads are temporary: after the retention period (default 7 days) they
 *   are auto-deleted ([cleanupExpired], run on app start and after each
 *   download) so they don't silently grow forever.
 *
 * Files live in [Context.getExternalFilesDir] (no permission needed) and are
 * named after a hash of the episode id, which is stable across feed refreshes.
 */
class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val settingsStore: SettingsStore,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val upsertMutex = Mutex()

    /** episodeId -> local file, for fast synchronous lookup by the player. */
    private val localFiles = ConcurrentHashMap<String, File>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, DownloadedEpisode>>(emptyMap())
    /** All persisted downloads, keyed by episode id. */
    val downloads: StateFlow<Map<String, DownloadedEpisode>> = _downloads.asStateFlow()

    /** Downloads currently in flight, keyed by episode id. */
    data class DownloadProgress(val episodeId: String, val title: String, val fraction: Float)

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    /** Last error per episode (episodeId -> message); cleared when a new attempt starts. */
    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors.asStateFlow()

    private val dir: File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "downloads").apply { mkdirs() }

    init {
        scope.launch(Dispatchers.IO) {
            downloadDao.getAllFlow().collect { list ->
                val map = list.associateBy { it.episodeId }
                _downloads.value = map
                // (Re)build the in-memory file map from the DB, dropping entries
                // whose file vanished from disk (e.g. external cleanup)
                localFiles.clear()
                map.values.forEach { dl ->
                    val file = File(dl.filePath)
                    if (file.exists() && file.length() > 0) {
                        localFiles[dl.episodeId] = file
                    }
                }
            }
        }
    }

    /** Local file for [episodeId] if a complete download exists, else null. */
    fun localFileFor(episodeId: String): File? {
        val file = localFiles[episodeId] ?: return null
        return if (file.exists() && file.length() > 0) file else null
    }

    fun isDownloaded(episodeId: String): Boolean = localFileFor(episodeId) != null

    /**
     * Days left before [download] is auto-deleted; -1 when auto-delete is
     * disabled ("Never"), 0 when already expired.
     */
    fun daysLeft(download: DownloadedEpisode, nowMs: Long = System.currentTimeMillis()): Int {
        val retention = settingsStore.retentionDays.value
        if (retention <= 0) return -1
        val cutoff = download.downloadedAt + retention * DAY_MS
        return ((cutoff - nowMs) / DAY_MS).toInt().coerceAtLeast(0)
    }

    /**
     * Downloads the audio for [episode] unless it is already available locally
     * or a download is already in flight. Emits progress into
     * [activeDownloads] while running.
     */
    fun downloadEpisode(episode: Episode): Result<Unit> {
        if (localFileFor(episode.id) != null) return Result.success(Unit)
        if (downloadJobs.containsKey(episode.id)) {
            // Already in flight — progress is visible in the UI, nothing to do
            return Result.success(Unit)
        }
        _downloadErrors.update { it - episode.id }
        val job = scope.launch(Dispatchers.IO) {
            val ctx = coroutineContext
            runCatching { performDownload(episode, ctx) }
                .onSuccess {
                    downloadJobs.remove(episode.id)
                    _activeDownloads.update { it - episode.id }
                    _downloadErrors.update { it - episode.id }
                    // A new download may push older ones past the retention window
                    cleanupExpired()
                }
                .onFailure { err ->
                    downloadJobs.remove(episode.id)
                    _activeDownloads.update { it - episode.id }
                    if (err !is CancellationException) {
                        _downloadErrors.update {
                            it + (episode.id to (err.message ?: "Unknown error"))
                        }
                    }
                }
        }
        downloadJobs[episode.id] = job
        return Result.success(Unit)
    }

    /** Cancels an in-flight download for [episodeId]. */
    fun cancelDownload(episodeId: String) {
        downloadJobs.remove(episodeId)?.cancel()
        _activeDownloads.update { it - episodeId }
    }

    suspend fun deleteDownload(episodeId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            cancelDownload(episodeId)
            _downloads.value[episodeId]?.let { dl ->
                runCatching { File(dl.filePath).delete() }
            }
            localFiles.remove(episodeId)
            downloadDao.deleteById(episodeId)
        }
    }

    /** Removes all local downloads belonging to a feed (used on unsubscribe). */
    suspend fun deleteDownloadsForFeed(feedUrl: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val affected = _downloads.value.values.filter { it.feedUrl == feedUrl }
            downloadJobs.keys.filter { _downloads.value[it]?.feedUrl == feedUrl }
                .forEach { cancelDownload(it) }
            affected.forEach { dl ->
                runCatching { File(dl.filePath).delete() }
                localFiles.remove(dl.episodeId)
            }
            downloadDao.deleteForFeed(feedUrl)
            _downloads.update { map -> map.filterValues { it.feedUrl != feedUrl } }
            affected.size
        }
    }

    suspend fun deleteAllDownloads(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            downloadJobs.values.forEach { it.cancel() }
            downloadJobs.clear()
            _activeDownloads.value = emptyMap()
            _downloads.value.values.forEach { runCatching { File(it.filePath).delete() } }
            localFiles.clear()
            downloadDao.deleteAll()
            _downloads.value.size
        }
    }

    /**
     * Removes downloads older than the configured retention period, returning
     * how many files were removed. Safe to call repeatedly (app start, after
     * each download, after retention changes).
     */
    suspend fun cleanupExpired(nowMs: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) {
            // 0 = "never" auto-delete: nothing to clean up
            if (settingsStore.retentionDays.value <= 0) return@withContext 0
            val cutoff = nowMs - settingsStore.retentionDays.value * DAY_MS
            val expired = downloadDao.getOlderThan(cutoff)
            if (expired.isEmpty()) return@withContext 0
            expired.forEach { dl ->
                runCatching { File(dl.filePath).delete() }
                localFiles.remove(dl.episodeId)
            }
            val removed = downloadDao.deleteOlderThan(cutoff)
            _downloads.update { map -> map.filterValues { it.downloadedAt >= cutoff } }
            removed
        }

    val totalSizeBytes: Long
        get() = _downloads.value.values.sumOf { it.sizeBytes }

    val fileCount: Int
        get() = _downloads.value.size

    private suspend fun performDownload(episode: Episode, ctx: CoroutineContext): DownloadedEpisode {
        val request = Request.Builder().url(episode.audioUrl).build()
        val targetFile = File(dir, fileNameFor(episode))
        val tmpFile = File(dir, targetFile.name + ".part")

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} downloading ${episode.title}")
            }
            val contentLength = response.body?.contentLength() ?: -1L
            var downloaded = 0L
            var lastEmitMs = 0L

            response.body?.source()?.use { source ->
                java.io.FileOutputStream(tmpFile).use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        // The read loop is blocking, so check cancellation
                        // explicitly to honour "Cancel download"
                        if (ctx.job?.isActive == false) {
                            tmpFile.delete()
                            throw CancellationException("Download cancelled")
                        }
                        val read = source.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (contentLength > 0 &&
                            System.currentTimeMillis() - lastEmitMs > 200L
                        ) {
                            val fraction = (downloaded.toFloat() / contentLength).coerceIn(0f, 1f)
                            lastEmitMs = System.currentTimeMillis()
                            _activeDownloads.update {
                                it + (episode.id to DownloadProgress(episode.id, episode.title, fraction))
                            }
                        }
                    }
                }
            }
        }

        if (tmpFile.length() <= 0L) {
            tmpFile.delete()
            throw IllegalStateException("Downloaded file is empty")
        }
        if (targetFile.exists()) targetFile.delete()
        tmpFile.renameTo(targetFile)

        val record = DownloadedEpisode(
            episodeId = episode.id,
            feedUrl = episode.feedUrl,
            title = episode.title,
            filePath = targetFile.absolutePath,
            downloadedAt = System.currentTimeMillis(),
            sizeBytes = targetFile.length()
        )
        localFiles[episode.id] = targetFile
        upsertMutex.withLock { downloadDao.upsert(record) }
        return record
    }

    /** Stable, filesystem-safe file name derived from the episode id. */
    private fun fileNameFor(episode: Episode): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(episode.id.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val ext = runCatching {
            val path = episode.audioUrl.substringAfterLast('/', "")
            val dot = path.lastIndexOf('.')
            if (dot > 0) path.substring(dot).lowercase() else ""
        }.getOrDefault("")
        val cleanExt = if (ext.matches(Regex("\\.[a-z0-9]{1,5}"))) ext else ".mp3"
        return "${digest.take(20)}$cleanExt"
    }

    companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
