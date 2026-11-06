package com.poddersinthechat.app.data.repository

import com.poddersinthechat.app.data.db.PodcastDao
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed
import com.poddersinthechat.app.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val podcastDao: PodcastDao,
    private val rssParser: RssParser = RssParser(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshError = MutableStateFlow<String?>(null)
    val lastRefreshError: StateFlow<String?> = _lastRefreshError.asStateFlow()

    val subscribedFeeds: Flow<List<PodcastFeed>> = podcastDao.getAllFeedsFlow()
    val allEpisodes: Flow<List<Episode>> = podcastDao.getAllEpisodesFlow()
    val newEpisodes: Flow<List<Episode>> = podcastDao.getNewEpisodesFlow()

    fun getEpisodesForFeed(feedUrl: String): Flow<List<Episode>> =
        podcastDao.getEpisodesForFeedFlow(feedUrl)

    fun getEpisodeFlow(id: String): Flow<Episode?> =
        podcastDao.getEpisodeFlow(id)

    suspend fun getEpisode(id: String): Episode? =
        podcastDao.getEpisodeById(id)

    /**
     * Subscribes to a new feed URL, fetches and parses it, and stores it in the database.
     */
    suspend fun subscribeFeed(url: String): Result<PodcastFeed> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Feed URL cannot be empty"))
        }

        try {
            val parsed = fetchAndParse(cleanUrl)
            podcastDao.insertFeed(parsed.feed)
            podcastDao.upsertEpisodesPreservingProgress(parsed.episodes)
            Result.success(parsed.feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unsubscribes from a feed and removes its episodes from database.
     */
    suspend fun unsubscribeFeed(feedUrl: String) = withContext(Dispatchers.IO) {
        podcastDao.deleteEpisodesForFeed(feedUrl)
        podcastDao.deleteFeed(feedUrl)
    }

    /**
     * Refreshes all subscribed feeds, updating episodes and highlighting new ones.
     */
    suspend fun refreshAllFeeds(): Result<Int> = withContext(Dispatchers.IO) {
        if (_isRefreshing.value) return@withContext Result.success(0)
        _isRefreshing.value = true
        _lastRefreshError.value = null

        var updatedFeedsCount = 0
        var anyError: String? = null

        try {
            val feeds = podcastDao.getAllFeeds()
            for (feed in feeds) {
                try {
                    val parsed = fetchAndParse(feed.feedUrl)
                    podcastDao.insertFeed(parsed.feed)
                    podcastDao.upsertEpisodesPreservingProgress(parsed.episodes)
                    updatedFeedsCount++
                } catch (e: Exception) {
                    anyError = "Error updating ${feed.title}: ${e.message}"
                }
            }
            if (anyError != null) {
                _lastRefreshError.value = anyError
            }
            Result.success(updatedFeedsCount)
        } catch (e: Exception) {
            _lastRefreshError.value = e.message
            Result.failure(e)
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun updatePlaybackProgress(
        episodeId: String,
        positionMs: Long,
        totalDurationMs: Long,
        isCompleted: Boolean
    ) = withContext(Dispatchers.IO) {
        podcastDao.updatePlaybackProgress(
            id = episodeId,
            positionMs = positionMs,
            totalDurationMs = totalDurationMs,
            lastPlayedAt = System.currentTimeMillis(),
            isCompleted = isCompleted
        )
    }

    suspend fun clearNewBadge(episodeId: String) = withContext(Dispatchers.IO) {
        podcastDao.clearNewBadge(episodeId)
    }

    private fun fetchAndParse(feedUrl: String): com.poddersinthechat.app.data.parser.ParsedPodcast {
        val request = Request.Builder()
            .url(feedUrl)
            .header("User-Agent", "PoddersInTheChat/1.0 (Android; Podcast Player)")
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw IllegalStateException("Empty response from server")
        return body.byteStream().use { stream ->
            rssParser.parse(stream, feedUrl)
        }
    }

    companion object {
        /**
         * Sample RSS feeds for quick test/demonstration
         */
        val SAMPLE_FEEDS = listOf(
            SampleFeed(
                title = "Syntax - Tasty Web Development Treats",
                url = "https://feed.syntax.fm/rss",
                description = "Full stack web development podcast with Wes Bos and Scott Tolinski."
            ),
            SampleFeed(
                title = "Rad Crew Podcast",
                url = "https://feed.radcrew.net/radcrew",
                description = "Norges største og eldste podcast om spill, popkultur og underholdning."
            ),
            SampleFeed(
                title = "The Changelog: Software Development",
                url = "https://changelog.com/podcast/feed",
                description = "Conversations with the hackers, leaders, and innovators of software development."
            ),
            SampleFeed(
                title = "BBC Global News Podcast",
                url = "https://podcasts.files.bbci.co.uk/p02nq0gn.rss",
                description = "The day's top stories from BBC News."
            )
        )
    }
}

data class SampleFeed(
    val title: String,
    val url: String,
    val description: String
)
