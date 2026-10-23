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
 * Relations as in json-server: `_expand=user` adds the item `userId` points
 * at, `_embed=comments` adds the comments whose `postId` is this post's id.
 *
 * Real apps also need accounts and files, so:
 *  - `POST /api/auth/register` and `/api/auth/login` (email or username +
 *    password) return `{token, user}`; `GET /api/auth/me` with
 *    `Authorization: Bearer <token>` returns the user; `POST /api/auth/logout`.
 *    Users live in the `users` collection with a salted password hash that is
 *    never sent back; sessions in a hidden `_sessions` object.
 *  - `POST /api/uploads` with `{name, dataUrl}` saves the file under the
 *    project's `uploads/` folder and returns `{id, url, name, type, size}`;
 *    the preview serves it at that url. DELETE removes the file too.
 * Collections whose name starts with `_` are internal and never served.
 * Items get `createdAt` / `updatedAt` stamps when their collection already
 * uses those fields.
 */
class MockApi(private val projectRoot: File) {

    data class Response(val status: Int, val body: String, val headers: Map<String, String> = emptyMap())

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }
    private val dbFile get() = File(projectRoot, "db.json")
    private val lock = Any()

    fun handle(method: String, rawPath: String, body: String?, headers: Map<String, String> = emptyMap()): Response = synchronized(lock) {
        try {
            route(method.uppercase(), rawPath, body, headers.mapKeys { it.key.lowercase() })
        } catch (e: BadRequest) {
            error(400, e.message ?: "Bad request")
        } catch (e: Exception) {
            error(500, "Mock API error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private class BadRequest(message: String) : Exception(message)

    private fun route(method: String, rawPath: String, body: String?, headers: Map<String, String>): Response {
        val pathPart = rawPath.substringBefore('?')
        val query = parseQuery(rawPath.substringAfter('?', ""))
        val segments = pathPart.trim('/').split('/').filter { it.isNotEmpty() }.drop(1) // drop "api"
            .map { URLDecoder.decode(it, "UTF-8") }
        val db = load()

        if (segments.isEmpty()) {
            if (method != "GET") return error(405, "Use GET /api to list collections.")
            val info = JsonObject(
                mapOf(
                    "collections" to JsonArray(db.filterValues { it is JsonArray }.keys.filterNot { it.startsWith("_") }.map { JsonPrimitive(it) }),
                    "singletons" to JsonArray(db.filterValues { it is JsonObject }.keys.filterNot { it.startsWith("_") }.map { JsonPrimitive(it) }),
                    "auth" to JsonPrimitive("POST /api/auth/register | /api/auth/login, GET /api/auth/me (Bearer token), POST /api/auth/logout"),
                    "uploads" to JsonPrimitive("POST /api/uploads {name, dataUrl} -> {url}"),
                    "hint" to JsonPrimitive("Add collections to db.json at the project root, e.g. {\"todos\": []}.")
                )
            )
            return ok(info)
        }

        val name = segments[0]
        val id = segments.getOrNull(1)
        if (name.startsWith("_")) return error(404, "\"$name\" is internal.")
        if (name == "auth") return auth(method, id, body, headers, db)
        if (name == "uploads" && method == "POST" && id == null) return upload(body, db)
        if (name == "uploads" && method == "DELETE" && id != null) deleteUploadFile(db, id)
        val existing = db[name]

        // Singleton resource.
        if (existing is JsonObject && id == null) {
            return when (method) {
                "GET" -> ok(public(existing))
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
                "GET" -> listResponse(list, query, name, db)
                "POST" -> {
                    val obj = parseObject(body)
                    val newId = obj["id"]?.takeIf { it !is JsonNull } ?: nextId(list)
                    if (list.any { idEquals(it["id"], newId) }) return error(409, "An item with id ${newId.render()} already exists.")
                    var created = JsonObject(mapOf("id" to newId) + obj.filterKeys { it != "id" })
                    if ("createdAt" !in created && list.any { "createdAt" in it }) created = JsonObject(created + ("createdAt" to JsonPrimitive(now())))
                    if ("updatedAt" !in created && list.any { "updatedAt" in it }) created = JsonObject(created + ("updatedAt" to JsonPrimitive(now())))
                    save(db + (name to JsonArray(list + created)))
                    Response(201, json.encodeToString(JsonElement.serializer(), public(created)))
                }
                "DELETE" -> error(405, "Refusing to delete a whole collection; DELETE /api/$name/:id instead.")
                else -> error(405, "Use GET or POST on /api/$name.")
            }
        }

        val index = list.indexOfFirst { idEquals(it["id"], id) }
        if (index < 0) return error(404, "No item with id $id in $name.")
        val item = list[index]
        return when (method) {
            "GET" -> ok(public(relate(item, query, name, db)))
            "PUT" -> {
                var replaced = JsonObject(mapOf("id" to item["id"]!!) + parseObject(body).filterKeys { it != "id" })
                replaced = keepSecrets(item, stamp(item, replaced))
                save(db + (name to JsonArray(list.toMutableList().also { it[index] = replaced })))
                ok(public(replaced))
            }
            "PATCH" -> {
                val merged = keepSecrets(item, stamp(item, JsonObject(item + parseObject(body).filterKeys { it != "id" })))
                save(db + (name to JsonArray(list.toMutableList().also { it[index] = merged })))
                ok(public(merged))
            }
            "DELETE" -> {
                save(db + (name to JsonArray(list.filterIndexed { i, _ -> i != index })))
                Response(204, "")
            }
            else -> error(405, "Unsupported method $method.")
        }
    }

    private fun listResponse(all: List<JsonObject>, q: Map<String, String>, name: String, db: Map<String, JsonElement>): Response {
        var items = all
        q["q"]?.takeIf { it.isNotBlank() }?.let { term ->
            val t = term.lowercase()
            items = items.filter { obj -> obj.values.any { it.render().lowercase().contains(t) } }
        }
        q.filterKeys { !it.startsWith("_") && it != "q" && it != "passwordHash" }.forEach { (field, value) ->
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
        items = items.map { public(relate(it, q, name, db)) }
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

    // ------------------------------------------------------------ relations

    /** json-server's `_expand` (parent via `<name>Id`) and `_embed` (children pointing here). */
    private fun relate(item: JsonObject, q: Map<String, String>, name: String, db: Map<String, JsonElement>): JsonObject {
        val expand = q["_expand"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val embed = q["_embed"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (expand.isEmpty() && embed.isEmpty()) return item
        val extra = LinkedHashMap<String, JsonElement>()
        for (parent in expand) {
            val ref = item[parent + "Id"] ?: continue
            val coll = collection(db, parent) ?: continue
            coll.firstOrNull { idEquals(it["id"], ref) }?.let { extra[parent] = public(it) }
        }
        val key = singular(name) + "Id"
        val itemId = item["id"]
        if (itemId != null) for (child in embed) {
            val coll = collection(db, child) ?: continue
            extra[child] = JsonArray(coll.filter { idEquals(it[key], itemId) }.map { public(it) })
        }
        return JsonObject(item + extra)
    }

    /** "user" -> users, "category" -> categories, or the name as given. */
    private fun collection(db: Map<String, JsonElement>, name: String): List<JsonObject>? {
        val candidates = listOf(name, name + "s", name + "es", name.removeSuffix("y") + "ies")
        val key = candidates.firstOrNull { db[it] is JsonArray } ?: return null
        return (db[key] as JsonArray).mapNotNull { it as? JsonObject }
    }

    private fun singular(name: String): String = when {
        name.endsWith("ies") -> name.dropLast(3) + "y"
        name.endsWith("ses") || name.endsWith("xes") -> name.dropLast(2)
        name.endsWith("s") -> name.dropLast(1)
        else -> name
    }

    // ------------------------------------------------------------ accounts

    private fun auth(method: String, action: String?, body: String?, headers: Map<String, String>, db: Map<String, JsonElement>): Response {
        val users = (db["users"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        val sessions = (db["_sessions"] as? JsonObject) ?: JsonObject(emptyMap())
        fun identity(obj: JsonObject): Pair<String, String>? {
            for (k in listOf("email", "username")) {
                val v = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()
                if (!v.isNullOrEmpty()) return k to (if (k == "email") v.lowercase() else v)
            }
            return null
        }
        fun session(user: JsonObject, extraDb: Map<String, JsonElement>): Response {
            val token = UUID.randomUUID().toString().replace("-", "")
            val s = JsonObject(sessions + (token to JsonObject(mapOf("userId" to user["id"]!!, "createdAt" to JsonPrimitive(now())))))
            save(extraDb + ("_sessions" to s))
            return Response(200, json.encodeToString(JsonElement.serializer(), JsonObject(mapOf("token" to JsonPrimitive(token), "user" to public(user)))))
        }
        return when (action) {
            "register", "signup" -> {
                if (method != "POST") return error(405, "POST /api/auth/register {email, password, ...}")
                val obj = parseObject(body)
                val (field, value) = identity(obj) ?: throw BadRequest("Register with an email or a username.")
                val password = (obj["password"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (password.length < 4) throw BadRequest("Password must be at least 4 characters.")
                if (users.any { (it[field] as? JsonPrimitive)?.contentOrNull?.let { v -> if (field == "email") v.lowercase() else v } == value }) {
                    return error(409, "An account with that $field already exists.")
                }
                val salt = UUID.randomUUID().toString().take(12)
                var user = JsonObject(
                    mapOf("id" to nextId(users), field to JsonPrimitive(value)) +
                        obj.filterKeys { it !in SECRET_INPUTS && it != "id" && it != field } +
                        mapOf("createdAt" to JsonPrimitive(now()), "passwordHash" to JsonPrimitive("sha256:$salt:${sha256(salt + password)}"))
                )
                if (user["name"] == null) user = JsonObject(user + ("name" to JsonPrimitive(value.substringBefore('@'))))
                val withUser = db + ("users" to JsonArray(users + user))
                session(user, withUser).copy(status = 201)
            }
            "login", "signin" -> {
                if (method != "POST") return error(405, "POST /api/auth/login {email, password}")
                val obj = parseObject(body)
                val (field, value) = identity(obj) ?: throw BadRequest("Log in with an email or a username.")
                val password = (obj["password"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val user = users.firstOrNull { (it[field] as? JsonPrimitive)?.contentOrNull?.let { v -> if (field == "email") v.lowercase() else v } == value }
                if (user == null || !passwordMatches(user, password)) return error(401, "Wrong $field or password.")
                session(user, db)
            }
            "me" -> {
                val user = currentUser(headers, users, sessions) ?: return error(401, "Not signed in: send Authorization: Bearer <token>.")
                ok(public(user))
            }
            "logout", "signout" -> {
                val token = bearer(headers)
                if (token != null && token in sessions) save(db + ("_sessions" to JsonObject(sessions - token)))
                Response(204, "")
            }
            else -> error(404, "Auth routes: POST /api/auth/register, POST /api/auth/login, GET /api/auth/me, POST /api/auth/logout.")
        }
    }

    private fun bearer(headers: Map<String, String>): String? =
        headers["authorization"]?.trim()?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()?.takeIf { it.isNotEmpty() }

    private fun currentUser(headers: Map<String, String>, users: List<JsonObject>, sessions: JsonObject): JsonObject? {
        val token = bearer(headers) ?: return null
        val ref = (sessions[token] as? JsonObject)?.get("userId") ?: return null
        return users.firstOrNull { idEquals(it["id"], ref) }
    }

    private fun passwordMatches(user: JsonObject, password: String): Boolean {
        val stored = (user["passwordHash"] as? JsonPrimitive)?.contentOrNull ?: return false
        val parts = stored.split(':')
        if (parts.size != 3 || parts[0] != "sha256") return false
        return sha256(parts[1] + password) == parts[2]
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Never send a password hash (or a stored password) to the page. */
    private fun public(obj: JsonObject): JsonObject =
        if (obj.keys.none { it in SECRET_FIELDS }) obj else JsonObject(obj.filterKeys { it !in SECRET_FIELDS })

    /** A PUT/PATCH from the page cannot see or clear the hash, so it is carried over. */
    private fun keepSecrets(old: JsonObject, new: JsonObject): JsonObject {
        val kept = old.filterKeys { it in SECRET_FIELDS }
        return if (kept.isEmpty()) new else JsonObject(new.filterKeys { it !in SECRET_FIELDS } + kept)
    }

    private fun stamp(old: JsonObject, new: JsonObject): JsonObject =
        if ("updatedAt" in old) JsonObject(new + ("updatedAt" to JsonPrimitive(now()))) else new

    private fun now(): String = java.time.Instant.now().toString()

    // ------------------------------------------------------------ uploads

    private fun upload(body: String?, db: Map<String, JsonElement>): Response {
        val obj = parseObject(body)
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty().ifEmpty { "file" }
        val dataUrl = (obj["dataUrl"] as? JsonPrimitive)?.contentOrNull ?: (obj["data"] as? JsonPrimitive)?.contentOrNull
            ?: throw BadRequest("Send {name, dataUrl}: read the file with FileReader.readAsDataURL().")
        val type = if (dataUrl.startsWith("data:")) dataUrl.substringAfter("data:").substringBefore(';').ifEmpty { "application/octet-stream" }
        else (obj["type"] as? JsonPrimitive)?.contentOrNull ?: "application/octet-stream"
        val b64 = if (dataUrl.startsWith("data:")) dataUrl.substringAfter(",", "") else dataUrl
        val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(b64) }.getOrElse { throw BadRequest("dataUrl is not base64 data.") }
        if (bytes.size > MAX_UPLOAD_BYTES) return error(413, "Uploads are limited to ${MAX_UPLOAD_BYTES / 1_000_000} MB.")
        val list = (db["uploads"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        val id = nextId(list)
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "-").trim('-').take(60).ifEmpty { "file" }
        val fileName = "${id.contentOrNull}-$safe"
        val dir = File(projectRoot, "uploads").apply { mkdirs() }
        File(dir, fileName).writeBytes(bytes)
        val record = JsonObject(
            mapOf(
                "id" to id, "name" to JsonPrimitive(name), "url" to JsonPrimitive("/uploads/$fileName"),
                "type" to JsonPrimitive(type), "size" to JsonPrimitive(bytes.size), "createdAt" to JsonPrimitive(now())
            )
        )
        save(db + ("uploads" to JsonArray(list + record)))
        return Response(201, json.encodeToString(JsonElement.serializer(), record))
    }

    private fun deleteUploadFile(db: Map<String, JsonElement>, id: String) {
        val item = (db["uploads"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { idEquals(it["id"], id) } ?: return
        val url = (item["url"] as? JsonPrimitive)?.contentOrNull ?: return
        if (url.startsWith("/uploads/") && !url.contains("..")) File(projectRoot, url.trimStart('/')).delete()
    }

    companion object {
        fun isApiPath(path: String): Boolean = path == "/api" || path.startsWith("/api/") || path.startsWith("/api?")

        private val SECRET_FIELDS = setOf("passwordHash")
        private val SECRET_INPUTS = setOf("password", "confirmPassword", "passwordConfirm", "passwordHash")
        private const val MAX_UPLOAD_BYTES = 3_000_000
    }
}
