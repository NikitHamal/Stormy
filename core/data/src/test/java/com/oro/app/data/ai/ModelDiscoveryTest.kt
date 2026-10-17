package com.oro.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** `/models` parsing against payload shapes recorded from live providers. */
class ModelDiscoveryTest {

    private val service = OpenAiCompatibleModelService()

    @Test
    fun `keyless kilo lists only free models and reads context and tool support`() {
        val body = """
            {"data":[
              {"id":"kilo-auto/free","name":"Auto Free","context_length":256000,
               "top_provider":{"context_length":256000,"max_completion_tokens":32768},
               "pricing":{"prompt":"0","completion":"0"},"supported_parameters":["max_tokens","tools"],"isFree":true},
              {"id":"reflection/beam-free","top_provider":{"context_length":1048576},"supported_parameters":["tools"],"isFree":true},
              {"id":"nvidia/nemotron-3.5-content-safety:free","context_length":128000,"supported_parameters":["max_tokens"],"isFree":true},
              {"id":"cohere/north-mini-code:free","context_length":256000,"supported_parameters":["max_tokens"]},
              {"id":"anthropic/claude-sonnet-5.5","context_length":200000,"pricing":{"prompt":"0.000003","completion":"0.000015"}}
            ]}
        """
        val models = service.parseModels(AiProvider.KILOCODE, body, hasKey = false)
        assertThat(models.map { it.id }).containsExactly(
            "kilo-auto/free", "reflection/beam-free", "cohere/north-mini-code:free"
        )
        val beam = models.first { it.id == "reflection/beam-free" }
        assertThat(beam.contextLength).isEqualTo(1_048_576)
        assertThat(beam.reportedContextLength).isEqualTo(1_048_576)
        assertThat(beam.supportsToolCalls).isTrue()
        assertThat(models.first { it.id.startsWith("cohere") }.supportsToolCalls).isFalse()

        // With a key the paid model is listed too.
        assertThat(service.parseModels(AiProvider.KILOCODE, body, hasKey = true).map { it.id })
            .contains("anthropic/claude-sonnet-5.5")
    }

    @Test
    fun `ovh catalogue drops non-chat models and keeps reported windows`() {
        val body = """
            {"data":[
              {"id":"stable-diffusion-xl-base-v10","context_length":0},
              {"id":"Qwen3-Embedding-8B","context_length":32768},
              {"id":"bge-m3","context_length":8192},
              {"id":"whisper-large-v3"},
              {"id":"Qwen3Guard-Gen-8B","context_length":32768},
              {"id":"Qwen3.5-397B-A17B","context_length":262144,"max_completion_tokens":262144},
              {"id":"Meta-Llama-3_3-70B-Instruct","context_length":131072}
            ]}
        """
        val models = service.parseModels(AiProvider.OVHCLOUD, body, hasKey = false)
        assertThat(models.map { it.id }).containsExactly("Meta-Llama-3_3-70B-Instruct", "Qwen3.5-397B-A17B")
        assertThat(models.first { it.id.startsWith("Qwen") }.contextLength).isEqualTo(262_144)
    }

    @Test
    fun `context_window object form and max_tokens is not mistaken for a window`() {
        val body = """
            {"data":[
              {"id":"GLM-5.3-Flash","context_window":{"tokens":400000}},
              {"id":"agnes-3.0-flash","max_tokens":65536},
              {"id":"mystery-model","max_tokens":4096}
            ]}
        """
        val models = service.parseModels(AiProvider.CUSTOM, body, hasKey = true).associateBy { it.id }
        assertThat(models.getValue("GLM-5.3-Flash").contextLength).isEqualTo(400_000)
        // Agnes reports only its 65k output cap; the family table (512K) wins.
        assertThat(models.getValue("agnes-3.0-flash").contextLength).isEqualTo(524_288)
        assertThat(models.getValue("agnes-3.0-flash").reportedContextLength).isNull()
        assertThat(models.getValue("mystery-model").contextLength).isEqualTo(ModelCapabilities.DEFAULT_CONTEXT)
    }
}
