package com.poddersinthechat.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.player.PodcastPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class EpisodeFilter {
    ALL,
    NEWEST,
    IN_PROGRESS
}

class PodcastViewModel(
    private val feedRepository: FeedRepository,
    val playerManager: PodcastPlayerManager
) : ViewModel() {

    val feeds: StateFlow<List<PodcastFeed>> = feedRepository.subscribedFeeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRefreshing: StateFlow<Boolean> = feedRepository.isRefreshing

    private val _selectedFilter = MutableStateFlow(EpisodeFilter.ALL)
    val selectedFilter: StateFlow<EpisodeFilter> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val episodes: StateFlow<List<Episode>> = combine(
        feedRepository.allEpisodes,
        _selectedFilter,
        _searchQuery
    ) { allEpisodes, filter, query ->
        var filtered = allEpisodes

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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }

    fun refreshFeeds() {
        viewModelScope.launch {
            val result = feedRepository.refreshAllFeeds()
            result.onSuccess { count ->
                _userMessage.value = "Updated $count podcast feeds"
            }.onFailure { err ->
                _userMessage.value = "Refresh failed: ${err.message}"
            }
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
            _userMessage.value = "Unsubscribed from feed"
        }
    }

    fun playEpisode(episode: Episode) {
        playerManager.playEpisode(episode)
    }

    fun togglePlayPause() {
        playerManager.togglePlayPause()
    }

    fun setFilter(filter: EpisodeFilter) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
