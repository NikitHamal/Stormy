package com.oro.app.data.ai.council

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** How bad a finding is; the council argues about everything except order. */
enum class Severity(val rank: Int) {
    CRITICAL(0), HIGH(1), MEDIUM(2), LOW(3), INFO(4);

    companion object {
        fun of(token: String?): Severity = when (token?.trim()?.lowercase()) {
            "critical", "crit", "p0" -> CRITICAL
            "high", "p1" -> HIGH
            "medium", "med", "p2" -> MEDIUM
            "low", "p3" -> LOW
            else -> INFO
        }
    }
}

/** One actionable thing the council found. */
data class Finding(val severity: Severity, val title: String, val action: String)

/** The part a member plays in the deliberation. */
enum class CouncilRole(val id: String) {
    /** What is happening, how bad, and what to do in the next hour. */
    FIRST_RESPONDER("first_responder"),

    /** The attacker's advocate: what everyone else missed, and the strongest contrary reading. */
    ADVERSARY("adversary"),

    /** The skeptic: what the artifact actually supports; confirmed vs suspected vs false alarm. */
    VERIFIER("verifier"),
}

data class CouncilMember(val model: AiModel, val role: CouncilRole = CouncilRole.FIRST_RESPONDER)

data class CouncilOpinion(
    val modelName: String,
    val role: CouncilRole,
    val text: String,
    val findings: List<Finding>,
    /** Why this seat stayed silent (provider down, refusal); a council counts its absences. */
    val failed: String? = null,
)

data class CouncilResult(
    val opinions: List<CouncilOpinion>,
    val verdict: String,
    val findings: List<Finding>,
    val disagreements: List<String>,
    val synthesizer: String,
)

/**
 * Several models, one deliberation.
 *
 * A single model reviewing a breach notice or a suspicious URL is one
 * opinion wearing a costume. The council runs three seats in parallel --
 * a first responder, an adversary's advocate and a skeptic -- each at the
 * model's deepest reasoning level, each seeing only the brief and the
 * artifact, never each other. A synthesizer (the first healthy member by
 * default) then chairs: it merges findings, keeps the disagreements on the
 * record, and writes the verdict. Divergence is a feature: the seats that
 * agree by accident are the ones a single model hides.
 *
 * Members are the user's choice; the owner's default is Mercury 3, K2 Horizon
 * and Hy3 at maximum effort, and any further model (keyed, custom, community)
 * can take a seat.
 */
class SecurityCouncil(private val clientFor: (AiModel) -> AgentModelClient) {

    suspend fun deliberate(
        topic: String,
        artifact: String?,
        members: List<CouncilMember>,
        synthesizer: AiModel? = null,
        onOpinion: (suspend (CouncilOpinion) -> Unit)? = null,
    ): CouncilResult = coroutineScope {
        val brief = buildString {
            append("Topic: ").append(topic).append('\n')
            if (!artifact.isNullOrBlank()) append("\nArtifact:\n").append(artifact.take(24_000)).append('\n')
        }
        val jobs = members.map { member ->
            async {
                val opinion = hear(member, brief)
                onOpinion?.invoke(opinion)
                opinion
            }
        }
        val opinions = jobs.map { it.await() }
        val heard = opinions.filter { it.failed == null }
        val chair = synthesizer ?: heard.firstOrNull()?.let { m -> members.firstOrNull { it.model.name == m.modelName }?.model }
        val (verdict, chairFindings, disagreements) = if (chair != null && heard.isNotEmpty()) {
            chairIt(chair, topic, heard)
        } else Triple("The council could not hear any member: every seat failed.", emptyList(), emptyList())

        val merged = mergeFindings(heard.map { it.findings } + listOf(chairFindings))
        CouncilResult(
            opinions = opinions,
            verdict = verdict,
            findings = merged,
            disagreements = disagreements,
            synthesizer = chair?.name ?: "none",
        )
    }

    /** One seat's opinion: role prompt in, structured findings out. */
    private suspend fun hear(member: CouncilMember, brief: String): CouncilOpinion {
        val system = ChatRequestMessage(role = "system", content = rolePrompt(member.role))
        val user = ChatRequestMessage(role = "user", content = brief)
        val text = textOf(clientFor(member.model), listOf(system, user))
            ?: return CouncilOpinion(member.model.name, member.role, "", emptyList(), failed = "the model did not answer")
        return CouncilOpinion(member.model.name, member.role, text, parseFindings(text))
    }

    private suspend fun chairIt(chair: AiModel, topic: String, heard: List<CouncilOpinion>): Triple<String, List<Finding>, List<String>> {
        val system = ChatRequestMessage(role = "system", content = CHAIR_PROMPT)
        val user = ChatRequestMessage(
            role = "user",
            content = buildString {
                append("Topic: ").append(topic).append("\n\nOpinions:\n")
                heard.forEach { o ->
                    append("\n## ").append(o.role.id).append(" (").append(o.modelName).append(")\n").append(o.text.take(9_000)).append('\n')
                }
            }
        )
        val text = textOf(clientFor(chair), listOf(system, user))
            ?: return Triple("The chair could not synthesise; the raw opinions stand as the record.", heard.flatMap { it.findings }, emptyList())
        Triple(
            text.substringBefore("FINDINGS:").substringBefore("DISAGREEMENTS:").trim(),
            parseFindings(text),
            parseDisagreements(text),
        )
    }

