package com.oro.app.device.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IslandGlowModelTest {

    @Test
    fun `the glow is lit while Orobi works, warm while it waits on the user, out otherwise`() {
        assertThat(IslandGlowModel.mood(IslandPhase.Working)).isEqualTo(GlowMood.Working)
        assertThat(IslandGlowModel.mood(IslandPhase.Question)).isEqualTo(GlowMood.Attention)
        assertThat(IslandGlowModel.mood(IslandPhase.Approval)).isEqualTo(GlowMood.Attention)
        for (p in listOf(IslandPhase.Paused, IslandPhase.Done, IslandPhase.Failed, IslandPhase.Idle)) {
            assertThat(IslandGlowModel.mood(p)).isEqualTo(GlowMood.Off)
        }
    }

    @Test
    fun `the halo's strokes reach evenly out to the full reach`() {
        val r = IslandGlowModel.haloReaches(4)
        assertThat(r.toList()).containsExactly(0.25f, 0.5f, 0.75f, 1f).inOrder()
    }

    @Test
    fun `stacked faint strokes give a soft edge, never a solid one`() {
        val edge = IslandGlowModel.edgeAlpha(layers = 7, alpha = 0.075f)
        assertThat(edge).isWithin(0.01f).of(0.42f)
        assertThat(IslandGlowModel.edgeAlpha(layers = 0, alpha = 0.5f)).isEqualTo(0f)
    }
}
