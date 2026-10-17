package com.oro.app.voice

import android.content.Context
import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** JNI surface of `liboro_whistle.so` (src/main/cpp/whistle_jni.cpp). */
internal object WhistleNative {
    /** False on ABIs the engine does not ship for (x86 emulators). */
    val available: Boolean = runCatching { System.loadLibrary("oro_whistle"); true }.getOrDefault(false)

    /** Returns null on success, else the engine's error. */
    external fun load(model: ByteArray): String?
    external fun isLoaded(): Boolean
    /** JSON: {"text","language",...} or {"error"}. */
    external fun transcribe(pcm: FloatArray, language: String?, keywords: String?): String
    /** Live: JSON {"text": committed-this-call, "pending": tail, ...}. */
    external fun streamProcess(pcm: FloatArray, language: String?, keywords: String?): String
    external fun streamStop(): String
}

/**
 * Whistle, Cactus Compute's 16.9 MB on-device speech model: downloaded once
 * on request, loaded into the Needle engine, then every transcription stays
 * on the phone -- no network, no account, no limits.
 */
object Whistle {

    const val MODEL_URL = "https://huggingface.co/Cactus-Compute/whistle/resolve/main/whistle.cact"
    const val MODEL_BYTES = 16_919_407L

    sealed interface State {
        data object Unsupported : State
        data object NotInstalled : State
        data class Downloading(val fraction: Float) : State
        data object Installed : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.NotInstalled)
    val state: StateFlow<State> = _state

    fun modelFile(context: Context) = File(context.filesDir, "models/whistle.cact")

    fun refresh(context: Context) {
        _state.value = when {
            !WhistleNative.available -> State.Unsupported
            modelFile(context).length() == MODEL_BYTES || modelFile(context).length() > 1_000_000 -> State.Installed
            else -> State.NotInstalled
        }
    }

    suspend fun download(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(WhistleNative.available) { "On-device dictation needs an ARM phone." }
            val target = modelFile(context)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "whistle.cact.part")
            _state.value = State.Downloading(0f)
            val client = AiHttp.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { r ->
                if (!r.isSuccessful) error("Download failed (${r.code})")
                val body = r.body ?: error("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: MODEL_BYTES
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            _state.value = State.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
            _state.value = State.Installed
        }.onFailure { _state.value = State.Failed(it.message ?: "Download failed") }
    }

    fun delete(context: Context) {
        modelFile(context).delete()
        refresh(context)
    }

    internal suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (WhistleNative.isLoaded()) return@withContext
        val f = modelFile(context)
        check(f.isFile) { "Download the Whistle model first (Settings > Voice dictation)." }
        WhistleNative.load(f.readBytes())?.let { error("Whistle could not load: $it") }
    }

    /** Transcribe 16 kHz mono float PCM. */
    suspend fun transcribe(context: Context, pcm: FloatArray, language: String? = null): Result<String> =
        withContext(Dispatchers.Default) {
            runCatching {
                ensureLoaded(context)
                val json = AiHttp.json.parseToJsonElement(WhistleNative.transcribe(pcm, language, KEYWORDS)) as JsonObject
                (json["error"] as? JsonPrimitive)?.contentOrNull?.let { error(it) }
                (json["text"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            }
        }

    /** Live session: feed chunks, get (newly committed text, pending tail). */
    class Stream internal constructor(private val context: Context) {
        private val committed = StringBuilder()
        suspend fun start() = ensureLoaded(context)
        /** Returns the whole transcript so far: committed words plus the unconfirmed tail. */
        fun feed(pcm: FloatArray): String {
            val o = parse(WhistleNative.streamProcess(pcm, null, KEYWORDS))
            append(o["text"])
            val pending = (o["pending"] as? JsonPrimitive)?.contentOrNull.orEmpty().trim()
            return (committed.toString() + " " + pending).trim()
        }
        fun stop(): String {
            append(parse(WhistleNative.streamStop())["text"])
            return committed.toString().trim()
        }
        private fun append(e: kotlinx.serialization.json.JsonElement?) {
            val t = (e as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (t.isNotEmpty()) { if (committed.isNotEmpty()) committed.append(' '); committed.append(t) }
        }
        private fun parse(raw: String): JsonObject {
            val o = AiHttp.json.parseToJsonElement(raw) as JsonObject
            (o["error"] as? JsonPrimitive)?.contentOrNull?.let { error(it) }
            return o
        }
    }

    fun stream(context: Context) = Stream(context)

    /** Words people say to a web-building agent, biased so they survive recognition. */
    private const val KEYWORDS = "Orobi\nOro\nHTML\nCSS\nJavaScript\nTailwind\nnavbar\nfooter\nhero section\nlanding page\nindex.html\nstyle.css"
}
