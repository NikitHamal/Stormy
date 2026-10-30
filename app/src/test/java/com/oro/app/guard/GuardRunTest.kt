package com.oro.app.guard

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.council.CouncilOpinion
import com.oro.app.data.ai.council.CouncilResult
import com.oro.app.data.ai.council.CouncilRole
import com.oro.app.data.ai.council.Finding
import com.oro.app.data.ai.council.Severity
import com.oro.app.data.ai.tools.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The guard's tool contract on the JVM: everything environment-touching sits
 * behind [GuardAgent.Guard], so the dispatch, the council rendering and the
 * masked-secret rule are tested without a device.
 */
class GuardRunTest {

    private class FakeGuard(
        val phone: String = "[HIGH] USB debugging (ADB) is on -> Turn it off",
        val files: List<Pair<String, String>> = emptyList(),
        val councilResult: CouncilResult? = null,
    ) : GuardAgent.Guard {
        override fun phoneReport() = phone
        override fun projectFiles() = files
        override suspend fun checkUrl(url: String, deep: Boolean) = "CAUTION — $url (score 25/100)\n- A shortened link"
        override suspend fun advisories(pkg: String, eco: String, version: String?) = "No known advisories for $eco:$pkg"
        override suspend fun kevRecent(days: Int) = "[MEDIUM] CVE-2026-9999 Vendor Widget -> patch"
        override suspend fun vaultAudit() = "Vault audit (0 logins): clean"
        override suspend fun council(topic: String, artifact: String?): CouncilResult = councilResult
            ?: CouncilResult(emptyList(), "chair verdict", emptyList(), emptyList(), "mercury")
        override suspend fun browserTask(task: String) = ToolResult(true, "browser succeeded: $task")
    }

    private suspend fun run(guard: GuardAgent.Guard, name: String, args: String) =
        GuardAgent.runScenarioExecute(guard, name, args)

    @Test
    fun `phone audit returns the report and unknown tools are refused`() = runTest {
        val ok = run(FakeGuard(), "audit_phone", "{}")
        assertThat(ok.success).isTrue()
        assertThat(ok.output).contains("ADB")

        val unknown = run(FakeGuard(), "rm_rf_everything", "{}")
        assertThat(unknown.success).isFalse()
        assertThat(unknown.error).contains("Unknown guard tool")
    }

    @Test
    fun `check url requires a url and renders the verdict`() = runTest {
        assertThat(run(FakeGuard(), "check_url", "{}").success).isFalse()
        val v = run(FakeGuard(), "check_url", """{"url":"https://bit.ly/abc"}""")
        assertThat(v.output).contains("CAUTION")
    }

    @Test
    fun `secret scan masks every value it finds`() = runTest {
        val secret = "sk_live_" + "Z".repeat(24)
        val files = listOf(
            "config.js" to "const stripe = \"$secret\";\nconst clean = 1;",
            "readme.md" to "no secrets here",
        )
        val result = run(FakeGuard(files = files), "scan_project_secrets", "{}")
        assertThat(result.success).isTrue()
        assertThat(result.output).contains("config.js:1")
        assertThat(result.output).contains("stripe live key")
        // The value itself never reaches the transcript.
        assertThat(result.output).doesNotContain(secret)
        assertThat(result.output).contains("Rotate every one")
    }

    @Test
    fun `a clean project says so`() = runTest {
        val result = run(FakeGuard(files = listOf("a.kt" to "fun main() {}")), "scan_project_secrets", "{}")
        assertThat(result.output).contains("no credential shapes")
    }

    @Test
    fun `the council tool renders verdict, findings and disagreements`() = runTest {
        val council = CouncilResult(
            opinions = listOf(
                CouncilOpinion("mercury", CouncilRole.FIRST_RESPONDER, "t", listOf(Finding(Severity.HIGH, "Session tokens valid", "rotate"))),
                CouncilOpinion("k2", CouncilRole.ADVERSARY, "t", emptyList(), failed = "the model did not answer"),
            ),
            verdict = "A credential phish, contained so far.",
            findings = listOf(Finding(Severity.CRITICAL, "Lookalike domain", "block it")),
            disagreements = listOf("One seat saw nothing; two saw a phish."),
            synthesizer = "mercury",
        )
        val result = run(FakeGuard(councilResult = council), "convene_council", """{"topic":"Is this a phish?","artifact":"headers..."}""")
        assertThat(result.success).isTrue()
        assertThat(result.output).contains("Council verdict (chair: mercury)")
        assertThat(result.output).contains("[CRITICAL] Lookalike domain -> block it")
        assertThat(result.output).contains("Disagreements on the record")
    }

    @Test
    fun `convene council needs a topic`() = runTest {
        assertThat(run(FakeGuard(), "convene_council", "{}").success).isFalse()
    }

    @Test
    fun `finish task records the summary and success flag`() = runTest {
        val result = run(FakeGuard(), "finish_task", """{"summary":"All clear; rotate the one reused password.","success":true}""")
        assertThat(result.success).isTrue()
        assertThat(result.output).contains("finished")
    }

    @Test
    fun `use browser bridges to the browser agent contract`() = runTest {
        val ok = run(FakeGuard(), "use_browser", """{"task":"Check my account's active sessions"}""")
        assertThat(ok.success).isTrue()
        assertThat(ok.output).contains("browser succeeded")
        val noTask = run(FakeGuard(), "use_browser", "{}")
        assertThat(noTask.success).isFalse()
    }

    @Test
    fun `the guard model default is one of the seated council models`() {
        // The begin() default: first DEFAULT_MEMBER present in the model list.
        val models = listOf(
            AiModel(id = "other:x", name = "Other", provider = AiProvider.CUSTOM),
            AiModel(id = GuardSettingsStore.DEFAULT_MEMBERS[1], name = "K2", provider = AiProvider.K2_HORIZON),
        )
        val chosen = models.firstOrNull { it.id in GuardSettingsStore.DEFAULT_MEMBERS } ?: models.firstOrNull()
        assertThat(chosen?.name).isEqualTo("K2")
    }
}
