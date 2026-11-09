package com.poddersinthechat.app.data.repository

import android.content.Context
import com.poddersinthechat.app.data.model.Recommendation
import com.poddersinthechat.app.data.parser.RecommendationsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Owns the local cache of the PodcastIndex recommendations catalog.
 *
 * The catalog ([RECOMMENDATIONS_URL], ~24 MB) is downloaded to
 * `files/recommendations.json` and kept for [CACHE_TTL] (one month).
 * While the cached file is younger than that the network is never touched.
 * When it is stale (or missing) a download is attempted; if the download
 * fails, the previous (stale) file is still used as a fallback so search
 * keeps working offline.
 */
class RecommendationsRepository(context: Context) {

    private val cacheFile = File(context.filesDir, "recommendations.json")
    private val tmpFile = File(context.filesDir, "recommendations.json.tmp")

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // the catalog is ~24 MB
        .followRedirects(true)
        .build()

    /**
     * Returns the parsed catalog, loading it from the local cache when fresh,
     * re-downloading when stale, and falling back to a stale cache when the
     * download fails.
     */
    suspend fun getRecommendations(): Result<List<Recommendation>> = withContext(Dispatchers.IO) {
        if (!isCacheFresh()) {
            tryDownload() // best effort — keeps the stale file on failure
        }

        if (!cacheFile.exists()) {
            return@withContext Result.failure(
                IllegalStateException("Podcast catalog not available (download failed and no cache)")
            )
        }

        try {
            Result.success(RecommendationsParser.parse(cacheFile.readText()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Age of the cached catalog file, in milliseconds (or [Long.MAX_VALUE] if absent). */
    fun cacheAgeMs(): Long =
        if (cacheFile.exists()) System.currentTimeMillis() - cacheFile.lastModified()
        else Long.MAX_VALUE

    private fun isCacheFresh(): Boolean = cacheAgeMs() < CACHE_TTL

    /** Downloads the catalog to a temp file and atomically swaps it into place. */
    private fun tryDownload() {
        try {
            val request = Request.Builder()
                .url(RECOMMENDATIONS_URL)
                .header("User-Agent", "PoddersInTheChat/1.0 (Android; Podcast Player)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                response.body?.byteStream()?.use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return
            }

            // Commit the fresh download — never destroy a good cache with a torn one
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmpFile.renameTo(cacheFile)) {
                tmpFile.copyTo(cacheFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            // Network/IO failure — a stale cache (if any) remains usable
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    companion object {
        const val RECOMMENDATIONS_URL = "https://public.podcastindex.org/recommendations.json"

        /** Catalog cache TTL: one month. */
        val CACHE_TTL: Long = TimeUnit.DAYS.toMillis(30)
    }
}
