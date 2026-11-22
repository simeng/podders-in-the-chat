package com.poddersinthechat.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A locally stored copy of an episode's audio, used for offline playback.
 *
 * Downloads are temporary: they are automatically deleted after the
 * configured retention period (default 7 days) so they never compete with
 * normal streaming for storage.
 */
@Entity(tableName = "downloads")
data class DownloadedEpisode(
    @PrimaryKey val episodeId: String,
    val feedUrl: String,
    val title: String,
    val filePath: String,
    val downloadedAt: Long, // epoch millis
    val sizeBytes: Long = 0L
)
