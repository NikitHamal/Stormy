package com.oro.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelDisplayNameTest {

    private fun name(name: String, provider: AiProvider, id: String = "x") =
        AiModel(id = id, name = name, provider = provider).displayName()

    @Test
    fun `tags that restate the provider or the tier are dropped`() {
        assertEquals("Mercury 3", name("Mercury 3 (community)", AiProvider.MERCURY))
        assertEquals("Claude Opus 4.8", name("Claude Opus 4.8 (UniKey)", AiProvider.UNIKEY))
        assertEquals("Qwen3.5 397B", name("Qwen3.5 397B (free, no key)", AiProvider.OVHCLOUD))
        assertEquals("Codestral", name("Codestral (LLM7, no key)", AiProvider.LLM7))
        assertEquals("K2 Horizon 375B", name("K2 Horizon 375B (community)", AiProvider.K2_HORIZON))
    }

    @Test
    fun `gateway paths are dropped`() {
        assertEquals("gpt-5.5", name("openai/gpt-5.5", AiProvider.OPENROUTER))
    }

    @Test
    fun `a tag that says something else stays`() {
        assertEquals("Gemma 4 26B (preview)", name("Gemma 4 26B (preview)", AiProvider.OPENROUTER))
        assertEquals("Mercury 3", name("Mercury 3", AiProvider.MERCURY))
    }

    @Test
    fun `a name that is only a tag is kept`() {
        assertEquals("(community)", name("(community)", AiProvider.MERCURY))
    }
}
