package com.oro.app.data.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

/**
 * What of a project may leave the phone in a deploy or a pull request.
 *
 * The preview's backend keeps runtime data in the project: `db.json` (the
 * sample data a site needs, but also passwords seeded for demo accounts and
 * internal `_collections`) and `uploads/` (files people picked while testing
 * their own app). A published site carries the sample data and never the
 * rest: `db.json` goes out with secret fields, internal collections and
 * upload records stripped, and `uploads/` stays on the phone.
 */
object PublishFilter {

    private val json = Json { prettyPrint = true; isLenient = true }

    /** [files] (relative path to file) as they may be published from [root]. */
    fun apply(root: File, files: List<Pair<String, File>>): List<Pair<String, File>> = files.mapNotNull { (path, file) ->
        when {
            path == UPLOADS || path.startsWith("$UPLOADS/") -> null
            path == "db.json.tmp" -> null
            path == "db.json" && file.parentFile?.canonicalPath == root.canonicalPath -> sanitizedCopy(file)?.let { path to it }
            else -> path to file
        }
    }

    /**
     * [text] of a db.json without anything private; null when it is not a JSON
     * object (then it is not published at all: it could hold anything).
     */
    fun sanitize(text: String): String? {
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
        val kept = LinkedHashMap<String, JsonElement>()
        for ((key, value) in root) {
            if (key.startsWith("_")) continue
            kept[key] = if (key == UPLOADS) withoutUploadRecords(value) else strip(value)
        }
        return json.encodeToString(JsonElement.serializer(), JsonObject(kept))
    }

    private fun strip(el: JsonElement): JsonElement = when (el) {
        is JsonObject -> JsonObject(el.filterKeys { it !in MockApi.SECRET_FIELDS }.mapValues { (_, v) -> strip(v) })
        is JsonArray -> JsonArray(el.map { strip(it) })
        else -> el
    }

    /** The API's own records of uploaded files point into uploads/, which is not published. */
    private fun withoutUploadRecords(el: JsonElement): JsonElement {
        val arr = el as? JsonArray ?: return strip(el)
        return JsonArray(arr.filterNot { r ->
            ((r as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull?.startsWith("/$UPLOADS/") == true
        }.map { strip(it) })
    }

    private fun sanitizedCopy(file: File): File? {
        val clean = runCatching { sanitize(file.readText()) }.getOrNull() ?: return null
        return File.createTempFile("oro-db", ".json").apply {
            deleteOnExit()
            writeText(clean)
        }
    }

    private const val UPLOADS = "uploads"
}
