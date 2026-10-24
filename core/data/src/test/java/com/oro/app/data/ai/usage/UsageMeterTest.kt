package com.oro.app.data.ai.usage

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import org.junit.Test

class UsageMeterTest {
    @Test
    fun `priced models cost money with cache discount, keyless free models cost zero`() {
        val paid = AiModel("m", "M", AiProvider.OPENROUTER, inputPricePerToken = 1e-6, outputPricePerToken = 2e-6)
        val u = SessionUsage().plus(paid, prompt = 1_000_000, completion = 100_000, cached = 500_000)
        assertThat(u.costUsd!!).isWithin(1e-9).of(0.5 + 0.05 + 0.2)
        assertThat(u.cacheHitPercent).isEqualTo(50)
        val free = SessionUsage().plus(AiModel("k", "K", AiProvider.K2_HORIZON), 10, 5, 0)
        assertThat(SessionUsage.formatCost(free.costUsd)).isEqualTo("Free")
        val unknown = SessionUsage().plus(AiModel("g", "G", AiProvider.GROQ), 10, 5, 0)
        assertThat(unknown.costUsd).isNull()
    }
}
