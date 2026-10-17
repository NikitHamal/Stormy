package com.oro.app.data.deploy

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Where a project was last deployed; kept in `.oro/deploy.json`. */
@Serializable
data class DeployState(
    val netlifySiteId: String? = null,
    val netlifyUrl: String? = null,
    val githubRepo: String? = null,
    val githubUrl: String? = null
)

enum class DeployTarget(val label: String) { NETLIFY("Netlify"), GITHUB_PAGES("GitHub Pages") }

/**
 * One-tap static deploys of the open project.
 *
 * Netlify takes the site as a single zip (`POST /sites/{id}/deploys`).
 * GitHub Pages gets a `gh-pages` branch written through the Git Data API --
 * blobs, one tree, one commit -- so no git binary or clone is needed, then
 * Pages is switched on for that branch. Both publish exactly the project's
 * files minus `.oro/`, `.git/` and `node_modules/`.
 */
class Deployer(private val projectRoot: File) {

    private val http = AiHttp.client.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS).writeTimeout(120, TimeUnit.SECONDS).callTimeout(300, TimeUnit.SECONDS).build()
    private val stateFile get() = File(projectRoot, ".oro/deploy.json")

    fun state(): DeployState = runCatching {
        AiHttp.json.decodeFromString(DeployState.serializer(), stateFile.readText())
    }.getOrDefault(DeployState())

    private fun save(s: DeployState) = runCatching {
        stateFile.parentFile?.mkdirs(); stateFile.writeText(AiHttp.json.encodeToString(DeployState.serializer(), s))
    }

    /** Project files to publish, as (relative path, file). */
    fun files(): List<Pair<String, File>> = projectRoot.walkTopDown()
        .onEnter { d -> d == projectRoot || d.name !in SKIP_DIRS }
        .filter { it.isFile }
        .map { it.relativeTo(projectRoot).invariantSeparatorsPath to it }
        .take(MAX_FILES)
        .toList()

    // ------------------------------------------------------------- Netlify

    suspend fun deployNetlify(token: String, siteName: String, progress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "Add a Netlify personal access token first." }
            val files = files()
            require(files.any { it.first == "index.html" }) { "There is no index.html at the project root to publish." }
            var s = state()
            val siteId = s.netlifySiteId ?: run {
                progress("Creating the site…")
                val created = netlify(token, "POST", "sites", buildJsonObject { put("name", slug(siteName)) }.toString())
                    ?: netlify(token, "POST", "sites", "{}") // name taken: let Netlify pick one
                    ?: throw IOException("Netlify would not create a site")
                created.str("id") ?: throw IOException("Netlify returned no site id")
            }
            progress("Uploading ${files.size} files…")
            val zip = ByteArrayOutputStream().also { bytes ->
                ZipOutputStream(bytes).use { z -> files.forEach { (path, f) -> z.putNextEntry(ZipEntry(path)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }
            }.toByteArray()
            val req = Request.Builder().url("$NETLIFY/sites/$siteId/deploys")
                .header("Authorization", "Bearer ${token.trim()}")
                .post(zip.toRequestBody("application/zip".toMediaType())).build()
            val deploy = http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw IOException("Netlify deploy failed (${r.code}): ${body.take(200)}")
                AiHttp.json.parseToJsonElement(body) as JsonObject
            }
            val url = deploy.str("ssl_url") ?: deploy.str("url") ?: deploy.str("deploy_ssl_url")
                ?: throw IOException("Netlify returned no URL")
            s = s.copy(netlifySiteId = siteId, netlifyUrl = url)
            save(s)
            url
        }
    }

    private fun netlify(token: String, method: String, path: String, json: String): JsonObject? {
        val req = Request.Builder().url("$NETLIFY/$path").header("Authorization", "Bearer ${token.trim()}")
            .method(method, json.toRequestBody(AiHttp.jsonMediaType)).build()
        return http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            when {
                r.code == 401 -> throw IOException("Netlify rejected the token (401)")
                r.isSuccessful -> AiHttp.json.parseToJsonElement(body) as? JsonObject
                else -> null
            }
        }
    }

    // ---------------------------------------------------------- GitHub Pages

    suspend fun deployGitHubPages(token: String, repoName: String, progress: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(token.isNotBlank()) { "Add a GitHub token in Git settings first." }
            val files = files()
            require(files.any { it.first == "index.html" }) { "There is no index.html at the project root to publish." }
            val login = gh(token, "GET", "user")?.str("login") ?: throw IOException("GitHub rejected the token")
            val repo = slug(repoName)
            progress("Preparing $login/$repo…")
            if (gh(token, "GET", "repos/$login/$repo") == null) {
                gh(token, "POST", "user/repos", buildJsonObject {
                    put("name", repo); put("auto_init", true); put("description", "Published from Oro")
                }.toString()) ?: throw IOException("Could not create the repository $repo")
            }
            val parent = gh(token, "GET", "repos/$login/$repo/git/ref/heads/gh-pages")?.obj("object")?.str("sha")
            progress("Uploading ${files.size} files…")
            val entries = (files.map { it } + listOf(".nojekyll" to null)).map { (path, f) ->
                val bytes = f?.readBytes() ?: ByteArray(0)
                val blob = gh(token, "POST", "repos/$login/$repo/git/blobs", buildJsonObject {
                    put("content", Base64.getEncoder().encodeToString(bytes)); put("encoding", "base64")
                }.toString())?.str("sha") ?: throw IOException("Upload failed for $path")
                path to blob
            }
            val tree = gh(token, "POST", "repos/$login/$repo/git/trees", buildJsonObject {
                putJsonArray("tree") {
                    entries.forEach { (path, sha) -> addJsonObject { put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", sha) } }
                }
            }.toString())?.str("sha") ?: throw IOException("Could not create the file tree")
            val commit = gh(token, "POST", "repos/$login/$repo/git/commits", buildJsonObject {
                put("message", "Publish from Oro")
                put("tree", tree)
                putJsonArray("parents") { parent?.let { add(JsonPrimitive(it)) } }
            }.toString())?.str("sha") ?: throw IOException("Could not create the commit")
            val ref = if (parent == null) {
                gh(token, "POST", "repos/$login/$repo/git/refs", buildJsonObject { put("ref", "refs/heads/gh-pages"); put("sha", commit) }.toString())
            } else {
                gh(token, "PATCH", "repos/$login/$repo/git/refs/heads/gh-pages", buildJsonObject { put("sha", commit); put("force", true) }.toString())
            }
            ref ?: throw IOException("Could not update the gh-pages branch")
            progress("Turning on Pages…")
            gh(token, "POST", "repos/$login/$repo/pages", buildJsonObject {
                putJsonObject("source") { put("branch", "gh-pages"); put("path", "/") }
            }.toString()) // 409 when already on: fine
            val url = "https://$login.github.io/$repo/"
            save(state().copy(githubRepo = "$login/$repo", githubUrl = url))
            url
        }
    }

    private fun gh(token: String, method: String, path: String, json: String? = null): JsonObject? {
        val b = Request.Builder().url("https://api.github.com/$path")
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (json != null) b.method(method, json.toRequestBody(AiHttp.jsonMediaType)) else b.get()
        return http.newCall(b.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 401) throw IOException("GitHub rejected the token (401)")
            if (!r.isSuccessful) null else runCatching { AiHttp.json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject

    companion object {
        private const val NETLIFY = "https://api.netlify.com/api/v1"
        private const val MAX_FILES = 2_000
        private val SKIP_DIRS = setOf(".oro", ".git", "node_modules", ".idea", ".gradle")

        fun slug(name: String): String = name.trim().lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-').take(60).ifEmpty { "oro-site" }
    }
}
