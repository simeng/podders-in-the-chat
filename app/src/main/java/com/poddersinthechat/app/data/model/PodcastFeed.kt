package com.poddersinthechat.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "podcast_feeds")
data class PodcastFeed(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val description: String = "",
    val author: String = "",
    val imageUrl: String? = null,
    val link: String = "",
    val lastFetched: Long = System.currentTimeMillis(),
    val episodeCount: Int = 0
)
