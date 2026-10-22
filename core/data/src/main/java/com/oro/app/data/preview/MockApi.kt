package com.oro.app.data.preview

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.URLDecoder
import java.util.UUID

/**
 * A REST backend for every project, served by the preview at `/api`, in the
 * spirit of json-server: `db.json` at the project root holds collections
 * (arrays of objects) and singletons (objects), and standard CRUD routes work
 * against it with persistence.
 *
 * ### Why
 *
 * Users ask for apps with "a backend" -- a todo list that syncs, a booking
 * form that stores bookings, an admin panel. There is no Node on the device,
 * so agents used to fake it with arrays in memory that vanished on reload.
 * With this, the agent writes ordinary `fetch('/api/todos')` code against a
 * real HTTP API, the data persists, and moving to a production server later
 * only means changing the base URL.
 *
 * Routes: `GET /api` (collections), `GET|POST /api/:c`, `GET|PUT|PATCH|DELETE
 * /api/:c/:id`; on singletons `GET|PUT|PATCH /api/:name`. List queries:
 * `q`, `field=value`, `_sort`, `_order`, `_page`, `_limit`, `_start`, `_end`.
 */
class MockApi(private val projectRoot: File) {

    data class Response(val status: Int, val body: String, val headers: Map<String, String> = emptyMap())

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }
    private val dbFile get() = File(projectRoot, "db.json")
    private val lock = Any()

    fun handle(method: String, rawPath: String, body: String?): Response = synchronized(lock) {
        try {
            route(method.uppercase(), rawPath, body)
        } catch (e: BadRequest) {
            error(400, e.message ?: "Bad request")
        } catch (e: Exception) {
            error(500, "Mock API error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private class BadRequest(message: String) : Exception(message)

    private fun route(method: String, rawPath: String, body: String?): Response {
        val pathPart = rawPath.substringBefore('?')
        val query = parseQuery(rawPath.substringAfter('?', ""))
        val segments = pathPart.trim('/').split('/').filter { it.isNotEmpty() }.drop(1) // drop "api"
            .map { URLDecoder.decode(it, "UTF-8") }
        val db = load()

        if (segments.isEmpty()) {
            if (method != "GET") return error(405, "Use GET /api to list collections.")
            val info = JsonObject(
                mapOf(
                    "collections" to JsonArray(db.filterValues { it is JsonArray }.keys.map { JsonPrimitive(it) }),
                    "singletons" to JsonArray(db.filterValues { it is JsonObject }.keys.map { JsonPrimitive(it) }),
                    "hint" to JsonPrimitive("Add collections to db.json at the project root, e.g. {\"todos\": []}.")
                )
            )
            return ok(info)
        }

        val name = segments[0]
        val id = segments.getOrNull(1)
        val existing = db[name]

        // Singleton resource.
        if (existing is JsonObject && id == null) {
            return when (method) {
                "GET" -> ok(existing)
                "PUT" -> { val v = parseObject(body); save(db + (name to v)); ok(v) }
                "PATCH" -> { val v = JsonObject(existing + parseObject(body)); save(db + (name to v)); ok(v) }
                else -> error(405, "$name is a single object; use GET, PUT or PATCH.")
            }
        }

        val items = (existing as? JsonArray)?.mapNotNull { it as? JsonObject }
        if (items == null && !(method == "POST" && id == null)) {
            return error(404, "No collection \"$name\" in db.json. Create it, e.g. {\"$name\": []}, or POST to /api/$name.")
        }
        val list = items ?: emptyList()

        if (id == null) {
            return when (method) {
                "GET" -> listResponse(list, query)
                "POST" -> {
                    val obj = parseObject(body)
                    val newId = obj["id"]?.takeIf { it !is JsonNull } ?: nextId(list)
                    if (list.any { idEquals(it["id"], newId) }) return error(409, "An item with id ${newId.render()} already exists.")
                    val created = JsonObject(mapOf("id" to newId) + obj.filterKeys { it != "id" })
                    save(db + (name to JsonArray(list + created)))
                    Response(201, json.encodeToString(JsonElement.serializer(), created))
                }
                "DELETE" -> error(405, "Refusing to delete a whole collection; DELETE /api/$name/:id instead.")
                else -> error(405, "Use GET or POST on /api/$name.")
            }
        }

        val index = list.indexOfFirst { idEquals(it["id"], id) }
        if (index < 0) return error(404, "No item with id $id in $name.")
        val item = list[index]
        return when (method) {
            "GET" -> ok(item)
            "PUT" -> {
                val replaced = JsonObject(mapOf("id" to item["id"]!!) + parseObject(body).filterKeys { it != "id" })
                save(db + (name to JsonArray(list.toMutableList().also { it[index] = replaced })))
                ok(replaced)
            }
            "PATCH" -> {
                val merged = JsonObject(item + parseObject(body).filterKeys { it != "id" })
                save(db + (name to JsonArray(list.toMutableList().also { it[index] = merged })))
                ok(merged)
            }
            "DELETE" -> {
                save(db + (name to JsonArray(list.filterIndexed { i, _ -> i != index })))
                Response(204, "")
            }
            else -> error(405, "Unsupported method $method.")
        }
    }

    private fun listResponse(all: List<JsonObject>, q: Map<String, String>): Response {
        var items = all
        q["q"]?.takeIf { it.isNotBlank() }?.let { term ->
            val t = term.lowercase()
            items = items.filter { obj -> obj.values.any { it.render().lowercase().contains(t) } }
        }
        q.filterKeys { !it.startsWith("_") && it != "q" }.forEach { (field, value) ->
            val (key, op) = when {
                field.endsWith("_like") -> field.removeSuffix("_like") to "like"
                field.endsWith("_ne") -> field.removeSuffix("_ne") to "ne"
                field.endsWith("_gte") -> field.removeSuffix("_gte") to "gte"
                field.endsWith("_lte") -> field.removeSuffix("_lte") to "lte"
                else -> field to "eq"
            }
            items = items.filter { obj ->
                val v = obj[key]?.render() ?: return@filter op == "ne"
                when (op) {
                    "like" -> v.contains(value, ignoreCase = true)
                    "ne" -> !v.equals(value, ignoreCase = true)
                    "gte" -> (v.toDoubleOrNull() ?: return@filter false) >= (value.toDoubleOrNull() ?: return@filter false)
                    "lte" -> (v.toDoubleOrNull() ?: return@filter false) <= (value.toDoubleOrNull() ?: return@filter false)
                    else -> v.equals(value, ignoreCase = true)
                }
            }
        }
        q["_sort"]?.let { field ->
            items = items.sortedWith { a, b -> compareJson(a[field], b[field]) }
            if (q["_order"].equals("desc", true)) items = items.reversed()
        }
        val total = items.size
        val limit = q["_limit"]?.toIntOrNull()
        val page = q["_page"]?.toIntOrNull()
        val start = q["_start"]?.toIntOrNull()
        val end = q["_end"]?.toIntOrNull()
        items = when {
            page != null -> { val l = limit ?: 10; items.drop((page - 1).coerceAtLeast(0) * l).take(l) }
            start != null -> items.drop(start).take(((end ?: (start + (limit ?: total))) - start).coerceAtLeast(0))
            limit != null -> items.take(limit)
            else -> items
        }
        return Response(
            200, json.encodeToString(JsonElement.serializer(), JsonArray(items)),
            mapOf("X-Total-Count" to total.toString(), "Access-Control-Expose-Headers" to "X-Total-Count")
        )
    }

    private fun compareJson(a: JsonElement?, b: JsonElement?): Int {
        val pa = a as? JsonPrimitive
        val pb = b as? JsonPrimitive
        if (pa == null || pb == null) return (if (pa == null) 1 else 0) - (if (pb == null) 1 else 0)
        val da = pa.doubleOrNull
        val db = pb.doubleOrNull
        if (da != null && db != null) return da.compareTo(db)
        return (pa.contentOrNull ?: "").compareTo(pb.contentOrNull ?: "", ignoreCase = true)
    }

    private fun nextId(list: List<JsonObject>): JsonPrimitive {
        val ids = list.mapNotNull { it["id"] as? JsonPrimitive }
        if (ids.isEmpty() || ids.all { it.longOrNull != null }) {
            return JsonPrimitive((ids.mapNotNull { it.longOrNull }.maxOrNull() ?: 0L) + 1)
        }
        return JsonPrimitive(UUID.randomUUID().toString().take(8))
    }

    private fun idEquals(a: JsonElement?, b: Any): Boolean {
        val left = (a as? JsonPrimitive)?.contentOrNull ?: return false
        val right = when (b) {
            is JsonPrimitive -> b.contentOrNull
            else -> b.toString()
        }
        return left == right
    }

    private fun JsonElement.render(): String = when (this) {
        is JsonPrimitive -> contentOrNull ?: ""
        else -> toString()
    }

    private fun parseObject(body: String?): JsonObject {
        if (body.isNullOrBlank()) throw BadRequest("Request body must be a JSON object.")
        val el = runCatching { json.parseToJsonElement(body) }.getOrElse { throw BadRequest("Body is not valid JSON: ${it.message}") }
        return el as? JsonObject ?: throw BadRequest("Body must be a JSON object, not ${el::class.simpleName}.")
    }

    private fun load(): Map<String, JsonElement> {
        val f = dbFile
        if (!f.exists()) return emptyMap()
        val text = f.readText().ifBlank { return emptyMap() }
        val el = runCatching { json.parseToJsonElement(text) }.getOrElse { throw BadRequest("db.json is not valid JSON: ${it.message}") }
        return (el as? JsonObject) ?: throw BadRequest("db.json must be a JSON object of collections.")
    }

    private fun save(db: Map<String, JsonElement>) {
        val tmp = File(projectRoot, "db.json.tmp")
        tmp.writeText(json.encodeToString(JsonElement.serializer(), JsonObject(db)))
        if (!tmp.renameTo(dbFile)) { dbFile.writeText(tmp.readText()); tmp.delete() }
    }

    private fun ok(el: JsonElement) = Response(200, json.encodeToString(JsonElement.serializer(), el))

    private fun error(status: Int, message: String) =
        Response(status, json.encodeToString(JsonElement.serializer(), JsonObject(mapOf("error" to JsonPrimitive(message)))))

    private fun parseQuery(q: String): Map<String, String> =
        q.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val k = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
            val v = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
            k to v
        }

    companion object {
        fun isApiPath(path: String): Boolean = path == "/api" || path.startsWith("/api/") || path.startsWith("/api?")
    }
}
