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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.data.ai.tools.UserQuestions
import com.oro.app.ui.components.message.AiMessageContent
import com.oro.app.ui.components.agent.AskUserCard
import com.oro.app.ui.components.agent.PendingQuestion
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Space
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The one-tap question card a vague request gets, and how the answers read afterwards. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class AskShots {

    @get:Rule
    val compose = createComposeRule()

    private val cafe = listOf(
        UserQuestion(
            "What's the cafe called?", header = "Name",
            options = listOf(QuestionOption("I'll type it"), QuestionOption("Make one up", recommended = true))
        ),
        UserQuestion(
            "What should visitors do first?", header = "Focus",
            options = listOf(
                QuestionOption("See the menu", "Menu, prices and opening hours up front", recommended = true),
                QuestionOption("Order online"), QuestionOption("Book a table")
            )
        ),
        UserQuestion(
            "Which feel fits the place?", header = "Look",
            options = listOf(
                QuestionOption("Warm and cozy", recommended = true), QuestionOption("Clean and minimal"),
                QuestionOption("Bold and playful")
            )
        ),
    )

    private val single = listOf(
        UserQuestion(
            "Should the tracker keep habits on this phone or in an account?", header = "Data",
            options = listOf(
                QuestionOption("This phone only", recommended = true), QuestionOption("Account with login")
            )
        )
    )

    @Composable
    private fun Turn(dark: Boolean, questions: List<UserQuestion>, intro: String) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    AskUserCard(
                        question = PendingQuestion("q", questions),
                        onSubmit = {},
                        onDismiss = {}
                    )
                }
            }
        }
    }

    @Test
    fun ask_three_light() = compose.shoot("ask_three_light", height = 760.dp) {
        Turn(false, cafe, "Happy to build it. Three quick things first so it feels like your cafe:")
    }

    @Test
    fun ask_three_dark() = compose.shoot("ask_three_dark", height = 760.dp) {
        Turn(true, cafe, "Happy to build it. Three quick things first so it feels like your cafe:")
    }

    @Test
    fun ask_one_light() = compose.shoot("ask_one_light", height = 360.dp) {
        Turn(false, single, "One thing before I start:")
    }

    @Test
    fun ask_one_dark() = compose.shoot("ask_one_dark", height = 360.dp) {
        Turn(true, single, "One thing before I start:")
    }

    @Test
    fun ask_other_typing_light() = compose.shoot(
        "ask_other_typing_light", height = 820.dp,
        interact = { onAllNodesWithText("Other")[0].performClick() }
    ) {
        Turn(false, cafe, "Happy to build it. Three quick things first so it feels like your cafe:")
    }

    @Test
    fun ask_answered_light() = compose.shoot("ask_answered_light", height = 300.dp) {
        OroTheme(darkTheme = false) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    AiMessageContent(
                        content = "Happy to build it. Three quick things first so it feels like your cafe:\n\n" +
                            UserQuestions.quote(cafe, listOf("Brew Haven", "See the menu", "Warm and cozy")) +
                            "\n\n" + UserQuestions.quote(single, null),
                        timestamp = "9:41"
                    )
                }
            }
        }
    }
}
