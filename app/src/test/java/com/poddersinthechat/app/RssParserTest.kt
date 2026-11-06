package com.poddersinthechat.app

import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.parser.RssParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RssParserTest {

    private val parser = RssParser()

    @Test
    fun testParseDuration() {
        assertEquals(3600L, parser.parseDuration("3600"))
        assertEquals(2730L, parser.parseDuration("45:30"))
        assertEquals(3665L, parser.parseDuration("01:01:05"))
        assertEquals(0L, parser.parseDuration(""))
    }

    @Test
    fun testParseDate() {
        val timestamp = parser.parseDate("Wed, 02 Oct 2024 13:00:00 +0000")
        assertTrue(timestamp > 0)

        val timestamp2 = parser.parseDate("02 Oct 2024 13:00:00 GMT")
        assertTrue(timestamp2 > 0)
    }

    @Test
    fun testEpisodeProgressCalculation() {
        val episode = Episode(
            id = "test-1",
            feedUrl = "https://example.com/rss",
            feedTitle = "Test Podcast",
            feedImageUrl = null,
            title = "Episode 1",
            description = "Test description",
            audioUrl = "https://example.com/audio.mp3",
            pubDate = System.currentTimeMillis(),
            pubDateStr = "Today",
            durationSeconds = 1000L,
            durationStr = "16m",
            isNew = true,
            playbackPositionMs = 500_000L, // 50%
            totalDurationMs = 1_000_000L,
            lastPlayedAt = System.currentTimeMillis(),
            isCompleted = false
        )

        assertEquals(0.5f, episode.progressFraction, 0.01f)
        assertTrue(episode.hasListened)
        assertFalse(episode.isCompleted)

        val completedEp = episode.copy(isCompleted = true)
        assertEquals(1.0f, completedEp.progressFraction, 0.01f)
        assertTrue(completedEp.hasListened)
    }

    @Test
    fun testParseRssStream() {
        val sampleRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel>
                <title>The Chat Pod</title>
                <description>A podcast for podders in the chat</description>
                <link>https://example.com</link>
                <itunes:author>Podder Team</itunes:author>
                <itunes:image href="https://example.com/art.jpg" />
                <item>
                  <title>Episode 42: The Beginning</title>
                  <description>Welcome to the show!</description>
                  <guid>ep-42</guid>
                  <pubDate>Wed, 02 Oct 2024 12:00:00 GMT</pubDate>
                  <enclosure url="https://example.com/ep42.mp3" length="12345" type="audio/mpeg" />
                  <itunes:duration>01:15:30</itunes:duration>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val parsed = parser.parse(ByteArrayInputStream(sampleRss.toByteArray()), "https://example.com/rss")

        assertEquals("The Chat Pod", parsed.feed.title)
        assertEquals("Podder Team", parsed.feed.author)
        assertEquals("https://example.com/art.jpg", parsed.feed.imageUrl)
        assertEquals(1, parsed.episodes.size)

        val ep = parsed.episodes[0]
        assertEquals("Episode 42: The Beginning", ep.title)
        assertEquals("ep-42", ep.id)
        assertEquals("https://example.com/ep42.mp3", ep.audioUrl)
        assertEquals(4530L, ep.durationSeconds) // 1h 15m 30s = 4530s
    }
}
