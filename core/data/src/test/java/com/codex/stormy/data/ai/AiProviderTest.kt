package com.codex.stormy.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the invariants that make the provider registry safe to extend.
 *
 * These are cheap, but each one encodes a bug that actually shipped: the app's
 * default provider stopped working with no key and nothing caught it, and the
 * per-provider `when` blocks silently disagreed about which providers were
 * usable.
 */
class AiProviderTest {

    @Test
    fun `default provider needs no api key`() {
        // A fresh install must be able to send a message before the user has
        // visited Settings. This is the invariant that broke when DeepInfra
        // retired its anonymous endpoint.
        assertThat(AiProvider.DEFAULT.worksWithoutKey).isTrue()
    }

    @Test
    fun `default provider has a seed model`() {
        assertThat(AiModels.getModelsForProvider(AiProvider.DEFAULT)).isNotEmpty()
    }

    @Test
    fun `every provider that requires a key tells the user where to get one`() {
        val missing = AiProvider.entries
            .filter { it.requiresApiKey && it.apiKeyUrl.isBlank() }
            .map { it.name }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `every provider except custom has a base url`() {
        val missing = AiProvider.entries
            .filter { it != AiProvider.CUSTOM && it.baseUrl.isBlank() }
            .map { it.name }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `base urls have no trailing slash so path joins do not double up`() {
        val offenders = AiProvider.entries.filter { it.baseUrl.endsWith("/") }.map { it.name }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `seed models are attributed to the provider that hosts them`() {
        AiProvider.entries.forEach { provider ->
            AiModels.getModelsForProvider(provider).forEach { model ->
                assertThat(model.provider).isEqualTo(provider)
            }
        }
    }

    @Test
    fun `seed model ids are unique within a provider`() {
        AiProvider.entries.forEach { provider ->
            val ids = AiModels.getModelsForProvider(provider).map { it.id }
            assertThat(ids).containsNoDuplicates()
        }
    }

    @Test
    fun `fromString is case insensitive and falls back to the default`() {
        assertThat(AiProvider.fromString("gemini")).isEqualTo(AiProvider.GEMINI)
        assertThat(AiProvider.fromString("GEMINI")).isEqualTo(AiProvider.GEMINI)
        assertThat(AiProvider.fromString("not-a-provider")).isEqualTo(AiProvider.DEFAULT)
    }

    @Test
    fun `recommended models are all real catalogue entries`() {
        AiModels.getRecommendedModels().forEach { model ->
            assertThat(AiModels.findModelById(model.id)).isNotNull()
        }
    }
}

class AiProviderManagerTest {

    @Test
    fun `provider needing a key reports a configuration error when none is set`() {
        val manager = AiProviderManager(apiKeys = emptyMap())
        assertThat(manager.configurationError(AiProvider.GEMINI)).isNotNull()
        assertThat(manager.isProviderConfigured(AiProvider.GEMINI)).isFalse()
    }

    @Test
    fun `keyless provider is configured out of the box`() {
        val manager = AiProviderManager(apiKeys = emptyMap())
        assertThat(manager.configurationError(AiProvider.DEFAULT)).isNull()
        assertThat(manager.getConfiguredProviders()).contains(AiProvider.DEFAULT)
    }

    @Test
    fun `whitespace-only key counts as absent`() {
        // Users paste keys with stray newlines; treating "  " as configured
        // sends an Authorization header of "Bearer" and gets a 401 the user
        // cannot explain.
        val manager = AiProviderManager(apiKeys = mapOf(AiProvider.GEMINI to "   "))
        assertThat(manager.isProviderConfigured(AiProvider.GEMINI)).isFalse()
    }

    @Test
    fun `custom provider needs a base url even though its key is optional`() {
        val noUrl = AiProviderManager(apiKeys = mapOf(AiProvider.CUSTOM to "k"))
        assertThat(noUrl.configurationError(AiProvider.CUSTOM)).isNotNull()

        val withUrl = AiProviderManager(
            apiKeys = mapOf(AiProvider.CUSTOM to "k"),
            customBaseUrl = "http://localhost:11434/v1"
        )
        assertThat(withUrl.configurationError(AiProvider.CUSTOM)).isNull()
    }

    @Test
    fun `a set key configures the provider`() {
        val manager = AiProviderManager(apiKeys = mapOf(AiProvider.GROQ to "gsk_test"))
        assertThat(manager.isProviderConfigured(AiProvider.GROQ)).isTrue()
    }
}

/**
 * Reasoning-effort support is matched on model id, so these pin the families
 * that must and must not receive the parameter. Being wrong in the permissive
 * direction costs a 400 on every request to that model.
 */
class ReasoningEffortTest {

    @Test
    fun `default is balanced`() {
        assertThat(ReasoningEffort.DEFAULT).isEqualTo(ReasoningEffort.BALANCED)
    }

    @Test
    fun `wire values are the OpenAI-compatible vocabulary`() {
        assertThat(ReasoningEffort.LOW.wireValue).isEqualTo("low")
        assertThat(ReasoningEffort.BALANCED.wireValue).isEqualTo("medium")
        assertThat(ReasoningEffort.DEEP.wireValue).isEqualTo("high")
    }

    @Test
    fun `fromName is lenient and falls back to the default`() {
        assertThat(ReasoningEffort.fromName("deep")).isEqualTo(ReasoningEffort.DEEP)
        assertThat(ReasoningEffort.fromName("DEEP")).isEqualTo(ReasoningEffort.DEEP)
        assertThat(ReasoningEffort.fromName(null)).isEqualTo(ReasoningEffort.DEFAULT)
        assertThat(ReasoningEffort.fromName("nonsense")).isEqualTo(ReasoningEffort.DEFAULT)
    }

    @Test
    fun `reasoning families are recognised`() {
        listOf(
            "gpt-5.4", "o3-mini", "gpt-5.1-codex", "gemini-3.8-flash",
            "gemini-2.5-pro", "claude-opus-5", "grok-4.5", "qwen3.8-max",
            "deepseek-v4-pro", "glm-5.3", "nvidia/nemotron-3-super-120b-a12b"
        ).forEach { id ->
            assertThat(ReasoningEffortSupport.supports(id)).isTrue()
        }
    }

    @Test
    fun `non-reasoning and lite tiers are excluded`() {
        listOf(
            "gpt-4o", "openai-fast", "mistral-large-4",
            // Lite tiers drop the budget even though the family matches.
            "gemini-3.5-flash-lite", "gpt-5.4-nano"
        ).forEach { id ->
            assertThat(ReasoningEffortSupport.supports(id)).isFalse()
        }
    }
}

/** DeepInfra was removed; nothing should still reference it. */
class RemovedProviderTest {

    @Test
    fun `deepinfra is gone from the provider set`() {
        assertThat(AiProvider.entries.map { it.name }).doesNotContain("DEEPINFRA")
        assertThat(AiProvider.entries.map { it.displayName }).doesNotContain("DeepInfra")
    }

    @Test
    fun `an unknown provider name still resolves to the keyless default`() {
        // Existing installs may have "DEEPINFRA" saved as their provider.
        assertThat(AiProvider.fromString("DEEPINFRA")).isEqualTo(AiProvider.DEFAULT)
        assertThat(AiProvider.DEFAULT.worksWithoutKey).isTrue()
    }
}
