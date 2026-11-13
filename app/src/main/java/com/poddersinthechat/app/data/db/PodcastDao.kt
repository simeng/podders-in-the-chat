package com.poddersinthechat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {

    // Feeds
    @Query("SELECT * FROM podcast_feeds ORDER BY title ASC")
    fun getAllFeedsFlow(): Flow<List<PodcastFeed>>

    @Query("SELECT * FROM podcast_feeds")
    suspend fun getAllFeeds(): List<PodcastFeed>

    @Query("SELECT * FROM podcast_feeds WHERE feedUrl = :feedUrl LIMIT 1")
    suspend fun getFeed(feedUrl: String): PodcastFeed?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: PodcastFeed)

    @Query("DELETE FROM podcast_feeds WHERE feedUrl = :feedUrl")
    suspend fun deleteFeed(feedUrl: String)

    // Episodes
    @Query("SELECT * FROM episodes ORDER BY pubDate DESC")
    fun getAllEpisodesFlow(): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE feedUrl = :feedUrl ORDER BY pubDate DESC")
    fun getEpisodesForFeedFlow(feedUrl: String): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    suspend fun getEpisodeById(id: String): Episode?

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    fun getEpisodeFlow(id: String): Flow<Episode?>

    /** The most recently played episode, or null if nothing has ever been played. */
    @Query("SELECT * FROM episodes WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 1")
    suspend fun getLastPlayedEpisode(): Episode?

    @Query("SELECT * FROM episodes WHERE isNew = 1 ORDER BY pubDate DESC")
    fun getNewEpisodesFlow(): Flow<List<Episode>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodesIgnore(episodes: List<Episode>): List<Long>

    @Query("""
        UPDATE episodes 
        SET playbackPositionMs = :positionMs, 
            totalDurationMs = CASE WHEN :totalDurationMs > 0 THEN :totalDurationMs ELSE totalDurationMs END,
            lastPlayedAt = :lastPlayedAt,
            isCompleted = :isCompleted
        WHERE id = :id
    """)
    suspend fun updatePlaybackProgress(
        id: String,
        positionMs: Long,
        totalDurationMs: Long,
        lastPlayedAt: Long,
        isCompleted: Boolean
    )

    @Query("UPDATE episodes SET isNew = 0 WHERE id = :id")
    suspend fun clearNewBadge(id: String)

    @Query("DELETE FROM episodes WHERE feedUrl = :feedUrl")
    suspend fun deleteEpisodesForFeed(feedUrl: String)

    @Transaction
    suspend fun upsertEpisodesPreservingProgress(incoming: List<Episode>) {
        for (ep in incoming) {
            val existing = getEpisodeById(ep.id)
            if (existing != null) {
                // Keep the user's progress and completed flag, update metadata
                val updated = ep.copy(
                    playbackPositionMs = existing.playbackPositionMs,
                    totalDurationMs = if (existing.totalDurationMs > 0) existing.totalDurationMs else ep.totalDurationMs,
                    lastPlayedAt = existing.lastPlayedAt,
                    isCompleted = existing.isCompleted,
                    isNew = existing.isNew // keep previous isNew status if already seen
                )
                insertOrUpdateEpisode(updated)
            } else {
                insertOrUpdateEpisode(ep)
            }
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateEpisode(episode: Episode)
}
