package com.oro.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiModels
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.SeedModels
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.local.entity.MessageStatus
import com.oro.app.data.local.entity.ProjectTemplate
import com.oro.app.domain.model.ChatMessage
import com.oro.app.domain.model.Project
import com.oro.app.ui.components.chat.ChatSettingsSheet
import com.oro.app.ui.components.chat.ModelSelectorSheet
import com.oro.app.ui.components.expressive.ExpressiveSearchTopBar
import com.oro.app.ui.components.expressive.FlowerIconButton
import com.oro.app.ui.screens.editor.agent.RunTranscript
import com.oro.app.ui.screens.editor.chat.ChatTab
import com.oro.app.ui.screens.home.DeviceAgentButton
import com.oro.app.ui.screens.home.EmptyState
import com.oro.app.ui.screens.home.HomeTitle
import com.oro.app.ui.screens.home.ProjectsList
import com.oro.app.ui.theme.OroTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screens people spend their time on: the editor chat with a real-looking
 * agent run, its settings sheet with each kind of reasoning control, the model
 * picker, and Home. Rendered from the real composables with made-up data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class MainScreenShots {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun stubApp() = OroAppStub.install()

    // ------------------------------------------------------------ data

    private val mercury = SeedModels.default
    private val gpt55 = AiModel(id = "openai/gpt-5.5", name = "GPT-5.5", provider = AiProvider.OPENROUTER)
    private val k2 = AiModels.getAllModels().first { it.provider == AiProvider.K2_HORIZON }

    private fun call(id: String, name: String, args: String) = ToolCallResponse(id, "function", FunctionCall(name, args))

    private fun runTranscript(): String {
        val t = RunTranscript()
        t.beginModelCall()
        t.appendReasoning(
            "A coffee shop landing page: hero with the name and a photo, the menu as cards, opening hours, " +
                "and a contact form that really saves. Start from the design system and the UI kit."
        )
        t.appendText("I'll set up the design system first, then build the page section by section.")
        val skill = call("c1", "use_skill", """{"name":"design-system"}""")
        t.toolStarted(skill)
        t.toolFinished(skill, ToolResult(true, "Skill design-system (97 lines)\nTokens, type scale, spacing, motion."))
        val kit = call("c2", "add_ui_kit", "{}")
        t.toolStarted(kit)
        t.toolFinished(kit, ToolResult(true, "Installed kit/oro.css and kit/oro.js"))
        t.beginModelCall()
        val page = "<!doctype html>\n<html lang=\"en\">\n" + (1..140).joinToString("\n") { "  <!-- section line $it -->" } + "\n</html>"
        val write = call("c3", "write_file", """{"path":"index.html","content":${jsonString(page)}}""")
        t.toolStarted(write)
        t.toolFinished(write, ToolResult(true, "Created index.html (142 lines).\nFile: index.html"))
        val css = "/* Brew & Bloom */\n" + (1..88).joinToString("\n") { ".rule-$it { gap: var(--space-3); }" }
        val writeCss = call("c4", "write_file", """{"path":"css/style.css","content":${jsonString(css)}}""")
        t.toolStarted(writeCss)
        t.toolFinished(writeCss, ToolResult(true, "Created css/style.css (89 lines).\nFile: css/style.css"))
        val edit = call("c5", "edit_file", """{"path":"js/app.js","old_string":"alert(","new_string":"toast("}""")
        t.toolStarted(edit)
        t.toolFinished(
            edit,
            ToolResult(
                true,
                "Edited js/app.js (1 replacement).\nFile: js/app.js\n```diff\n-  alert('Thanks!');\n+  Oro.toast('Thanks — we will reply within a day');\n+  form.reset();\n```"
            )
        )
        val check = call("c6", "check_syntax", "{}")
        t.toolStarted(check)
        t.toolFinished(check, ToolResult(true, "No issues found in 5 files."))
        t.beginModelCall()
        t.appendText(
            "**Brew & Bloom** is live in the preview:\n\n" +
                "- Hero with the shop photo and a *Visit us* button\n" +
                "- Menu as cards with prices, opening hours, and a map link\n" +
                "- The contact form saves to `/api/messages` and shows a toast\n\n" +
                "Next you could add online ordering or a gallery."
        )
        t.closeThinking()
        return t.render()
    }

    private fun jsonString(s: String) = kotlinx.serialization.json.JsonPrimitive(s).toString()

    private fun conversation(): List<ChatMessage> {
        val now = System.currentTimeMillis()
        val user = ChatMessage.createUserMessage(
            "p", "Build a landing page for my coffee shop Brew & Bloom: menu, opening hours and a contact form."
        ).copy(id = "u1", timestamp = now - 95_000)
        val ai = ChatMessage.createAssistantMessage("p", runTranscript(), status = MessageStatus.RECEIVED)
            .copy(id = "a1", timestamp = now - 20_000)
        return listOf(user, ai)
    }

    // ------------------------------------------------------------ frames

    /** Mid-run: the reply so far is streaming and Orobi is writing a file. */
    private fun working(): List<ChatMessage> {
        val now = System.currentTimeMillis()
        val t = RunTranscript()
        t.beginModelCall()
        t.appendText("Setting up the design system, then the page.")
        val skill = call("w1", "use_skill", """{"name":"design-system"}""")
        t.toolStarted(skill)
        t.toolFinished(skill, ToolResult(true, "Skill design-system (97 lines)"))
        val write = call("w2", "write_file", """{"path":"index.html","content":"<!doctype html>"}""")
        t.toolStarted(write)
        val user = ChatMessage.createUserMessage("p", "Build a landing page for my coffee shop Brew & Bloom.")
            .copy(id = "u2", timestamp = now - 30_000)
        val ai = ChatMessage.createAssistantMessage("p", t.render(), status = MessageStatus.STREAMING)
            .copy(id = "a2", timestamp = now - 5_000)
        return listOf(user, ai)
    }

    @Composable
    private fun Chat(
        dark: Boolean,
        messages: List<ChatMessage>,
        model: AiModel = mercury,
        loading: Boolean = false,
        workingLabel: String? = null,
    ) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                ChatTab(
                    messages = messages,
                    projectName = "Brew & Bloom",
                    inputText = "",
                    isLoading = loading,
                    workingLabel = workingLabel,
                    onStopGeneration = {},
                    agentMode = true,
                    currentModel = model,
                    availableModels = AiModels.getAllModels(),
                    onInputChange = {},
                    onSendMessage = {},
                    onToggleAgentMode = {},
                )
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun sheet(name: String, dark: Boolean, model: AiModel, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Chat(dark = dark, messages = conversation(), model = model)
            OroTheme(darkTheme = dark) { content() }
        }
        compose.mainClock.advanceTimeBy(2_000)
        captureScreenRoboImage("$SHOTS_DIR/$name.png")
    }

    private fun settings(name: String, dark: Boolean, model: AiModel) = sheet(name, dark, model) {
        ChatSettingsSheet(
            agentMode = true,
            onAgentModeChange = {},
            reasoningLevels = emptyMap(),
            onReasoningLevelChange = { _, _ -> },
            currentModel = model,
            onDismiss = {},
        )
    }

    private fun projects(): List<Project> {
        val now = System.currentTimeMillis()
        fun p(id: String, name: String, description: String, template: ProjectTemplate, ago: Long, pinned: Boolean = false) =
            Project(id, name, description, template, now - ago - 86_400_000, now - ago, now - ago, null, "/tmp/$id", isPinned = pinned)
        return listOf(
            p("1", "Brew & Bloom", "Landing page for the coffee shop", ProjectTemplate.LANDING_PAGE, 120_000, pinned = true),
            p("2", "Portfolio 2026", "Case studies, motion and a contact form", ProjectTemplate.TAILWIND, 3_600_000),
            p("3", "Neon Snake", "Canvas game with touch controls", ProjectTemplate.BLANK, 86_400_000 * 2),
            p("4", "Ledger", "Expense tracker with charts and a REST backend", ProjectTemplate.HTML_BASIC, 86_400_000 * 9),
        )
    }

    @Composable
    private fun Home(dark: Boolean, empty: Boolean = false) {
        OroTheme(darkTheme = dark) {
            Scaffold(
                topBar = {
                    Column {
                        ExpressiveSearchTopBar(
                            searchActive = false,
                            onSearchActiveChange = {},
                            query = "",
                            onQueryChange = {},
                            placeholder = "Search projects",
                            title = { HomeTitle() },
                            actions = {
                                DeviceAgentButton(onClick = {})
                                FlowerIconButton(icon = Icons.Outlined.Search, contentDescription = "Search projects", onClick = {})
                                FlowerIconButton(icon = Icons.Outlined.Settings, contentDescription = "Settings", onClick = {}, alternate = true)
                            },
                        )
                    }
                },
            ) { padding ->
                if (empty) {
                    EmptyState(onCreateClick = {}, modifier = Modifier.fillMaxSize().padding(padding))
                } else {
                    ProjectsList(
                        projects = projects(),
                        sessionCounts = mapOf("1" to 3, "2" to 1, "4" to 5),
                        onProjectClick = {},
                        onDeleteClick = {},
                        onEditClick = {},
                        onPinClick = {},
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------ chat

    @Test
    fun chat_run_light() = compose.shoot("chat_run_light") { Chat(dark = false, messages = conversation()) }

    @Test
    fun chat_run_dark() = compose.shoot("chat_run_dark") { Chat(dark = true, messages = conversation()) }

    @Test
    fun chat_welcome_light() = compose.shoot("chat_welcome_light") { Chat(dark = false, messages = emptyList()) }

    @Test
    fun chat_working_dark() = compose.shoot("chat_working_dark") {
        Chat(dark = true, messages = working(), loading = true, workingLabel = "Writing index.html")
    }

    // -------------------------------------------------------- settings

    @Test
    fun chat_settings_mercury_light() = settings("chat_settings_mercury_light", dark = false, model = mercury)

    @Test
    fun chat_settings_gpt55_dark() = settings("chat_settings_gpt55_dark", dark = true, model = gpt55)

    @Test
    fun chat_settings_no_control_light() = settings("chat_settings_no_control_light", dark = false, model = k2)

    // ---------------------------------------------------------- models

    @Test
    fun model_selector_light() = sheet("model_selector_light", dark = false, model = mercury) {
        ModelSelectorSheet(
            models = AiModels.getAllModels(),
            selectedModel = mercury,
            onModelSelected = {},
            onDismiss = {},
            onReasoningLevelChange = { _, _ -> },
        )
    }

    @Test
    fun model_selector_dark() = sheet("model_selector_dark", dark = true, model = gpt55) {
        ModelSelectorSheet(
            models = AiModels.getAllModels() + gpt55,
            selectedModel = gpt55,
            onModelSelected = {},
            onDismiss = {},
            onReasoningLevelChange = { _, _ -> },
        )
    }

    // ------------------------------------------------------------ home

    @Test
    fun home_projects_light() = compose.shoot("home_projects_light") { Home(dark = false) }

    @Test
    fun home_projects_dark() = compose.shoot("home_projects_dark") { Home(dark = true) }

    @Test
    fun home_empty_light() = compose.shoot("home_empty_light") { Home(dark = false, empty = true) }
}
