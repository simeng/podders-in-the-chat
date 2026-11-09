package com.poddersinthechat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import coil.compose.AsyncImage
import com.poddersinthechat.app.data.model.PodcastFeed
import com.poddersinthechat.app.data.model.Recommendation
import com.poddersinthechat.app.data.repository.FeedRepository
import com.poddersinthechat.app.ui.theme.AccentCoral
import com.poddersinthechat.app.ui.theme.PrimaryPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsDialog(
    subscribedFeeds: List<PodcastFeed>,
    podcastSearchResults: List<Recommendation>,
    recommendationsStatus: String?,
    podcastSearchQuery: String,
    onPodcastSearchQueryChange: (String) -> Unit,
    onLoadRecommendations: () -> Unit,
    onSubscribe: (url: String, onComplete: (Boolean) -> Unit) -> Unit,
    onUnsubscribe: (feedUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember { mutableStateOf("") }
    var isSubscribing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchSubscribingUrl by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.RssFeed,
                        contentDescription = "RSS Subscriptions",
                        tint = AccentCoral,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Podcast Subscriptions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manual Feed URL Input
            Text(
                text = "Enter RSS Feed URL",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = urlInput,
                onValueChange = {
                    urlInput = it
                    errorMessage = null
                },
                placeholder = { Text("https://example.com/podcast/rss.xml") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                trailingIcon = {
                    if (isSubscribing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryPurple
                        )
                    }
                }
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    val target = urlInput.trim()
                    if (target.isNotEmpty()) {
                        isSubscribing = true
                        errorMessage = null
                        onSubscribe(target) { success ->
                            isSubscribing = false
                            if (success) {
                                urlInput = ""
                            } else {
                                errorMessage = "Could not parse or reach RSS feed URL."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = urlInput.isNotBlank() && !isSubscribing,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Subscribe to Feed", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Podcast catalog search (PodcastIndex recommendations, cached locally for a month)
            LaunchedEffect(Unit) { onLoadRecommendations() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = AccentCoral,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Search Podcasts",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (podcastSearchResults.isNotEmpty() && recommendationsStatus == null) {
                    Text(
                        text = "catalog loaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = podcastSearchQuery,
                onValueChange = onPodcastSearchQueryChange,
                placeholder = { Text("Search the catalog by podcast name…") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryPurple,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (recommendationsStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (recommendationsStatus.startsWith("Loading")) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = recommendationsStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recommendationsStatus.startsWith("Loading")) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            } else {
                Text(
                    text = if (podcastSearchQuery.isBlank()) {
                        "Popular podcasts — or search above"
                    } else {
                        "Matches for “${podcastSearchQuery.trim()}”"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                CatalogSearchResults(
                    results = podcastSearchResults,
                    subscribedUrls = subscribedFeeds.map { it.feedUrl }.toSet(),
                    subscribingUrl = searchSubscribingUrl,
                    onAdd = { rec ->
                        if (searchSubscribingUrl == null) {
                            searchSubscribingUrl = rec.url
                            onSubscribe(rec.url) { success ->
                                searchSubscribingUrl = null
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick-add popular suggestions
            Text(
                text = "Popular Suggestions",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeedRepository.SAMPLE_FEEDS.take(3).forEach { sample ->
                    val isSubbed = subscribedFeeds.any { it.feedUrl == sample.url }
                    Surface(
                        onClick = {
                            if (!isSubbed && !isSubscribing) {
                                isSubscribing = true
                                onSubscribe(sample.url) {
                                    isSubscribing = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSubbed) PrimaryPurple.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSubbed) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Subscribed",
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = sample.title.split("-", ":").first().trim(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // Subscribed Feeds List
            Text(
                text = "Your Subscribed Feeds (${subscribedFeeds.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (subscribedFeeds.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No feeds subscribed yet.\nEnter an RSS URL above to get started!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(subscribedFeeds, key = { it.feedUrl }) { feed ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!feed.imageUrl.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = feed.imageUrl,
                                        contentDescription = feed.title,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = feed.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = feed.feedUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(onClick = { onUnsubscribe(feed.feedUrl) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Unsubscribe",
                                        tint = AccentCoral.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Results list for the podcast catalog search: artwork, title, host,
 * and an Add button (or subscribed checkmark / progress spinner).
 */
@Composable
private fun CatalogSearchResults(
    results: List<Recommendation>,
    subscribedUrls: Set<String>,
    subscribingUrl: String?,
    onAdd: (Recommendation) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No podcasts match your search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(results, key = { it.url }) { rec ->
            val isSubbed = rec.url in subscribedUrls
            val isAdding = subscribingUrl == rec.url

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSubbed) PrimaryPurple.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rec.image.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryPurple.copy(alpha = 0.25f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = PrimaryPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = rec.image,
                            contentDescription = rec.title,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = rec.host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    when {
                        isSubbed -> Icon(
                            Icons.Filled.Check,
                            contentDescription = "Subscribed",
                            tint = PrimaryPurple,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(20.dp)
                        )
                        isAdding -> CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryPurple
                        )
                        else -> FilledTonalButton(
                            onClick = { onAdd(rec) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryPurple,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add podcast", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
