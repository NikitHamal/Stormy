package com.oro.app.data.repository

import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.SeedModels
import kotlinx.coroutines.flow.first

/**
 * One-time moves of saved model choices when the catalogue changes shape.
 *
 * Runs at startup, after the model table is seeded; each version runs once.
 * Only choices that came from Oro's own defaults move: a default model the
 * user set on purpose stays where it is. A choice of a model that no longer
 * exists moves to Mercury 3 (High), because it would only fail.
 */
object ModelMigrations {

    const val CURRENT = 2

    private const val K2 = "k2:IFM/K2-Horizon-375B-A23B"
    private const val MERCURY_INSTANT = "mercury:mercury-3-instant"

    /**
     * Keyless models removed on 2026-10-23 because their routes could not
     * carry an agent run (docs/research/keyless-providers.md): Pollinations,
     * VLM Run and OpenBMB's MiniCPM demo are gone, and Cloudflare's GLM-4.7
     * Flash could not make reliable text tool calls.
     */
    internal val REMOVED_KEYLESS = setOf(
        "openai-fast",                        // Pollinations: about one reply per 30 s
        "qwen3.8-27b",                        // VLM Run: 30 requests an hour, 100 a day
        "minicpm:MiniCPM-V-4.7-35B-A3B",      // MiniCPM: 4 requests at a time for all visitors
        "cfai:@cf/zai-org/glm-4.7-flash"      // Cloudflare: unreliable text tool calls
    )

    /**
     * Anonymous-tier seeds of providers that are key-only now: they still
     * work for someone with a key, so they move only without one.
     */
    internal val KEY_ONLY_NOW: Map<AiProvider, Set<String>> = mapOf(
        // 2 requests a minute per model without a key.
        AiProvider.OVHCLOUD to setOf("Qwen3.5-397B-A17B", "Qwen3.8-27B", "Meta-Llama-3_3-70B-Instruct"),
        // About 100k tokens a day without a key.
        AiProvider.LLM7 to setOf("codestral-latest", "gpt-oss:20b", "default", "minimax-m2.7")
    )

    suspend fun run(prefs: PreferencesRepository, projects: ProjectRepository, models: AiModelRepository) {
        val version = prefs.modelMigration()
        if (version >= CURRENT) return
        val mercury = SeedModels.default.id

        if (version < 1) {
            // 1. "Mercury 3 Instant" was its own seed; it is Mercury 3 at Instant now.
            var instant = projects.replaceLastUsedModelId(MERCURY_INSTANT, mercury)
            if (prefs.defaultModelId.first() == MERCURY_INSTANT) {
                prefs.setDefaultModelId(mercury)
                instant++
            }
            if (prefs.aiModel.first() == MERCURY_INSTANT) {
                prefs.setAiModel(mercury)
                instant++
            }
            if (instant > 0) prefs.setReasoningLevel(mercury, "instant")

            // 2. The out-of-the-box default moved from K2 Horizon to Mercury 3 (High).
            //    Only that default follows: a project's last-used model and the
            //    selected model are written by explicit picks, and someone who chose
            //    K2 (say, because Mercury's browser check fails on their network)
            //    keeps it. Projects with no pick already use the new default.
            if (!prefs.hasSetDefaultModel.first() && prefs.defaultModelId.first() == K2) prefs.clearDefaultModel()
        }

        if (version < 2) {
            // 3. Keyless routes too limited for an agent run were removed. Rows of
            //    deleted providers go at every launch (AiModelRepository); the
            //    anonymous rows of OVHcloud and LLM7 go here, once, unless the user
            //    has a key for them. Choices of a removed model move to Mercury 3.
            val gone = REMOVED_KEYLESS.toMutableSet()
            for ((provider, ids) in KEY_ONLY_NOW) {
                if (prefs.getApiKeyForProvider(provider).isBlank()) {
                    models.retireProviderRows(provider)
                    gone += ids
                }
            }
            moveChoices(gone, mercury, prefs, projects)
        }

        prefs.setModelMigration(CURRENT)
    }

    /**
     * Points every saved choice of a model in [gone] at [target]: projects'
     * last-used model, the selected model, and a default the user set (cleared,
     * so the out-of-the-box default -- Mercury 3 -- applies).
     */
    private suspend fun moveChoices(gone: Set<String>, target: String, prefs: PreferencesRepository, projects: ProjectRepository) {
        for (id in gone) projects.replaceLastUsedModelId(id, target)
        if (prefs.defaultModelId.first() in gone) prefs.clearDefaultModel()
        if (prefs.aiModel.first() in gone) prefs.setAiModel(target)
    }
}
