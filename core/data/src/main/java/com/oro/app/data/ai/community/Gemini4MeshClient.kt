package com.oro.app.data.ai.community

import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ChatCompletionRequest
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.ChatStreamParser
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.agent.TextToolProtocol
import com.oro.app.data.ai.community.CommunityHeaders.site
import com.oro.app.data.ai.encodeChatRequest
import com.oro.app.data.ai.providers.AnthropicClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * Google Gemini 4 Family & Antigravity Cloud Code Hub client, ported from
 * ZeusDesigns (`stream_antigravity`, `stream_gemini4hydrogen`, `stream_gemini4`,
 * `stream_gemini4_direct`, `stream_gemini4_cluster`, `stream_freecc`).
 *
 * Load-balances and fails over across:
 * 1. Google Antigravity & Cloud Code Hub (`w902287-firebase-gemini-proxy.hf.space/ag/v1/chat/completions`
 *    with `Bearer sk-antigravity` — supports native tool calling and multimodal vision).
 * 2. Direct anonymous Google Gemini 4 `StreamGenerate` (`gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate`
 *    with `MODE_CATEGORY` `inner[79]` = 1..6 and `think_mode` `inner[17]` = 0/4, automatic `bl` refresh on 405,
 *    and `wrb.fr` snapshot delta extraction).
 * 3. Multi-node `gemini-web2api` OpenAI cluster (`chenian233-gemini-web2api.hf.space/v1/chat/completions`,
 *    `giabaopro1908-gemini2api.hf.space/v1/chat/completions`).
 * 4. FreeClaude Gemini 4 Anthropic Messages SSE (`calkotter-free-claude-code.hf.space/v1/messages`
 *    and `dherain24-claude-proxy.hf.space/v1/messages`).
 */
