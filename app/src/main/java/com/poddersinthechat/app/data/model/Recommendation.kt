package com.poddersinthechat.app.data.model

/**
 * A podcast entry from the PodcastIndex recommendations catalog
 * (https://public.podcastindex.org/recommendations.json).
 */
data class Recommendation(
    val feedId: Int,
    val title: String,
    val url: String,
    val image: String?,
    val popularity: Int
) {
    /** Short host label for display, e.g. "rss.buzzsprout.com" */
    val host: String
        get() = url.substringAfter("://").substringBefore("/").substringBefore(":")
}
