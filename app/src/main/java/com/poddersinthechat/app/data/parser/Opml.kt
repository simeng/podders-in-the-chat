package com.poddersinthechat.app.data.parser

import com.poddersinthechat.app.data.model.PodcastFeed
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A podcast entry read from (or written to) an OPML subscription file.
 *
 * OPML 2.0 is the de-facto format for moving subscriptions between podcast
 * apps: each feed is an `<outline text="Title" xmlUrl="https://…"/>` element,
 * optionally nested in category groups.
 */
data class OpmlFeedEntry(
    val title: String,
    val url: String
)

object OpmlParser {

    /**
     * Parses an OPML stream and returns all feed entries, in document order.
     * Nested outlines (category groupings) are flattened; outlines without a
     * `xmlUrl` attribute (e.g. category headers) are ignored.
     *
     * @throws Exception if the stream is not well-formed XML.
     */
    fun parse(inputStream: InputStream): List<OpmlFeedEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        doc.documentElement.normalize()

        val outlineNodes = doc.getElementsByTagName("outline")
        val entries = mutableListOf<OpmlFeedEntry>()
        for (i in 0 until outlineNodes.length) {
            val outline = outlineNodes.item(i) as? Element ?: continue
            val xmlUrl = outline.getAttribute("xmlUrl")
            if (xmlUrl.isBlank()) continue
            val url = xmlUrl.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            val title = outline.getAttribute("text").trim().ifEmpty { url }
            entries += OpmlFeedEntry(title = title, url = url)
        }
        return entries
    }
}

object OpmlExporter {

    /**
     * Builds a complete OPML 2.0 document from the given feeds.
     */
    fun build(feeds: List<PodcastFeed>): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("""<opml version="2.0">""")
        sb.appendLine("    <head>")
        sb.appendLine("        <title>Podders in the Chat subscriptions</title>")
        sb.appendLine("    </head>")
        sb.appendLine("    <body>")
        for (feed in feeds) {
            sb.append("        <outline text=\"")
            sb.append(escapeXmlAttribute(feed.title))
            sb.append("\" title=\"")
            sb.append(escapeXmlAttribute(feed.title))
            sb.append("\" type=\"rss\" xmlUrl=\"")
            sb.append(escapeXmlAttribute(feed.feedUrl))
            sb.append("\"")
            if (feed.link.isNotBlank()) {
                sb.append(" htmlUrl=\"").append(escapeXmlAttribute(feed.link)).append("\"")
            }
            sb.appendLine("/>")
        }
        sb.appendLine("    </body>")
        sb.append("</opml>")
        return sb.toString()
    }

    private fun escapeXmlAttribute(value: String): String = buildString(value.length) {
        for (c in value) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\n' -> append("&#10;")
                '\r' -> append("&#13;")
                else -> append(c)
            }
        }
    }
}
