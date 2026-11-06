package com.poddersinthechat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["feedUrl"]),
        Index(value = ["pubDate"])
    ]
)
data class Episode(
    @PrimaryKey val id: String, // guid or composite feedUrl + audioUrl
    val feedUrl: String,
    val feedTitle: String,
    val feedImageUrl: String?,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val pubDate: Long = 0L, // timestamp ms
    val pubDateStr: String = "",
    val durationSeconds: Long = 0L,
    val durationStr: String = "",
    val isNew: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val isCompleted: Boolean = false
) {
    /**
     * Percentage progress from 0.0f to 1.0f
     */
    val progressFraction: Float
        get() {
            if (isCompleted) return 1.0f
            val total = if (totalDurationMs > 0) totalDurationMs else (durationSeconds * 1000L)
            if (total <= 0L || playbackPositionMs <= 0L) return 0.0f
            return (playbackPositionMs.toFloat() / total.toFloat()).coerceIn(0.0f, 1.0f)
        }

    /**
     * Whether the user has started listening to this episode earlier
     */
    val hasListened: Boolean
        get() = playbackPositionMs > 5000L || isCompleted

    val formattedRemainingTime: String
        get() {
            val total = if (totalDurationMs > 0) totalDurationMs else (durationSeconds * 1000L)
            if (total <= 0L) return durationStr.ifEmpty { "Audio" }
            val remainingMs = (total - playbackPositionMs).coerceAtLeast(0L)
            val minutes = remainingMs / 1000 / 60
            val hours = minutes / 60
            val remMinutes = minutes % 60
            return if (hours > 0) "${hours}h ${remMinutes}m left" else "${remMinutes}m left"
        }
}