class Gemini4MeshClient(
    private val antigravityEndpoint: String = ANTIGRAVITY_ENDPOINT
) : CommunityChatClient {

    override fun takesTools(provider: AiProvider): Boolean = true

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> =
        stream(model, messages, null, null)

    override fun stream(model: AiModel, messages: List<ChatRequestMessage>, reasoningLevel: String?): Flow<StreamEvent> =
        stream(model, messages, reasoningLevel, null)

    override fun stream(
        model: AiModel,
        messages: List<ChatRequestMessage>,
        reasoningLevel: String?,
        tools: List<Tool>?
    ): Flow<StreamEvent> = CommunityHttp.guarded(model.provider.displayName) {
        val rawUpstream = CommunityClients.upstreamId(model)
        val resolvedModel = applyEffortSuffix(rawUpstream, reasoningLevel)
        val hasImages = messages.any { it.images.isNotEmpty() }

        // 1. For Antigravity, or whenever native tools or images are present on Gemini 4 Mesh,
        // try Antigravity Hub first (supports native OpenAI tool_calls + multimodal image_url).
        if (model.provider == AiProvider.ANTIGRAVITY || hasImages || !tools.isNullOrEmpty()) {
            val agModel = toAntigravityModel(resolvedModel)
            if (tryOpenAiTarget(antigravityEndpoint, agModel, messages, tools, bearer = "sk-antigravity")) {
                return@guarded
            }
            // If a Claude model was requested via Antigravity, fail over to the Claude 5.5 Messages proxy
            if (resolvedModel.startsWith("claude-")) {
                val claudeModel = when {
                    resolvedModel.startsWith("claude-opus-5-5") -> "claude-sonnet-5-5"
                    resolvedModel.startsWith("claude-haiku-5-5") -> "claude-haiku-5-5"
                    else -> "claude-sonnet-5-5"
                }
                if (tryAnthropicTarget("https://dherain24-claude-proxy.hf.space/v1/messages", claudeModel, messages, tools)) {
                    return@guarded
                }
                if (tryAnthropicTarget("https://calkotter-free-claude-code.hf.space/v1/messages", claudeModel, messages, tools)) {
                    return@guarded
                }
            }
        }

        // Prepare messages with inline text tool definitions when falling back to text-only Gemini 4 nodes
        val textModeMessages = if (!tools.isNullOrEmpty()) injectTextTools(messages, tools) else messages

        // 2. Direct anonymous Google Gemini 4 StreamGenerate (fastest ~2s, zero HF Space cooldowns)
        if (!hasImages && !resolvedModel.startsWith("claude-") && !resolvedModel.startsWith("gpt-oss")) {
            val (modeId, thinkMode, _) = resolveGemini4Mode(resolvedModel)
            if (tryDirectStreamGenerate(modeId, thinkMode, textModeMessages)) {
                return@guarded
            }
        }

        // 3. Multi-node 5-node gemini-web2api OpenAI cluster
        val (_, _, legacyAlias) = resolveGemini4Mode(resolvedModel)
        val chenianModel = resolveChenianModel(resolvedModel)
        val compactedWeb2ApiMsgs = compactGeminiWeb2ApiMessages(textModeMessages)
        val clusterTargets = listOf(
            "https://chenian233-gemini-web2api.hf.space/v1/chat/completions" to chenianModel,
            "https://pouluo-gemini-web2api.hf.space/v1/chat/completions" to chenianModel,
            "https://peijun1-gemini-web2api.hf.space/v1/chat/completions" to legacyAlias,
            "https://peijun1-1-gemini-web2api.hf.space/v1/chat/completions" to legacyAlias,
            "https://giabaopro1908-gemini2api.hf.space/v1/chat/completions" to legacyAlias,
            antigravityEndpoint to toAntigravityModel(resolvedModel)
        )
        for ((endpoint, targetModel) in clusterTargets) {
            val bearer = if (endpoint == antigravityEndpoint) "sk-antigravity" else null
            val targetTools = if (endpoint == antigravityEndpoint) tools else null
            val targetMsgs = when {
                endpoint == antigravityEndpoint -> messages
                endpoint.contains("gemini-web2api") || endpoint.contains("gemini2api") -> compactedWeb2ApiMsgs
                else -> textModeMessages
            }
            if (tryOpenAiTarget(endpoint, targetModel, targetMsgs, targetTools, bearer = bearer)) {
                return@guarded
            }
        }

        // 4. FreeClaude Gemini 4 Hydrogen / Flash Anthropic Messages fallback
        val freeccModel = if (resolvedModel.contains("gemini-3.5-flash")) {
            "claude-3-freecc-no-thinking/gemini/models/gemini-3.5-flash"
        } else {
            "claude-3-freecc-no-thinking/gemini/models/gemini-4-hydrogen"
        }
        if (tryAnthropicTarget("https://calkotter-free-claude-code.hf.space/v1/messages", freeccModel, messages, tools)) {
            return@guarded
        }

        emit(
            StreamEvent.Error(
                "${model.provider.displayName}: all Gemini 4 mesh nodes are temporarily busy",
                httpCode = 503,
                retryable = true
            )
        )
    }

    private suspend fun FlowCollector<StreamEvent>.tryOpenAiTarget(
        endpoint: String,
        targetModel: String,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>?,
        bearer: String?
    ): Boolean {
        val chatReq = ChatCompletionRequest(
            model = targetModel,
            messages = messages,
            stream = true,
            temperature = null,
            maxTokens = 16384,
            tools = tools,
            toolChoice = if (!tools.isNullOrEmpty()) "auto" else null
        )
        val bodyStr = encodeChatRequest(AiHttp.json, chatReq)
        val ip = KeylessCrypto.fakeIpv4()
        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(bodyStr.toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream, application/json")
            .header("User-Agent", CommunityHeaders.USER_AGENT)
            .header("X-Forwarded-For", ip)
            .header("X-Real-IP", ip)
        if (!bearer.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $bearer")
        }
        return runCatching {
            CommunityHttp.execute(reqBuilder.build()) { response ->
                if (!response.isSuccessful) return@execute false
                val source = response.body?.source() ?: return@execute false
                val parser = ChatStreamParser()
                var started = false
                var rejected = false
                val firstText = StringBuilder()
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    val data = when {
                        trimmed.startsWith("data:") -> trimmed.removePrefix("data:").trim()
                        trimmed.startsWith("{") -> trimmed
                        else -> continue
                    }
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val events = parser.onData(data)
                    for (ev in events) {
                        if (ev is StreamEvent.Error) {
                            if (!started) rejected = true
                            break
                        }
                        if (ev is StreamEvent.ContentDelta && firstText.length < 160) {
                            firstText.append(ev.content)
                            if (KeylessCrypto.isUpstreamErrorText(firstText.toString()) && !started) {
                                rejected = true
                                break
                            }
                        }
                        if (!started) {
                            started = true
                            emit(StreamEvent.Started)
                        }
                        emit(ev)
                    }
                    if (rejected) break
                }
                if (rejected || !parser.hasOutput) return@execute false
                if (!started) emit(StreamEvent.Started)
                parser.finish().forEach { emit(it) }
                true
            }
        }.getOrDefault(false)
    }

    private suspend fun FlowCollector<StreamEvent>.tryAnthropicTarget(
        endpoint: String,
        targetModel: String,
        messages: List<ChatRequestMessage>,
        tools: List<Tool>?
    ): Boolean {
        val body = AnthropicClient.buildBody(
            model = targetModel,
            messages = messages,
            tools = tools,
            temperature = null,
            maxTokens = 16384
        )
        val ip = KeylessCrypto.fakeIpv4()
        val request = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("x-api-key", "free")
            .header("anthropic-version", AnthropicClient.ANTHROPIC_VERSION)
            .header("X-Forwarded-For", ip)
            .header("X-Real-IP", ip)
            .build()
        return runCatching {
            CommunityHttp.execute(request) { response ->
                if (!response.isSuccessful) return@execute false
                val source = response.body?.source() ?: return@execute false
                val parser = com.oro.app.data.ai.providers.AnthropicStreamParser("Gemini4Mesh")
                var started = false
                var sawOutput = false
                while (!parser.finished) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val events = parser.onData(data)
                    for (ev in events) {
                        if (ev is StreamEvent.Error && !sawOutput) return@execute false
                        if (ev is StreamEvent.ContentDelta && KeylessCrypto.isUpstreamErrorText(ev.content) && !sawOutput) {
                            return@execute false
                        }
                        if (!started) {
                            started = true
                            emit(StreamEvent.Started)
                        }
                        if (ev is StreamEvent.ContentDelta || ev is StreamEvent.ReasoningDelta || ev is StreamEvent.ToolCalls) {
                            sawOutput = true
                        }
                        emit(ev)
                    }
                }
                if (!sawOutput) return@execute false
                parser.finish().forEach { emit(it) }
                true
            }
        }.getOrDefault(false)
    }

    private suspend fun FlowCollector<StreamEvent>.tryDirectStreamGenerate(
        modeId: Int,
        thinkMode: Int,
        messages: List<ChatRequestMessage>
    ): Boolean {
        val prompt = buildStreamGeneratePrompt(messages, thinkMode == 0 || modeId in setOf(2, 3, 5))
        if (prompt.isBlank()) return false
        val body = buildStreamGenerateFormBody(prompt, modeId, thinkMode)
        var bl = cachedBl
        for (attempt in 0..1) {
            val reqId = 100_000 + ThreadLocalRandom.current().nextInt(800_000)
            val encodedBl = URLEncoder.encode(bl, "UTF-8")
            val url = "https://gemini.google.com/_/BardChatUi/data/assistant.lamda.BardFrontendService/StreamGenerate?bl=$encodedBl&hl=en&_reqid=$reqId&rt=c"
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody(FORM))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .site("https://gemini.google.com", "https://gemini.google.com/app")
                .header("X-Same-Domain", "1")
                .build()
            val ok = runCatching {
                CommunityHttp.execute(request) { response ->
                    if (response.code == 405 && attempt == 0) {
                        refreshBl()?.let { cachedBl = it; bl = it }
                        return@execute false
                    }
                    if (!response.isSuccessful) return@execute false
                    val source = response.body?.source() ?: return@execute false
                    val parser = WrbFrSnapshotParser()
                    var started = false
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        val deltas = parser.onLine(line)
                        for (delta in deltas) {
                            if (!started) {
                                started = true
                                emit(StreamEvent.Started)
                            }
                            emit(delta)
                        }
                    }
                    if (!parser.sawOutput) return@execute false
                    emit(StreamEvent.FinishReason("stop"))
                    emit(StreamEvent.Completed)
                    true
                }
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private suspend fun refreshBl(): String? = runCatching {
        val req = Request.Builder()
            .url("https://gemini.google.com/app")
            .get()
            .site("https://gemini.google.com", "https://gemini.google.com/app")
            .build()
        CommunityHttp.execute(req, AiHttp.client) { resp ->
            val html = resp.body?.string().orEmpty()
            BL_REGEX.find(html)?.value
        }
    }.getOrNull()

    companion object {
        const val ANTIGRAVITY_ENDPOINT = "https://w902287-firebase-gemini-proxy.hf.space/ag/v1/chat/completions"
        private val JSON = "application/json".toMediaType()
        private val FORM = "application/x-www-form-urlencoded".toMediaType()
        private val BL_REGEX = Regex("""boq_assistant-bard-web-server_[A-Za-z0-9_.]+""")

        @Volatile
        private var cachedBl: String = "boq_assistant-bard-web-server_20260716.08_p0"

        /**
         * Maps a Gemini 4 model id and optional reasoning effort level to the
         * upstream model id expected by Antigravity / Gemini 4 Mesh.
         */
        fun applyEffortSuffix(upstreamId: String, reasoningLevel: String?): String {
            if (reasoningLevel.isNullOrBlank()) return upstreamId
            val base = upstreamId
                .removeSuffix("-xhigh")
                .removeSuffix("-high")
                .removeSuffix("-medium")
                .removeSuffix("-low")
            return when {
                base in setOf("gemini-4-hydrogen", "gemini-4-neon", "gemini-4-carbon", "gemini-4-argon", "claude-opus-5-5", "claude-sonnet-5-5", "gemini-3.8-flash") &&
                    reasoningLevel in setOf("low", "medium", "high", "xhigh") -> "$base-$reasoningLevel"
                else -> upstreamId
            }
        }

        fun toAntigravityModel(modelId: String): String = when (modelId) {
            "gemini-4-hydrogen", "gemini-hydrogen" -> "gemini-4-hydrogen-medium"
            "gemini-4-hydrogen-thinking" -> "gemini-4-hydrogen-high"
            "gemini-4-neon", "gemini-neon", "gemini-3.5-flash-thinking" -> "gemini-4-neon-medium"
            "gemini-4-neon-thinking" -> "gemini-4-neon-high"
            "gemini-4-carbon", "gemini-3.1-pro" -> "gemini-4-carbon-medium"
            "gemini-4-argon" -> "gemini-4-argon-medium"
            "gemini-auto", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash" -> "gemini-4-hydrogen-medium"
            "gemini-flash-lite" -> "gemini-4-hydrogen-low"
            else -> modelId
        }

        /**
         * Returns `(modeId, thinkMode, legacyAlias)` for Google Gemini 4 `StreamGenerate`.
         */
        fun resolveGemini4Mode(modelId: String): Triple<Int, Int, String> = when (modelId) {
            "gemini-4-hydrogen-thinking",
            "gemini-4-hydrogen-xhigh",
            "gemini-4-hydrogen-high",
            "gemini-4-hydrogen-tiered-xhigh",
            "gemini-4-hydrogen-tiered-high",
            "gemini-4-neon",
            "gemini-4-neon-xhigh",
            "gemini-4-neon-high",
            "gemini-4-neon-medium",
            "gemini-4-neon-thinking",
            "gemini-pro-orchestrator-neon",
            "gemini-3.5-flash-thinking",
            "gemini-2.5-flash-thinking" -> Triple(2, 0, "gemini-3.5-flash-thinking")

            "gemini-4-carbon",
            "gemini-4-carbon-high",
            "gemini-4-carbon-medium",
            "gemini-4-carbon-low",
            "gemini-4-argon",
            "gemini-4-argon-high",
            "gemini-4-argon-medium",
            "gemini-4-argon-low",
            "gemini-pro-orchestrator",
            "gemini-pro-orchestrator-argon",
            "gemini-3.1-pro" -> Triple(3, 4, "gemini-3.1-pro")

            "gemini-auto" -> Triple(4, 4, "gemini-auto")
            "gemini-3.5-flash-thinking-lite", "gemini-3.8-flash-tiered" -> Triple(5, 0, "gemini-3.5-flash-thinking-lite")
            "gemini-flash-lite", "gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "gemini-2.5-flash-lite" ->
                Triple(6, 4, "gemini-flash-lite")
            else -> Triple(1, 4, "gemini-3.5-flash")
        }

        fun resolveChenianModel(modelId: String): String = when (modelId) {
            "gemini-4-hydrogen",
            "gemini-4-hydrogen-thinking",
            "gemini-4-neon",
            "gemini-4-neon-thinking",
            "gemini-4-carbon",
            "gemini-auto",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-thinking",
            "gemini-3.5-flash-thinking-lite",
            "gemini-flash-lite" -> modelId
            else -> {
                val (modeId, _, _) = resolveGemini4Mode(modelId)
                when (modeId) {
                    2 -> "gemini-4-hydrogen-thinking"
                    3 -> "gemini-4-carbon"
                    else -> "gemini-4-hydrogen"
                }
            }
        }

        fun injectTextTools(messages: List<ChatRequestMessage>, tools: List<Tool>): List<ChatRequestMessage> {
            val toolSection = TextToolProtocol.render(tools, wholeFiles = true)
            val hasSys = messages.any { it.role == "system" }
            if (!hasSys) {
                return listOf(ChatRequestMessage(role = "system", content = toolSection)) + messages
            }
            return messages.mapIndexed { idx, msg ->
                if (idx == 0 && msg.role == "system" && msg.content?.contains("<tool_call>") != true) {
                    msg.copy(content = "${msg.content.orEmpty()}\n\n$toolSection")
                } else msg
            }
        }

        /**
         * Preserves multi-turn conversation history when routing through `gemini-web2api` / `gemini2api`
         * gateways that only read the system message and final user message.
         */
        fun compactGeminiWeb2ApiMessages(messages: List<ChatRequestMessage>): List<ChatRequestMessage> {
            val turns = Transcript.turns(messages)
            val nonSys = turns.filter { it.role != "system" }
            if (nonSys.size <= 1) return messages
            val prior = nonSys.dropLast(1).joinToString("\n\n") { t ->
                val tag = if (t.role == "assistant") "[Assistant]" else "[User]"
                "$tag: ${t.text.take(4_000)}"
            }
            val lastUser = nonSys.last().text
            val mergedUser = "Prior conversation & tool turns:\n$prior\n\nCurrent turn:\n$lastUser"
            val sysMsgs = messages.filter { it.role == "system" }
            return sysMsgs + ChatRequestMessage(role = "user", content = mergedUser)
        }

        fun buildStreamGeneratePrompt(messages: List<ChatRequestMessage>, thinking: Boolean): String {
            val turns = Transcript.turns(messages)
            if (turns.none { it.role == "user" }) return ""
            return buildString {
                if (thinking) {
                    append("[Thinking Mode Enabled]: Begin your response with a concise <think>...</think> reasoning block analyzing the request, then provide your final answer after </think>.\n\n")
                }
                val systems = turns.filter { it.role == "system" }
                if (systems.isNotEmpty()) {
                    val sysText = systems.joinToString("\n\n") { it.text }.take(24_000)
                    append("[System instruction]: ").append(sysText).append("\n\n")
                }
                val convo = turns.filter { it.role != "system" }
                convo.dropLast(1).forEach { turn ->
                    val tag = if (turn.role == "assistant") "[Assistant]" else "[User]"
                    append(tag).append(": ").append(turn.text).append("\n\n")
                }
                convo.lastOrNull()?.let { last ->
                    append("[User]: ").append(last.text)
                }
            }
        }

        fun buildStreamGenerateFormBody(prompt: String, modeId: Int, thinkMode: Int): String {
            val inner = MutableList<kotlinx.serialization.json.JsonElement>(80) { JsonNull }
            inner[0] = buildJsonArray {
                add(JsonPrimitive(prompt))
                add(JsonPrimitive(0))
                add(JsonNull)
                add(JsonNull)
                add(JsonNull)
                add(JsonNull)
                add(JsonPrimitive(0))
            }
            inner[1] = buildJsonArray { add(JsonPrimitive("en")) }
            inner[2] = buildJsonArray {
                add(JsonPrimitive(""))
                add(JsonPrimitive(""))
                add(JsonPrimitive(""))
                repeat(6) { add(JsonNull) }
                add(JsonPrimitive(""))
            }
            inner[6] = buildJsonArray { add(JsonPrimitive(0)) }
            inner[7] = JsonPrimitive(1)
            inner[10] = JsonPrimitive(1)
            inner[11] = JsonPrimitive(0)
            inner[17] = buildJsonArray { addJsonArray { add(JsonPrimitive(thinkMode)) } }
            inner[18] = JsonPrimitive(0)
            inner[27] = JsonPrimitive(1)
            inner[30] = buildJsonArray { add(JsonPrimitive(4)) }
            inner[41] = buildJsonArray { add(JsonPrimitive(2)) }
            inner[53] = JsonPrimitive(0)
            inner[59] = JsonPrimitive(UUID.randomUUID().toString())
            inner[61] = buildJsonArray {}
            inner[68] = JsonPrimitive(1)
            inner[79] = JsonPrimitive(modeId)

            val innerJson = JsonArray(inner).toString()
            val outerJson = buildJsonArray {
                add(JsonNull)
                add(JsonPrimitive(innerJson))
            }.toString()
            return "f.req=" + URLEncoder.encode(outerJson, "UTF-8")
        }
    }
}

