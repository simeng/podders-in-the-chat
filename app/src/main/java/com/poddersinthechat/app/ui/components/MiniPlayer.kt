package com.poddersinthechat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.ui.theme.PrimaryPurple
import com.poddersinthechat.app.ui.theme.ProgressTrack

/**
 * Persistent mini-player shown at the bottom of the main screen.
 *
 * When [episode] is null nothing has ever been played: the widget stays
 * visible in a stopped state with no usable play button (there is no media
 * to point at).
 */
@Composable
fun MiniPlayer(
    episode: Episode?,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onSkipForward: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStopped = episode == null
    val total = if (durationMs > 0) durationMs else ((episode?.durationSeconds ?: 0L) * 1000L)
    val progress = if (!isStopped && total > 0) (currentPosMs.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        tonalElevation = 6.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Thin progress bar along top edge
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = PrimaryPurple,
                trackColor = ProgressTrack
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isStopped) {
                    // No episode selected yet: placeholder artwork, nothing to play
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryPurple.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    val ep = episode
                    if (!ep.feedImageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ep.feedImageUrl,
                            contentDescription = ep.title,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = episode?.title ?: "Nothing playing yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episode?.feedTitle ?: "Pick an episode to start listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause (disabled in stopped state - there is no media to play)
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(36.dp)
                            .padding(6.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryPurple
                    )
                } else {
                    IconButton(onClick = onPlayPauseClick, enabled = !isStopped) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = if (isStopped) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Skip +30s
                IconButton(onClick = onSkipForward, enabled = !isStopped) {
                    Icon(
                        imageVector = Icons.Filled.Forward30,
                        contentDescription = "Skip 30 seconds",
                        tint = if (isStopped) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
