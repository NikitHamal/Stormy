package com.oro.app.screenshots

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.ui.components.message.AiMessageContent
import com.oro.app.ui.components.message.ParsedBlocksCache
import com.oro.app.ui.components.message.parseAiMessageContent
import com.oro.app.ui.screens.editor.agent.RunTranscript
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Space
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A very long agent run in the chat: the newest steps show, the rest wait behind one control. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class LongRunShots {

    @get:Rule
    val compose = createComposeRule()

    private fun call(id: String, name: String, args: String) = ToolCallResponse(id, "function", FunctionCall(name, args))

    private fun longRun(): String {
        val t = RunTranscript()
        repeat(120) { i ->
            t.beginModelCall()
            t.appendReasoning("Checking section $i against the design system before editing. ".repeat(60))
            t.appendText("Section $i: tightening spacing and adding the hover state.")
            val read = call("r$i", "read_file", """{"path":"sections/s$i.html"}""")
            t.toolStarted(read)
            t.toolFinished(read, ToolResult(true, "File: sections/s$i.html (64 lines)\n1|<section>"))
            val edit = call("e$i", "edit_file", """{"path":"css/s$i.css"}""")
            t.toolStarted(edit)
            t.toolFinished(edit, ToolResult(true, "Edited css/s$i.css (1 replacement).\nFile: css/s$i.css\n```diff\n-  gap: 10px;\n+  gap: var(--space-3);\n```"))
        }
        t.beginModelCall()
        t.appendText("All 120 sections now use the spacing scale, and every interactive element has hover, focus and pressed states.")
        return t.render()
    }

    @Test
    fun chat_long_run_light() {
        val content = longRun()
        ParsedBlocksCache.put("long-run", content, false, parseAiMessageContent(content))
        compose.shoot("chat_long_run_light") {
            OroTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    AiMessageContent(
                        content = content,
                        timestamp = "11:02 PM",
                        modifier = Modifier.padding(Space.lg),
                        showWorkingIndicator = false,
                        cacheKey = "long-run",
                    )
                }
            }
        }
    }

    @Test
    fun chat_long_run_parsing_dark() {
        // Not cached and too big to parse in composition: the placeholder shows first.
        val content = longRun()
        compose.shoot("chat_long_run_parsing_dark", height = 300.dp_()) {
            OroTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    AiMessageContent(
                        content = content,
                        timestamp = "11:02 PM",
                        modifier = Modifier.padding(Space.lg),
                        showWorkingIndicator = false,
                        cacheKey = "long-run-cold",
                    )
                }
            }
        }
    }

    private fun Int.dp_() = androidx.compose.ui.unit.Dp(this.toFloat())
}
