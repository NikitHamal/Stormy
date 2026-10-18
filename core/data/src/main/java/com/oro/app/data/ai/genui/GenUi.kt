package com.oro.app.data.ai.genui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Generative UI in chat replies: a fenced ```ui block holding an A2UI
 * surface, rendered natively by the app (A2UI Compose) instead of as text.
 *
 * Models write the short form -- `{"components":[...], "data":{...}}` -- or
 * full A2UI v0.9 messages. [toA2uiMessages] turns either into the exact
 * message stream the renderer consumes, and repairs what weaker models get
 * wrong: nested child objects are hoisted into the flat component list,
 * `type` is accepted for `component`, missing ids are minted, and a root is
 * added when none is named `root`.
 */
object GenUi {

    const val FENCE = "ui"
    const val CATALOG_ID = "https://oro.app/a2ui/catalog/v1"
    private const val VERSION = "v0.9"

    private val json = Json { isLenient = true; ignoreUnknownKeys = true; allowTrailingComma = true }

    /** Fence languages that hold a UI block. */
    fun isUiFence(language: String?): Boolean = language?.trim()?.lowercase() in setOf("ui", "a2ui", "genui")

    sealed interface Result {
        data class Ok(val messages: List<String>) : Result
        data class Invalid(val reason: String) : Result
    }

    fun toA2uiMessages(block: String, surfaceId: String): Result {
        val text = block.trim()
        if (text.isEmpty()) return Result.Invalid("empty block")
        // Full protocol: one message per line (JSONL) or an array of messages.
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val lineObjects = if (lines.size > 1) lines.map { parse(it) as? JsonObject } else emptyList()
        val messages: List<JsonObject>? = when {
            lineObjects.isNotEmpty() && lineObjects.all { it != null && it.isProtocolMessage() } -> lineObjects.filterNotNull()
            else -> when (val root = parse(text)) {
                is JsonArray -> if (root.isNotEmpty() && root.all { (it as? JsonObject)?.isProtocolMessage() == true }) {
                    root.map { it as JsonObject }
                } else null
                is JsonObject -> if (root.isProtocolMessage()) listOf(root) else null
                else -> null
            }
        }
        if (messages != null) return Result.Ok(normalizeProtocol(messages, surfaceId))

        val root = parse(text) ?: return Result.Invalid("not valid JSON")
        val (components, data) = when (root) {
            is JsonArray -> root to null
            is JsonObject -> when (val c = root["components"]) {
                is JsonArray -> c to root["data"]
                // A single component object.
                else -> if (root.containsKey("component") || root.containsKey("type")) JsonArray(listOf(root)) to null
                else return Result.Invalid("no components")
            }
            else -> return Result.Invalid("no components")
        }
        val flat = flatten(components)
        if (flat.isEmpty()) return Result.Invalid("no components")
        val out = mutableListOf<String>()
        out += msg("createSurface", buildJsonObject { put("surfaceId", surfaceId); put("catalogId", CATALOG_ID) })
        out += msg("updateComponents", JsonObject(mapOf("surfaceId" to JsonPrimitive(surfaceId), "components" to JsonArray(flat))))
        if (data is JsonObject && data.isNotEmpty()) {
            out += msg("updateDataModel", JsonObject(mapOf("surfaceId" to JsonPrimitive(surfaceId), "path" to JsonPrimitive("/"), "value" to data)))
        }
        return Result.Ok(out)
    }

    private fun parse(s: String): JsonElement? = runCatching { json.parseToJsonElement(s) }.getOrNull()

    private fun JsonObject.isProtocolMessage() =
        keys.any { it in setOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface") }

    private fun msg(kind: String, body: JsonObject) =
        JsonObject(mapOf("version" to JsonPrimitive(VERSION), kind to body)).toString()

    /** Full messages: pin version, surface id and catalog (one block is one surface), flatten components. */
    private fun normalizeProtocol(messages: List<JsonObject>, surfaceId: String): List<String> {
        val out = messages.mapNotNull { m ->
            val kind = m.keys.firstOrNull { it in setOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface") } ?: return@mapNotNull null
            val body = (m[kind] as? JsonObject ?: JsonObject(emptyMap())).toMutableMap()
            body["surfaceId"] = JsonPrimitive(surfaceId)
            if (kind == "createSurface") body["catalogId"] = JsonPrimitive(CATALOG_ID)
            if (kind == "updateComponents") (body["components"] as? JsonArray)?.let { body["components"] = JsonArray(flatten(it)) }
            msg(kind, JsonObject(body))
        }.toMutableList()
        if (out.none { it.contains("\"createSurface\"") }) {
            out.add(0, msg("createSurface", buildJsonObject { put("surfaceId", surfaceId); put("catalogId", CATALOG_ID) }))
        }
        return out
    }

    /**
     * Flat A2UI component list from what a model wrote. Children written
     * inline as objects become separate components referenced by id.
     */
    internal fun flatten(components: JsonArray): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        val used = mutableSetOf<String>()
        var counter = 0
        fun mint(prefix: String): String {
            var id: String
            do { id = "${prefix.lowercase()}_${++counter}" } while (id in used)
            return id
        }

        fun visit(node: JsonObject): String {
            val m = node.toMutableMap()
            if (!m.containsKey("component")) m["type"]?.let { m["component"] = it }
            m.remove("type")
            val type = (m["component"] as? JsonPrimitive)?.contentOrNull ?: "Text"
            m["component"] = JsonPrimitive(type)
            val id = (m["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it !in used } ?: mint(type)
            used += id
            m["id"] = JsonPrimitive(id)
            // Bare strings as Text content, a common shortcut.
            if (type == "Text" && m["text"] == null) m["content"]?.let { m["text"] = it; m.remove("content") }
            for (key in listOf("child", "trigger", "content")) {
                if (type == "Text" && key == "content") continue
                (m[key] as? JsonObject)?.let { child -> m[key] = JsonPrimitive(visit(child)) }
            }
            when (val children = m["children"]) {
                is JsonArray -> m["children"] = JsonArray(children.map { c ->
                    when (c) {
                        is JsonObject -> JsonPrimitive(visit(c))
                        else -> c
                    }
                })
                else -> {}
            }
            (m["tabs"] as? JsonArray)?.let { tabs ->
                m["tabs"] = JsonArray(tabs.map { t ->
                    val tab = (t as? JsonObject)?.toMutableMap() ?: return@map t
                    (tab["child"] as? JsonObject)?.let { tab["child"] = JsonPrimitive(visit(it)) }
                    JsonObject(tab)
                })
            }
            out += JsonObject(m)
            return id
        }

        val tops = components.mapNotNull { it as? JsonObject }.map { visit(it) }
        // Children are visited first, so the list ends with the outermost components.
        if (out.none { (it["id"] as? JsonPrimitive)?.contentOrNull == "root" }) {
            // The top-level components that nobody references become the root's children.
            val referenced = out.flatMap { c ->
                listOfNotNull(c["child"], c["trigger"], c["content"].takeIf { (c["component"] as? JsonPrimitive)?.contentOrNull != "Text" }) +
                    ((c["children"] as? JsonArray).orEmpty()) +
                    ((c["tabs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.get("child") })
            }.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            val roots = tops.filter { it !in referenced }
            if (roots.size == 1) {
                val i = out.indexOfFirst { (it["id"] as? JsonPrimitive)?.contentOrNull == roots[0] }
                val renamed = out[i].toMutableMap().apply { put("id", JsonPrimitive("root")) }
                out[i] = JsonObject(renamed)
                // Re-point references to the renamed component.
                for (j in out.indices) out[j] = repoint(out[j], roots[0], "root")
            } else if (roots.isNotEmpty()) {
                out += JsonObject(mapOf(
                    "id" to JsonPrimitive("root"), "component" to JsonPrimitive("Column"),
                    "children" to JsonArray(roots.map { JsonPrimitive(it) })
                ))
            }
        }
        return out
    }

    private fun repoint(c: JsonObject, from: String, to: String): JsonObject {
        val m = c.toMutableMap()
        for (key in listOf("child", "trigger", "content")) {
            if ((m[key] as? JsonPrimitive)?.contentOrNull == from) m[key] = JsonPrimitive(to)
        }
        (m["children"] as? JsonArray)?.let { arr ->
            m["children"] = JsonArray(arr.map { if ((it as? JsonPrimitive)?.contentOrNull == from) JsonPrimitive(to) else it })
        }
        return JsonObject(m)
    }

    /** How a tap in a UI block reaches the model, as the next user message. */
    fun actionMessage(name: String, context: Map<String, Any?>): String {
        if (name == "say" || name == "suggestion") {
            (context["text"] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val ctx = context.entries.joinToString(", ") { (k, v) -> "$k: $v" }
        return "[UI action: $name" + (if (ctx.isNotEmpty()) " — $ctx" else "") + "]"
    }

    /** System prompt section that teaches the format. Kept short: free models have small windows. */
    val PROMPT = """

# Rich answers (UI blocks)
When an answer is clearer as an interface than as prose -- options to pick from, a comparison, a table, numbers, steps, a palette, a small calculator, a summary card -- put a UI block in your reply. It renders as native app UI. Plain conversational answers stay plain text. Never put code the user must copy in a UI block; use normal code fences for code.

Write a fenced block with the language `ui` holding JSON: {"components":[...], "data":{...}}. Each component: {"id":"...", "component":"Name", ...props}. Children may be written inline as objects. The outermost component is the root.

Components:
- Layout: Column{children, justify, align}, Row{children, justify, align}, Card{child}, List{children, direction}, Tabs{tabs:[{title, child}]}, Divider{}, Modal{trigger, content}
- Content: Text{text, variant: h1|h2|h3|h4|h5|body|caption} (light markdown ok), Image{url, variant}, Icon{name}, Badge{text, tone}, Callout{title, text, tone: info|success|warning|danger}, Stat{label, value, delta, tone}, Table{columns:[..], rows:[[..],..]}, Chart{kind: bar|line|pie, title, labels:[..], values:[..]}, Steps{items:[..], current}, Progress{value 0..1, label}, Swatches{colors:["#hex",..], labels}, Code{code, language}, Link{text, url}
- Input: Button{child, variant: primary|default|borderless, action}, TextField{label, value}, CheckBox{label, value}, ChoicePicker{label, options:[{label, value}], value:[..], variant: mutuallyExclusive|multipleSelection, displayStyle: chips|checkbox}, Slider{label, min, max, value}, DateTimeInput{value, enableDate, enableTime}
- Suggestions{items:["...",..]}: tappable follow-ups; a tap sends that text as the user's next message.

State: put values in "data" and bind with {"path":"/name"}; inputs bound to a path update it live, and Text/Stat bound to the same path update with them. Formatting: {"call":"formatString","args":{"value":"Total: ${'$'}{/price}"}}.
Actions: a Button's action {"event":{"name":"choose","context":{"plan":"pro"}}} sends the choice back to you as the next message. Use {"event":{"name":"say","context":{"text":"..."}}} to send a ready-made reply.
Keep blocks small (under ~40 components) and always add one sentence of plain text before or after the block.
""".trimEnd()
}
