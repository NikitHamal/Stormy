package com.oro.app.data.ai.mcp

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.FunctionDefinition
import com.oro.app.data.ai.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A remote MCP server the user added. */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    /** Streamable HTTP endpoint, e.g. https://mcp.context7.com/mcp */
    val url: String,
    /** Sent with every request; typically Authorization: Bearer <token>. */
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
)

@Serializable
data class McpSettings(val servers: List<McpServerConfig> = emptyList())

/** One tool a server offers, as discovered by tools/list. */
data class McpTool(val server: McpServerConfig, val name: String, val description: String, val inputSchema: JsonElement) {
    /** The name the model sees: mcp__<server>__<tool>, sanitised to the function-name charset. */
    val qualifiedName: String = McpNames.qualify(server.name, name)

    fun asTool(): Tool = Tool(
        type = "function",
        function = FunctionDefinition(
            name = qualifiedName,
            description = "[${server.name} MCP] " + description.ifBlank { name }.take(900),
            parameters = (inputSchema as? JsonObject)?.takeIf { it["type"] != null }
                ?: buildJsonObject { put("type", "object"); putJsonObject("properties") {} }
        )
    )
}

object McpNames {
    const val PREFIX = "mcp__"
    private val BAD = Regex("[^A-Za-z0-9_-]")

    fun slug(s: String): String = s.trim().lowercase().replace(BAD, "_").trim('_').take(20).ifEmpty { "server" }

    fun qualify(server: String, tool: String): String = (PREFIX + slug(server) + "__" + tool.replace(BAD, "_")).take(64)

    fun isMcp(name: String): Boolean = name.startsWith(PREFIX)
}

/**
 * Minimal MCP client over the Streamable HTTP transport (spec 2025-06-18):
 * JSON-RPC 2.0 POSTed to one endpoint, answered either as JSON or as an SSE
 * stream carrying the response; the server's `Mcp-Session-Id` is echoed on
 * every later request. Only what an agent needs: initialize, tools/list,
 * tools/call. Remote servers only -- there is no way to spawn a stdio server
 * on Android.
 */
class McpClient(private val config: McpServerConfig) {

    private val ids = AtomicInteger(1)
    @Volatile private var sessionId: String? = null
    @Volatile private var initialized = false

    private val http by lazy {
        AiHttp.client.newBuilder().readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    }

