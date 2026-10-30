package com.oro.app.data.security

import com.oro.app.data.ai.AiHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * A disposable place to run a script the guard wrote, with no account and no
 * key. Judge0 CE and Wandbox are public, rate-limited runners; a user with a
 * free VM or a Cloudflare tunnel can point [Config.runnerUrl] at their own
 * runner instead, which is the only option that keeps code off a third party.
 *
 * The guard's scripts are defensive analysis tools (parsers, hashers,
 * timeline builders), and the runtime is ephemeral: nothing is persisted on
 * the runner, and nothing secret belongs in the code or stdin. The tool
 * description and the system brief say so; this file enforces size and time
 * limits, not trust.
 */
object Sandbox {

    data class Config(val runnerUrl: String? = null, val runnerToken: String? = null)

    enum class Engine(val label: String) {
        CUSTOM("your runner"),
        JUDGE0("Judge0"),
        WANDBOX("Wandbox"),
    }

    data class Result(
        val ok: Boolean,
        val engine: String,
        val stdout: String = "",
        val stderr: String = "",
        val exitCode: Int? = null,
        val detail: String? = null,
    ) {
        fun render(): String = buildString {
            append("Sandbox ").append(if (ok) "finished" else "failed").append(" on ").append(engine)
            exitCode?.let { append(" (exit ").append(it).append(')') }
            append(".\n")
            if (stdout.isNotBlank()) append("\nstdout:\n").append(stdout.trimEnd()).append('\n')
            if (stderr.isNotBlank()) append("\nstderr:\n").append(stderr.trimEnd()).append('\n')
            if (stdout.isBlank() && stderr.isBlank()) append("\n(no output)\n")
            detail?.let { append("\n").append(it) }
        }
    }

    val languages = listOf("python", "javascript", "typescript", "bash", "ruby", "php", "go", "rust", "java", "c", "cpp")

    fun normalizeLanguage(raw: String): String? {
        val v = raw.trim().lowercase(Locale.ROOT)
        return when (v) {
            "py", "python", "python3" -> "python"
            "js", "node", "javascript", "nodejs" -> "javascript"
            "ts", "typescript" -> "typescript"
            "sh", "shell", "bash" -> "bash"
            "rb", "ruby" -> "ruby"
            "php" -> "php"
            "go", "golang" -> "go"
            "rs", "rust" -> "rust"
            "java" -> "java"
            "c" -> "c"
            "cpp", "c++", "cxx" -> "cpp"
            else -> null
        }
    }

