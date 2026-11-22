package com.poddersinthechat.app.data.db

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Proves the v1 -> v2 upgrade is a real, data-preserving migration:
 * a database created with the *old* schema (subscribed feeds + episode
 * progress) survives `MIGRATION_1_2` with all its rows intact and the new
 * `downloads` table present.
 *
 * The v1 database is built from the exported schema snapshot
 * (src/test/assets/schema/1.json), not re-created from the current entities,
 * so this exercises the exact migration the app runs on upgrade.
 *
 * The exported schema snapshots live in src/main/assets/schema/ (Robolectric
 * reads them from the app assets). Keep them in sync: run
 * `./gradlew kspDebugKotlin` and copy the JSON files from
 * app/schemas/com.poddersinthechat.app.data.db.AppDatabase/ into
 * src/main/assets/schema/ whenever the Room version changes.
 */
/** Minimal application: keeps PoddersApp.onCreate (WorkManager, DB) out of the test. */
class MigrationTestApp : Application()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = MigrationTestApp::class)
class MigrationTest {

    /**
     * assetsFolder points at the app assets, where schema/1.json and
     * schema/2.json (exported schema snapshots) live.
     */
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        assetsFolder = "schema",
        openFactory = FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `upgrade v1 to v2 keeps subscribed feeds and episode progress`() {
        // 1. Create a database using the OLD (v1) schema
        val oldDb = helper.createDatabase("podders_old", 1)

        // A subscribed feed the user added
        oldDb.execSQL(
            "INSERT INTO `podcast_feeds` (" +
                "`feedUrl`, `title`, `description`, `author`, `imageUrl`, `link`, `lastFetched`, `episodeCount`) " +
                "VALUES (?,?,?,?,?,?,?,?)",
            arrayOf(
                "https://feed.syntax.fm/rss",
                "Syntax",
                "Web dev podcast",
                "Wes & Scott",
                "https://img.example/syntax.png",
                "https://syntax.fm",
                1700000000000L,
                42
            )
        )

        // An episode with partial playback progress + a completed one
        oldDb.execSQL(
            "INSERT INTO `episodes` (" +
                "`id`, `feedUrl`, `feedTitle`, `feedImageUrl`, `title`, `description`, `audioUrl`, " +
                "`pubDate`, `pubDateStr`, `durationSeconds`, `durationStr`, `isNew`, " +
                "`playbackPositionMs`, `totalDurationMs`, `lastPlayedAt`, `isCompleted`) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                "ep-1",
                "https://feed.syntax.fm/rss",
                "Syntax",
                null,
                "Episode 100",
                "desc",
                "https://media.example/ep100.mp3",
                1700000000000L,
                "2026-11-01",
                3600,
                "1:00:00",
                0,
                540000L, // 9 minutes in
                3600000L,
                1700000001000L,
                0
            )
        )
        oldDb.execSQL(
            "INSERT INTO `episodes` (" +
                "`id`, `feedUrl`, `feedTitle`, `feedImageUrl`, `title`, `description`, `audioUrl`, " +
                "`pubDate`, `pubDateStr`, `durationSeconds`, `durationStr`, `isNew`, " +
                "`playbackPositionMs`, `totalDurationMs`, `lastPlayedAt`, `isCompleted`) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf(
                "ep-0",
                "https://feed.syntax.fm/rss",
                "Syntax",
                null,
                "Episode 99",
                "desc",
                "https://media.example/ep99.mp3",
                1699900000000L,
                "2026-10-31",
                3600,
                "1:00:00",
                0,
                3600000L,
                3600000L,
                1699900001000L,
                1 // completed
            )
        )
        oldDb.close()

        // 2. Run the migration and validate the resulting schema against v2
        val newDb = helper.runMigrationsAndValidate(
            "podders_old",
            2,
            /* validateDroppedTables = */ false,
            AppDatabase.MIGRATION_1_2
        )
        try {
            // Feed survived
            val feeds = newDb.query(
                "SELECT feedUrl, title, episodeCount FROM podcast_feeds"
            )
            feeds.use {
                assertEquals(1, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("https://feed.syntax.fm/rss", it.getString(0))
                assertEquals("Syntax", it.getString(1))
                assertEquals(42, it.getInt(2))
            }

            // Episode rows + progress survived
            val episodes = newDb.query(
                "SELECT id, playbackPositionMs, isCompleted FROM episodes ORDER BY id"
            )
            episodes.use {
                assertEquals(2, it.count)
                assertTrue(it.moveToFirst())
                assertEquals("ep-0", it.getString(0))
                assertEquals(3600000L, it.getLong(1))
                assertEquals(1, it.getInt(2))
                assertTrue(it.moveToNext())
                assertEquals("ep-1", it.getString(0))
                assertEquals(540000L, it.getLong(1))
                assertEquals(0, it.getInt(2))
            }

            // New table exists (empty)
            val downloads = newDb.query("SELECT COUNT(*) FROM downloads")
            downloads.use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        } finally {
            newDb.close()
        }
    }
}
