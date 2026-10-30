package com.oro.app.data.ai.council

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The council itself: three seats hear the same brief, the chair merges, and
 * disagreement survives into the record. Scripted clients stand in for the
 * providers, so the deliberation logic -- roles, parsing, merging, absences --
 * is tested without a network.
 */
class SecurityCouncilTest {

    private fun model(name: String) = AiModel(id = "bench:$name", name = name, provider = AiProvider.CUSTOM, supportsToolCalls = true)

    private fun seat(reply: String) = AgentModelClient { _: AgentRequest ->
        flowOf(StreamEvent.Started, StreamEvent.ContentDelta(reply), StreamEvent.Completed) as Flow<StreamEvent>
    }

    private fun deadSeat() = AgentModelClient { _: AgentRequest ->
        flowOf(StreamEvent.Error("503 upstream", retryable = false)) as Flow<StreamEvent>
    }

    private val responder = """
        The mail headers show a spoofed From with a passing-looking SPF but a failed DMARC.
        Contain first: do not click, preserve the message.
        FINDINGS:
        high | DMARC failure on a payment-themed mail | Quarantine the message and warn anyone who opened it
        medium | Reply-to differs from From | Treat the reply address as attacker-controlled
    """.trimIndent()

    private val adversary = """
        The attacker's read: a failed DMARC on a lookalike domain is a credential phish, not spam.
        The harmless reading (a misconfigured legitimate sender) is weaker: legit senders fix DMARC fast.
        FINDINGS:
        critical | Lookalike sender domain targeting finance staff | Block the domain at the resolver and reset any session that clicked
    """.trimIndent()

    private val verifier = """
        CONFIRMED: dmarc=fail in the Authentication-Results header quoted in the artifact.
        SUSPECTED: that anyone clicked -- no log in the artifact; check the mail gateway.
        FALSE ALARM: the "urgent invoice" body alone proves nothing; invoices are always urgent.
        FINDINGS:
        high | DMARC fail is evidenced in the header | Preserve the original .eml as evidence
    """.trimIndent()

    private val chair = """
        Three seats agree on one thing: the mail is spoofed and the DMARC failure is evidenced.
        VERDICT paragraph: a credential-phishing attempt, contained so far; act on the domain today.
        FINDINGS:
        critical | Lookalike sender domain targeting finance staff | Block the domain and reset clicked sessions
        high | DMARC failure on a payment-themed mail | Quarantine and warn
        DISAGREEMENTS:
        - The verifier rates "anyone clicked" as unproven while the responder treats it as likely; check the gateway logs.
    """.trimIndent()

    @Test
    fun `three seats and a chair produce a merged, severity-ranked record`() = runTest {
        val members = listOf(
            CouncilMember(model("mercury"), CouncilRole.FIRST_RESPONDER),
            CouncilMember(model("k2"), CouncilRole.ADVERSARY),
            CouncilMember(model("hy3"), CouncilRole.VERIFIER),
        )
        val clients = mapOf(
            "mercury" to seat(responder),
            "k2" to seat(adversary),
            "hy3" to seat(verifier),
            "mercury-chair" to seat(chair),
        )
        val council = SecurityCouncil { m -> clients[m.name] ?: clients["mercury"]!! }

        val heard = mutableListOf<CouncilOpinion>()
        val result = council.deliberate(
            topic = "Is this mail a phish?",
            artifact = "Authentication-Results: dmarc=fail header.from=paypa1-notifications.com",
            members = members,
            onOpinion = { heard += it },
        )

        assertThat(heard.map { it.role }).containsExactly(
            CouncilRole.FIRST_RESPONDER, CouncilRole.ADVERSARY, CouncilRole.VERIFIER
        )
        // The chair's merged list leads with the worst severity...
        assertThat(result.findings.first().severity).isEqualTo(Severity.CRITICAL)
        // ...deduplicates the DMARC finding the responder and verifier both raised...
        assertThat(result.findings.count { it.title.contains("DMARC", true) }).isEqualTo(1)
        // ...and keeps the disagreement on the record.
        assertThat(result.disagreements.joinToString()).contains("gateway")
        assertThat(result.synthesizer).isEqualTo("mercury")
    }

    @Test
    fun `a seat that fails is counted absent, not silent`() = runTest {
        val members = listOf(
            CouncilMember(model("up"), CouncilRole.FIRST_RESPONDER),
            CouncilMember(model("down"), CouncilRole.ADVERSARY),
        )
        val council = SecurityCouncil { m -> if (m.name == "down") deadSeat() else seat(responder) }
        val result = council.deliberate("t", null, members, synthesizer = model("up"))

        val absent = result.opinions.first { it.modelName == "down" }
        assertThat(absent.failed).isNotNull()
        assertThat(result.opinions.first { it.modelName == "up" }.failed).isNull()
        assertThat(result.findings).isNotEmpty()
    }

    @Test
    fun `when every seat fails the record says so instead of inventing a verdict`() = runTest {
        val members = listOf(CouncilMember(model("a")), CouncilMember(model("b")))
        val council = SecurityCouncil { _ -> deadSeat() }
        val result = council.deliberate("t", null, members)
        assertThat(result.verdict).contains("could not hear any member")
        assertThat(result.findings).isEmpty()
    }

    @Test
    fun `findings parsing tolerates prose around the block and caps the list`() {
        val text = "Some reasoning first.\nFINDINGS:\nhigh | One | Do it\nnonsense line\nlow | Two | Later\nDISAGREEMENTS:\n- x versus y"
        val findings = SecurityCouncil.parseFindings(text)
        assertThat(findings).hasSize(2)
        assertThat(findings[0].severity).isEqualTo(Severity.HIGH)
        assertThat(findings[0].action).isEqualTo("Do it")
        // Nothing after DISAGREEMENTS leaks into findings.
        assertThat(SecurityCouncil.parseDisagreements(text)).containsExactly("x versus y")
    }

    @Test
    fun `merge keeps the worst severity and the longer action for duplicates`() {
        val merged = SecurityCouncil.mergeFindings(
            listOf(
                listOf(Finding(Severity.LOW, "Old session tokens valid", "rotate")),
                listOf(Finding(Severity.HIGH, "Old session tokens valid!", "Rotate every session token issued before today")),
            )
        )
        assertThat(merged).hasSize(1)
        assertThat(merged[0].severity).isEqualTo(Severity.HIGH)
        assertThat(merged[0].action).contains("every session token")
    }
}
