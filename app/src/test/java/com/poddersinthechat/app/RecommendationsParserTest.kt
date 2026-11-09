package com.poddersinthechat.app

import com.poddersinthechat.app.data.parser.RecommendationsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class RecommendationsParserTest {

    @Test
    fun `parses entries and skips nested sources and unknown fields`() {
        val json = """
            [
              {
                "popularity": 196,
                "feedId": 499394,
                "podcastGuid": "82421610-30bc-5211-9fee-5b788bc86012",
                "medium": null,
                "title": "The Diary Of A CEO with Steven Bartlett",
                "url": "https://rss2.flightcast.com/xmsftuzjjykcmqwolaqn6mdn",
                "image": "https://assets.flightcast.com/img.png",
                "sourceFeedId": 33263,
                "sourceFeedUrl": "https://rss.buzzsprout.com/1039483.rss",
                "sources": [
                  { "feedId": 33263, "url": "https://rss.buzzsprout.com/1039483.rss", "host": "buzzsprout.com" },
                  { "feedId": 210307, "url": "https://anchor.fm/s/x/rss", "host": "anchor.fm" }
                ]
              },
              {
                "feedId": 7,
                "title": "Rad Crew",
                "url": "https://feed.radcrew.net/radcrew",
                "image": null
              }
            ]
        """.trimIndent()

        val parsed = RecommendationsParser.parse(json)

        assertEquals(2, parsed.size)

        assertEquals("The Diary Of A CEO with Steven Bartlett", parsed[0].title)
        assertEquals(499394, parsed[0].feedId)
        assertEquals(196, parsed[0].popularity)
        assertEquals("https://rss2.flightcast.com/xmsftuzjjykcmqwolaqn6mdn", parsed[0].url)
        assertEquals("https://assets.flightcast.com/img.png", parsed[0].image)

        assertEquals("Rad Crew", parsed[1].title)
        assertNull(parsed[1].image) // null image → null
        assertEquals(0, parsed[1].popularity) // missing → default
        assertEquals("feed.radcrew.net", parsed[1].host)
    }

    @Test
    fun `decodes string escapes and unicode`() {
        // JSON text contains literal escapes: \" \\ \uXXXX
        val json = "[\n" +
            "  { \"feedId\": 1, " +
            "    \"title\": \"Podcast \\\"A \\\\ B\\\" \\u2014 \\u00e5\\u00f8\\u00e6 \\u00e4nda\", " +
            "    \"url\": \"https://x.example/rss\", " +
            "    \"image\": \"https://x.example/i.png\" }\n" +
            "]"

        val parsed = RecommendationsParser.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("Podcast \"A \\ B\" — åøæ ända", parsed[0].title)
    }

    @Test
    fun `skips entries with missing or empty title or url`() {
        val json = """
            [
              { "feedId": 1, "url": "https://a.example/rss" },
              { "feedId": 2, "title": "", "url": "https://b.example/rss" },
              { "feedId": 3, "title": "No Url" },
              { "feedId": 4, "title": "Good", "url": "https://d.example/rss" }
            ]
        """.trimIndent()

        val parsed = RecommendationsParser.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("Good", parsed[0].title)
    }

    @Test
    fun `handles empty array`() {
        assertTrue(RecommendationsParser.parse("[]").isEmpty())
    }

    @Test
    fun `handles whitespace between tokens`() {
        val json = """
            [
               {   "feedId" :  5  ,  "title":"Spaced"  ,   "url":"https://s.example/rss"   }
            ]
        """.trimIndent()

        val parsed = RecommendationsParser.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("Spaced", parsed[0].title)
        assertEquals(5, parsed[0].feedId)
    }

    /**
     * Integration test against the real catalog file (~24 MB) kept in the
     * project root by get-recommended.sh. Skipped automatically when absent.
     */
    @Test
    fun `parses the real catalog file`() {
        val file = File("../recommendations.json")
        assumeTrue("recommendations.json not present, skipping", file.exists())

        val parsed = RecommendationsParser.parse(file.readText())

        assertTrue("expected thousands of entries, got ${parsed.size}", parsed.size > 10_000)
        assertTrue(parsed.all { it.title.isNotBlank() })
        assertTrue(parsed.all { it.url.startsWith("http") })
        assertTrue(parsed.any { it.image != null })
    }
}
