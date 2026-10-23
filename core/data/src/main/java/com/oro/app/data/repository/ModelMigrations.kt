package com.oro.app.data.repository

import com.oro.app.data.ai.SeedModels
import kotlinx.coroutines.flow.first

/**
 * One-time moves of saved model choices when the catalogue changes shape.
 *
 * Runs at startup, after the model table is seeded; each version runs once.
 * Only choices that came from Oro's own defaults move: a default model the
 * user set on purpose stays where it is.
 */
object ModelMigrations {

    const val CURRENT = 1

    private const val K2 = "k2:IFM/K2-Horizon-375B-A23B"
    private const val MERCURY_INSTANT = "mercury:mercury-3-instant"

    suspend fun run(prefs: PreferencesRepository, projects: ProjectRepository) {
        if (prefs.modelMigration() >= CURRENT) return
        val mercury = SeedModels.default.id

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

        prefs.setModelMigration(CURRENT)
    }
}
