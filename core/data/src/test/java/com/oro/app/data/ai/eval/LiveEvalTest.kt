package com.oro.app.data.ai.eval

import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.KeylessFallback
import com.oro.app.data.preview.MockApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Live eval of the coding agent with real keyless models: each model builds
 * each task from an empty project through Oro's real harness, then the result
 * is screenshotted and smoke-tested in headless Chromium. Opt-in only -- it
 * calls real providers and needs Playwright:
 *
 *   ORO_LIVE_EVAL=1 ORO_EVAL_OUT=/root/oro/eval/out/base \
 *   ORO_EVAL_MODELS="nvidia/nemotron-3-ultra-550b-a55b:free,hy3:hy3,nvidia/nemotron-3-ultra-550b-a55b:free@text" \
 *   ORO_EVAL_TASKS=landing,app,game \
 *     ./gradlew :core:data:testDebugUnitTest --tests '*LiveEvalTest*' --rerun
 *
 * A model id ending in @text runs on the text tool protocol even when it has
 * native tools. Runs on one provider go one after another (its rate limit);
 * providers run side by side. Per run: the project folder, transcript.md,
 * raw.md, PNG screenshots in shots and result.json; all results in
 * results.jsonl and summary.md.
 * See docs/research/live-eval.md.
 */
class LiveEvalTest {

    @Test
    fun live() = runBlocking {
        Assume.assumeTrue("Set ORO_LIVE_EVAL=1 to run the live eval", System.getenv("ORO_LIVE_EVAL") == "1")
        val out = File(System.getenv("ORO_EVAL_OUT") ?: "build/oro-eval").apply { mkdirs() }
        val models = (System.getenv("ORO_EVAL_MODELS") ?: "nvidia/nemotron-3-ultra-550b-a55b:free")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val tasks = (System.getenv("ORO_EVAL_TASKS") ?: "landing,app,game")
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(EvalTask::parse)
        val maxTurns = System.getenv("ORO_EVAL_MAX_TURNS")?.toIntOrNull() ?: 60
        val browser = EvalBrowser(System.getenv("ORO_EVAL_PYTHON") ?: "python3", out)
        // Only the model under test answers: no keyless stand-ins.
        KeylessFallback.enabledIds = emptySet()
        MockApi.authDir = File(out, ".mock-auth").apply { mkdirs() }

        val specs = models.flatMap { m -> tasks.map { t -> EvalSpec(m.removeSuffix("@text"), m.endsWith("@text"), t) } }
        val progress = File(out, "progress.log")
        val results = File(out, "results.jsonl")
        // One lane per model and protocol; the shared rate limiter still paces each provider.
        val groups = specs.groupBy { it.label }
        coroutineScope {
            groups.values.map { group ->
                async(Dispatchers.IO) {
                    for (spec in group) {
                        val model = AiModels.findModelById(spec.modelId)
                        val dir = File(out, "${spec.label}/${spec.task.name.lowercase()}")
                        log(progress, "start ${spec.label} ${spec.task}")
                        val result = if (model == null) {
                            buildJsonObject { put("label", spec.label); put("task", spec.task.name.lowercase()); put("outcome", "unknown model") }
                        } else {
                            runCatching { EvalRun(spec, model, dir.apply { mkdirs() }, browser, maxTurns).run() }
                                .getOrElse { e ->
                                    buildJsonObject {
                                        put("label", spec.label); put("task", spec.task.name.lowercase())
                                        put("outcome", "harness error"); put("detail", "${e.javaClass.simpleName}: ${e.message}")
                                    }
                                }
                        }
                        File(dir, "result.json").writeText(result.toString())
                        synchronized(results) { results.appendText(result.toString() + "\n") }
                        log(progress, "end ${spec.label} ${spec.task} ${result.s("outcome")} ${result.s("seconds")}s")
                    }
                }
            }.awaitAll()
        }
        File(out, "summary.md").writeText(summary(results.readLines().filter { it.isNotBlank() }))
    }

