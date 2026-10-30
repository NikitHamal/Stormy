package com.oro.app.data.security

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * The fresh part of the Shield's knowledge: the latest security news, pulled
 * from public RSS feeds with no account and no key. Training data is always
 * stale; an agent that answers "what is being exploited this week" from memory
 * is guessing. The feeds are evidence of *reporting*, not of an incident --
 * the guard says so and still verifies with KEV/OSV and the artifact.
 *
 * Parsing is pure and fixture-tested; fetching is four parallel HTTPS GETs
 * with hard timeouts and a five-minute cache.
 */
object SecurityNews {

    data class Item(
        val source: String,
        val title: String,
        val link: String,
        val date: String?,
        val epochMillis: Long,
        val summary: String,
    )

    /**
     * Security desks that publish a stable RSS feed and are readable without
     * a key. CISA's full advisory feed is five megabytes for one page, so the
     * KEV tool remains the CISA route and this list stays fast.
     */
    val sources = listOf(
        "The Hacker News" to "https://feeds.feedburner.com/TheHackersNews",
        "BleepingComputer" to "https://www.bleepingcomputer.com/feed/",
        "Krebs on Security" to "https://krebsonsecurity.com/feed/",
        "SANS ISC" to "https://isc.sans.edu/rssfeed.xml",
    )

    private data class Cached(val at: Long, val items: List<Item>)
    private val cache = object : LinkedHashMap<String, Cached>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 8
    }
    private const val CACHE_MS = 5 * 60_000L

    /** Latest items across every source, newest first; [topic] filters title and summary words. */
    suspend fun latest(topic: String? = null, limit: Int = 12): List<Item> = coroutineScope {
        val key = topic.orEmpty().trim().lowercase(Locale.ROOT)
        synchronized(cache) {
            cache[key]?.takeIf { System.currentTimeMillis() - it.at < CACHE_MS }?.let { return@coroutineScope it.items.take(limit) }
        }
        val fetched = sources.map { (name, url) ->
            async {
                runCatching {
                    val xml = get(url) ?: return@runCatching emptyList()
                    parseRss(xml, name)
                }.getOrDefault(emptyList())
            }
        }.awaitAll()
        var all = fetched.flatten().sortedByDescending { it.epochMillis }
        if (key.isNotBlank()) {
            val words = key.split(Regex("\\s+")).filter { it.length >= 3 }
            all = all.filter { item ->
                val hay = (item.title + " " + item.summary).lowercase(Locale.ROOT)
                words.isEmpty() || words.any { it in hay }
            }
        }
        val result = all.take(80)
        synchronized(cache) { cache[key] = Cached(System.currentTimeMillis(), result) }
        result.take(limit)
    }

    /** One feed's RSS 2.0 or Atom entries into [Item]s. */
    fun parseRss(xml: String, source: String): List<Item> {
        val blocks = Regex("(?is)<item\\b[^>]*>(.*?)</item>").findAll(xml).map { it.groupValues[1] }.toList()
            .ifEmpty { Regex("(?is)<entry\\b[^>]*>(.*?)</entry>").findAll(xml).map { it.groupValues[1] }.toList() }
        return blocks.mapNotNull { block ->
            val title = tag(block, "title")?.let(::cleanText)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = tag(block, "link")?.let(::cleanText)?.takeIf { it.startsWith("http") }
                ?: Regex("""(?is)<link[^>]+href=["']([^"']+)["']""").find(block)?.groupValues?.get(1)
                ?: return@mapNotNull null
            val rawDate = tag(block, "pubDate") ?: tag(block, "published") ?: tag(block, "updated") ?: tag(block, "dc:date")
            val date = rawDate?.let(::cleanText)
            val summary = (tag(block, "description") ?: tag(block, "summary") ?: tag(block, "content"))
                ?.let(::cleanText)?.take(400).orEmpty()
            Item(
                source = source,
                title = title.take(220),
                link = link,
                date = date,
                epochMillis = parseDate(date),
                summary = summary,
            )
        }.distinctBy { it.link }
    }

    private fun tag(block: String, name: String): String? =
        Regex("(?is)<" + Regex.escape(name) + "\\b[^>]*>(.*?)</" + Regex.escape(name) + ">")
            .find(block)?.groupValues?.get(1)

    private fun cleanText(raw: String): String {
        var text = raw.trim()
        text = Regex("(?is)<!\\[CDATA\\[(.*?)]]>").find(text)?.groupValues?.get(1) ?: text
        text = text.replace(Regex("(?is)<[^>]+>"), " ")
        text = decodeEntities(text)
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeEntities(text: String): String {
        var out = text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
        out = Regex("""&#x([0-9a-fA-F]+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        out = Regex("""&#(\d+);""").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        return out
    }

    private fun parseDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val value = raw.trim()
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
            ?: 0L
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val client = AiHttp.client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Oro-Shield/1.0 (personal security agent; RSS)")
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .get()
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val length = r.body?.contentLength() ?: -1
                if (length > 2_000_000) return@use null
                r.body?.string()?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }
}