    /** Streams one completion and keeps the visible text; null when the call failed. */
    private suspend fun textOf(client: AgentModelClient, messages: List<ChatRequestMessage>): String? {
        val sb = StringBuilder()
        var ok = false
        runCatching {
            client.stream(AgentRequest(messages, null)).collect { event ->
                when (event) {
                    is StreamEvent.ContentDelta -> sb.append(event.content)
                    is StreamEvent.Completed -> ok = true
                    is StreamEvent.Error -> return@collect
                    else -> Unit
                }
            }
        }
        return if (ok || sb.isNotEmpty()) sb.toString().trim().takeIf { it.isNotEmpty() } else null
    }

    companion object {

        fun rolePrompt(role: CouncilRole): String = when (role) {
            CouncilRole.FIRST_RESPONDER -> """
                You are the FIRST RESPONDER on a personal security council. Read the brief and artifact and say
                what is happening, how bad it is, and what the person should do in the next hour -- concrete
                actions in order (contain, rotate, notify, preserve). Reason as deep as you can; do not pad.
                Only claim what the artifact supports. End with a block:
                FINDINGS:
                severity | title | immediate action
                (one line each; severity in critical/high/medium/low/info; zero lines is a valid answer)
            """.trimIndent()
            CouncilRole.ADVERSARY -> """
                You are the ADVERSARY'S ADVOCATE on a personal security council. Think like the attacker in this
                brief: what the first responder would miss, how each symptom could be produced on purpose, which
                "harmless" reading is actually the trap -- and, equally, the strongest case that this is nothing
                (false alarms waste the victim's night). Reason as deep as you can. End with a block:
                FINDINGS:
                severity | title | immediate action
                (one line each; zero lines is a valid answer)
            """.trimIndent()
            CouncilRole.VERIFIER -> """
                You are the VERIFIER on a personal security council. For every claim the brief invites, ask: what
                evidence in the artifact supports it? Separate CONFIRMED (the artifact shows it), SUSPECTED
                (plausible, unproven -- say what would prove it) and FALSE ALARM (the benign reading). Check
                dates, domains, headers, hashes and maths yourself. Reason as deep as you can. End with a block:
                FINDINGS:
                severity | title | immediate action
                (one line each; zero lines is a valid answer)
            """.trimIndent()
        }

        const val CHAIR_PROMPT = """
            You chair a personal security council. Three seats have spoken; their opinions follow. Write the
            record: a VERDICT paragraph (what is happening, confidence, what to do first), then merge their
            findings -- deduplicate, keep the highest severity claimed for a duplicate, drop anything no seat
            evidences -- then list the disagreements worth the user's attention (a seat saying "nothing" against
            a seat saying "critical" is the most important line in the record).
            Format:
            <verdict paragraph(s)>
            FINDINGS:
            severity | title | immediate action
            DISAGREEMENTS:
            - one line per real disagreement
        """

        /** `FINDINGS:` block lines, `severity | title | action`. Tolerant of prose around them. */
        fun parseFindings(text: String): List<Finding> {
            val start = text.indexOf("FINDINGS:")
            val block = if (start >= 0) text.substring(start + "FINDINGS:".length) else text
            val cut = block.indexOf("DISAGREEMENTS:")
            val body = if (cut >= 0) block.substring(0, cut) else block
            return body.lineSequence()
                .map { it.trim() }
                .filter { it.contains('|') }
                .mapNotNull { line ->
                    val parts = line.split('|').map { it.trim() }
                    if (parts.size < 2) return@mapNotNull null
                    val severity = Severity.of(parts[0])
                    val title = parts[1]
                    if (title.isBlank() || title.length < 8) return@mapNotNull null
                    Finding(severity, title.take(140), (parts.getOrNull(2) ?: "").take(240))
                }
                .take(24)
                .toList()
        }

        fun parseDisagreements(text: String): List<String> {
            val start = text.indexOf("DISAGREEMENTS:")
            if (start < 0) return emptyList()
            return text.substring(start + "DISAGREEMENTS:".length).lineSequence()
                .map { it.trim().removePrefix("-").trim() }
                .filter { it.isNotBlank() && it.length > 12 }
                .take(8)
                .toList()
        }

        /** Union of findings; duplicates (same normalised title) keep the worst severity and longest action. */
        fun mergeFindings(lists: List<List<Finding>>): List<Finding> {
            val byKey = LinkedHashMap<String, Finding>()
            for (list in lists) {
                for (f in list) {
                    val key = f.title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().take(60)
                    if (key.isEmpty()) continue
                    val old = byKey[key]
                    byKey[key] = if (old == null) f
                    else old.copy(
                        severity = if (f.severity.rank < old.severity.rank) f.severity else old.severity,
                        action = if (f.action.length > old.action.length) f.action else old.action,
                    )
                }
            }
            return byKey.values.sortedWith(compareBy({ it.severity.rank }, { it.title }))
        }
    }
}
