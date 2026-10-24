package com.oro.app.ui.genui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.A2uiMessageParser
import androidx.a2ui.compose.runtime.observeA2uiComponentState
import androidx.a2ui.compose.ui.A2uiCatalog
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.model.catalog.basiccatalog.createBasicCatalogFunctions
import androidx.a2ui.model.processor.A2uiActionInterceptor
import androidx.a2ui.model.processor.processInput
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiEventAction
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.genui.GenUi
import com.oro.app.ui.brand.OrobiLoader
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where a tap in a UI block goes: the chat sends the text as the user's next
 * message. Provided by the chat screen; elsewhere taps do nothing.
 */
val LocalGenUiActions = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Oro's A2UI catalog: the basic catalog drawn in Oro's style, plus the chat components. */
object OroA2uiCatalog {
    @Volatile private var cached: A2uiCatalog? = null

    fun get(context: Context): A2uiCatalog = cached ?: synchronized(this) {
        cached ?: build(context.applicationContext).also { cached = it }
    }

    private fun build(app: Context): A2uiCatalog {
        val basic = A2uiBasicCatalogV1(
            text = OroText, image = OroImage, icon = OroIcon, video = OroVideo, audioPlayer = OroAudio,
            row = OroRow, column = OroColumn, list = OroList, card = OroCard, tabs = OroTabs, modal = OroModal,
            divider = OroDivider, button = OroButton, textField = OroTextField, checkBox = OroCheckBox,
            choicePicker = OroChoicePicker, slider = OroSlider, dateTimeInput = OroDateTimeInput,
            functions = createBasicCatalogFunctions(
                urlOpener = { url ->
                    runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                messageFormatter = { pattern, locale, args ->
                    android.icu.text.MessageFormat(pattern, locale).format(args)
                }
            )
        )
        return A2uiCatalog(
            catalogId = GenUi.CATALOG_ID,
            components = basic.components + OroExtraComponents,
            functions = basic.functions,
            themeSchema = basic.themeSchema
        )
    }
}

/**
 * One ```ui block of a reply, rendered as native UI.
 *
 * While the reply streams, the block is incomplete JSON, so it shows a
 * placeholder rather than flickering through partial layouts. If the model
 * wrote something the renderer rejects, the block says so and offers the
 * source, instead of an empty space.
 */
@Composable
fun GenUiBlock(source: String, streaming: Boolean, modifier: Modifier = Modifier) {
    if (streaming) {
        Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                OrobiLoader(modifier = Modifier.width(28.dp), contentDescription = null)
                Spacer(Modifier.width(Space.sm))
                Text("Building the interface…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val context = LocalContext.current
    val onAction = rememberUpdatedState(LocalGenUiActions.current)
    val surfaceId = remember(source) { "ui" + Integer.toHexString(source.hashCode()) }
    val parsed = remember(source) { GenUi.toA2uiMessages(source, surfaceId) }
    if (parsed is GenUi.Result.Invalid) {
        Fallback(source, parsed.reason, modifier)
        return
    }
    val messages = (parsed as GenUi.Result.Ok).messages
    val errors = remember(source) { mutableStateListOf<String>() }
    val processor = remember(source) {
        A2uiMessageProcessor(
            catalogs = listOf(OroA2uiCatalog.get(context)),
            interceptors = listOf(A2uiActionInterceptor { action ->
                if (action is A2uiEventAction) {
                    val text = GenUi.actionMessage(action.eventName, action.context)
                    withContext(Dispatchers.Main) { onAction.value(text) }
                    null
                } else action
            })
        )
    }
    LaunchedEffect(processor) {
        val parser = A2uiMessageParser()
        messages.forEach { runCatching { processor.processInput(parser, it) }.onFailure { e -> errors += e.message ?: "bad message" } }
        processor.collectMessages()
    }
    LaunchedEffect(processor) {
        processor.outboundEvents.collect { e -> if (e is A2uiClientErrorMessage) errors += e.message }
    }
    val surfaces by processor.activeSurfaces.collectAsState()

    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        var rendered = false
        surfaces.forEach { surface ->
            when (val st = observeA2uiComponentState(surface)) {
                is A2uiComponentState.Success -> {
                    rendered = true
                    A2uiComponent(st.component, Modifier.fillMaxWidth())
                }
                is A2uiComponentState.Error -> Fallback(source, st.exception.message ?: "invalid component", Modifier)
                else -> Box(Modifier.height(24.dp))
            }
        }
        if (!rendered && errors.isNotEmpty()) Fallback(source, errors.first(), Modifier)
    }
}

@Composable
private fun Fallback(source: String, reason: String, modifier: Modifier) {
    var show by remember { mutableStateOf(false) }
    Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text(
                    "This part of the answer could not be shown (${reason.take(120)}).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { show = !show }) {
                    Icon(Icons.Outlined.Code, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text(if (show) "Hide" else "Source")
                }
            }
            if (show) Text(
                source.take(6_000), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.sm)
            )
        }
    }
}

