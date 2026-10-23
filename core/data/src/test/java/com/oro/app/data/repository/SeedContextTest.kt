package com.oro.app.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** A launch follows the catalogue's windows, but never undoes a provider refresh. */
class SeedContextTest {

    @Test
    fun `a row still on an old seed window follows the catalogue`() {
        assertThat(AiModelRepository.shouldCorrectSeedContext("mercury:mercury-3", 131_072, 1_000_000)).isTrue()
        assertThat(AiModelRepository.shouldCorrectSeedContext("gpt-5.4", 400_000, 1_050_000)).isTrue()
        // Never set: the catalogue's figure.
        assertThat(AiModelRepository.shouldCorrectSeedContext("kilo-auto/free", null, 256_000)).isTrue()
    }

    @Test
    fun `a window a provider reported stays`() {
        // A refresh found a smaller live window: the next launch must not restore the seed's.
        assertThat(AiModelRepository.shouldCorrectSeedContext("gpt-5.4", 272_000, 1_050_000)).isFalse()
        assertThat(AiModelRepository.shouldCorrectSeedContext("kilo-auto/free", 200_000, 256_000)).isFalse()
        // Already right.
        assertThat(AiModelRepository.shouldCorrectSeedContext("mercury:mercury-3", 1_000_000, 1_000_000)).isFalse()
    }
}
