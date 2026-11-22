package com.poddersinthechat.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.poddersinthechat.app.data.model.DownloadedEpisode
import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed

@Database(
    entities = [PodcastFeed::class, Episode::class, DownloadedEpisode::class],
    version = 2,
    // Schemas are exported to app/schemas/ (committed) so migration tests and
    // future auto-migrations can rely on them.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        /**
         * v1 -> v2: adds the `downloads` table for offline playback.
         * All existing data (subscribed feeds, episodes, playback progress) is
         * preserved — the user never has to re-add their subscriptions.
         *
         * The DDL must match exactly what Room generates for the
         * [DownloadedEpisode] entity, otherwise Room's schema assertion fails
         * on the version bump. Keep it in sync if that entity changes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `downloads` (
                        `episodeId` TEXT NOT NULL,
                        `feedUrl` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `downloadedAt` INTEGER NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        PRIMARY KEY(`episodeId`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "podders_database"
                )
                    // Always register explicit migrations so user data survives
                    // upgrades (see AGENTS.md). Destructive fallback stays only
                    // as a last resort for any future version without one.
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
