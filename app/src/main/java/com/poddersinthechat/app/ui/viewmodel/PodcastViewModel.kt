package com.poddersinthechat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.poddersinthechat.app.data.SettingsStore
import com.poddersinthechat.app.data.ThemeMode
import com.poddersinthechat.app.data.model.DownloadedEpisode
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed
import com.poddersinthechat.app.data.model.Recommendation
import com.poddersinthechat.app.data.repository.DownloadRepository
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.data.repository.RecommendationsRepository
import com.poddersinthechat.app.player.PodcastPlayerManager
import com.poddersinthechat.app.worker.FeedRefreshScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EpisodeFilter {
    ALL,
    NEWEST,
    IN_PROGRESS,
    UNPLAYED
}

class PodcastViewModel(
    private val appContext: Context,
    private val feedRepository: FeedRepository,
    val playerManager: PodcastPlayerManager,
    private val recommendationsRepository: RecommendationsRepository,
    val downloadRepository: DownloadRepository,
    val settingsStore: SettingsStore
) : ViewModel() {

    val feeds: StateFlow<List<PodcastFeed>> = feedRepository.subscribedFeeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRefreshing: StateFlow<Boolean> = feedRepository.isRefreshing

    // Start on the "Newest" tab so fresh episodes are what the app opens to
    private val _selectedFilter = MutableStateFlow(EpisodeFilter.NEWEST)
    val selectedFilter: StateFlow<EpisodeFilter> = _selectedFilter.asStateFlow()

    /** Restrict the episode list to a single feed (null = all feeds). */
    private val _selectedFeedUrl = MutableStateFlow<String?>(null)
    val selectedFeedUrl: StateFlow<String?> = _selectedFeedUrl.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Podcast catalog (PodcastIndex recommendations, cached locally for a month)
    private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val recommendations: StateFlow<List<Recommendation>> = _recommendations.asStateFlow()

    /** null = loaded; non-null = human-readable loading/error message. */
    private val _recommendationsStatus = MutableStateFlow<String?>(null)
    val recommendationsStatus: StateFlow<String?> = _recommendationsStatus.asStateFlow()

    private val _podcastSearchQuery = MutableStateFlow("")
    val podcastSearchQuery: StateFlow<String> = _podcastSearchQuery.asStateFlow()

    /**
     * Catalog search results: top popular podcasts while the query is empty,
     * otherwise title matches ranked by popularity.
     */
    val podcastSearchResults: StateFlow<List<Recommendation>> = combine(
        _recommendations,
        _podcastSearchQuery
    ) { recs, query ->
        if (query.isBlank()) {
            recs.sortedByDescending { it.popularity }.take(TOP_POPULAR_LIMIT)
        } else {
            val q = query.trim().lowercase()
            recs.filter { it.title.lowercase().contains(q) }
                .sortedByDescending { it.popularity }
                .take(SEARCH_RESULT_LIMIT)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val episodes: StateFlow<List<Episode>> = combine(
        feedRepository.allEpisodes,
        _selectedFilter,
        _searchQuery,
        _selectedFeedUrl
    ) { allEpisodes, filter, query, feedUrl ->
        var filtered = allEpisodes

        if (feedUrl != null) {
            filtered = filtered.filter { it.feedUrl == feedUrl }
        }

        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter {
                it.title.lowercase().contains(q) ||
                    it.feedTitle.lowercase().contains(q) ||
                    it.description.lowercase().contains(q)
            }
        }

        when (filter) {
            EpisodeFilter.ALL -> filtered
            EpisodeFilter.NEWEST -> filtered.filter { it.isNew }
            EpisodeFilter.IN_PROGRESS -> filtered.filter { it.hasListened && !it.isCompleted }
            EpisodeFilter.UNPLAYED -> filtered.filter { !it.hasListened }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The episode that would play next in the current feed (chronologically
     * later, unplayed). Recomputes whenever the current episode or any
     * episode's completion state changes.
     */
    val nextEpisode: StateFlow<Episode?> = combine(
        playerManager.currentEpisode,
        feedRepository.allEpisodes
    ) { current, _ -> current }
        .flatMapLatest { current ->
            if (current == null) flowOf(null)
            else flowOf(feedRepository.getNextEpisodeInFeed(current))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Local offline downloads, keyed by episode id. */
    val downloads: StateFlow<Map<String, DownloadedEpisode>> =
        downloadRepository.downloads

    /** Downloads currently in flight, keyed by episode id. */
    val activeDownloads: StateFlow<Map<String, DownloadRepository.DownloadProgress>> =
        downloadRepository.activeDownloads

    /** Last download error per episode id (if any). */
    val downloadErrors: StateFlow<Map<String, String>> =
        downloadRepository.downloadErrors

    init {
        // Automatically refresh feeds and highlight newest when app starts
        viewModelScope.launch {
            val existingFeeds = feedRepository.subscribedFeeds.first()
            if (existingFeeds.isEmpty()) {
                // First launch: subscribe to a couple of top sample feeds
                subscribeFeed(FeedRepository.SAMPLE_FEEDS[0].url)
                subscribeFeed(FeedRepository.SAMPLE_FEEDS[1].url)
            } else {
                refreshFeeds()
            }
        }

        // Surface auto-advance ("now playing next") as a snackbar
        viewModelScope.launch {
            playerManager.autoAdvanceEvents.collect { next ->
                _userMessage.value = "▶ Now playing next: ${next.title}"
            }
        }
    }

    /**
     * Refreshes all feeds in the background, but only when the cached feed
     * data (from the last successful refresh — background or foreground)
     * is older than the 6h stale threshold. Called on app start; if the
     * cache is fresh, it's a no-op so the current list just stays on screen.
     */
    fun refreshFeeds() {
        if (!feedRepository.isRefreshStale()) {
            _userMessage.value = "Feeds are up to date (${describeAge(feedRepository.lastRefreshTimeMs)})"
            return
        }
        refreshFeedsNow()
    }

    /**
     * Forces a refresh of all feeds, ignoring the stale-cache check.
     * Called by the manual refresh button.
     */
    fun refreshFeedsNow() {
        viewModelScope.launch {
            val result = feedRepository.refreshAllFeeds()
            result.onSuccess { count ->
                _userMessage.value = "Updated $count podcast feeds"
            }.onFailure { err ->
                _userMessage.value = "Refresh failed: ${err.message}"
            }
        }
    }

    private fun describeAge(lastRefreshMs: Long): String {
        val minutes = (System.currentTimeMillis() - lastRefreshMs) / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }

    fun subscribeFeed(url: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val result = feedRepository.subscribeFeed(url)
            result.onSuccess { feed ->
                _userMessage.value = "Subscribed to ${feed.title}"
                onComplete?.invoke(true)
            }.onFailure { err ->
                _userMessage.value = "Subscription failed: ${err.message}"
                onComplete?.invoke(false)
            }
        }
    }

    fun unsubscribeFeed(feedUrl: String) {
        viewModelScope.launch {
            feedRepository.unsubscribeFeed(feedUrl)
            // Offline copies of that feed are orphaned by the unsubscribe — remove them
            downloadRepository.deleteDownloadsForFeed(feedUrl)
            if (_selectedFeedUrl.value == feedUrl) _selectedFeedUrl.value = null
            _userMessage.value = "Unsubscribed from feed"
        }
    }

    fun playEpisode(episode: Episode) {
        playerManager.playEpisode(episode)
    }

    fun playNextEpisode() {
        nextEpisode.value?.let { playerManager.playEpisode(it) }
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    /** Sets the playback speed both on the live player and as the saved preference. */
    fun setPlaybackSpeed(speed: Float) {
        settingsStore.setPlaybackSpeed(speed)
        playerManager.setPlaybackSpeed(speed)
    }

    fun setFilter(filter: EpisodeFilter) {
        _selectedFilter.value = filter
    }

    fun setSelectedFeed(feedUrl: String?) {
        _selectedFeedUrl.value = feedUrl
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Loads the podcast catalog for search. Served from the local cache when
     * it is younger than a month; otherwise downloaded first (stale cache is
     * used as a fallback if the download fails). Safe to call repeatedly.
     */
    fun loadRecommendations() {
        if (_recommendations.value.isNotEmpty()) return // already loaded (in memory)
        if (_recommendationsStatus.value == LOADING_STATUS) return // already in flight
        viewModelScope.launch {
            _recommendationsStatus.value = LOADING_STATUS
            recommendationsRepository.getRecommendations()
                .onSuccess { list ->
                    _recommendations.value = list
                    _recommendationsStatus.value = if (list.isEmpty()) "Podcast catalog is empty" else null
                }
                .onFailure { err ->
                    // failure keeps a non-LOADING status so the next call can retry
                    _recommendationsStatus.value = err.message ?: "Unknown error"
                }
        }
    }

    fun setPodcastSearchQuery(query: String) {
        _podcastSearchQuery.value = query
    }

    // ----- Settings (all exposed to the Settings screen) -----

    val themeMode: StateFlow<ThemeMode> = settingsStore.themeMode
    val autoPlayNext: StateFlow<Boolean> = settingsStore.autoPlayNext
    val autoRefreshEnabled: StateFlow<Boolean> = settingsStore.autoRefreshEnabled
    val refreshHourOfDay: StateFlow<Int> = settingsStore.refreshHourOfDay
    val retentionDays: StateFlow<Int> = settingsStore.retentionDays

    fun setThemeMode(mode: ThemeMode) {
        settingsStore.setThemeMode(mode)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        settingsStore.setAutoPlayNext(enabled)
    }

    /** Enables/disables the daily background feed refresh and reschedules it. */
    fun setAutoRefreshEnabled(enabled: Boolean) {
        settingsStore.setAutoRefreshEnabled(enabled)
        FeedRefreshScheduler.applySettings(appContext, settingsStore)
        _userMessage.value = if (enabled) {
            "Daily feed refresh enabled (${describeHour(settingsStore.refreshHourOfDay.value)})"
        } else {
            "Daily feed refresh off — feeds update on demand only"
        }
    }

    /** Changes the hour of the daily refresh (and reschedules it). */
    fun setRefreshHourOfDay(hour: Int) {
        settingsStore.setRefreshHourOfDay(hour)
        if (settingsStore.autoRefreshEnabled.value) {
            FeedRefreshScheduler.applySettings(appContext, settingsStore)
            _userMessage.value = "Daily feed refresh at ${describeHour(hour)}"
        }
    }

    /** 0 = never auto-delete downloads. */
    fun setRetentionDays(days: Int) {
        settingsStore.setRetentionDays(days)
        // Shortening the window may make existing downloads expire right away
        viewModelScope.launch { downloadRepository.cleanupExpired() }
        _userMessage.value = if (days <= 0) "Downloads will be kept until you remove them" else
            "Downloads auto-delete after $days days"
    }

    private fun describeHour(hour: Int): String = "%02d:00".format(hour)

    // ----- Offline downloads -----

    /**
     * Starts (or no-ops) an offline download for [episode]. Downloads run in
     * the background of the app process; progress is exposed via
     * [activeDownloads].
     */
    fun downloadEpisode(episode: Episode) {
        if (downloadRepository.isDownloaded(episode.id)) return
        val result = downloadRepository.downloadEpisode(episode)
        if (result.isSuccess) {
            _userMessage.value = "Downloading \"${episode.title}\"…"
        } else {
            _userMessage.value = "Download failed: ${result.exceptionOrNull()?.message}"
        }
    }

    fun cancelDownload(episodeId: String) {
        downloadRepository.cancelDownload(episodeId)
    }

    fun deleteDownload(episodeId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(episodeId)
            _userMessage.value = "Download removed — episode streams as usual"
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadRepository.deleteAllDownloads()
            _userMessage.value = "All offline downloads removed"
        }
    }

    // ----- Manual completion -----

    fun markEpisodePlayed(episode: Episode, completed: Boolean) {
        viewModelScope.launch {
            feedRepository.setCompleted(episode.id, completed)
            _userMessage.value = if (completed) "Marked as played" else "Marked as unplayed"
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    companion object {
        private const val TOP_POPULAR_LIMIT = 20
        private const val SEARCH_RESULT_LIMIT = 50
        private const val LOADING_STATUS = "Loading podcast catalog…"
    }
}
