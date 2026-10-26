package com.oro.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.oro.app.ui.brand.OrobiWorking
import com.oro.app.ui.components.agent.ToolCallEntry
import com.oro.app.ui.components.agent.ToolCallGroup
import com.oro.app.ui.components.agent.ToolCallStatus
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Space
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** How a use_browser call reads in the coding chat, while it runs and after. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class ChatShots {

    @get:Rule
    val compose = createComposeRule()

    private val task = "Open the live preview and sign up with a test email; report anything broken"

    private val before = listOf(
        ToolCallEntry("read_file", "index.html", ToolCallStatus.Success, output = "File: index.html (182 lines)"),
        ToolCallEntry("edit_file", "js/signup.js", ToolCallStatus.Success, additions = 12, deletions = 3),
    )

    @Composable
    private fun ChatTurn(dark: Boolean, running: Boolean) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(
                        "The form validates now. I'll try it in a real browser the way a visitor would.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val phone = ToolCallEntry(
                        "use_browser", task,
                        if (running) ToolCallStatus.Running else ToolCallStatus.Success,
                        output = if (running) null else "Browser task done in 48 s, 9 actions (ended on localhost:8080/signup). " +
                            "Signed up as test@oro.dev; the success toast appeared. The password hint overlaps the field at 360 px.",
                    )
                    ToolCallGroup(entries = before + phone, initiallyExpanded = true)
                    if (running) {
                        OrobiWorking(label = "In the browser: Click “Create account”", maxLines = 1)
                    } else {
                        Text(
                            "Signup works end to end. One layout bug: the password hint overlaps the field on " +
                                "small phones. Fixing it next.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun chat_use_browser_running_light() = compose.shoot("chat_use_browser_running_light", height = 420.dp) {
        ChatTurn(dark = false, running = true)
    }

    @Test
    fun chat_use_browser_done_dark() = compose.shoot("chat_use_browser_done_dark", height = 420.dp) {
        ChatTurn(dark = true, running = false)
    }
}
