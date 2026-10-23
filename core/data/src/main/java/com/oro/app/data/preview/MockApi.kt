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
 *    Users live in the `users` collection (profile fields only). Password
 *    hashes (PBKDF2) and sessions live outside the project folder in
 *    [authFile], so a deploy, a pull request or a `git push` of the project
 *    can never publish them. A seeded user's plain `password` is accepted at
 *    login once, then hashed and removed from db.json.
 *  - `POST /api/uploads` with `{name, dataUrl}` saves the file under the
 *    project's `uploads/` folder and returns `{id, url, name, type, size}`;
 *    the preview serves it at that url. DELETE removes the file too.
 * Collections whose name starts with `_` are internal and never served.
 * Items get `createdAt` / `updatedAt` stamps when their collection already
 * uses those fields.
 */
class MockApi(
    private val projectRoot: File,
    /** Password hashes and sessions; never inside the project folder in the app (see [authDir]). */
    private val authFile: File = defaultAuthFile(projectRoot),
) {

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
        val db = moveSecretsOut(load())

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
                    if (name == USERS) {
                        // A new user never inherits the hash or sessions of a deleted one with the same id.
                        forgetUser(newId)
                        created = withoutPassword(created, setHash = true)
                    }
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
                val incoming = parseObject(body).filterKeys { it != "id" }
                val replaced = if (name == USERS) {
                    // A seeded plain password is hashed first; only a new one in the body replaces it.
                    adoptSeededPassword(item)
                    withoutPassword(stamp(item, JsonObject(mapOf("id" to item["id"]!!) + incoming)), setHash = true)
                } else keepSecrets(item, stamp(item, JsonObject(mapOf("id" to item["id"]!!) + incoming)))
                save(db + (name to JsonArray(list.toMutableList().also { it[index] = replaced })))
                ok(public(replaced))
            }
            "PATCH" -> {
                val incoming = parseObject(body).filterKeys { it != "id" }
                val merged = if (name == USERS) {
                    adoptSeededPassword(item)
                    withoutPassword(stamp(item, JsonObject(item.filterKeys { it !in SECRET_FIELDS } + incoming)), setHash = true)
                } else keepSecrets(item, stamp(item, JsonObject(item + incoming)))
                save(db + (name to JsonArray(list.toMutableList().also { it[index] = merged })))
                ok(public(merged))
            }
            "DELETE" -> {
                if (name == USERS) item["id"]?.let { forgetUser(it) }
                save(db + (name to JsonArray(list.filterIndexed { i, _ -> i != index })))
                Response(204, "")
            }
            else -> error(405, "Unsupported method $method.")
        }
    }

    private fun listResponse(all: List<JsonObject>, q: Map<String, String>, name: String, db: Map<String, JsonElement>): Response {
        // Search, filters and sort see exactly what the page may see: a
        // secret can never be probed through ?passwordHash_like=, ?q= or the
        // order of a sort, with X-Total-Count as the oracle.
        var items = all.map { public(it) }
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
            if (!queryable(key)) return error(400, "\"$key\" cannot be filtered on.")
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
            if (!queryable(field)) return error(400, "\"$field\" cannot be sorted on.")
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
        items = items.map { relate(it, q, name, db) }
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
        if (name.startsWith("_")) return null
        val candidates = listOf(name, name + "s", name + "es", name.removeSuffix("y") + "ies")
        val key = candidates.firstOrNull { !it.startsWith("_") && db[it] is JsonArray } ?: return null
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
        val users = (db[USERS] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
        fun identity(obj: JsonObject): Pair<String, String>? {
            for (k in listOf("email", "username")) {
                val v = (obj[k] as? JsonPrimitive)?.contentOrNull?.trim()
                if (!v.isNullOrEmpty()) return k to (if (k == "email") v.lowercase() else v)
            }
            return null
        }
        fun session(user: JsonObject): Response {
            val token = UUID.randomUUID().toString().replace("-", "")
            val store = authStore()
            store.sessions[token] = JsonObject(mapOf("userId" to user["id"]!!, "createdAt" to JsonPrimitive(now())))
            saveAuth(store)
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
                var user = JsonObject(
                    mapOf("id" to nextId(users), field to JsonPrimitive(value)) +
                        obj.filterKeys { it !in SECRET_FIELDS && it != "id" && it != field } +
                        mapOf("createdAt" to JsonPrimitive(now()))
                )
                if (user["name"] == null) user = JsonObject(user + ("name" to JsonPrimitive(value.substringBefore('@'))))
                setPassword(user["id"]!!, password)
                save(db + (USERS to JsonArray(users + user)))
                session(user).copy(status = 201)
            }
            "login", "signin" -> {
                if (method != "POST") return error(405, "POST /api/auth/login {email, password}")
                val obj = parseObject(body)
                val (field, value) = identity(obj) ?: throw BadRequest("Log in with an email or a username.")
                val password = (obj["password"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val index = users.indexOfFirst { (it[field] as? JsonPrimitive)?.contentOrNull?.let { v -> if (field == "email") v.lowercase() else v } == value }
                val user = users.getOrNull(index)
                if (user == null || !checkPassword(user, password)) return error(401, "Wrong $field or password.")
                // A seeded plain password has done its job: hashed, it leaves db.json.
                if (user.keys.any { it in SECRET_FIELDS }) {
                    val cleaned = JsonObject(user.filterKeys { it !in SECRET_FIELDS })
                    save(db + (USERS to JsonArray(users.toMutableList().also { it[index] = cleaned })))
                }
                session(user)
            }
            "me" -> {
                val user = currentUser(headers, users) ?: return error(401, "Not signed in: send Authorization: Bearer <token>.")
                ok(public(user))
            }
            "logout", "signout" -> {
                val token = bearer(headers)
                val store = authStore()
                if (token != null && store.sessions.remove(token) != null) saveAuth(store)
                Response(204, "")
            }
            else -> error(404, "Auth routes: POST /api/auth/register, POST /api/auth/login, GET /api/auth/me, POST /api/auth/logout.")
        }
    }

    private fun bearer(headers: Map<String, String>): String? =
        headers["authorization"]?.trim()?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()?.takeIf { it.isNotEmpty() }

    private fun currentUser(headers: Map<String, String>, users: List<JsonObject>): JsonObject? {
        val token = bearer(headers) ?: return null
        val ref = (authStore().sessions[token] as? JsonObject)?.get("userId") ?: return null
        return users.firstOrNull { idEquals(it["id"], ref) }
    }

    /**
     * The stored hash when there is one (an old SHA-256 hash is upgraded on
     * success); else a plain `password` seeded into db.json, compared in
     * constant time and then hashed.
     */
    private fun checkPassword(user: JsonObject, password: String): Boolean {
        val id = user["id"] ?: return false
        val stored = authStore().hashes[key(id)]
        if (stored != null) {
            val ok = PasswordHash.verify(stored, password)
            if (ok && PasswordHash.needsUpgrade(stored)) setPassword(id, password)
            return ok
        }
        val seeded = (user["password"] as? JsonPrimitive)?.contentOrNull ?: return false
        val ok = java.security.MessageDigest.isEqual(seeded.toByteArray(), password.toByteArray())
        if (ok) setPassword(id, password)
        return ok
    }

    private fun setPassword(id: JsonElement, password: String) {
        val store = authStore()
        store.hashes[key(id)] = PasswordHash.create(password)
        saveAuth(store)
    }

    /** A seeded plain password on [user] is hashed before a write replaces the record. */
    private fun adoptSeededPassword(user: JsonObject) {
        val id = user["id"] ?: return
        val seeded = (user["password"] as? JsonPrimitive)?.contentOrNull ?: return
        if (authStore().hashes[key(id)] == null) setPassword(id, seeded)
    }

    /**
     * [user] as it is saved: a `password` it carries becomes the stored hash
     * ([setHash]); every secret field leaves the record.
     */
    private fun withoutPassword(user: JsonObject, setHash: Boolean): JsonObject {
        val id = user["id"]
        val password = (user["password"] as? JsonPrimitive)?.contentOrNull
        if (setHash && id != null && password != null) setPassword(id, password)
        return if (user.keys.none { it in SECRET_FIELDS }) user else JsonObject(user.filterKeys { it !in SECRET_FIELDS })
    }

    /** A deleted user's hash and sessions go with it, so a later user with the same id inherits nothing. */
    private fun forgetUser(id: JsonElement) {
        val store = authStore()
        val k = key(id)
        val removed = store.hashes.remove(k) != null
        val sessions = store.sessions.entries.filter { (_, s) -> (s as? JsonObject)?.get("userId")?.let { key(it) } == k }
        sessions.forEach { store.sessions.remove(it.key) }
        if (removed || sessions.isNotEmpty()) saveAuth(store)
    }

    /**
     * Hashes and sessions written into db.json by earlier versions (a
     * `passwordHash` on users, a `_sessions` object) move to [authFile] the
     * first time the API sees them, so they stop being part of the project.
     */
    private fun moveSecretsOut(db: Map<String, JsonElement>): Map<String, JsonElement> {
        val sessions = db["_sessions"] as? JsonObject
        val users = db[USERS] as? JsonArray
        val hashed = users?.any { (it as? JsonObject)?.containsKey("passwordHash") == true } == true
        if (sessions == null && !hashed) return db
        val store = authStore()
        sessions?.forEach { (token, s) -> store.sessions.putIfAbsent(token, s) }
        var out = db - "_sessions"
        if (hashed && users != null) {
            val cleaned = users.map { el ->
                val u = el as? JsonObject
                if (u == null || !u.containsKey("passwordHash")) el
                else {
                    val hash = (u["passwordHash"] as? JsonPrimitive)?.contentOrNull
                    val id = u["id"]
                    if (hash != null && id != null) store.hashes.putIfAbsent(key(id), hash)
                    JsonObject(u - "passwordHash")
                }
            }
            out = out + (USERS to JsonArray(cleaned))
        }
        saveAuth(store)
        save(out)
        return out
    }

    private class AuthStore(val hashes: MutableMap<String, String>, val sessions: MutableMap<String, JsonElement>)

    private fun authStore(): AuthStore {
        val el = runCatching { if (authFile.isFile) json.parseToJsonElement(authFile.readText()) else null }.getOrNull() as? JsonObject
        val hashes = LinkedHashMap<String, String>()
        (el?.get("hashes") as? JsonObject)?.forEach { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { hashes[k] = it } }
        val sessions = LinkedHashMap<String, JsonElement>()
        (el?.get("sessions") as? JsonObject)?.forEach { (k, v) -> sessions[k] = v }
        return AuthStore(hashes, sessions)
    }

    private fun saveAuth(store: AuthStore) {
        authFile.parentFile?.mkdirs()
        // Newest sessions win; a long-lived preview does not grow this file forever.
        val sessions = store.sessions.entries.toList().takeLast(MAX_SESSIONS).associate { it.key to it.value }
        val body = JsonObject(mapOf("hashes" to JsonObject(store.hashes.mapValues { JsonPrimitive(it.value) }), "sessions" to JsonObject(sessions)))
        val tmp = File(authFile.parentFile, authFile.name + ".tmp")
        tmp.writeText(json.encodeToString(JsonElement.serializer(), body))
        if (!tmp.renameTo(authFile)) { authFile.writeText(tmp.readText()); tmp.delete() }
    }

    /** Hash keys are ids as text, so 1 and "1" are the same user. */
    private fun key(id: JsonElement): String = (id as? JsonPrimitive)?.contentOrNull ?: id.toString()

    /** Never send a password hash (or a stored password) to the page. */
    private fun public(obj: JsonObject): JsonObject =
        if (obj.keys.none { it in SECRET_FIELDS }) obj else JsonObject(obj.filterKeys { it !in SECRET_FIELDS })

    /** A PUT/PATCH from the page cannot see or clear a secret field, so it is carried over. */
    private fun keepSecrets(old: JsonObject, new: JsonObject): JsonObject {
        val kept = old.filterKeys { it in SECRET_FIELDS }
        return if (kept.isEmpty()) new else JsonObject(new.filterKeys { it !in SECRET_FIELDS } + kept)
    }

    /** A field the page may filter or sort on: not a secret, not internal. */
    private fun queryable(field: String): Boolean = field.isNotEmpty() && !field.startsWith("_") && field !in SECRET_FIELDS

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
        if (bytes.size > MAX_UPLOAD_BYTES) return error(413, UPLOAD_LIMIT_MESSAGE)
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

        /**
         * Where every project's mock-account hashes and sessions are kept: app
         * storage the app sets at startup (no backup, never in a project). Unset
         * (unit tests), they go to the project's `.oro/`, which deploys and pull
         * requests skip.
         */
        @Volatile var authDir: File? = null

        fun defaultAuthFile(projectRoot: File): File {
            val dir = authDir ?: return File(projectRoot, ".oro/mock-auth.json")
            val id = java.security.MessageDigest.getInstance("SHA-1")
                .digest(runCatching { projectRoot.canonicalPath }.getOrDefault(projectRoot.absolutePath).toByteArray())
                .joinToString("") { "%02x".format(it) }.take(20)
            return File(dir, "$id.json")
        }

        const val USERS = "users"

        /** Never served, searched, sorted, embedded or published. */
        val SECRET_FIELDS = setOf(
            "passwordHash", "password", "confirmPassword", "passwordConfirm", "passwordConfirmation",
            "password_confirmation", "confirm_password", "newPassword", "currentPassword", "oldPassword"
        )

        /** Largest file /api/uploads accepts: a phone photo fits. */
        const val MAX_UPLOAD_BYTES = 8_000_000

        /** Largest request body the API reads: an [MAX_UPLOAD_BYTES] file as a base64 data URL, plus slack. */
        const val MAX_BODY_BYTES = MAX_UPLOAD_BYTES / 3 * 4 + 64_000

        val UPLOAD_LIMIT_MESSAGE = "Uploads are limited to ${MAX_UPLOAD_BYTES / 1_000_000} MB."

        private const val MAX_SESSIONS = 200
    }
}