    suspend fun listTools(): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
            val out = mutableListOf<McpTool>()
            var cursor: String? = null
            repeat(10) {
                val params = buildJsonObject { cursor?.let { put("cursor", it) } }
                val result = rpc("tools/list", params)
                (result["tools"] as? JsonArray).orEmpty().forEach { t ->
                    val o = t as? JsonObject ?: return@forEach
                    val name = (o["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    out += McpTool(
                        config, name,
                        (o["description"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                        o["inputSchema"] ?: buildJsonObject { put("type", "object") }
                    )
                }
                cursor = (result["nextCursor"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching out
            }
            out
        }
    }

    /** Call a tool; returns its text content (and whether the server flagged an error). */
    suspend fun callTool(name: String, arguments: JsonObject): Result<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInitialized()
            val result = rpc("tools/call", buildJsonObject {
                put("name", name)
                put("arguments", arguments)
            })
            val isError = (result["isError"] as? JsonPrimitive)?.contentOrNull == "true"
            val text = (result["content"] as? JsonArray).orEmpty().joinToString("\n") { c ->
                val o = c as? JsonObject ?: return@joinToString ""
                when ((o["type"] as? JsonPrimitive)?.contentOrNull) {
                    "text" -> (o["text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                    "resource" -> ((o["resource"] as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull.orEmpty()
                    "image" -> "[image omitted]"
                    else -> o.toString().take(2_000)
                }
            }.ifBlank { result["structuredContent"]?.toString().orEmpty() }
            text to isError
        }
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            rpc("initialize", buildJsonObject {
                put("protocolVersion", PROTOCOL_VERSION)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") { put("name", "Oro"); put("version", "1.0") }
            })
            notify("notifications/initialized")
            initialized = true
        }
    }

    private fun notify(method: String) {
        val body = buildJsonObject { put("jsonrpc", "2.0"); put("method", method) }
        runCatching { http.newCall(request(body)).execute().close() }
    }

    private fun rpc(method: String, params: JsonObject): JsonObject {
        val id = ids.getAndIncrement()
        val body = buildJsonObject {
            put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params)
        }
        http.newCall(request(body)).execute().use { r ->
            r.header("Mcp-Session-Id")?.let { sessionId = it }
            val text = r.body?.string().orEmpty()
            if (r.code == 404 && sessionId != null) {
                // Session expired on the server: start over once.
                sessionId = null; initialized = false
                throw java.io.IOException("${config.name}: session expired, retry")
            }
            if (!r.isSuccessful) {
                throw java.io.IOException("${config.name} answered ${r.code}" +
                    (com.oro.app.data.ai.ChatStreamParser.describeErrorBody(text)?.let { ": $it" } ?: ""))
            }
            val message = if ((r.header("Content-Type") ?: "").contains("event-stream")) {
                text.lineSequence().filter { it.startsWith("data:") }.map { it.removePrefix("data:").trim() }
                    .mapNotNull { runCatching { AiHttp.json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                    .firstOrNull { (it["id"] as? JsonPrimitive)?.contentOrNull == id.toString() }
            } else {
                runCatching { AiHttp.json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            } ?: throw java.io.IOException("${config.name}: unreadable reply to $method")
            (message["error"] as? JsonObject)?.let { e ->
                throw java.io.IOException("${config.name}: ${(e["message"] as? JsonPrimitive)?.contentOrNull ?: "error"}")
            }
            return message["result"] as? JsonObject ?: JsonObject(emptyMap())
        }
    }

    private fun request(body: JsonObject): Request {
        val b = Request.Builder().url(config.url)
            .post(body.toString().toRequestBody(AiHttp.jsonMediaType))
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
        sessionId?.let { b.header("Mcp-Session-Id", it) }
        config.headers.forEach { (k, v) -> if (k.isNotBlank()) b.header(k, v) }
        return b.build()
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"
    }
}

/**
 * Process-wide MCP clients and the tools they offer, cached briefly so each
 * agent turn does not re-list every server.
 */
object McpRegistry {
    private val clients = java.util.concurrent.ConcurrentHashMap<String, Pair<McpServerConfig, McpClient>>()
    @Volatile private var cache: Pair<Long, List<McpTool>> = 0L to emptyList()
    private const val TTL_MS = 5 * 60_000L

    private fun client(config: McpServerConfig): McpClient {
        val existing = clients[config.id]
        if (existing != null && existing.first == config) return existing.second
        return McpClient(config).also { clients[config.id] = config to it }
    }

    /** Tools of every enabled server; servers that fail are skipped (and reported in [lastErrors]). */
    suspend fun tools(settings: McpSettings, force: Boolean = false): List<McpTool> {
        val now = System.currentTimeMillis()
        val enabled = settings.servers.filter { it.enabled }
        if (enabled.isEmpty()) return emptyList()
        if (!force && now - cache.first < TTL_MS && cache.second.map { it.server }.toSet() == enabled.toSet()) return cache.second
        val errors = mutableMapOf<String, String>()
        val all = enabled.flatMap { s ->
            client(s).listTools().getOrElse { errors[s.id] = it.message ?: "failed"; emptyList() }
        }.distinctBy { it.qualifiedName }
        lastErrors = errors
        cache = now to all
        return all
    }

    @Volatile var lastErrors: Map<String, String> = emptyMap()
        private set

    suspend fun test(config: McpServerConfig): Result<Int> = McpClient(config).listTools().map { it.size }

    /** Route a qualified tool call to its server. */
    suspend fun call(settings: McpSettings, qualifiedName: String, arguments: JsonObject): Result<Pair<String, Boolean>> {
        // Names arrive lower-cased by the alias layer.
        val tool = tools(settings).firstOrNull { it.qualifiedName.equals(qualifiedName, ignoreCase = true) }
            ?: return Result.failure(IllegalArgumentException("No MCP tool named $qualifiedName (server off or unreachable)."))
        return client(tool.server).callTool(tool.name, arguments)
    }
}
