package com.oro.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/** Dead and silent free models hand over fast; temporary trouble does not retire a model. */
class KeylessFallbackTest {

    @Test
    fun `a retired model id hands over and stays skipped for hours`() {
        // LLM7, 2026-10-22, for a model id the app still shipped.
        val gone = StreamEvent.Error("Model 'DeepSeek-V4-Flash-0731' is currently unavailable.", httpCode = 400)
        assertThat(KeylessFallback.isRetiredModel(gone)).isTrue()
        assertThat(KeylessFallback.shouldFallBack(gone)).isTrue()
        val id = "test:retired-model"
        KeylessFallback.markBusy(id, gone, now = 0L)
        assertThat(KeylessFallback.isBusy(id, now = 2 * 60 * 60_000L)).isTrue()
        assertThat(KeylessFallback.isBusy(id, now = 7 * 60 * 60_000L)).isFalse()
        KeylessFallback.markHealthy(id)
    }

    @Test
    fun `gateway wording for missing models counts as retired`() {
        listOf(
            StreamEvent.Error("No endpoints found for qwen/qwen3.8-27b:free.", httpCode = 404),
            StreamEvent.Error("""{"error":{"code":"model_not_found"}}""", httpCode = 404),
            StreamEvent.Error("Unknown model: glm-5.3-flash", httpCode = 400),
            StreamEvent.Error("The model deepseek-v4.1-flash has been retired", httpCode = 410)
        ).forEach { assertThat(KeylessFallback.isRetiredModel(it)).isTrue() }
    }

    @Test
    fun `temporary trouble and tool rejections are not retirements`() {
        val overloaded = StreamEvent.Error("Service temporarily unavailable", httpCode = 503)
        assertThat(KeylessFallback.isRetiredModel(overloaded)).isFalse()
        assertThat(KeylessFallback.shouldFallBack(overloaded)).isTrue()
        val tools = StreamEvent.Error("tools are not supported for this model", httpCode = 400)
        assertThat(KeylessFallback.shouldFallBack(tools)).isFalse()
        val rateLimited = StreamEvent.Error("xing4.0 is temporarily rate-limited upstream", httpCode = 429)
        assertThat(KeylessFallback.isRetiredModel(rateLimited)).isFalse()
    }

    @Test
    fun `a candidate that sends nothing hands over after the timeout`() = runTest {
        val silent = flow<StreamEvent> { awaitCancellation() }
        val out = silent.firstEventWithin(35_000) { StreamEvent.Error("No answer: X sent nothing for 35 s", retryable = true) }.toList()
        assertThat(out).hasSize(1)
        val error = out.single() as StreamEvent.Error
        assertThat(error.message).startsWith("No answer:")
        assertThat(KeylessFallback.shouldFallBack(error)).isTrue()
    }

    @Test
    fun `any first event disarms the guard`() = runTest {
        val slowAfterHeaders = flow {
            emit(StreamEvent.Started)
            delay(120_000) // a long think after the headers is fine
            emit(StreamEvent.ContentDelta("hi"))
            emit(StreamEvent.Completed)
        }
        val out = slowAfterHeaders.firstEventWithin(35_000) { StreamEvent.Error("No answer") }.toList()
        assertThat(out).containsExactly(StreamEvent.Started, StreamEvent.ContentDelta("hi"), StreamEvent.Completed).inOrder()
    }

    @Test
    fun `an upstream failure is not swallowed`() = runTest {
        val broken = flow<StreamEvent> { throw IOException("boom") }
        val failure = runCatching { broken.firstEventWithin(35_000) { StreamEvent.Error("No answer") }.toList() }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IOException::class.java)
    }

    private val anchor get() = AiModels.findModelById("k2:IFM/K2-Horizon-375B-A23B")!!

    @Test
    fun `a model the user switched off is never a stand-in`() {
        val all = KeylessFallback.candidates(anchor, needsNativeTools = false, enabled = null) { AiModels.findModelById(it) }.map { it.id }
        assertThat(all).contains("kilo-auto/free")
        val only = setOf("kilo-auto/free", "codestral-latest")
        val chosen = KeylessFallback.candidates(anchor, needsNativeTools = false, enabled = only) { AiModels.findModelById(it) }.map { it.id }
        assertThat(chosen).containsExactlyElementsIn(only.filter { it in all })
    }

    @Test
    fun `a stand-in must hold the request`() {
        val big = KeylessFallback.candidates(anchor, needsNativeTools = false, minContextTokens = 200_000, enabled = null) {
            AiModels.findModelById(it)
        }
        assertThat(big).isNotEmpty()
        assertThat(big.map { it.id }).doesNotContain("codestral-latest") // 32K
        assertThat(big.all { it.contextLength >= 200_000 }).isTrue()
        val none = KeylessFallback.candidates(anchor, needsNativeTools = false, minContextTokens = 5_000_000, enabled = null) {
            AiModels.findModelById(it)
        }
        assertThat(none).isEmpty()
    }

    @Test
    fun `minicpm's logging demo stands in for nothing`() {
        listOf(true, false).forEach { tools ->
            val ids = KeylessFallback.candidates(anchor, needsNativeTools = tools, enabled = null) { AiModels.findModelById(it) }
                .map { it.provider }
            assertThat(ids).doesNotContain(AiProvider.MINICPM)
        }
        assertThat(KeylessFallback.order).doesNotContain("minicpm:MiniCPM-V-4.7-35B-A3B")
    }

    @Test
    fun `silence windows fit each kind of site`() {
        assertThat(KeylessFallback.silenceTimeoutMs(AiProvider.PTERO)).isNull()
        assertThat(KeylessFallback.silenceTimeoutMs(AiProvider.MERCURY)).isEqualTo(75_000L)
        assertThat(KeylessFallback.silenceTimeoutMs(AiProvider.QWEN_WEB)).isEqualTo(75_000L)
        assertThat(KeylessFallback.silenceTimeoutMs(AiProvider.KILOCODE)).isEqualTo(KeylessFallback.SILENT_TIMEOUT_MS)
    }

    @Test
    fun `a gateway whose upstream timed out stays parked for minutes`() {
        // Kilo's free Nemotron on agent-sized prompts (live eval, 2026-10-23).
        val id = "nvidia/nemotron-3-ultra-550b-a55b:free"
        KeylessFallback.markBusy(id, StreamEvent.Error("Upstream idle timeout exceeded", retryable = true), now = 0L)
        assertThat(KeylessFallback.isBusy(id, now = 3 * 60_000L)).isTrue()
        assertThat(KeylessFallback.isBusy(id, now = 6 * 60_000L)).isFalse()
        KeylessFallback.markHealthy(id)
    }
}
