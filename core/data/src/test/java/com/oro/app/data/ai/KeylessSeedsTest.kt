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
        // The VLM Run seed is the gateway's alias, not the canonical id Kilo also lists.
        assertThat(ids).doesNotContain("qwen/qwen3.8-27b")
    }

    @Test
    fun `new seeds are found by id and listed under their provider`() {
        assertThat(AiModels.findModelById("nvidia/nemotron-3-ultra-550b-a55b:free")?.provider)
            .isEqualTo(AiProvider.KILOCODE)
        assertThat(AiModels.getModelsForProvider(AiProvider.VLM_RUN).map { it.id }).containsExactly("qwen3.8-27b")
        val llm7 = AiModels.getModelsForProvider(AiProvider.LLM7).map { it.id }
        assertThat(llm7).contains("minimax-m2.7")
        assertThat(llm7).containsAtLeastElementsIn(SeedModels[AiProvider.LLM7].map { it.id })
        assertThat(AiModels.getAllModels().map { it.id }).containsAtLeastElementsIn(KeylessSeeds.all.map { it.id })
    }

    @Test
    fun `every fallback id is a model the app ships`() {
        KeylessFallback.order.forEach { id ->
            assertThat(AiModels.findModelById(id)).isNotNull()
        }
    }

    @Test
    fun `native tool requests hand over to the new kilo models first`() {
        val from = AiModels.findModelById("k2:IFM/K2-Horizon-375B-A23B")!!
        KeylessFallback.order.forEach { KeylessFallback.markHealthy(it) }
        val native = KeylessFallback.candidates(from, needsNativeTools = true) { AiModels.findModelById(it) }.map { it.id }
        assertThat(native.first()).isEqualTo("nvidia/nemotron-3-ultra-550b-a55b:free")
        assertThat(native).containsAtLeast("cohere/north-mini-code:free", "qwen3.8-27b", "minimax-m2.7")
        assertThat(native.none { it.startsWith("unikey:") || it.startsWith("mercury:") }).isTrue()
    }

    @Test
    fun `anonymous vlm run catalogue is the seed whatever the body lists`() {
        val body = """
            {"object":"list","data":[
              {"id":"google/gemini-3.5-flash-lite","object":"model","context_length":1048576},
              {"id":"qwen/qwen3.8-27b","object":"model","aliases":["Qwen/Qwen3.8-27B","qwen3.8-27b"],"context_length":168000}
            ]}
        """
        val service = OpenAiCompatibleModelService()
        assertThat(service.parseModels(AiProvider.VLM_RUN, body, hasKey = false).map { it.id })
            .containsExactly("qwen3.8-27b")
        // A key unlocks the paid catalogue, listed as the gateway names it.
        assertThat(service.parseModels(AiProvider.VLM_RUN, body, hasKey = true).map { it.id })
            .containsAtLeast("google/gemini-3.5-flash-lite", "qwen/qwen3.8-27b")
    }
}
