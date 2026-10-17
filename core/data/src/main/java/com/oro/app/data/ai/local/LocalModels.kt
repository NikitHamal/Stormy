package com.oro.app.data.ai.local

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.oro.app.data.ai.AiHttp
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.community.CommunityChatClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** A downloadable on-device model. */
data class LocalModelSpec(
    val id: String,
    val name: String,
    val url: String,
    val bytes: Long,
    /** Input + output tokens the model was converted for. */
    val maxTokens: Int,
    val note: String
) {
    val fileName: String get() = url.substringAfterLast('/')
}

/**
 * On-device LLMs through MediaPipe LLM Inference: download, load, stream.
 *
 * Models are LiteRT `.task` builds of Qwen 2.5 from litert-community on
 * Hugging Face (Apache-2.0, no account or licence click needed). They run on
 * the CPU; small enough for mid-range phones, and fully offline once
 * downloaded. One engine is kept loaded at a time (they are hundreds of MB).
 */
object LocalModels {

    val catalogue = listOf(
        LocalModelSpec(
            "local:qwen2.5-0.5b", "Qwen 2.5 0.5B",
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            547_000_000L, 1_280, "Fast on most phones. Short answers and small edits."
        ),
        LocalModelSpec(
            "local:qwen2.5-1.5b", "Qwen 2.5 1.5B",
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
            1_599_000_000L, 4_096, "Smarter, 4K context. Needs ~3 GB of free RAM."
        )
    )

    sealed interface State {
        data object Missing : State
        data class Downloading(val fraction: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _states = MutableStateFlow<Map<String, State>>(emptyMap())
    val states: StateFlow<Map<String, State>> = _states

    @Volatile private var appContext: Context? = null
    fun init(context: Context) {
        appContext = context.applicationContext
        refresh()
    }

    private fun dir(): File = File(requireNotNull(appContext) { "LocalModels.init not called" }.filesDir, "llm")
    fun file(spec: LocalModelSpec) = File(dir(), spec.fileName)
    fun spec(modelId: String) = catalogue.firstOrNull { it.id == modelId }

    fun refresh() {
        if (appContext == null) return
        _states.value = catalogue.associate { s ->
            s.id to if (file(s).isFile && file(s).length() > s.bytes * 0.95) State.Ready else (_states.value[s.id] as? State.Downloading ?: State.Missing)
        }
    }

    /** Resumable download (Range) with progress. */
    suspend fun download(spec: LocalModelSpec): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dir().mkdirs()
            val part = File(dir(), spec.fileName + ".part")
            val have = part.length()
            _states.update { it + (spec.id to State.Downloading(have.toFloat() / spec.bytes)) }
            val client = AiHttp.client.newBuilder().readTimeout(90, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
            val req = Request.Builder().url(spec.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (${r.code})")
                val append = r.code == 206
                val body = r.body ?: error("Empty download")
                var done = if (append) have else 0L
                val total = if (append) have + body.contentLength() else body.contentLength().takeIf { it > 0 } ?: spec.bytes
                java.io.FileOutputStream(part, append).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(256 * 1024)
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - last > 4_000_000) {
                                last = done
                                _states.update { it + (spec.id to State.Downloading((done.toFloat() / total).coerceIn(0f, 1f))) }
                            }
                        }
                    }
                }
            }
            if (!part.renameTo(file(spec))) error("Could not save the model")
            _states.update { it + (spec.id to State.Ready) }
        }.onFailure { e -> _states.update { it + (spec.id to State.Failed(e.message ?: "Download failed")) } }
    }

    fun delete(spec: LocalModelSpec) {
        if (loadedId == spec.id) unload()
        file(spec).delete()
        File(dir(), spec.fileName + ".part").delete()
        refresh()
    }

    // ------------------------------------------------------------- engine

    @Volatile private var engine: LlmInference? = null
    @Volatile private var loadedId: String? = null
    private val lock = Any()

    private fun engineFor(spec: LocalModelSpec): LlmInference = synchronized(lock) {
        engine?.takeIf { loadedId == spec.id }?.let { return it }
        unload()
        val ctx = requireNotNull(appContext)
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file(spec).absolutePath)
            .setMaxTokens(spec.maxTokens)
            .setMaxTopK(40)
            .build()
        LlmInference.createFromOptions(ctx, options).also { engine = it; loadedId = spec.id }
    }

    fun unload() = synchronized(lock) {
        runCatching { engine?.close() }
        engine = null
        loadedId = null
    }

    /**
     * Qwen chat template, trimmed from the oldest turns to fit the model's
     * window (roughly 3.5 characters a token), always keeping the system
     * turn and the newest user turn.
     */
    fun prompt(messages: List<ChatRequestMessage>, maxTokens: Int): String {
        val budgetChars = ((maxTokens * 0.7) * 3.5).toInt()
        fun turn(role: String, text: String) = "<|im_start|>$role\n$text<|im_end|>\n"
        val turns = com.oro.app.data.ai.community.Transcript.turns(messages)
        val system = turns.firstOrNull { it.role == "system" }?.let { turn("system", it.text.take(budgetChars / 2)) }.orEmpty()
        val rest = turns.filter { it.role != "system" }.map { turn(it.role, it.text) }
        val kept = ArrayDeque<String>()
        var used = system.length
        for (t in rest.asReversed()) {
            if (used + t.length > budgetChars && kept.isNotEmpty()) break
            kept.addFirst(t.take(budgetChars - system.length))
            used += t.length
        }
        return system + kept.joinToString("") + "<|im_start|>assistant\n"
    }

    /** The "On this phone" provider's chat client. */
    val client: CommunityChatClient = object : CommunityChatClient {
        override fun stream(model: AiModel, messages: List<ChatRequestMessage>): Flow<StreamEvent> = callbackFlow {
            val spec = spec(model.id)
            when {
                appContext == null -> { trySend(StreamEvent.Error("On-device models are not set up")); close(); return@callbackFlow }
                spec == null -> { trySend(StreamEvent.Error("Unknown on-device model ${model.id}")); close(); return@callbackFlow }
                !file(spec).isFile -> {
                    trySend(StreamEvent.Error("${spec.name} is not downloaded yet. Get it in Settings > On-device models."))
                    close(); return@callbackFlow
                }
            }
            val s = spec!!
            trySend(StreamEvent.Started)
            val listener = com.google.mediapipe.tasks.genai.llminference.ProgressListener<String> { partial, done ->
                // Partial results are deltas; strip the template's end marker.
                val clean = (partial ?: "").replace("<|im_end|>", "")
                if (clean.isNotEmpty()) trySend(StreamEvent.ContentDelta(clean))
                if (done) {
                    trySend(StreamEvent.FinishReason("stop"))
                    trySend(StreamEvent.Completed)
                    close()
                }
            }
            runCatching {
                val future = engineFor(s).generateResponseAsync(prompt(messages, s.maxTokens), listener)
                future.addListener({
                    runCatching { future.get() }.onFailure { e ->
                        trySend(StreamEvent.Error("${s.name}: ${e.message ?: "generation failed"}"))
                        close()
                    }
                }, Runnable::run)
            }.onFailure {
                trySend(StreamEvent.Error("${s.name} could not run: ${it.message}"))
                close()
            }
            awaitClose { }
        }.flowOn(Dispatchers.Default)
    }
}
