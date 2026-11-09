package com.poddersinthechat.app.data.parser

import com.poddersinthechat.app.data.model.Recommendation

/**
 * Minimal streaming tokenizer for the PodcastIndex recommendations catalog —
 * a ~24 MB JSON array with ~25k entries, each carrying a nested `sources`
 * array. Instead of building a full JSON DOM (which would retain every nested
 * source entry in memory), this parser walks the text once and extracts only
 * the scalar fields the app needs.
 *
 * Expected shape (unknown fields/keys are skipped):
 * ```json
 * [
 *   {
 *     "popularity": 196,
 *     "feedId": 499394,
 *     "podcastGuid": "…",
 *     "title": "Some Podcast",
 *     "url": "https://example.com/rss",
 *     "image": "https://example.com/art.png",   // may be null or missing
 *     "sources": [ { "feedId": 1, "url": "…", "host": "…" }, … ]
 *   },
 *   …
 * ]
 * ```
 */
object RecommendationsParser {

    fun parse(text: String): List<Recommendation> {
        val p = Parser(text)
        p.skipWhitespace()
        p.expect('[')

        val result = ArrayList<Recommendation>(32_000)
        var first = true
        while (true) {
            p.skipWhitespace()
            if (p.peek() == ']') break
            if (!first) {
                p.expect(',')
            }
            first = false
            p.skipWhitespace()
            if (p.peek() == '{') {
                parseEntry(p)?.let { result.add(it) }
            } else {
                skipValue(p)
            }
        }
        return result
    }

    private fun parseEntry(p: Parser): Recommendation? {
        p.expect('{')
        var title: String? = null
        var url: String? = null
        var image: String? = null
        var feedId = 0
        var popularity = 0

        while (true) {
            p.skipWhitespace()
            val c = p.peek()
            if (c == '}') {
                p.consume()
                break
            }
            if (c == ',') {
                p.consume()
                continue
            }
            val key = readString(p)
            p.skipWhitespace()
            p.expect(':')
            p.skipWhitespace()
            when (key) {
                "title" -> title = parseStringOrNull(p)
                "url" -> url = parseStringOrNull(p)
                "image" -> image = parseStringOrNull(p)
                "feedId" -> feedId = parseIntOrSkip(p)
                "popularity" -> popularity = parseIntOrSkip(p)
                else -> skipValue(p)
            }
        }

        if (title.isNullOrEmpty() || url.isNullOrEmpty()) return null
        return Recommendation(feedId, title, url, image, popularity)
    }

    private fun parseStringOrNull(p: Parser): String? {
        if (p.peek() != '"') {
            skipValue(p)
            return null
        }
        return readString(p).ifEmpty { null }
    }

    private fun parseIntOrSkip(p: Parser): Int {
        val c = p.peek()
        if (c == '-' || (c in '0'..'9')) {
            val start = p.pos
            while (p.pos < p.text.length) {
                val ch = p.text[p.pos]
                if (ch == '-' || ch == '.' || ch == 'e' || ch == 'E' || (ch in '0'..'9')) {
                    p.consume()
                } else {
                    break
                }
            }
            return p.text.substring(start, p.pos).toIntOrNull() ?: 0
        }
        skipValue(p)
        return 0
    }

    /** Skips any JSON value (object, array, string, number, true/false/null) without retaining it. */
    private fun skipValue(p: Parser) {
        when (p.peek()) {
            '{' -> {
                p.consume() // the opening brace itself
                var depth = 1
                while (depth > 0) {
                    val ch = p.consume()
                    when (ch) {
                        '"' -> skipStringBody(p)
                        '{' -> depth++
                        '}' -> depth--
                    }
                }
            }
            '[' -> {
                p.consume() // the opening bracket itself
                var depth = 1
                while (depth > 0) {
                    val ch = p.consume()
                    when (ch) {
                        '"' -> skipStringBody(p)
                        '[' -> depth++
                        ']' -> depth--
                    }
                }
            }
            '"' -> skipStringBody(p)
            else -> {
                // number / true / false / null → consume up to the next delimiter
                while (p.pos < p.text.length) {
                    val ch = p.text[p.pos]
                    if (ch == ',' || ch == '}' || ch == ']' || ch.isWhitespace()) break
                    p.consume()
                }
            }
        }
    }

    /** Consumes the opening quote and returns the decoded string. */
    private fun readString(p: Parser): String {
        val sb = StringBuilder(64)
        p.consume() // opening quote
        while (true) {
            val ch = p.consume()
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> appendEscape(p, sb)
                else -> sb.append(ch)
            }
        }
    }

    /** Consumes a quoted string starting at the current quote, returning nothing. */
    private fun skipStringBody(p: Parser) {
        p.consume() // opening quote
        while (true) {
            val ch = p.consume()
            when (ch) {
                '"' -> return
                '\\' -> p.consume()
            }
        }
    }

    private fun appendEscape(p: Parser, sb: StringBuilder) {
        when (val esc = p.consume()) {
            '"' -> sb.append('"')
            '\\' -> sb.append('\\')
            '/' -> sb.append('/')
            'b' -> sb.append('\b')
            'f' -> sb.append('\u000C')
            'n' -> sb.append('\n')
            'r' -> sb.append('\r')
            't' -> sb.append('\t')
            'u' -> {
                val hex = p.text.substring(p.pos, p.pos + 4)
                p.skip(4)
                sb.append(hex.toInt(16).toChar())
            }
            else -> sb.append(esc)
        }
    }

    private class Parser(val text: String) {
        var pos = 0
            private set

        fun peek(): Char = text[pos]
        fun consume(): Char = text[pos++]
        fun skip(n: Int) {
            pos += n
        }
        fun expect(c: Char) {
            val actual = text[pos]
            if (actual != c) {
                throw IllegalArgumentException("Malformed JSON: expected '$c' but found '$actual' at offset $pos")
            }
            pos++
        }
        fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
