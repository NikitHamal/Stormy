package com.oro.app.data.git

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Opens a GitHub pull request with the project's current files, without a
 * local git push: a branch off the base, one commit through the Git Data API
 * (only files whose content differs from the base are uploaded), then the PR.
 *
 * Files are added or updated, never deleted: a project may hold only part of
 * the repository.
 */
class PullRequests(private val projectRoot: File, private val token: String) {

    data class Opened(val url: String, val number: Int, val branch: String, val changedFiles: List<String>)

    private val http = AiHttp.client

    suspend fun open(
        repo: String? = null,
        title: String,
        body: String,
        branch: String? = null,
        base: String? = null,
        progress: (String) -> Unit = {}
    ): Result<Opened> = withContext(Dispatchers.IO) {
        runCatching {
            val slug = repo?.trim()?.removePrefix("https://github.com/")?.removeSuffix(".git")?.trim('/')?.ifEmpty { null }
                ?: originRepo(projectRoot)
                ?: throw IOException("This project has no GitHub remote. Pass repo as owner/name.")
            require(Regex("[\\w.-]+/[\\w.-]+").matches(slug)) { "repo must look like owner/name, got '$slug'" }

            progress("Reading $slug…")
            val info = gh("GET", "repos/$slug") ?: throw IOException("Repository $slug not found, or the token cannot see it")
            val baseBranch = base?.trim()?.ifEmpty { null } ?: info.str("default_branch") ?: "main"
            val baseSha = gh("GET", "repos/$slug/git/ref/heads/$baseBranch")?.obj("object")?.str("sha")
                ?: throw IOException("Branch $baseBranch not found in $slug")
            val baseTree = gh("GET", "repos/$slug/git/commits/$baseSha")?.obj("tree")?.str("sha")
                ?: throw IOException("Could not read the $baseBranch commit")
            val remote = (gh("GET", "repos/$slug/git/trees/$baseTree?recursive=1")?.get("tree") as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.str("type") == "blob" }
                .associate { it.str("path").orEmpty() to it.str("sha").orEmpty() }

            val changed = files(projectRoot).filter { (path, f) -> remote[path] != blobSha(f.readBytes()) }
            if (changed.isEmpty()) throw IOException("Nothing to propose: the project matches $baseBranch.")

            progress("Uploading ${changed.size} changed file${if (changed.size == 1) "" else "s"}…")
            val entries = changed.map { (path, f) ->
                val sha = gh("POST", "repos/$slug/git/blobs", buildJsonObject {
                    put("content", android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                    put("encoding", "base64")
                }.toString())?.str("sha") ?: throw IOException("Upload failed for $path")
                path to sha
            }
            val tree = gh("POST", "repos/$slug/git/trees", buildJsonObject {
                put("base_tree", baseTree)
                putJsonArray("tree") {
                    entries.forEach { (path, sha) -> addJsonObject { put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", sha) } }
                }
            }.toString())?.str("sha") ?: throw IOException("Could not create the file tree")
            val commit = gh("POST", "repos/$slug/git/commits", buildJsonObject {
                put("message", title)
                put("tree", tree)
                putJsonArray("parents") { add(JsonPrimitive(baseSha)) }
            }.toString())?.str("sha") ?: throw IOException("Could not create the commit")

            val name = branchName(branch ?: title)
            progress("Creating branch $name…")
            val created = gh("POST", "repos/$slug/git/refs", buildJsonObject { put("ref", "refs/heads/$name"); put("sha", commit) }.toString())
            if (created == null) {
                // The branch exists (a second attempt): move it to the new commit.
                gh("PATCH", "repos/$slug/git/refs/heads/$name", buildJsonObject { put("sha", commit); put("force", true) }.toString())
                    ?: throw IOException("Could not create the branch $name")
            }

            progress("Opening the pull request…")
            val owner = slug.substringBefore('/')
            val pr = gh("POST", "repos/$slug/pulls", buildJsonObject {
                put("title", title); put("body", body); put("head", name); put("base", baseBranch)
            }.toString())
                ?: gh("GET", "repos/$slug/pulls?head=$owner:$name&state=open")
                ?: throw IOException("GitHub refused the pull request (is there already one for $name?)")
            Opened(pr.str("html_url") ?: "https://github.com/$slug/pulls", pr.str("number")?.toIntOrNull() ?: 0, name, changed.map { it.first })
        }
    }

    private fun gh(method: String, path: String, json: String? = null): JsonObject? {
        val b = Request.Builder().url("https://api.github.com/$path")
            .header("Authorization", "Bearer ${token.trim()}")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (json != null) b.method(method, json.toRequestBody(AiHttp.jsonMediaType)) else b.get()
        return http.newCall(b.build()).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) throw IOException("GitHub rejected the token (401). Sign in again under Git credentials.")
            if (r.code == 403 && text.contains("rate limit", true)) throw IOException("GitHub rate limit reached; try again later.")
            if (!r.isSuccessful) return@use null
            when (val e = runCatching { AiHttp.json.parseToJsonElement(text) }.getOrNull()) {
                is JsonObject -> e
                is JsonArray -> e.firstOrNull() as? JsonObject // pulls?head= lists
                else -> JsonObject(emptyMap())
            }
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject

    companion object {
        private val SKIP_DIRS = setOf(".oro", ".git", "node_modules", ".idea", ".gradle", "build")
        private const val MAX_FILES = 2_000
        private const val MAX_BYTES = 5L * 1024 * 1024

        /** Files a pull request may carry: db.json without its secrets, never the preview's uploads. */
        fun files(root: File): List<Pair<String, File>> = com.oro.app.data.preview.PublishFilter.apply(
            root,
            root.walkTopDown()
                .onEnter { d -> d == root || d.name !in SKIP_DIRS }
                .filter { it.isFile && it.length() <= MAX_BYTES }
                .map { it.relativeTo(root).invariantSeparatorsPath to it }
                .take(MAX_FILES)
                .toList()
        )

        /** Git's blob id: sha1("blob <size>\0" + content). */
        fun blobSha(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-1")
            md.update("blob ${bytes.size}\u0000".toByteArray())
            md.update(bytes)
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        /** owner/name of the project's GitHub `origin`, from .git/config. */
        fun originRepo(root: File): String? {
            val config = File(root, ".git/config").takeIf { it.isFile }?.readText() ?: return null
            val origin = Regex("""(?s)\[remote "origin"\](.*?)(?=\n\[|$)""").find(config)?.groupValues?.get(1) ?: config
            val url = Regex("""(?m)^\s*url\s*=\s*(\S+)""").find(origin)?.groupValues?.get(1) ?: return null
            return Regex("""github\.com[:/]([^/\s]+)/([^/\s]+?)(?:\.git)?/?$""").find(url)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
        }

        fun branchName(seed: String): String =
            "oro/" + seed.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "changes" }
    }
}
