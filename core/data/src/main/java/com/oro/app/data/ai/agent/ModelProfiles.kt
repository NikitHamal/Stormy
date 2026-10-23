package com.oro.app.data.ai.agent

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.providers.ModelIds

/**
 * How Oro drives one model: the harness knobs that depend on what the model
 * is good at, chosen per model by [ModelProfiles.forModel].
 *
 * One harness does not fit every model. A diffusion model that answers a
 * 1,500-line page in forty seconds is held back by advice written for models
 * whose replies get cut off at a few thousand tokens ("write long files in
 * parts"), and gains most from fewer round trips and from checks the app runs
 * for it; a small model on a slow guest bridge needs the opposite. Every knob
 * here is wired in [com.oro.app.data.ai.agent.AgentLoop]'s config, the system
 * prompt or the request; [DEFAULT] keeps the harness as it was.
 */
data class ModelProfile(
    /** Shown in logs and the eval: "mercury", "fast", "frontier", "default". */
    val name: String,
    /**
     * Write each file whole in one call, several files per reply. Off: the
     * prompt teaches long files in parts (write_file, then append=true),
     * which models with small output limits need.
     */
    val wholeFiles: Boolean = false,
    /** Fix passes the final check may ask for ([AgentLoopConfig.maxQualityPasses]). */
    val qualityPasses: Int = 2,
    /**
     * After a round that changed pages, styles or scripts, the app renders the
     * changed pages at phone width and adds what it measured to the tool
     * results ([AgentLoopConfig.roundNote]). Saves the screenshot_preview
     * round trip a careful model would spend, and tells a careless one.
     */
    val previewEachRound: Boolean = false,
    /** The final check also renders the changed pages and sends back hard layout defects. */
    val visualFinalCheck: Boolean = true,
    /** Silence on an open stream before it is abandoned and retried ([AgentLoopConfig.idleTimeoutMs]). */
    val idleTimeoutMs: Long = 150_000,
    /** Base cadence for pushing a streaming reply to the chat (ms). */
    val streamPushMs: Long = 80,
    /** Output budget to ask for where the provider takes one; null: the provider's default. */
    val maxOutputTokens: Int? = null,
    /**
     * Ceiling on the context the pruner fills, below the model's window; null:
     * the window. Every turn resends the whole history, and a million-token
     * window filled on every turn is slow to upload from a phone and slow to
     * read, for no gain once older steps are folded into the run ledger.
     */
    val contextBudgetTokens: Int? = null,
    /**
     * Put a small project's files into the run's context snapshot, up to this
     * many characters (0: off). A model with a large, fast context then edits
     * without spending turns reading what it could have been given.
     */
    val inlineProjectChars: Int = 0,
    /**
     * Appended to the system prompt. Constant per profile, so the prompt
     * prefix (and providers' prompt caches) stay stable for the whole run.
     */
    val promptSection: String = "",
)

object ModelProfiles {

    /** The harness as it was before profiles: parts for long files, two fix passes. */
    val DEFAULT = ModelProfile(name = "default")

    /**
     * Inception's Mercury (keyless guest chat, the official API, or a gateway).
     *
     * Measured on the keyless site (2026-10-23): a short reply in 0.3-0.7 s; a
     * complete 1,549-line page (84k characters) in one reply in 42 s at High;
     * a needle found in an 840k-character (~200k-token) prompt in 4 s; eight
     * back-to-back requests without a limit. Its weak spot is not speed or
     * length but round trips, so: whole files, everything a step needs in one
     * reply, and the app's own checks feeding it between replies.
     */
    val MERCURY = ModelProfile(
        name = "mercury",
        wholeFiles = true,
        qualityPasses = 3,
        previewEachRound = true,
        visualFinalCheck = true,
        // Long pages take ~15 s of reasoning before the first text at High, and
        // the in-app browser path may load the site first; a stall still shows
        // well before the default's two and a half minutes.
        idleTimeoutMs = 90_000,
        streamPushMs = 40,
        // The official API takes up to 131k; enough for any single file Oro writes.
        maxOutputTokens = 32_768,
        contextBudgetTokens = 128_000,
        inlineProjectChars = 120_000,
        promptSection = """
            # Working at your speed
            You write very fast and your output limit is large. Round trips, not output length, are
            what cost the user time, so work in few, complete replies:
            - Start a build with one reply that states the plan in a few lines (each file and what it
              holds) and then contains every write_file call for those files. Write each file whole in
              ONE write_file call, as long as it needs to be: never in parts, never with append, never
              with placeholders. The advice above about writing long files in parts does not apply to you.
            - Make every edit a change needs in that same reply; read every file you need in one turn.
              Files shown under "Project files" below are their current text: you have read them.
            - After each reply that changes pages, styles or scripts, the app renders the changed pages
              on a phone screen and reports what it measured with the tool results. Fix what it reports
              in your next reply, then finish with check_syntax (no path).
        """.trimIndent(),
    )

