package com.poddersinthechat.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed
import com.poddersinthechat.app.ui.components.EpisodeActionSheet
import com.poddersinthechat.app.ui.components.EpisodeItem
import com.poddersinthechat.app.ui.components.FullPlayerSheet
import com.poddersinthechat.app.ui.components.MiniPlayer
import com.poddersinthechat.app.ui.components.SettingsSheet
import com.poddersinthechat.app.ui.components.SubscriptionsDialog
import com.poddersinthechat.app.ui.theme.AccentCoral
import com.poddersinthechat.app.ui.theme.NewBadgeColor
import com.poddersinthechat.app.ui.theme.PrimaryPurple
import com.poddersinthechat.app.ui.viewmodel.EpisodeFilter
import com.poddersinthechat.app.ui.viewmodel.PodcastViewModel

/**
 * MIME types offered by the document picker when importing OPML files. OPML
 * is usually served as text/x-opml, but file managers often report it as
 * plain text, generic XML, or no type at all.
 */
private val OPML_MIME_TYPES = arrayOf(
    "text/x-opml",
    "application/x-opml",
    "text/xml",
    "application/xml",
    "text/plain",
    "application/octet-stream"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: PodcastViewModel) {
    val episodes by viewModel.episodes.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    // Podcast catalog search state
    val podcastSearchResults by viewModel.podcastSearchResults.collectAsState()
    val podcastSearchQuery by viewModel.podcastSearchQuery.collectAsState()
    val recommendationsStatus by viewModel.recommendationsStatus.collectAsState()

    // Player state
    val currentEpisode by viewModel.playerManager.currentEpisode.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val isLoadingAudio by viewModel.playerManager.isLoading.collectAsState()
    val currentPosMs by viewModel.playerManager.playbackPositionMs.collectAsState()
    val durationMs by viewModel.playerManager.durationMs.collectAsState()
    val playbackSpeed by viewModel.playerManager.playbackSpeed.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val selectedFeedUrl by viewModel.selectedFeedUrl.collectAsState()

    // Offline downloads + settings
    val downloads by viewModel.downloads.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val downloadErrors by viewModel.downloadErrors.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsState()
    val refreshHour by viewModel.refreshHourOfDay.collectAsState()
    val retentionDays by viewModel.retentionDays.collectAsState()

    var showSubscriptionsDialog by remember { mutableStateOf(false) }
    var showFullPlayerSheet by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var episodeForActions by remember { mutableStateOf<Episode?>(null) }

    // OPML import: system document picker, result handled in the ViewModel
    val importOpmlLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importOpml(uri)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryPurple,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Podders in the Chat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.2.sp
                                )
                                Text(
                                    text = "${feeds.size} feeds subscribed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Settings
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Search Toggle
                        IconButton(onClick = { showSearchField = !showSearchField }) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search Episodes",
                                tint = if (searchQuery.isNotEmpty()) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Subscriptions Button with count badge
                        IconButton(onClick = { showSubscriptionsDialog = true }) {
                            BadgedBox(
                                badge = {
                                    if (feeds.isNotEmpty()) {
                                        Badge(
                                            containerColor = AccentCoral,
                                            contentColor = Color.White
                                        ) {
                                            Text("${feeds.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.RssFeed,
                                    contentDescription = "Manage RSS Feeds",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Refresh Feeds Button (animates when refreshing)
                        val infiniteTransition = rememberInfiniteTransition(label = "spin")
                        val rotationAngle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1000, easing = LinearEasing)
                            ),
                            label = "rotation"
                        )

                        IconButton(
                            onClick = { viewModel.refreshFeedsNow() },
                            enabled = !isRefreshing
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh Feeds",
                                modifier = if (isRefreshing) Modifier.rotate(rotationAngle) else Modifier,
                                tint = if (isRefreshing) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Refreshing progress line
                if (isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryPurple
                    )
                }

                // Search field (if expanded)
                AnimatedVisibility(visible = showSearchField) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Filter episodes by keyword...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryPurple
                            )
                        )
                    }
                }

            }
        },
        bottomBar = {
            Column {
                // Filter chips near the thumb (above the mini-player) for easy mobile reach
                FilterChipsRow(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { viewModel.setFilter(it) },
                    feeds = feeds,
                    selectedFeedUrl = selectedFeedUrl,
                    onFeedSelected = { viewModel.setSelectedFeed(it) }
                )

                // Persistent Mini-Player at bottom (always visible; shows a
                // stopped state when nothing has been played yet)
                MiniPlayer(
                    episode = currentEpisode,
                    isPlaying = isPlaying,
                    isLoading = isLoadingAudio,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSkipForward = { viewModel.playerManager.skipForward(30) },
                    onClick = { if (currentEpisode != null) showFullPlayerSheet = true }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = PrimaryPurple.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (feeds.isEmpty()) {
                                "No podcast subscriptions yet."
                            } else if (searchQuery.isNotEmpty()) {
                                "No episodes match \"$searchQuery\"."
                            } else if (selectedFilter == EpisodeFilter.NEWEST) {
                                "No newest episodes found right now.\nTap refresh to check your feeds!"
                            } else if (selectedFilter == EpisodeFilter.IN_PROGRESS) {
                                "No episodes in progress.\nStart listening to one below!"
                            } else if (selectedFilter == EpisodeFilter.UNPLAYED) {
                                "No unplayed episodes.\nYou're all caught up!"
                            } else {
                                "No episodes found."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(episodes, key = { it.id }) { episode ->
                        val isCurrent = currentEpisode?.id == episode.id
                        EpisodeItem(
                            episode = episode,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            onPlayClick = { viewModel.playEpisode(episode) },
                            onClick = {
                                if (isCurrent) {
                                    showFullPlayerSheet = true
                                } else {
                                    viewModel.playEpisode(episode)
                                    showFullPlayerSheet = true
                                }
                            },
                            isDownloaded = viewModel.downloadRepository.isDownloaded(episode.id),
                            downloadProgress = activeDownloads[episode.id]?.fraction,
                            onLongClick = { episodeForActions = episode }
                        )
                    }
                }
            }
        }
    }

    // Modal Subscriptions Dialog
    if (showSubscriptionsDialog) {
        SubscriptionsDialog(
            subscribedFeeds = feeds,
            podcastSearchResults = podcastSearchResults,
            recommendationsStatus = recommendationsStatus,
            podcastSearchQuery = podcastSearchQuery,
            onPodcastSearchQueryChange = { viewModel.setPodcastSearchQuery(it) },
            onLoadRecommendations = { viewModel.loadRecommendations() },
            onSubscribe = { url, onComplete ->
                viewModel.subscribeFeed(url, onComplete)
            },
            onUnsubscribe = { feedUrl ->
                viewModel.unsubscribeFeed(feedUrl)
            },
            onDismiss = { showSubscriptionsDialog = false }
        )
    }

    // Modal Full Screen Player Sheet
    if (showFullPlayerSheet && currentEpisode != null) {
        FullPlayerSheet(
            episode = currentEpisode!!,
            isPlaying = isPlaying,
            isLoading = isLoadingAudio,
            currentPosMs = currentPosMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            onPlayPauseClick = { viewModel.togglePlayPause() },
            onSeekTo = { posMs -> viewModel.playerManager.seekTo(posMs) },
            onSkipBackward = { viewModel.playerManager.skipBackward(10) },
            onSkipForward = { viewModel.playerManager.skipForward(30) },
            onCycleSpeed = { viewModel.playerManager.cyclePlaybackSpeed() },
            onDismiss = { showFullPlayerSheet = false },
            nextEpisodeTitle = nextEpisode?.title,
            onPlayNext = { viewModel.playNextEpisode() }
        )
    }

    // Settings sheet (gear icon)
    if (showSettingsSheet) {
        SettingsSheet(
            themeMode = themeMode,
            onThemeModeChange = { viewModel.setThemeMode(it) },
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = { viewModel.setPlaybackSpeed(it) },
            autoPlayNext = autoPlayNext,
            onAutoPlayNextChange = { viewModel.setAutoPlayNext(it) },
            autoRefreshEnabled = autoRefreshEnabled,
            onAutoRefreshEnabledChange = { viewModel.setAutoRefreshEnabled(it) },
            refreshHourOfDay = refreshHour,
            onRefreshHourChange = { viewModel.setRefreshHourOfDay(it) },
            retentionDays = retentionDays,
            onRetentionDaysChange = { viewModel.setRetentionDays(it) },
            downloadFileCount = downloads.size,
            downloadTotalSizeBytes = viewModel.downloadRepository.totalSizeBytes,
            onClearAllDownloads = { viewModel.clearAllDownloads() },
            onImportOpml = {
                importOpmlLauncher.launch(OPML_MIME_TYPES)
            },
            onExportOpml = { viewModel.exportOpml() },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Long-press episode action sheet (download / mark played)
    episodeForActions?.let { episode ->
        val dl = downloads[episode.id]
        EpisodeActionSheet(
            episode = episode,
            isDownloaded = viewModel.downloadRepository.isDownloaded(episode.id),
            downloadedAt = dl?.downloadedAt ?: 0L,
            daysLeft = dl?.let { viewModel.downloadRepository.daysLeft(it) } ?: 0,
            downloadProgress = activeDownloads[episode.id]?.fraction,
            downloadError = downloadErrors[episode.id],
            onDownload = { viewModel.downloadEpisode(episode) },
            onCancelDownload = { viewModel.cancelDownload(episode.id) },
            onDeleteDownload = {
                viewModel.deleteDownload(episode.id)
                episodeForActions = null
            },
            onMarkPlayed = { completed ->
                viewModel.markEpisodePlayed(episode, completed)
                episodeForActions = null
            },
            onDismiss = { episodeForActions = null }
        )
    }
}

/**
 * The episode list filter chips. Rendered at the bottom of the screen (just
 * above the mini-player) so they are easy to reach on mobile.
 */
@Composable
private fun FilterChipsRow(
    selectedFilter: EpisodeFilter,
    onFilterSelected: (EpisodeFilter) -> Unit,
    feeds: List<PodcastFeed>,
    selectedFeedUrl: String?,
    onFeedSelected: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedFilter == EpisodeFilter.ALL,
                onClick = { onFilterSelected(EpisodeFilter.ALL) },
                label = { Text("All Episodes") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryPurple,
                    selectedLabelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == EpisodeFilter.NEWEST,
                onClick = { onFilterSelected(EpisodeFilter.NEWEST) },
                label = { Text("★ Newest") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NewBadgeColor,
                    selectedLabelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == EpisodeFilter.IN_PROGRESS,
                onClick = { onFilterSelected(EpisodeFilter.IN_PROGRESS) },
                label = { Text("In Progress") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryPurple,
                    selectedLabelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = selectedFilter == EpisodeFilter.UNPLAYED,
                onClick = { onFilterSelected(EpisodeFilter.UNPLAYED) },
                label = { Text("Unplayed") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryPurple,
                    selectedLabelColor = Color.White
                )
            )
        }

        // Per-feed filters (only when there's more than one feed)
        if (feeds.size > 1) {
            item {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            items(feeds, key = { it.feedUrl }) { feed ->
                val isFeedSelected = selectedFeedUrl == feed.feedUrl
                FilterChip(
                    selected = isFeedSelected,
                    onClick = {
                        // Tapping the selected feed chip again clears the filter
                        onFeedSelected(if (isFeedSelected) null else feed.feedUrl)
                    },
                    label = {
                        Text(
                            text = feed.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryPurple,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}
