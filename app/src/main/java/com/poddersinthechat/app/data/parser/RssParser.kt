package com.poddersinthechat.app.data.parser

import com.poddersinthechat.app.data.model.Episode
import com.poddersinthechat.app.data.model.PodcastFeed
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedPodcast(
    val feed: PodcastFeed,
    val episodes: List<Episode>
)

class RssParser {

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH)
    )

    fun parse(inputStream: InputStream, feedUrl: String): ParsedPodcast {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(inputStream)
        doc.documentElement.normalize()

        val channelNodes = doc.getElementsByTagName("channel")
        val channelElem = if (channelNodes.length > 0) channelNodes.item(0) as? Element else doc.documentElement

        val channelTitle = getDirectChildText(channelElem, "title")
        val channelDesc = stripHtml(getDirectChildText(channelElem, "description").ifEmpty {
            getDirectChildText(channelElem, "itunes:summary")
        })
        val channelAuthor = getDirectChildText(channelElem, "itunes:author").ifEmpty {
            getDirectChildText(channelElem, "author")
        }.ifEmpty {
            getDirectChildText(channelElem, "managingEditor")
        }
        val channelLink = getDirectChildText(channelElem, "link")

        var channelImage: String? = null
        // Check itunes:image href
        val itunesImages = channelElem?.getElementsByTagName("itunes:image")
        if (itunesImages != null && itunesImages.length > 0) {
            val elem = itunesImages.item(0) as? Element
            val href = elem?.getAttribute("href")
            if (!href.isNullOrEmpty()) channelImage = href
        }
        // Fallback to <image><url>
        if (channelImage.isNullOrEmpty()) {
            val imageNodes = channelElem?.getElementsByTagName("image")
            if (imageNodes != null && imageNodes.length > 0) {
                val imgElem = imageNodes.item(0) as? Element
                val url = getDirectChildText(imgElem, "url")
                if (url.isNotEmpty()) channelImage = url
            }
        }

        val episodes = mutableListOf<Episode>()
        val itemNodes = doc.getElementsByTagName("item")
        val now = System.currentTimeMillis()

        for (i in 0 until itemNodes.length) {
            val itemNode = itemNodes.item(i)
            if (itemNode.nodeType != Node.ELEMENT_NODE) continue
            val itemElem = itemNode as Element

            val title = getDirectChildText(itemElem, "title")
            val desc = stripHtml(
                getDirectChildText(itemElem, "description").ifEmpty {
                    getDirectChildText(itemElem, "itunes:summary")
                }.ifEmpty {
                    getDirectChildText(itemElem, "content:encoded")
                }
            )

            // Audio enclosure URL
            var audioUrl = ""
            val enclosures = itemElem.getElementsByTagName("enclosure")
            for (j in 0 until enclosures.length) {
                val enc = enclosures.item(j) as? Element ?: continue
                val url = enc.getAttribute("url")
                val type = enc.getAttribute("type").lowercase(Locale.ROOT)
                if (url.isNotEmpty()) {
                    if (type.contains("audio") || type.contains("mpeg") || type.contains("mp3") ||
                        url.contains(".mp3") || url.contains(".m4a") || url.contains(".aac") || audioUrl.isEmpty()
                    ) {
                        audioUrl = url
                        break
                    }
                }
            }

            // Fallback to media:content
            if (audioUrl.isEmpty()) {
                val mediaContents = itemElem.getElementsByTagName("media:content")
                for (j in 0 until mediaContents.length) {
                    val mc = mediaContents.item(j) as? Element ?: continue
                    val url = mc.getAttribute("url")
                    val type = mc.getAttribute("type").lowercase(Locale.ROOT)
                    if (url.isNotEmpty() && (type.contains("audio") || url.contains(".mp3"))) {
                        audioUrl = url
                        break
                    }
                }
            }

            if (title.isEmpty() || audioUrl.isEmpty()) continue

            val guid = getDirectChildText(itemElem, "guid").ifEmpty {
                "${feedUrl}_${audioUrl.hashCode()}"
            }

            val pubDateStr = getDirectChildText(itemElem, "pubDate").ifEmpty {
                getDirectChildText(itemElem, "dc:date")
            }
            val pubDate = parseDate(pubDateStr)

            val rawDuration = getDirectChildText(itemElem, "itunes:duration")
            val durationSecs = parseDuration(rawDuration)
            val formattedDuration = if (rawDuration.isNotEmpty()) {
                formatDurationPretty(rawDuration, durationSecs)
            } else if (durationSecs > 0) {
                formatDurationSeconds(durationSecs)
            } else {
                ""
            }

            // Episode specific artwork if available
            var itemImage: String? = null
            val itemItunesImages = itemElem.getElementsByTagName("itunes:image")
            if (itemItunesImages.length > 0) {
                val elem = itemItunesImages.item(0) as? Element
                itemImage = elem?.getAttribute("href")
            }

            // Mark as new if published within the last 48 hours
            val isRecent = pubDate > 0 && (now - pubDate) < (48 * 60 * 60 * 1000L)

            episodes.add(
                Episode(
                    id = guid,
                    feedUrl = feedUrl,
                    feedTitle = channelTitle.ifEmpty { "Podcast" },
                    feedImageUrl = itemImage ?: channelImage,
                    title = title,
                    description = desc,
                    audioUrl = audioUrl,
                    pubDate = pubDate,
                    pubDateStr = pubDateStr,
                    durationSeconds = durationSecs,
                    durationStr = formattedDuration,
                    isNew = isRecent,
                    playbackPositionMs = 0L,
                    totalDurationMs = durationSecs * 1000L,
                    lastPlayedAt = 0L,
                    isCompleted = false
                )
            )
        }

        val feed = PodcastFeed(
            feedUrl = feedUrl,
            title = channelTitle.ifEmpty { "Untitled Podcast" },
            description = channelDesc,
            author = channelAuthor,
            imageUrl = channelImage,
            link = channelLink,
            lastFetched = now,
            episodeCount = episodes.size
        )

        return ParsedPodcast(feed, episodes)
    }

    private fun getDirectChildText(parent: Element?, tagName: String): String {
        if (parent == null) return ""
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val nodeName = node.nodeName
                if (nodeName.equals(tagName, ignoreCase = true) ||
                    (tagName.contains(":") && nodeName.substringAfter(":").equals(tagName.substringAfter(":"), ignoreCase = true))
                ) {
                    return node.textContent?.trim() ?: ""
                }
            }
        }
        return ""
    }

    fun parseDate(rawDate: String): Long {
        if (rawDate.isEmpty()) return 0L
        val trimmed = rawDate.trim()
        for (format in dateFormats) {
            try {
                val parsed = format.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    fun parseDuration(duration: String): Long {
        val trimmed = duration.trim()
        if (trimmed.isEmpty()) return 0L
        return try {
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":")
                var seconds = 0L
                for (part in parts) {
                    seconds = seconds * 60 + (part.toLongOrNull() ?: 0L)
                }
                seconds
            } else {
                trimmed.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun formatDurationPretty(raw: String, secs: Long): String {
        return if (raw.contains(":")) {
            val parts = raw.split(":")
            if (parts.size == 3) {
                "${parts[0]}h ${parts[1]}m"
            } else if (parts.size == 2) {
                "${parts[0]}m ${parts[1]}s"
            } else {
                raw
            }
        } else {
            formatDurationSeconds(secs)
        }
    }

    private fun formatDurationSeconds(secs: Long): String {
        if (secs <= 0) return ""
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
