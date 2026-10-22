package com.oro.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.oro.app.device.DeviceAgent
import com.oro.app.device.overlay.AgentIsland
import com.oro.app.ui.theme.OroTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The floating island over another app, in each of its states. The island is
 * always the dark theme (it floats over arbitrary apps), exactly as
 * AgentOverlay hosts it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class IslandShots {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun IslandOverApp(state: DeviceAgent.State) {
        Box(Modifier.fillMaxSize()) {
            FakeVideoApp()
            Box(Modifier.align(Alignment.TopCenter).padding(top = 30.dp)) {
                OroTheme(darkTheme = true) {
                    AgentIsland(
                        state = state,
                        visible = true,
                        screenVisible = false,
                        lite = false,
                        cardWidth = 360.dp,
                        actions = NoIslandActions,
                    )
                }
            }
        }
    }

    /** Taps the pill: the island's only clickable node while collapsed. */
    private val expand: androidx.compose.ui.test.junit4.ComposeContentTestRule.() -> Unit = {
        onAllNodes(hasClickAction()).onFirst().performClick()
    }

    @Test
    fun island_collapsed_working() = compose.shoot("island_collapsed_working", height = 300.dp) {
        IslandOverApp(Runs.working())
    }

    @Test
    fun island_collapsed_paused() = compose.shoot("island_collapsed_paused", height = 300.dp) {
        IslandOverApp(Runs.paused())
    }

    @Test
    fun island_expanded_working() = compose.shoot("island_expanded_working", height = 560.dp, interact = expand) {
        IslandOverApp(Runs.working())
    }

    @Test
    fun island_approval() = compose.shoot("island_approval", height = 560.dp) {
        IslandOverApp(Runs.approval())
    }

    @Test
    fun island_question() = compose.shoot("island_question", height = 560.dp) {
        IslandOverApp(Runs.question())
    }

    @Test
    fun island_done_collapsed() = compose.shoot("island_done_collapsed", height = 300.dp) {
        IslandOverApp(Runs.finished())
    }

    @Test
    fun island_done_expanded() = compose.shoot("island_done_expanded", height = 560.dp, interact = expand) {
        IslandOverApp(Runs.finished())
    }

    @Test
    fun island_failed_collapsed() = compose.shoot("island_failed_collapsed", height = 300.dp) {
        IslandOverApp(Runs.failed())
    }

    @Test
    fun island_failed_expanded() = compose.shoot("island_failed_expanded", height = 560.dp, interact = expand) {
        IslandOverApp(Runs.failed())
    }
}
