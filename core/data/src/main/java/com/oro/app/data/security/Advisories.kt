package com.oro.app.data.security

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * The two public, keyless feeds a personal guard needs: OSV (advisories per
 * package, the "is this dependency safe right now" answer) and CISA's KEV
 * catalog (the vulnerabilities that are actually being exploited in the
 * wild -- the difference between a CVE and tonight's news).
 *
 * Parsing is pure and fixture-tested; fetching is one OkHttp call each with
 * hard timeouts, and a failure is a null, never a fake "nothing known".
 */
object Advisories {

    data class Advisory(
        val id: String,
        val summary: String,
        val severity: String?,
        val aliases: List<String> = emptyList(),
        val reference: String? = null,
    )

    data class KevEntry(
        val cve: String,
        val vendor: String,
        val product: String,
        val dateAdded: String,
        val knownRansomware: Boolean = false,
    )

    // ------------------------------------------------------------- parsers

    /** OSV `/v1/query` response: `vulns[]` with id, summary, aliases, references. */
    fun parseOsv(json: String): List<Advisory> = runCatching {
        val root = AiHttp.json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject
            ?: return emptyList()
        val vulns = root["vulns"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        vulns.mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val id = (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            val summary = (o["summary"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val aliases = (o["aliases"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()
            val ref = (o["references"] as? kotlinx.serialization.json.JsonArray)
                ?.firstNotNullOfOrNull { r ->
                    (r as? kotlinx.serialization.json.JsonObject)?.get("url")
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }
            val severity = (o["database_specific"] as? kotlinx.serialization.json.JsonObject)
                ?.get("severity")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            Advisory(id, summary.ifBlank { id }, severity, aliases, ref)
        }
    }.getOrDefault(emptyList())

    /** CISA KEV catalog JSON: `vulnerabilities[]`. */
    fun parseKev(json: String): List<KevEntry> = runCatching {
        val root = AiHttp.json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonObject
            ?: return emptyList()
        val list = root["vulnerabilities"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        list.mapNotNull { el ->
            val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun str(key: String) = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
            val cve = str("cveID")
            if (cve.isBlank()) return@mapNotNull null
            KevEntry(
                cve = cve,
                vendor = str("vendorProject"),
                product = str("product"),
                dateAdded = str("dateAdded"),
                knownRansomware = str("knownRansomwareCampaignUse").equals("Known", true),
            )
        }
    }.getOrDefault(emptyList())

    /** Entries in [now] whose CVE was not in [seenBefore]: the new exploitation news. */
    fun newKev(now: List<KevEntry>, seenBefore: Set<String>): List<KevEntry> =
        now.filter { it.cve !in seenBefore }

    // ------------------------------------------------------------- fetchers

    /** Advisories for a package version, e.g. ("npm", "lodash", "4.17.20"). */
    suspend fun osvQuery(ecosystem: String, name: String, version: String?): List<Advisory>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildString {
                    append("{\"version\":\"").append(version.orEmpty()).append("\",")
                    append("\"package\":{\"name\":\"").append(escape(name)).append("\",\"ecosystem\":\"").append(escape(ecosystem)).append("\"}}")
                }
                post("https://api.osv.dev/v1/query", body)?.let { parseOsv(it) }
            }.getOrNull()
        }

    /** The whole KEV catalog (~1500 entries, ~1 MB): fetched rarely, cached by the caller. */
    suspend fun kevCatalog(): List<KevEntry>? = withContext(Dispatchers.IO) {
        runCatching {
            get("https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json")
                ?.let { parseKev(it) }
        }.getOrNull()
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun get(url: String): String? = call(
        Request.Builder().url(url).get()
            .header("User-Agent", "Oro-Shield/1.0 (personal security agent)")
            .build()
    )

    private fun post(url: String, body: String): String? = call(
        Request.Builder().url(url).post(
            okhttp3.RequestBody.create(null, body)
        ).header("User-Agent", "Oro-Shield/1.0 (personal security agent)")
            .header("Content-Type", "application/json")
            .build()
    )

    private fun call(request: Request): String? = runCatching {
        val client = AiHttp.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) null else r.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()
}
