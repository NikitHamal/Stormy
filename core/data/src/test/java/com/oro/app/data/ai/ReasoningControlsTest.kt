package com.oro.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.community.MercuryWebClient
import com.oro.app.data.ai.community.QwenWebClient
import com.oro.app.data.ai.providers.AnthropicClient
import com.oro.app.data.ai.providers.ModelIds
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Each model's reasoning control is its provider's own scale: these pin the
 * levels, wire values and defaults from the provider docs (October 2026). A
 * level a provider does not accept is a 400 on every request.
 */
class ReasoningControlsTest {

    private fun model(provider: AiProvider, id: String) = AiModel(id = id, name = id, provider = provider)

    private fun wires(provider: AiProvider, id: String) =
        ReasoningControls.forModel(model(provider, id))?.levels?.map { it.wire }

    private fun initial(provider: AiProvider, id: String) = ReasoningControls.forModel(model(provider, id))?.initial

    @Test
    fun `mercury 3 is one model with the site's four levels, high by default`() {
        val c = SeedModels.default.reasoningControl!!
        assertThat(SeedModels.default.id).isEqualTo("mercury:mercury-3")
        assertThat(c.levels.map { it.wire }).containsExactly("instant", "low", "medium", "high").inOrder()
        assertThat(c.levels.map { it.label }).containsExactly("Instant", "Low", "Medium", "High").inOrder()
        assertThat(c.initial).isEqualTo("high")
        assertThat(c.wire).isEqualTo(ReasoningWire.CLIENT)
        assertThat(AiProvider.DEFAULT).isEqualTo(AiProvider.MERCURY)
    }

    @Test
    fun `inception official api takes the same scale on every mercury`() {
        assertThat(wires(AiProvider.INCEPTION, "mercury-3")).containsExactly("instant", "low", "medium", "high").inOrder()
        assertThat(initial(AiProvider.INCEPTION, "mercury-3")).isEqualTo("high")
        assertThat(initial(AiProvider.INCEPTION, "mercury-2.5")).isEqualTo("medium")
        assertThat(ReasoningControls.forModel(model(AiProvider.INCEPTION, "mercury-3"))!!.wire)
            .isEqualTo(ReasoningWire.REASONING_EFFORT)
        assertThat(wires(AiProvider.INCEPTION, "mercury-edit-2")).isNull()
    }

    @Test
    fun `openai scales follow each gpt release`() {
        assertThat(wires(AiProvider.OPENCODE_ZEN, "gpt-5.5")).containsExactly("none", "low", "medium", "high", "xhigh").inOrder()
        assertThat(initial(AiProvider.OPENCODE_ZEN, "gpt-5.5")).isEqualTo("medium")
        assertThat(initial(AiProvider.OPENCODE_ZEN, "gpt-5.4")).isEqualTo("none")
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-5")).containsExactly("minimal", "low", "medium", "high").inOrder()
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-5-mini")).contains("minimal")
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-5.1")).containsExactly("none", "low", "medium", "high").inOrder()
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-5.4-pro")).containsExactly("medium", "high", "xhigh").inOrder()
        assertThat(wires(AiProvider.OPENROUTER, "openai/o4-mini")).containsExactly("low", "medium", "high").inOrder()
        // Newer than the documented set: only the levels every OpenAI reasoning model takes.
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-5.6")).containsExactly("low", "medium", "high").inOrder()
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-5-chat-latest")).isNull()
        assertThat(wires(AiProvider.OPENROUTER, "openai/gpt-4o")).isNull()
        assertThat(wires(AiProvider.OPENROUTER, "openai/o1-mini")).isNull()
    }