/**
 * Stateful parser for Google Gemini `StreamGenerate` `wrb.fr` cumulative snapshots,
 * including anchor-based recovery when Gemini rewrites markdown fences mid-stream.
 */
class WrbFrSnapshotParser {
    private var prevText = ""
    private var inThink = false
    var sawOutput = false
        private set

    fun onLine(line: String): List<StreamEvent> {
        val trimmed = line.trim()
        if (!trimmed.contains("\"wrb.fr\"") || trimmed.length <= 40) return emptyList()
        val arr = runCatching { AiHttp.json.parseToJsonElement(trimmed) as? JsonArray }.getOrNull()
            ?: return emptyList()
        val out = mutableListOf<StreamEvent>()
        for (entryEl in arr) {
            val entry = entryEl as? JsonArray ?: continue
            if ((entry.getOrNull(0) as? JsonPrimitive)?.contentOrNull != "wrb.fr") continue
            val innerStr = (entry.getOrNull(2) as? JsonPrimitive)?.contentOrNull ?: continue
            val innerVal = runCatching { AiHttp.json.parseToJsonElement(innerStr) as? JsonArray }.getOrNull() ?: continue
            val parts = innerVal.getOrNull(4) as? JsonArray ?: continue
            for (partEl in parts) {
                val part = partEl as? JsonArray ?: continue
                val texts = part.getOrNull(1) as? JsonArray ?: continue
                for (tEl in texts) {
                    val snapRaw = (tEl as? JsonPrimitive)?.contentOrNull ?: continue
                    val snap = cleanGeminiCodeBlocks(snapRaw)
                    if (snap.isEmpty()) continue
                    val delta = computeSnapshotDelta(prevText, snap)
                    if (delta.isNotEmpty()) {
                        sawOutput = true
                        out += routeThinkState(delta)
                    }
                    if (snap.length >= prevText.length) {
                        prevText = snap
                    }
                }
            }
        }
        return out
    }