    private val http: OkHttpClient by lazy {
        AiHttp.client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Runs [code]; tries custom runner, then Judge0, then Wandbox (which has no shell). */
    suspend fun run(languageRaw: String, code: String, stdin: String?, config: Config = Config()): Result {
        val language = normalizeLanguage(languageRaw)
            ?: return Result(false, "none", detail = "Unsupported language. Use one of: ${languages.joinToString()}.")
        if (code.isBlank()) return Result(false, "none", detail = "No code to run.")
        if (code.length > 60_000) return Result(false, "none", detail = "Code is too large (limit 60k characters).")
        val input = stdin.orEmpty().take(20_000)
        val failures = ArrayList<String>()

        if (!config.runnerUrl.isNullOrBlank()) {
            val custom = runCatching { runCustom(config, language, code, input) }.getOrElse {
                failures += "custom runner: ${it.message ?: it.javaClass.simpleName}"
                null
            }
            if (custom != null && custom.ok) return custom
            if (custom != null) failures += "custom runner: ${custom.detail ?: "failed"}"
        }
        val judge = runCatching { runJudge0(language, code, input) }.getOrElse {
            failures += "Judge0: ${it.message ?: it.javaClass.simpleName}"
            null
        }
        if (judge != null && judge.ok) return judge
        if (judge != null) failures += "Judge0: ${judge.detail ?: "failed"}"
        val wandbox = runCatching { runWandbox(language, code, input) }.getOrElse {
            failures += "Wandbox: ${it.message ?: it.javaClass.simpleName}"
            null
        }
        if (wandbox != null && wandbox.ok) return wandbox
        if (wandbox != null) failures += "Wandbox: ${wandbox.detail ?: "failed"}"
        return Result(false, "none", detail = "Every sandbox failed. " + failures.joinToString(" | "))
    }

    // ---------------------------------------------------------------- Judge0

    private suspend fun runJudge0(language: String, code: String, stdin: String): Result = withContext(Dispatchers.IO) {
        val id = judge0LanguageId(language) ?: return@withContext Result(false, Engine.JUDGE0.label, detail = "$language has no Judge0 language id.")
        val request = Request.Builder().url("https://ce.judge0.com/submissions?base64_encoded=false&wait=true")
            .header("User-Agent", "Oro-Shield/1.0 (defensive script sandbox)")
            .post(judge0Body(id, code, stdin).toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@use Result(false, Engine.JUDGE0.label, detail = "HTTP ${r.code}: ${body.take(200)}")
            parseJudge0(body)
        }
    }

    fun judge0LanguageId(language: String): Int? = when (language) {
        "python" -> 71
        "javascript" -> 63
        "typescript" -> 74
        "bash" -> 46
        "ruby" -> 72
        "php" -> 68
        "go" -> 60
        "rust" -> 73
        "java" -> 62
        "c" -> 50
        "cpp" -> 54
        else -> null
    }

    fun judge0Body(languageId: Int, code: String, stdin: String): String = buildJsonObject {
        put("language_id", languageId)
        put("source_code", code)
        put("stdin", stdin)
        put("cpu_time_limit", 10)
        put("wall_time_limit", 20)
        put("memory_limit", 256_000)
    }.toString()

    fun parseJudge0(json: String): Result = runCatching {
        val root = AiHttp.json.parseToJsonElement(json) as? JsonObject ?: return@runCatching Result(false, Engine.JUDGE0.label, detail = "Unreadable Judge0 reply.")
        val status = (root["status"] as? JsonObject)?.let { (it["description"] as? JsonPrimitive)?.contentOrNull }
        val exitCode = (root["exit_code"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val stdout = (root["stdout"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val stderr = buildString {
            (root["compile_output"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
            (root["stderr"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { append(it) }
        }.trim()
        val ok = status?.contains("Accepted", true) == true || (exitCode == 0 && stderr.isBlank())
        Result(ok, Engine.JUDGE0.label, stdout.take(20_000), stderr.take(20_000), exitCode,
            detail = if (!ok) status?.let { "Judge0 status: $it" } else null)
    }.getOrElse { Result(false, Engine.JUDGE0.label, detail = it.message) }

    // --------------------------------------------------------------- Wandbox

    private suspend fun runWandbox(language: String, code: String, stdin: String): Result = withContext(Dispatchers.IO) {
        val compiler = wandboxCompiler(language)
            ?: return@withContext Result(false, Engine.WANDBOX.label, detail = "$language has no Wandbox compiler.")
        val request = Request.Builder().url("https://wandbox.org/api/compile.json")
            .header("User-Agent", "Oro-Shield/1.0 (defensive script sandbox)")
            .post(wandboxBody(compiler, code, stdin).toRequestBody(jsonMedia))
            .build()
        http.newCall(request).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@use Result(false, Engine.WANDBOX.label, detail = "HTTP ${r.code}: ${body.take(200)}")
            parseWandbox(body)
        }
    }

    fun wandboxCompiler(language: String): String? = when (language) {
        "python" -> "cpython-3.14.0"
        "javascript" -> "nodejs-20.17.0"
        "typescript" -> "typescript-5.6.2"
        "ruby" -> "ruby-4.0.2"
        "php" -> "php-8.3.12"
        "go" -> "go-1.23.2"
        "rust" -> "rust-1.82.0"
        "java" -> "openjdk-jdk-22+36"
        "c" -> "gcc-13.2.0-c"
        "cpp" -> "gcc-13.2.0"
        "bash" -> null
        else -> null
    }

    fun wandboxBody(compiler: String, code: String, stdin: String): String = buildJsonObject {
        put("compiler", compiler)
        put("code", code)
        put("stdin", stdin)
    }.toString()

    fun parseWandbox(json: String): Result = runCatching {
        val root = AiHttp.json.parseToJsonElement(json) as? JsonObject ?: return@runCatching Result(false, Engine.WANDBOX.label, detail = "Unreadable Wandbox reply.")
        fun str(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val status = str("status").toIntOrNull()
        val stdout = str("program_output")
        val stderr = buildString {
            str("compiler_error").takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
            str("program_error").takeIf { it.isNotBlank() }?.let { append(it) }
        }.trim()
        Result(status == 0, Engine.WANDBOX.label, stdout.take(20_000), stderr.take(20_000), status,
            detail = if (status != 0) "Wandbox status $status" else null)
    }.getOrElse { Result(false, Engine.WANDBOX.label, detail = it.message) }

    // ---------------------------------------------------------------- custom

    private suspend fun runCustom(config: Config, language: String, code: String, stdin: String): Result = withContext(Dispatchers.IO) {
        val url = config.runnerUrl!!.trim()
        val builder = Request.Builder().url(url)
            .header("User-Agent", "Oro-Shield/1.0 (defensive script sandbox)")
            .post(customBody(language, code, stdin).toRequestBody(jsonMedia))
        config.runnerToken?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        http.newCall(builder.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) return@use Result(false, Engine.CUSTOM.label, detail = "HTTP ${r.code}: ${body.take(200)}")
            parseCustom(body)
        }
    }

    fun customBody(language: String, code: String, stdin: String): String = buildJsonObject {
        put("language", language)
        put("code", code)
        put("stdin", stdin)
    }.toString()

    fun parseCustom(json: String): Result = runCatching {
        val root = AiHttp.json.parseToJsonElement(json) as? JsonObject ?: return@runCatching Result(false, Engine.CUSTOM.label, detail = "Unreadable runner reply.")
        fun str(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val stdout = str("stdout").ifBlank { str("output") }
        val stderr = str("stderr")
        val exit = str("exit_code").toIntOrNull() ?: str("exitCode").toIntOrNull()
        Result(exit == 0 || (exit == null && str("status") in listOf("", "ok", "success", "Accepted")), Engine.CUSTOM.label,
            stdout.take(20_000), stderr.take(20_000), exit)
    }.getOrElse { Result(false, Engine.CUSTOM.label, detail = it.message) }
}
