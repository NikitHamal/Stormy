package com.oro.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.data.local.entity.ProjectTemplate
import com.oro.app.R
import com.oro.app.ui.components.inspector.InspectorElementData
import com.oro.app.ui.components.inspector.InspectorRect
import com.oro.app.ui.components.inspector.SourceTextEdit
import com.oro.app.ui.components.inspector.VisualElementInspector
import com.oro.app.ui.screens.home.TemplateCard
import com.oro.app.ui.screens.preview.PreviewDevice
import com.oro.app.ui.screens.preview.PreviewDeviceBar
import com.oro.app.ui.screens.preview.PreviewLoadError
import com.oro.app.ui.theme.OroTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The preview's device bar and edit panel, and the new-project template
 * picker with its studio starters. Real composables, made-up page data.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = ShotsApplication::class)
class PreviewEditShots {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun stubApp() = OroAppStub.install()

    @Composable
    private fun Themed(dark: Boolean, content: @Composable () -> Unit) {
        OroTheme(darkTheme = dark) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
        }
    }

    // ------------------------------------------------------ edit mode

    private val heading = InspectorElementData(
        tagName = "H1",
        id = "",
        className = "hero-title display",
        innerHTML = "Invoices that <em>pay themselves.</em>",
        outerHTML = "<h1 class=\"hero-title display\">Invoices that <em>pay themselves.</em></h1>",
        computedStyles = mapOf(
            "color" to "rgb(15, 23, 42)", "background-color" to "rgba(0, 0, 0, 0)", "font-size" to "56px",
            "font-weight" to "700", "line-height" to "58px", "letter-spacing" to "-1.5px", "text-align" to "left",
            "margin-top" to "0px", "margin-bottom" to "16px", "padding-top" to "0px", "display" to "block",
            "border-top-left-radius" to "0px", "opacity" to "1"
        ),
        attributes = mapOf("class" to "hero-title display"),
        boundingRect = InspectorRect(24f, 180f, 342f, 116f),
        selector = "section.hero > h1",
        selectorIsUnique = true,
        ownText = "Invoices that",
        ownTextNodes = 1,
        textTarget = SourceTextEdit.Target("Invoices that", "main>section>div>div>h1", 0, 1, 1),
        pagePath = "/",
        hasParent = true,
        hasChild = true
    )

    /** A stand-in for the page under the chrome: the landing starter's hero. */
    @Composable
    private fun StandInPage(modifier: Modifier = Modifier) {
        Column(modifier.background(Color(0xFFFFFFFF)).padding(24.dp)) {
            Text("FOR FREELANCERS AND SMALL STUDIOS", color = Color(0xFF0F7A4F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.border(2.dp, Color(0xFF6200EE)).padding(4.dp)) {
                Column {
                    Text("Invoices that", color = Color(0xFF0F172A), fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
                    Text("pay themselves.", color = Color(0xFF0F7A4F), fontSize = 40.sp, lineHeight = 42.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Send a polished invoice in thirty seconds. Paperline follows up on late payments.",
                color = Color(0xFF475569), fontSize = 16.sp
            )
        }
    }

    @Composable
    private fun EditMode(dark: Boolean, device: PreviewDevice = PreviewDevice.PHONE) = Themed(dark) {
        Column(Modifier.fillMaxSize()) {
            PreviewDeviceBar(
                device = device,
                landscape = false,
                layoutWidth = device.width(false) ?: 411,
                errorCount = 2,
                onDeviceChange = {},
                onRotate = {},
                onFit = {},
                onShowErrors = {},
                darkPage = dark
            )
            StandInPage(Modifier.weight(1f).fillMaxWidth())
            VisualElementInspector(
                element = heading,
                onClose = {},
                onStyleChange = {},
                onTextChange = {},
                onAiEditRequest = {},
                hasValidModel = true,
                onSelectParent = {},
                onSelectChild = {},
                onUndo = {},
                canUndo = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Test
    fun preview_edit_light() = compose.shoot("preview_edit_light") { EditMode(dark = false) }

    @Test
    fun preview_edit_dark() = compose.shoot("preview_edit_dark") { EditMode(dark = true) }

    @Test
    fun preview_edit_text_light() = compose.shoot(
        "preview_edit_text_light",
        interact = { onAllNodesWithText("Text")[0].performClick() }
    ) { EditMode(dark = false, device = PreviewDevice.TABLET) }

    @Test
    fun preview_load_error_light() = compose.shoot("preview_load_error_light", height = 600.dp) {
        Themed(false) {
            Column(Modifier.fillMaxSize()) {
                PreviewDeviceBar(
                    device = PreviewDevice.DESKTOP, landscape = false, layoutWidth = 1280, errorCount = 0,
                    onDeviceChange = {}, onRotate = {}, onFit = {}, onShowErrors = {}
                )
                PreviewLoadError(
                    message = "The preview server isn't answering. Start it again from the menu, or retry.",
                    onRetry = {}
                )
            }
        }
    }

    // ------------------------------------------------- template picker

    private val studio = listOf(
        ProjectTemplate.LANDING_PAGE to R.drawable.template_landing,
        ProjectTemplate.PORTFOLIO to R.drawable.template_portfolio,
        ProjectTemplate.BLOG to R.drawable.template_blog,
        ProjectTemplate.DASHBOARD to R.drawable.template_dashboard,
        ProjectTemplate.STORE to R.drawable.template_store,
        ProjectTemplate.DOCS to R.drawable.template_docs,
        ProjectTemplate.ARCADE to R.drawable.template_arcade,
    )

    @Composable
    private fun Picker(dark: Boolean) = Themed(dark) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Start from", style = MaterialTheme.typography.titleMedium)
            studio.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { k, (template, thumbnail) ->
                        TemplateCard(
                            template = template,
                            icon = Icons.Outlined.Code,
                            isSelected = template == ProjectTemplate.DASHBOARD && k == 1,
                            onClick = {},
                            thumbnail = thumbnail
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TemplateCard(template = ProjectTemplate.HTML_BASIC, icon = Icons.Outlined.Code, isSelected = false, onClick = {})
                TemplateCard(template = ProjectTemplate.REACT, icon = Icons.Outlined.Code, isSelected = false, onClick = {})
                Spacer(Modifier.width(4.dp))
            }
        }
    }

    @Test
    fun template_picker_light() = compose.shoot("template_picker_light", height = 1_050.dp) { Picker(dark = false) }

    @Test
    fun template_picker_dark() = compose.shoot("template_picker_dark", height = 1_050.dp) { Picker(dark = true) }
}
