package com.poddersinthechat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.ui.components.EpisodeItem
import com.poddersinthechat.app.ui.components.FullPlayerSheet
import com.poddersinthechat.app.ui.components.MiniPlayer
import com.poddersinthechat.app.ui.components.SubscriptionsDialog
import com.poddersinthechat.app.ui.theme.AccentCoral
import com.poddersinthechat.app.ui.theme.NewBadgeColor
import com.poddersinthechat.app.ui.theme.PrimaryPurple
import com.poddersinthechat.app.ui.viewmodel.EpisodeFilter
import com.poddersinthechat.app.ui.viewmodel.PodcastViewModel

private const val LOADING_PLACEHOLDER_COUNT = 8

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

    var showSubscriptionsDialog by remember { mutableStateOf(false) }
    var showFullPlayerSheet by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }

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
                            onClick = { viewModel.refreshFeeds() },
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

                // Filter Chips Row
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
                            onClick = { viewModel.setFilter(EpisodeFilter.ALL) },
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
                            onClick = { viewModel.setFilter(EpisodeFilter.NEWEST) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("★ Newest")
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NewBadgeColor,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == EpisodeFilter.IN_PROGRESS,
                            onClick = { viewModel.setFilter(EpisodeFilter.IN_PROGRESS) },
                            label = { Text("In Progress") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Persistent Mini-Player at bottom
            currentEpisode?.let { ep ->
                MiniPlayer(
                    episode = ep,
                    isPlaying = isPlaying,
                    isLoading = isLoadingAudio,
                    currentPosMs = currentPosMs,
                    durationMs = durationMs,
                    onPlayPauseClick = { viewModel.togglePlayPause() },
                    onSkipForward = { viewModel.playerManager.skipForward(30) },
                    onClick = { showFullPlayerSheet = true }
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
            if (isRefreshing) {
                // Skeleton placeholder cards while the feeds are refreshing
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(LOADING_PLACEHOLDER_COUNT) { index ->
                        EpisodePlaceholderCard(
                            // Vary the width per row so the shimmer looks less uniform
                            titleWidthFraction = 0.45f + ((index * 13) % 40) / 100f,
                            descriptionVisible = index % 3 != 2
                        )
                    }
                }
            } else if (episodes.isEmpty()) {
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
                            }
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
            onDismiss = { showFullPlayerSheet = false }
        )
    }
}

/**
 * Skeleton placeholder card shown in the content list while feeds are
 * refreshing. Mirrors the layout of [EpisodeItem] and gently pulses.
 */
@Composable
private fun EpisodePlaceholderCard(
    titleWidthFraction: Float,
    descriptionVisible: Boolean,
    modifier: Modifier = Modifier
) {
    // Gentle pulse between 35% and 100% alpha
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val blockColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .alpha(pulseAlpha)
        ) {
            // Top row: feed title line + "NEW" badge block
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(blockColor)
                )
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(blockColor)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle row: artwork square, title + description lines, play button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(blockColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(titleWidthFraction)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(blockColor)
                    )
                    if (descriptionVisible) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(blockColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(11.dp)
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(blockColor)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = blockColor,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom row: duration line
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(blockColor)
                )
            }
        }
    }
}
