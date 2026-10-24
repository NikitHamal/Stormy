package com.oro.app.data.ai.eval

import com.oro.app.data.ai.community.MercuryBrowserBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.util.UUID

/**
 * The live eval's [MercuryBrowserBridge]: headless Chromium (mercury_bridge.py)
 * keeps chat.inceptionlabs.ai open and makes the in-app bridge's same-origin
 * requests in the page, so Mercury answers in the eval through the same path
 * the app takes when Vercel's checkpoint refuses plain HTTP. One request at a
 * time, like the app's single page.
 */
internal class EvalMercuryBridge(private val python: String, workDir: File) : MercuryBrowserBridge {

    private val script = File(workDir, "mercury_bridge.py").also { f ->
        f.parentFile?.mkdirs()
        f.writeText(javaClass.classLoader!!.getResource("oro-eval/mercury_bridge.py")!!.readText())
    }
    private val err = File(workDir, "mercury_bridge.err")
    private var process: Process = start()
    private var out = process.outputStream.bufferedWriter()
    private var input = process.inputStream.bufferedReader()
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private fun start(): Process = ProcessBuilder(python, script.path)
        .redirectError(ProcessBuilder.Redirect.appendTo(err))
        .start()

    /** A browser that died (it happens on long runs) is started again for the next request. */
    private fun ensureAlive() {
        if (process.isAlive) return
        process = start()
        out = process.outputStream.bufferedWriter()
        input = process.inputStream.bufferedReader()
    }

    override fun chat(body: String): Flow<String> = flow {
        lock.withLock {
            ensureAlive()
            val id = UUID.randomUUID().toString()
            out.write(buildJsonObject { put("id", id); put("body", body) }.toString())
            out.newLine()
            out.flush()
            while (true) {
                val line = input.readLine() ?: break
                val o = runCatching { json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: continue
                if ((o["id"] as? JsonPrimitive)?.contentOrNull != id) continue
                when ((o["t"] as? JsonPrimitive)?.contentOrNull) {
                    "line" -> emit((o["v"] as? JsonPrimitive)?.contentOrNull.orEmpty())
                    "end" -> break
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        runCatching { out.close() }
        process.destroy()
    }
}
