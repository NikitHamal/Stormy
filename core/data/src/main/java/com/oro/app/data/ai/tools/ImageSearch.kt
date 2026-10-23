package com.oro.app.data.ai.tools

import com.oro.app.data.ai.AiHttp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Real photos that match a page's content, through Openverse (keyless,
 * openly licensed images, mostly Flickr).
 *
 * Models used to invent `images.unsplash.com/photo-…` ids, which 404, then
 * fall back to random picsum photos: a skyscraper for a coffee shop's hero.
 * The prompt teaches `web_fetch` on Openverse's search URL instead; the
 * reply is condensed here to what a page needs -- the image URL, its size and
 * the credit -- because the raw JSON is long and mostly irrelevant.
 */
object ImageSearch {

    const val ENDPOINT = "https://api.openverse.org/v1/images/"

    fun isOpenverse(url: String): Boolean = url.contains("api.openverse.org/v1/images", ignoreCase = true)

    /** The search results as numbered lines, or null when [json] is not an Openverse result page. */
    fun format(json: String, max: Int = 12): String? {
        val root = runCatching { AiHttp.json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        val results = (root["results"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
        if (results.isEmpty()) return "No photos found. Try a broader or different subject (one or two plain words)."
        return buildString {
            append("Photos (openly licensed; credit the creator in a small caption or the footer):\n")
            results.take(max).forEachIndexed { i, r ->
                val url = r.str("url") ?: return@forEachIndexed
                append(i + 1).append(". ").append(url)
                val w = (r["width"] as? JsonPrimitive)?.intOrNull
                val h = (r["height"] as? JsonPrimitive)?.intOrNull
                if (w != null && h != null) append(" (").append(w).append('x').append(h).append(')')
                r.str("title")?.takeIf { it.isNotBlank() }?.let { append(" — \"").append(it.take(80)).append('"') }
                r.str("creator")?.takeIf { it.isNotBlank() }?.let { append(" by ").append(it.take(60)) }
                val license = r.str("license")?.uppercase()
                if (license != null) {
                    append(", CC ").append(license)
                    r.str("license_version")?.let { append(' ').append(it) }
                }
                append('\n')
            }
            append("Use the URL as is in <img src> with width, height, loading=\"lazy\" and alt text describing the photo.")
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