    /**
     * Re-measure finished projects without running a model (ORO_EVAL_REPROBE=1,
     * ORO_EVAL_OUT=<an earlier run>): Oro's dev server plus the current probes,
     * so runs from different rounds are judged by the same yardstick.
     */
    @Test
    fun reprobe() = runBlocking {
        Assume.assumeTrue("Set ORO_EVAL_REPROBE=1 to re-measure an earlier eval", System.getenv("ORO_EVAL_REPROBE") == "1")
        val out = File(System.getenv("ORO_EVAL_OUT") ?: error("ORO_EVAL_OUT"))
        val browser = EvalBrowser(System.getenv("ORO_EVAL_PYTHON") ?: "python3", File(out, "reprobe-tools"))
        MockApi.authDir = File(out, ".mock-auth-reprobe").apply { mkdirs() }
        val lines = mutableListOf<String>()
        out.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.forEach { label ->
            // Finished runs only: the smoke test writes to the mock API a running agent is reading.
            label.listFiles().orEmpty().filter { File(it, "project").isDirectory && File(it, "result.json").isFile }
                .sortedBy { it.name }.forEach { taskDir ->
                val project = File(taskDir, "project")
                val task = runCatching { EvalTask.parse(taskDir.name) }.getOrNull() ?: return@forEach
                val server = com.oro.app.data.preview.EmbeddedDevServer(project)
                val port = server.start()
                try {
                    val entry = if (File(project, "index.html").isFile) "index.html" else null
                    val url = entry?.let { "http://127.0.0.1:$port/$it" }
                    val smoke = url?.let {
                        when (task) {
                            EvalTask.APP -> browser.run("interact", it, "Eval habit 7")
                            EvalTask.GAME -> browser.run("play", it)
                            EvalTask.LANDING, EvalTask.VAGUE -> null
                        }
                    }
                    val audit = url?.let { browser.run("audit", it) }
                    val issues = (audit?.get("issues") as? kotlinx.serialization.json.JsonArray)?.size ?: -1
                    val result = buildJsonObject {
                        put("label", label.name); put("task", taskDir.name)
                        put("pageIssues", issues)
                        smoke?.let { put("smoke", it) }
                    }
                    File(taskDir, "reprobe.json").writeText(result.toString())
                    val verdict = smoke?.let { sm ->
                        if (sm.containsKey("shownAfterReload")) "write ${(sm["writes"] as? kotlinx.serialization.json.JsonArray)?.isNotEmpty() == true} / shown ${sm.s("shownAfterSubmit")} / persists ${sm.s("shownAfterReload")}"
                        else "moves ${sm.s("changed")}, errors ${(sm["pageErrors"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0}"
                    } ?: if (entry == null) "no index.html" else ""
                    lines += "| ${label.name} | ${taskDir.name} | $issues | $verdict |"
                } finally {
                    server.stop()
                }
            }
        }
        File(out, "reprobe.md").writeText("| model | task | page issues | smoke |\n|---|---|---|---|\n" + lines.joinToString("\n") + "\n")
    }

    private fun log(f: File, line: String) = synchronized(f) { f.appendText("${System.currentTimeMillis() / 1000} $line\n") }

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.contentOrNull ?: ""

    private fun summary(lines: List<String>): String = buildString {
        append("| model | task | outcome | turns | tool calls | tool errors | nudges (malformed/cut/reasoning) | gate passes | audit E/W | page issues | runtime errors | phone overflow | smoke | asked first / uses answers | time |\n")
        append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
        lines.forEach { line ->
            val o = kotlinx.serialization.json.Json.parseToJsonElement(line) as JsonObject
            val smoke = (o["smoke"] as? JsonObject)?.let { s ->
                if (s.containsKey("shownAfterReload")) "add ${s.s("shownAfterSubmit")}/persist ${s.s("shownAfterReload")}"
                else "moves ${s.s("changed")}"
            } ?: ""
            val errors = (o["runtimeErrors"] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
            append("| ").append(o.s("label")).append(" | ").append(o.s("task")).append(" | ").append(o.s("outcome"))
            append(" | ").append(o.s("turns")).append(" | ").append(o.s("toolCalls")).append(" | ").append(o.s("toolErrors"))
            append(" | ").append(o.s("malformedNudges")).append('/').append(o.s("truncatedNudges")).append('/').append(o.s("reasoningOnlyNudges"))
            append(" | ").append(o.s("qualityGatePasses")).append(" | ").append(o.s("auditErrors")).append('/').append(o.s("auditWarnings"))
            append(" | ").append(o.s("pageIssues")).append(" | ").append(errors).append(" | ").append(o.s("phoneOverflow"))
            append(" | ").append(smoke)
            append(" | ").append(o.s("askedUser")).append("x, ").append(o.s("askedBeforeBuilding")).append('/').append(o.s("usesAnswers"))
            append(" | ").append(o.s("seconds")).append("s |\n")
        }
    }
}