    private fun routeThinkState(delta: String): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        var rem = delta
        while (rem.isNotEmpty()) {
            if (!inThink) {
                val openIdx = rem.indexOf("<think>")
                if (openIdx < 0) {
                    events += StreamEvent.ContentDelta(rem)
                    break
                }
                if (openIdx > 0) {
                    events += StreamEvent.ContentDelta(rem.substring(0, openIdx))
                }
                inThink = true
                rem = rem.substring(openIdx + 7)
            } else {
                val closeIdx = rem.indexOf("</think>")
                if (closeIdx < 0) {
                    events += StreamEvent.ReasoningDelta(rem)
                    break
                }
                if (closeIdx > 0) {
                    events += StreamEvent.ReasoningDelta(rem.substring(0, closeIdx))
                }
                inThink = false
                rem = rem.substring(closeIdx + 8)
            }
        }
        return events
    }

    companion object {
        private val CODE_EXEC_MARKERS = listOf(
            "```python?code_reference",
            "```javascript?code_reference",
            "```text?code_stdout",
            "```text?code_reference"
        )

        /**
         * Strips internal Gemini Web code execution blocks (` ```python?code_reference...``` `
         * and ` ```text?code_stdout...``` `), including unclosed trailing blocks mid-stream
         * so `prevText` prefix matching never breaks.
         */
        fun cleanGeminiCodeBlocks(raw: String): String {
            var out = raw
            for (marker in CODE_EXEC_MARKERS) {
                while (true) {
                    val start = out.indexOf(marker)
                    if (start < 0) break
                    val after = start + marker.length
                    val closeRel = out.indexOf("```", startIndex = after)
                    if (closeRel >= 0) {
                        var end = closeRel + 3
                        while (end < out.length && out[end] == '\n') end++
                        out = out.removeRange(start, end)
                    } else {
                        out = out.substring(0, start)
                        break
                    }
                }
            }
            return out
        }

        fun computeSnapshotDelta(prev: String, snap: String): String {
            if (prev.isEmpty()) return snap
            if (snap.length > prev.length && snap.startsWith(prev)) {
                return snap.substring(prev.length)
            }
            if (snap.length > prev.length) {
                val commonPrefix = prev.commonPrefixWith(snap).length
                val anchor = prev.takeLast(12)
                val resumeFrom = if (anchor.isNotEmpty()) {
                    val idx = snap.lastIndexOf(anchor)
                    if (idx >= 0) idx + anchor.length else commonPrefix
                } else commonPrefix
                if (resumeFrom < snap.length) {
                    return snap.substring(resumeFrom)
                }
            }
            return ""
        }
    }
}