    @Test
    fun `gemini, grok and claude use their own scales`() {
        assertThat(wires(AiProvider.GEMINI, "models/gemini-3-flash-preview")).containsExactly("minimal", "low", "medium", "high").inOrder()
        assertThat(wires(AiProvider.GEMINI, "gemini-3.1-pro-preview")).containsExactly("low", "medium", "high").inOrder()
        assertThat(initial(AiProvider.GEMINI, "gemini-3.1-pro-preview")).isEqualTo("high")
        assertThat(wires(AiProvider.GEMINI, "gemini-2.5-flash-lite")).containsExactly("none", "low", "medium", "high").inOrder()
        assertThat(wires(AiProvider.GEMINI, "gemma-4-31b-it")).isNull()

        assertThat(wires(AiProvider.OPENROUTER, "x-ai/grok-4.3")).containsExactly("none", "low", "medium", "high").inOrder()
        assertThat(initial(AiProvider.OPENROUTER, "x-ai/grok-4.3")).isEqualTo("low")
        assertThat(wires(AiProvider.OPENROUTER, "x-ai/grok-4.7")).containsExactly("low", "medium", "high", "xhigh").inOrder()
        assertThat(wires(AiProvider.OPENROUTER, "x-ai/grok-4")).isNull()

        assertThat(wires(AiProvider.OPENROUTER, "anthropic/claude-opus-4.8")).containsExactly("low", "medium", "high", "xhigh").inOrder()
        assertThat(initial(AiProvider.OPENROUTER, "anthropic/claude-opus-4.8")).isEqualTo("high")
        assertThat(wires(AiProvider.OPENROUTER, "anthropic/claude-sonnet-4.6")).containsExactly("low", "medium", "high").inOrder()
        assertThat(initial(AiProvider.OPENROUTER, "anthropic/claude-opus-5.5")).isEqualTo("medium")
        assertThat(wires(AiProvider.OPENROUTER, "anthropic/claude-opus-4.5")).isNull()
        // Over Anthropic's own protocol: effort without extended thinking, up to High.
        val custom = ReasoningControls.forAnthropicCustom(ModelIds.of("p1", "claude-opus-4-8"))!!
        assertThat(custom.levels.map { it.wire }).containsExactly("low", "medium", "high").inOrder()
        assertThat(custom.wire).isEqualTo(ReasoningWire.ANTHROPIC_EFFORT)
    }

    @Test
    fun `hosts get a control only where it is documented`() {
        assertThat(wires(AiProvider.GROQ, "openai/gpt-oss-120b")).containsExactly("low", "medium", "high").inOrder()
        assertThat(wires(AiProvider.GROQ, "qwen/qwen3.8-27b")).containsExactly("none", "default", "low", "medium", "high").inOrder()
        assertThat(initial(AiProvider.GROQ, "qwen/qwen3.8-27b")).isEqualTo("default")
        assertThat(wires(AiProvider.GROQ, "moonshotai/kimi-k2-instruct")).isNull()
        assertThat(wires(AiProvider.CEREBRAS, "gpt-oss-120b")).containsExactly("low", "medium", "high").inOrder()
        // Same model, no documented parameter on these hosts.
        assertThat(wires(AiProvider.OVHCLOUD, "gpt-oss-120b")).isNull()
        assertThat(wires(AiProvider.POLLINATIONS, "openai-fast")).isNull()
        assertThat(wires(AiProvider.LLM7, "GLM-5.3-Flash")).isNull()
        assertThat(wires(AiProvider.NVIDIA, "deepseek-ai/deepseek-v4.1-flash")).isNull()
        assertThat(wires(AiProvider.UNIKEY, "unikey:x-ai/grok-4.3")).isNull()
        assertThat(wires(AiProvider.K2_HORIZON, "k2:IFM/K2-Horizon-375B-A23B")).isNull()
        // Gateways translate their own reasoning_effort per upstream.
        assertThat(wires(AiProvider.KILOCODE, "deepseek/deepseek-v4-pro")).containsExactly("none", "low", "high").inOrder()
        assertThat(wires(AiProvider.KILOCODE, "kilo-auto/free")).isNull()
        // DeepSeek's own API through a custom provider: low, high, max.
        assertThat(wires(AiProvider.CUSTOM, ModelIds.of("ds", "deepseek-v4-pro"))).containsExactly("low", "high", "max").inOrder()
    }

    @Test
    fun `qwen chat has a thinking switch where the model is not always thinking`() {
        val c = ReasoningControls.forModel(model(AiProvider.QWEN_WEB, "qwen:qwen3.7-plus"))!!
        assertThat(c.title).isEqualTo("Thinking")
        assertThat(c.levels.map { it.label }).containsExactly("Off", "On").inOrder()
        assertThat(c.initial).isEqualTo("off")
        assertThat(wires(AiProvider.QWEN_WEB, "qwen:qwen3.8-max")).isNull()
        assertThat(QwenWebClient.thinkingFor("qwen3.7-plus", "on")).isTrue()
        assertThat(QwenWebClient.thinkingFor("qwen3.7-plus", null)).isFalse()
        assertThat(QwenWebClient.thinkingFor("qwen3.8-max", "off")).isTrue()
    }

