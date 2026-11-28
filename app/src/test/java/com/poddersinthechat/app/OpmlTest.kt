package com.poddersinthechat.app

import com.poddersinthechat.app.data.model.PodcastFeed
import com.poddersinthechat.app.data.parser.OpmlExporter
import com.poddersinthechat.app.data.parser.OpmlFeedEntry
import com.poddersinthechat.app.data.parser.OpmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class OpmlTest {

    private fun parseOpml(xml: String): List<OpmlFeedEntry> =
        OpmlParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    private fun feed(url: String, title: String = url, link: String = "") = PodcastFeed(
        feedUrl = url,
        title = title,
        link = link
    )

    @Test
    fun testParseFlattensNestedOutlines() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
                <head><title>Test subscriptions</title></head>
                <body>
                    <outline text="Technology">
                        <outline text="Syntax" xmlUrl="https://feed.syntax.fm/rss"/>
                        <outline text="The Changelog" xmlUrl="https://changelog.com/podcast/feed"/>
                        <outline text="Sub-group">
                            <outline text="BBC News" xmlUrl="https://podcasts.files.bbci.co.uk/p02nq0gn.rss"/>
                        </outline>
                    </outline>
                    <outline text="Rad Crew" xmlUrl="https://feed.radcrew.net/radcrew"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parseOpml(xml)
        assertEquals(4, entries.size)
        assertEquals(
            listOf(
                "https://feed.syntax.fm/rss",
                "https://changelog.com/podcast/feed",
                "https://podcasts.files.bbci.co.uk/p02nq0gn.rss",
                "https://feed.radcrew.net/radcrew"
            ),
            entries.map { it.url }
        )
        assertEquals("Syntax", entries[0].title)
        assertEquals("Rad Crew", entries[3].title)
    }

    @Test
    fun testParseIgnoresOutlinesWithoutXmlUrl() {
        val xml = """
            <opml version="2.0">
                <body>
                    <outline text="Category header only"/>
                    <outline text="Not a URL" xmlUrl="ftp://nope.example/feed"/>
                    <outline text="Empty url" xmlUrl="   "/>
                    <outline text="Real" xmlUrl="https://example.com/feed"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parseOpml(xml)
        assertEquals(1, entries.size)
        assertEquals(OpmlFeedEntry("Real", "https://example.com/feed"), entries[0])
    }

    @Test
    fun testParseTitleFallsBackToUrl() {
        val xml = """
            <opml version="2.0">
                <body>
                    <outline text="" xmlUrl="https://example.com/feed"/>
                </body>
            </opml>
        """.trimIndent()

        val entries = parseOpml(xml)
        assertEquals("https://example.com/feed", entries[0].title)
    }

    @Test
    fun testParseEmptyBody() {
        val xml = """
            <opml version="2.0">
                <head><title>Empty</title></head>
                <body/>
            </opml>
        """.trimIndent()

        assertTrue(parseOpml(xml).isEmpty())
    }

    @Test
    fun testParseMalformedXmlThrows() {
        try {
            parseOpml("<opml><body><outline>")
            fail("Expected an exception for malformed XML")
        } catch (e: Exception) {
            // expected — caller surfaces a friendly error
        }
    }

    @Test
    fun testExportProducesWellFormedOpml() {
        val feeds = listOf(
            feed("https://feed.syntax.fm/rss", "Syntax", "https://syntax.fm"),
            feed("https://changelog.com/podcast/feed", "The Changelog")
        )

        val opml = OpmlExporter.build(feeds)
        assertTrue(opml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(opml.contains("version=\"2.0\""))
        assertTrue(opml.contains("""xmlUrl="https://feed.syntax.fm/rss""""))
        assertTrue(opml.contains("""htmlUrl="https://syntax.fm""""))
        // Feed without a link gets no htmlUrl
        val changelogLine = opml.lines().first { it.contains("changelog.com/podcast/feed") }
        assertTrue(changelogLine.contains("""xmlUrl="https://changelog.com/podcast/feed""""))
        assertTrue(changelogLine.endsWith("/>"))
        assertTrue(!changelogLine.contains("htmlUrl"))
    }

    @Test
    fun testExportRoundTripsThroughParser() {
        val feeds = listOf(
            feed("https://feed.syntax.fm/rss", "Syntax", "https://syntax.fm"),
            feed("https://feed.radcrew.net/radcrew", "Rad Crew Podcast"),
            feed("https://example.com/feed", "AT&T & <The Other> \"Show\"")
        )

        val entries = parseOpml(OpmlExporter.build(feeds))
        assertEquals(feeds.map { it.title }, entries.map { it.title })
        assertEquals(feeds.map { it.feedUrl }, entries.map { it.url })
    }
}
