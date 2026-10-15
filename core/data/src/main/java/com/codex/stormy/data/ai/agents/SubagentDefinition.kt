package com.codex.stormy.data.ai.agents

import kotlinx.serialization.Serializable

/**
 * What a subagent is allowed to do.
 *
 * The tool allowlist is the whole design decision for a subagent, not an
 * afterthought: a read-only investigator and a worker that mutates the tree
 * are different safety propositions even when they share a model and a prompt.
 * Encoding that as a capability set means the orchestrator can refuse an
 * out-of-scope call instead of trusting the prompt to have discouraged it.
 */
@Serializable
enum class ToolCapability {
    /** Read files, list directories, search. */
    READ,

    /** Create, modify and delete files. */
    WRITE,

    /** Run shell commands. */
    EXECUTE,

    /** Git operations. */
    GIT,

    /** Persist and recall long-term memory. */
    MEMORY,

    /** Spawn further subagents. */
    DELEGATE
}

/**
 * A subagent type.
 *
 * Patterned on the built-ins that the current generation of coding agents
 * settled on, because the shape has proven out: a fast read-only explorer, a
 * planner that researches without editing, and a general worker cover most
 * delegation with no configuration. Specialised types are additive.
 *
 * @param label shown in the UI; this is what the user reads while it runs.
 * @param purpose one line for the model, describing when to pick this type.
 * @param capabilities hard limit on what the subagent may call.
 * @param accentIndex stable colour slot, so a given type always looks the same.
 * @param maxTurns upper bound on tool-use rounds before the subagent is
 *   stopped. An agent that has not finished in this many rounds is usually
 *   looping rather than making progress.
 * @param returnsSummaryOnly when true, only the subagent's final message
 *   reaches the parent conversation. This is the point of delegation: the
 *   verbose middle -- file reads, failed attempts, command output -- stays in
 *   the subagent's own context instead of crowding the orchestrator's.
 */
@Serializable
data class SubagentDefinition(
    val id: String,
    val label: String,
    val purpose: String,
    val capabilities: Set<ToolCapability>,
    val accentIndex: Int,
    val maxTurns: Int = 24,
    val returnsSummaryOnly: Boolean = true,
    val systemPrompt: String
) {
    fun allows(capability: ToolCapability): Boolean = capability in capabilities
}

/**
 * The built-in subagent types.
 *
 * Deliberately few. Every additional type is another choice the model has to
 * make correctly, and a mis-picked specialist is worse than a general worker.
 */
object Subagents {

    val EXPLORE = SubagentDefinition(
        id = "explore",
        label = "Explore",
        purpose = "Read-only search across the project. Use when the answer " +
            "means reading many files and only the conclusion is needed.",
        capabilities = setOf(ToolCapability.READ),
        accentIndex = 0,
        maxTurns = 16,
        systemPrompt = """
            You are a read-only exploration agent inside a mobile web IDE.

            Find what was asked for and report the conclusion. You cannot edit
            files -- do not plan edits or describe diffs you would make.

            Read excerpts rather than whole files; you are locating code, not
            reviewing it. Prefer search over opening files speculatively.

            Finish with a compact answer: the paths that matter, the specific
            lines, and the one-paragraph conclusion. Do not narrate your search.
        """.trimIndent()
    )

    val PLAN = SubagentDefinition(
        id = "plan",
        label = "Plan",
        purpose = "Research and produce an implementation plan without " +
            "changing anything. Use before a large or risky change.",
        capabilities = setOf(ToolCapability.READ),
        accentIndex = 1,
        maxTurns = 20,
        systemPrompt = """
            You are a planning agent inside a mobile web IDE.

            Read enough of the project to design a concrete plan, then return
            it. You cannot modify files.

            A useful plan names the files to touch and what changes in each, in
            the order the work should happen, and calls out the decisions that
            are not obvious along with the trade-off. State what you verified
            and what you assumed.

            Do not pad the plan with steps that are obvious from the code.
        """.trimIndent()
    )

    val BUILD = SubagentDefinition(
        id = "build",
        label = "Build",
        purpose = "Implement a scoped, self-contained change end to end.",
        capabilities = setOf(
            ToolCapability.READ,
            ToolCapability.WRITE,
            ToolCapability.MEMORY
        ),
        accentIndex = 2,
        maxTurns = 32,
        systemPrompt = """
            You are an implementation agent inside a mobile web IDE.

            Carry out the change you were given, completely. Match the
            surrounding code's conventions rather than importing your own.

            Stay inside the stated scope. If you find a second problem, finish
            the first and mention the second in your summary -- do not widen the
            work on your own.

            Report what you changed, file by file, and anything you chose not to
            do and why.
        """.trimIndent()
    )

    val REVIEW = SubagentDefinition(
        id = "review",
        label = "Review",
        purpose = "Read the current changes and report real defects.",
        capabilities = setOf(ToolCapability.READ, ToolCapability.GIT),
        accentIndex = 3,
        maxTurns = 18,
        systemPrompt = """
            You are a review agent inside a mobile web IDE.

            Look for defects that would actually bite: wrong behaviour, broken
            edge cases, state that can desynchronise. For each, give the
            concrete input or sequence that produces the wrong result.

            Do not report style preferences, and do not restate what the code
            does. If you find nothing real, say so plainly -- a clean review is
            a useful result, an invented finding is not.
        """.trimIndent()
    )

    val GENERAL = SubagentDefinition(
        id = "general",
        label = "General",
        purpose = "Multi-step work that does not fit the other types.",
        capabilities = setOf(
            ToolCapability.READ,
            ToolCapability.WRITE,
            ToolCapability.GIT,
            ToolCapability.MEMORY
        ),
        accentIndex = 4,
        maxTurns = 32,
        systemPrompt = """
            You are a general-purpose agent inside a mobile web IDE.

            Do the task you were given and report the outcome. Prefer finishing
            over asking; where a detail is genuinely ambiguous, state the
            assumption you made rather than stopping.

            Report faithfully. If something did not work, say so and say what
            you observed.
        """.trimIndent()
    )

    val all: List<SubagentDefinition> = listOf(EXPLORE, PLAN, BUILD, REVIEW, GENERAL)

    operator fun get(id: String): SubagentDefinition? = all.find { it.id.equals(id, true) }

    /**
     * Maximum nesting depth.
     *
     * A subagent may delegate, but not without limit: each level multiplies
     * token spend and the user loses the thread of who is doing what. Two
     * levels below the main conversation is enough for "explore, then build
     * from what you found" without becoming a tree nobody can follow.
     */
    const val MAX_DEPTH = 2
}