    @Test
    fun `the saved choice wins, then an old global choice the scale has, then the initial level`() {
        val gpt = model(AiProvider.OPENCODE_ZEN, "gpt-5.5")
        assertThat(ReasoningControls.chosen(gpt, emptyMap())).isEqualTo("medium")
        assertThat(ReasoningControls.chosen(gpt, mapOf("gpt-5.5" to "xhigh"))).isEqualTo("xhigh")
        // A value the scale lacks is never sent.
        assertThat(ReasoningControls.chosen(gpt, mapOf("gpt-5.5" to "instant"))).isEqualTo("medium")
        // The old app-wide Deep becomes High where the scale has it...
        assertThat(ReasoningControls.legacyLevel("DEEP")).isEqualTo("high")
        assertThat(ReasoningControls.legacyLevel("BALANCED")).isNull()
        assertThat(ReasoningControls.chosen(gpt, mapOf(ReasoningControls.LEGACY_KEY to "high"))).isEqualTo("high")
        // ...but never over Mercury's deliberate High, and a model's own choice beats it.
        val mercury = SeedModels.default
        assertThat(ReasoningControls.chosen(mercury, mapOf(ReasoningControls.LEGACY_KEY to "low"))).isEqualTo("high")
        assertThat(ReasoningControls.chosen(mercury, mapOf(mercury.id to "instant"))).isEqualTo("instant")
        // No control, nothing sent.
        assertThat(ReasoningControls.chosen(model(AiProvider.OVHCLOUD, "Qwen3.8-27B"), mapOf(ReasoningControls.LEGACY_KEY to "high"))).isNull()
    }

    @Test
    fun `openai reasoning models leave temperature out where the api is openai's own`() {
        assertThat(ReasoningControls.omitsTemperature(model(AiProvider.OPENCODE_ZEN, "gpt-5.4"))).isTrue()
        assertThat(ReasoningControls.omitsTemperature(model(AiProvider.CUSTOM, ModelIds.of("p", "o4-mini")))).isTrue()
        assertThat(ReasoningControls.omitsTemperature(model(AiProvider.OPENROUTER, "openai/gpt-5.4"))).isFalse()
    }

