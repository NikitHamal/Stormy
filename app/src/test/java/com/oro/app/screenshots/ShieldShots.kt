package com.oro.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.oro.app.guard.GuardAgent
import com.oro.app.ui.screens.shield.GuardToolCard
import com.oro.app.ui.screens.shield.ShieldErrorCard
import com.oro.app.ui.screens.shield.ShieldGuardMessage
import com.oro.app.ui.screens.shield.ShieldQuickChips
import com.oro.app.ui.screens.shield.ShieldUserBubble
import com.oro.app.ui.screens.shield.ShieldWelcome
import com.oro.app.ui.screens.shield.ShieldWorking
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Space
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Shield's chat surface: the welcome with starters and quick orders, and a
 * live-looking transcript with a user bubble, guard prose, an expandable tool
 * card, a failed tool and the working line. Previews of the real composables,
 * not golden images (the suite only runs with `-Poro.screenshots=true`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class ShieldShots {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    @Composable
    private fun Frame(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize(), content = content)
            }
        }
    }

    @Test
    fun shield_welcome_light() = compose.shoot("shield_welcome_light") {
        Frame(dark = false) {
            ShieldWelcome(
                seatNames = listOf("Mercury 3", "K2 Horizon", "Hy3"),
                findingCount = 3,
                onSuggestion = {},
                onOpenCouncil = {},
                onOpenFindings = {},
                modifier = Modifier.weight(1f),
            )
            ShieldQuickChips(onPick = {})
            Spacer(Modifier.height(Space.md))
        }
    }

    @Test
    fun shield_transcript_light() = compose.shoot("shield_transcript_light") {
        Frame(dark = false) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                ShieldUserBubble(
                    text = "My Instagram was hijacked last night. I got a login alert from a device in another country and the recovery email was changed.",
                    at = now - 90_000,
                )
                ShieldGuardMessage(
                    message = GuardAgent.Message(
                        id = 1,
                        role = GuardAgent.MsgRole.GUARD,
                        text = "**Contain first.** Do not log in through the alert's link. Open Instagram from this app's browser, " +
                            "kill every session, then change the password from a clean device.\n\n" +
                            "I am building the evidence record now; the alert's headers decide whether this was a phish or a real session.",
                        at = now - 80_000,
                    ),
                    isStreaming = false,
                )
                GuardToolCard(
                    GuardAgent.Message(
                        id = 2,
                        role = GuardAgent.MsgRole.TOOL,
                        text = "Building the evidence record",
                        tool = "trace_incident",
                        detail = "Evidence record (6 indicators, 2 dated events).\n\n" +
                            "Indicators:\n- domain: login-alert-instagram.xyz\n- ip: 185.199.108.153\n" +
                            "- email: security@login-alert-instagram.xyz\n\n" +
                            "Timeline:\n- 2026-10-29 23:14  login alert received\n" +
                            "- 2026-10-30 01:02  recovery email changed",
                        status = GuardAgent.MsgStatus.DONE,
                        callId = "call_1",
                        at = now - 60_000,
                    ),
                )
                GuardToolCard(
                    GuardAgent.Message(
                        id = 3,
                        role = GuardAgent.MsgRole.TOOL,
                        text = "Auditing your site's posture",
                        tool = "audit_web_posture",
                        detail = "The site did not answer; check the URL and try again.",
                        status = GuardAgent.MsgStatus.FAILED,
                        callId = "call_2",
                        at = now - 40_000,
                    ),
                )
                ShieldWorking("Convening the council")
            }
            ShieldQuickChips(onPick = {})
            Spacer(Modifier.height(Space.md))
        }
    }

    @Test
    fun shield_transcript_dark() = compose.shoot("shield_transcript_dark") {
        Frame(dark = true) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                ShieldUserBubble(
                    text = "Is this link safe? https://paypa1-secure-login.xyz/verify",
                    at = now - 30_000,
                )
                ShieldGuardMessage(
                    message = GuardAgent.Message(
                        id = 4,
                        role = GuardAgent.MsgRole.GUARD,
                        text = "**Do not touch it.** The host imitates PayPal on a different domain, " +
                            "the certificate is three days old, and the path asks for a login.\n\n" +
                            "If you already entered anything, treat that password as burned and rotate it now.",
                        at = now - 20_000,
                    ),
                    isStreaming = false,
                )
                ShieldErrorCard(
                    result = GuardAgent.Result(false, "", "Stopped."),
                    onNewChat = {},
                )
            }
            ShieldQuickChips(onPick = {})
            Spacer(Modifier.height(Space.md))
        }
    }
}