    /**
     * Groq and Cerebras serve open models at ~1,000 tokens/s too, but with free
     * tiers counted in tokens per minute: more checks between replies, nothing
     * that inflates the prompt.
     */
    val FAST = ModelProfile(
        name = "fast",
        wholeFiles = false,
        qualityPasses = 3,
        previewEachRound = true,
        idleTimeoutMs = 90_000,
        streamPushMs = 50,
    )

    /**
     * Frontier models with long outputs (Claude, GPT-5 and later, Gemini 2.5+,
     * Grok 4, DeepSeek V4, Qwen 3.8 Max, Kimi K2.7/K3, GLM 5, MiniMax M3):
     * whole files are safe, a preview after each changing round saves them a
     * verification turn, and their million-token windows are worth capping.
     */
    val FRONTIER = ModelProfile(
        name = "frontier",
        wholeFiles = true,
        previewEachRound = true,
        contextBudgetTokens = 256_000,
        promptSection = """
            # Long files
            Your output limit is large: write each file whole in one write_file call. The advice above
            about writing long files in parts is for models with small output limits.
            After each reply that changes pages, styles or scripts, the app renders the changed pages on
            a phone screen and reports what it measured with the tool results; fix what it reports.
        """.trimIndent(),
    )

    /**
     * Guest web bridges other than Mercury (K2, Qwen, Ptero, UniKey, the lab
     * demos): their limits are the site's, so the context stays moderate.
     */
    val COMMUNITY = DEFAULT.copy(name = "community", contextBudgetTokens = 128_000)

    fun forModel(model: AiModel): ModelProfile = forIds(model.provider, model.id)

    fun forIds(provider: AiProvider, modelId: String): ModelProfile {
        val id = familyId(provider, modelId)
        return when {
            provider == AiProvider.MERCURY || provider == AiProvider.INCEPTION -> MERCURY
            // A guest bridge's limits are the site's, whatever model it names (Ptero
            // answers its "mercury" ids with another model altogether).
            provider.isCommunity -> COMMUNITY
            id.startsWith("mercury") -> MERCURY
            provider == AiProvider.GROQ || provider == AiProvider.CEREBRAS -> FAST
            FRONTIER_IDS.any { it.containsMatchIn(id) } -> FRONTIER
            else -> DEFAULT
        }
    }

    /** The upstream model id, lowercase, without gateway owner, community namespace or variant tag. */
    internal fun familyId(provider: AiProvider, modelId: String): String {
        var id = ModelIds.upstream(modelId).lowercase()
        if (provider.isCommunity) id = id.substringAfter(':')
        return id.substringAfterLast('/').substringBefore(':')
    }

    private val FRONTIER_IDS = listOf(
        Regex("""^claude-(opus|sonnet|fable|mythos)"""),
        Regex("""^gpt-([5-9]|\d\d)"""),
        Regex("""^o[34](-|$)"""),
        Regex("""^gemini-(2\.5|[3-9])"""),
        Regex("""^grok-[4-9]"""),
        Regex("""^deepseek-v[4-9]"""),
        Regex("""^qwen3\.[5-9]-max|^qwen3-max"""),
        Regex("""^kimi-k(2\.[5-9]|[3-9])"""),
        Regex("""^glm-[5-9]"""),
        Regex("""^minimax-m[3-9]"""),
    )
}
