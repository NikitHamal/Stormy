package com.codex.stormy.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Context windows were being reported as 8192 for essentially every model,
 * because the only fallback matched ids that literally contained a size
 * ("…-128k") and almost none do. The visible symptom was a context meter
 * reading 49% on a model with a 1M window; the invisible one was compaction
 * firing on conversations that had used a fraction of what was available.
 */
class ModelCapabilitiesTest {

    @Test
    fun `million-token families report a million-token window`() {
        listOf(
            "gemini-3.8-flash", "gemini-3-flash", "gemini-2.5-pro",
            "models/gemini-3.1-pro"
        ).forEach { id ->
            assertThat(ModelCapabilities.contextWindowFor(id)).isAtLeast(1_000_000)
        }
    }

    @Test
    fun `large families are not flattened to the default`() {
        val cases = mapOf(
            "gpt-5.4" to 400_000,
            "claude-opus-5" to 200_000,
            "qwen3-coder-480b" to 262_144,
            "deepseek-v4-pro" to 163_840,
            "nvidia/nemotron-3-super-120b-a12b" to 131_072,
            "openai/gpt-oss-120b" to 131_072,
            "agnes-2.5-flash" to 524_288
        )
        cases.forEach { (id, expected) ->
            assertThat(ModelCapabilities.contextWindowFor(id)).isEqualTo(expected)
        }
    }

    @Test
    fun `an explicit size in the id wins over the family default`() {
        // A provider serving a cut-down variant of a big model means it.
        assertThat(ModelCapabilities.contextWindowFor("gemini-3-flash-64k"))
            .isEqualTo(64 * 1_024)
    }

    @Test
    fun `longest match wins so specific beats general`() {
        // "qwen3-coder" must not be decided by the shorter "qwen" entry.
        assertThat(ModelCapabilities.contextWindowFor("qwen3-coder-480b"))
            .isGreaterThan(ModelCapabilities.contextWindowFor("qwen-turbo"))
    }

    @Test
    fun `an unknown model gets a modern default, not 8k`() {
        val unknown = ModelCapabilities.contextWindowFor("some-brand-new-model-v9")
        assertThat(unknown).isEqualTo(ModelCapabilities.DEFAULT_CONTEXT)
        assertThat(unknown).isGreaterThan(8_192)
    }

    @Test
    fun `provider prefixes do not defeat matching`() {
        // The same model appears with and without a vendor prefix depending on
        // which gateway is serving it.
        assertThat(ModelCapabilities.contextWindowFor("openai/gpt-oss-120b"))
            .isEqualTo(ModelCapabilities.contextWindowFor("gpt-oss-120b"))
    }
}

class ProviderRateLimiterTest {

    /** Deterministic clock, so these assert behaviour rather than timing. */
    private class FakeClock {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        suspend fun sleep(ms: Long) {
            sleeps += ms
            now += ms
        }
    }

    @Test
    fun `requests under the limit are not delayed`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        // Cerebras allows 5/min; three should pass straight through.
        repeat(3) {
            assertThat(limiter.acquire(AiProvider.CEREBRAS, 100)).isEqualTo(0L)
        }
        assertThat(clock.sleeps).isEmpty()
    }

    @Test
    fun `exceeding requests per minute waits instead of failing`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        repeat(5) { limiter.acquire(AiProvider.CEREBRAS, 100) }

        // The sixth has to wait out the window rather than returning a 429
        // mid-agent-run.
        val waited = limiter.acquire(AiProvider.CEREBRAS, 100)
        assertThat(waited).isGreaterThan(0L)
    }

    @Test
    fun `the window slides rather than resetting on the minute`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        repeat(5) { limiter.acquire(AiProvider.CEREBRAS, 100) }

        // Once the first request ages out, capacity returns -- a fixed window
        // would instead allow a full burst at the boundary, which is exactly
        // what providers measure and reject.
        clock.now += 61_000
        assertThat(limiter.acquire(AiProvider.CEREBRAS, 100)).isEqualTo(0L)
    }

    @Test
    fun `token budget is enforced, not just request count`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        // Groq's 6k TPM binds long before its 28 RPM does.
        limiter.acquire(AiProvider.GROQ, 5_000)
        val waited = limiter.acquire(AiProvider.GROQ, 2_000)
        assertThat(waited).isGreaterThan(0L)
    }

    @Test
    fun `a request larger than the whole budget is let through`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        // It can never fit, so waiting would hang forever. The provider's own
        // error is a better outcome than a frozen app.
        assertThat(limiter.acquire(AiProvider.GROQ, 999_999)).isEqualTo(0L)
    }

    @Test
    fun `providers without a published limit are not paced`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        repeat(50) {
            assertThat(limiter.acquire(AiProvider.CUSTOM, 10_000)).isEqualTo(0L)
        }
    }

    @Test
    fun `budgets are tracked per provider`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        repeat(5) { limiter.acquire(AiProvider.CEREBRAS, 100) }
        // Spending Cerebras' budget must not throttle Groq.
        assertThat(limiter.acquire(AiProvider.GROQ, 100)).isEqualTo(0L)
    }

    @Test
    fun `remaining capacity is reported for the UI`() = runTest {
        val clock = FakeClock()
        val limiter = ProviderRateLimiter(now = { clock.now }, sleep = clock::sleep)
        limiter.acquire(AiProvider.CEREBRAS, 100)
        assertThat(limiter.remainingThisMinute(AiProvider.CEREBRAS)).isEqualTo(4)
    }
}