    @Test
    fun `claude from opus 4_7 on never gets a temperature, on any host`() {
        // Removed (400) on Opus 4.7+, Opus 5.x, Sonnet 5, Fable, Mythos; only the
        // default accepted on Sonnet 5.5 and Haiku 5.5.
        listOf(
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-opus-4-7")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-opus-4-8")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-opus-5")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-opus-5-5")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-sonnet-5")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-haiku-5-5")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-fable-5-1")),
            model(AiProvider.CUSTOM, ModelIds.of("anthropic", "claude-mythos-5-1")),
            model(AiProvider.OPENCODE_ZEN, "claude-sonnet-5-5"),
            model(AiProvider.OPENROUTER, "anthropic/claude-opus-4.8"),
            model(AiProvider.KILOCODE, "anthropic/claude-sonnet-5.5")
        ).forEach { assertThat(ReasoningControls.omitsTemperature(it)).isTrue() }
        // Still taken: Opus 4.6, Sonnet 4.6, Haiku 4.5 and older.
        listOf("claude-opus-4-6", "claude-sonnet-4-6", "claude-haiku-4-5-20251001", "claude-3-5-sonnet-latest").forEach {
            assertThat(ReasoningControls.omitsTemperature(model(AiProvider.CUSTOM, ModelIds.of("anthropic", it)))).isFalse()
        }
    }

    @Test
    fun `a custom anthropic-protocol provider's picker shows what its requests send`() {
        val anthropic = com.oro.app.data.ai.providers.CustomProviderConfig(
            id = "anth", name = "Anthropic", protocol = com.oro.app.data.ai.providers.WireProtocol.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1"
        )
        val openai = com.oro.app.data.ai.providers.CustomProviderConfig(id = "oai", name = "Other", baseUrl = "https://x.test/v1")
        com.oro.app.data.ai.providers.CustomProviderRegistry.update(
            com.oro.app.data.ai.providers.ProviderSettings(custom = listOf(anthropic, openai))
        )
        try {
            // Sonnet 4.6 over Anthropic's protocol: Claude's own effort, picked like everything else.
            val sonnet = model(AiProvider.CUSTOM, ModelIds.of("anth", "claude-sonnet-4-6")).reasoningControl!!
            assertThat(sonnet.wire).isEqualTo(ReasoningWire.ANTHROPIC_EFFORT)
            assertThat(sonnet.levels.map { it.wire }).containsExactly("low", "medium", "high").inOrder()
            assertThat(sonnet).isEqualTo(ReasoningControls.forAnthropicCustom(ModelIds.of("anth", "claude-sonnet-4-6")))
            // A non-Claude model there gets no effort, since none is sent.
            assertThat(model(AiProvider.CUSTOM, ModelIds.of("anth", "deepseek-v4-pro")).reasoningControl).isNull()
            // The same ids behind an OpenAI-protocol provider keep that protocol's rules.
            assertThat(model(AiProvider.CUSTOM, ModelIds.of("oai", "deepseek-v4-pro")).reasoningControl!!.levels.map { it.wire })
                .containsExactly("low", "high", "max").inOrder()
        } finally {
            com.oro.app.data.ai.providers.CustomProviderRegistry.update(com.oro.app.data.ai.providers.ProviderSettings())
        }
    }

    @Test
    fun `gateway ids are reduced to the upstream family`() {
        assertThat(ReasoningControls.familyId("anthropic/claude-opus-4.8")).isEqualTo("claude-opus-4-8")
        assertThat(ReasoningControls.familyId("openai/gpt-oss-120b:free")).isEqualTo("gpt-oss-120b")
        assertThat(ReasoningControls.familyId("models/gemini-3-flash")).isEqualTo("gemini-3-flash")
    }

    @Test
    fun `levels reach the wire in each protocol's own field`() {
        // The manager leaves temperature out for Opus 4.8 (Anthropic rejects it there).
        val body = AnthropicClient.buildBody(
            "claude-opus-4-8", listOf(ChatRequestMessage("user", "hi")), null, null, 1_024, effort = "medium"
        )
        assertThat(body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content).isEqualTo("medium")
        assertThat(body.containsKey("thinking")).isFalse()
        assertThat(body.containsKey("temperature")).isFalse()
        val plain = AnthropicClient.buildBody("claude-opus-4-8", listOf(ChatRequestMessage("user", "hi")), null, null, 1_024)
        assertThat(plain.containsKey("output_config")).isFalse()
        // Older Claude still takes one.
        val older = AnthropicClient.buildBody("claude-sonnet-4-6", listOf(ChatRequestMessage("user", "hi")), null, 0.5f, 1_024)
        assertThat(older["temperature"]!!.jsonPrimitive.content).isEqualTo("0.5")

        assertThat(MercuryWebClient.EFFORTS).containsExactly("instant", "low", "medium", "high")
        val mercury = MercuryWebClient.buildBody("instant", listOf(ChatRequestMessage("user", "hi")))!!
        assertThat(mercury["reasoningEffort"]!!.jsonPrimitive.content).isEqualTo("instant")
        // A retired "Mercury 3 Instant" id still asks for Instant if one is left anywhere.
        assertThat(MercuryWebClient.effortFor("mercury-3-instant")).isEqualTo("instant")
        assertThat(MercuryWebClient.effortFor("mercury-3")).isEqualTo("high")
    }

    @Test
    fun `a failed browser check parks the model for minutes, not seconds`() {
        val now = 1_000_000L
        KeylessFallback.markBusy("test:browser", StreamEvent.Error("Mercury: the site's browser check has not passed yet; try again in a minute", httpCode = 429), now)
        assertThat(KeylessFallback.isBusy("test:browser", now + 9 * 60_000L)).isTrue()
        KeylessFallback.markBusy("test:quota", StreamEvent.Error("rate limit", httpCode = 429), now)
        assertThat(KeylessFallback.isBusy("test:quota", now + 2 * 60_000L)).isFalse()
        KeylessFallback.markHealthy("test:browser")
    }
}
