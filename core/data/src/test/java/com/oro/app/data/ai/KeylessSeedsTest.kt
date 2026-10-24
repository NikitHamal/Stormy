package com.oro.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The 2026-10-22 keyless additions: well-formed, findable, collision-free. */
class KeylessSeedsTest {

    @Test
    fun `every new seed works without a key and takes native tools`() {
        assertThat(KeylessSeeds.all).isNotEmpty()
        KeylessSeeds.all.forEach { m ->
            assertThat(m.provider.worksWithoutKey).isTrue()
            assertThat(m.contextLength).isAtLeast(128_000)
            // Gateways take native tools; a community bridge does only when its client says so.
            if (!m.provider.isCommunity) assertThat(m.supportsToolCalls).isTrue()
            else assertThat(m.supportsToolCalls).isEqualTo(com.oro.app.data.ai.community.CommunityClients.takesTools(m.provider))
        }
    }

    @Test
    fun `new seed ids are unique and collide with no shipped id`() {
        val ids = KeylessSeeds.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
        val shipped = SeedModels.all.map { it.id }.toSet()
        assertThat(ids.filter { it in shipped }).isEmpty()
    }

    @Test
    fun `new seeds are found by id and listed under their provider`() {
        assertThat(AiModels.findModelById("nvidia/nemotron-3-ultra-550b-a55b:free")?.provider)
            .isEqualTo(AiProvider.KILOCODE)
        assertThat(AiModels.findModelById("hy3:hy3")?.provider).isEqualTo(AiProvider.HY3)
        assertThat(AiModels.getAllModels().map { it.id }).containsAtLeastElementsIn(KeylessSeeds.all.map { it.id })
    }

    @Test
    fun `every fallback id is a model the app ships`() {
        KeylessFallback.order.forEach { id ->
            assertThat(AiModels.findModelById(id)).isNotNull()
        }
    }

    @Test
    fun `native tool requests hand over to hy3 first, then the kilo models`() {
        val from = AiModels.findModelById("k2:IFM/K2-Horizon-375B-A23B")!!
        KeylessFallback.order.forEach { KeylessFallback.markHealthy(it) }
        val native = KeylessFallback.candidates(from, needsNativeTools = true) { AiModels.findModelById(it) }.map { it.id }
        // Hy3 leads on the live eval's evidence; Kilo's Nemotron times out on agent-sized prompts.
        assertThat(native.take(2)).containsExactly("hy3:hy3", "nvidia/nemotron-3-ultra-550b-a55b:free").inOrder()
        assertThat(native).containsAtLeast("cohere/north-mini-code:free", "dots-studio/dots-3-note-preview:free", "laguna:laguna-s-2.1")
        assertThat(native.none { it.startsWith("unikey:") || it.startsWith("mercury:") || it.startsWith("cfai:") }).isTrue()
    }

    // ---------------------------------------- routes too limited for agent runs (2026-10-23)

    @Test
    fun `removed providers are gone and their stored rows are dropped by name`() {
        val removed = com.oro.app.data.repository.AiModelRepository.REMOVED_PROVIDERS
        assertThat(removed).containsExactly("POLLINATIONS", "VLM_RUN", "MINICPM")
        assertThat(AiProvider.entries.map { it.name }).containsNoneIn(removed)
    }

    @Test
    fun `ovhcloud and llm7 are key-only now, with no keyless seeds or stand-ins`() {
        for (provider in listOf(AiProvider.OVHCLOUD, AiProvider.LLM7)) {
            assertThat(provider.requiresApiKey).isTrue()
            assertThat(AiModels.getModelsForProvider(provider)).isEmpty()
        }
        val keyOnly = com.oro.app.data.repository.ModelMigrations.KEY_ONLY_NOW.values.flatten()
        assertThat(KeylessFallback.order).containsNoneIn(keyOnly)
        assertThat(KeylessFallback.order).containsNoneIn(com.oro.app.data.repository.ModelMigrations.REMOVED_KEYLESS)
    }

    @Test
    fun `removed models are no longer shipped`() {
        val shipped = AiModels.getAllModels().map { it.id }
        assertThat(shipped).containsNoneIn(com.oro.app.data.repository.ModelMigrations.REMOVED_KEYLESS)
        assertThat(shipped).containsNoneIn(com.oro.app.data.repository.ModelMigrations.KEY_ONLY_NOW.values.flatten())
    }

    @Test
    fun `every keyless provider left takes part in the fallback, and text-mode requests find stand-ins`() {
        val keylessProviders = AiModels.getAllModels()
            .filter { it.provider.worksWithoutKey && it.provider != AiProvider.CUSTOM }
            .map { it.provider }.toSet()
        val inOrder = KeylessFallback.order.mapNotNull { AiModels.findModelById(it)?.provider }.toSet()
        assertThat(inOrder).containsAtLeastElementsIn(keylessProviders)
        KeylessFallback.order.forEach { KeylessFallback.markHealthy(it) }
        val text = KeylessFallback.candidates(SeedModels.default, needsNativeTools = false, enabled = null) { AiModels.findModelById(it) }
        assertThat(text.map { it.id }).containsAtLeast("hy3:hy3", "cfai:@cf/google/gemma-4-26b-a4b-it", "k2:IFM/K2-Horizon-375B-A23B")
        assertThat(text.map { it.id }).doesNotContain(SeedModels.default.id)
    }
}
