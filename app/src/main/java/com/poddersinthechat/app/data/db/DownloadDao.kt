package com.poddersinthechat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.poddersinthechat.app.data.model.DownloadedEpisode
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads")
    fun getAllFlow(): Flow<List<DownloadedEpisode>>

    @Query("SELECT * FROM downloads WHERE episodeId = :id LIMIT 1")
    suspend fun getById(id: String): DownloadedEpisode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadedEpisode)

    @Query("DELETE FROM downloads WHERE episodeId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE feedUrl = :feedUrl")
    suspend fun deleteForFeed(feedUrl: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    /** Deletes downloads older than [cutoff] (epoch millis) and returns how many were removed. */
    @Query("DELETE FROM downloads WHERE downloadedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT * FROM downloads WHERE downloadedAt < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<DownloadedEpisode>
}
