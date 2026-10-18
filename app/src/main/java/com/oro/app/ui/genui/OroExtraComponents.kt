package com.oro.app.ui.genui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/*
 * Components beyond the A2UI basic catalog, for answers in a coding chat:
 * the "intelligent UI" set (tables, charts, stats, callouts, steps, colour
 * palettes, code, follow-up suggestions). Each declares its properties so the
 * engine validates what the model sent before anything is drawn.
 */

private val Weight get() = A2uiBasicCatalogV1.WeightProperty

@Composable
private fun toneColor(tone: String?): Color {
    val s = MaterialTheme.colorScheme
    return when (tone) {
        "success" -> Color(0xFF2E9E62)
        "warning" -> Color(0xFFD08A1F)
        "danger" -> s.error
        "info" -> s.primary
        else -> s.onSurfaceVariant
    }
}

private val TONES = listOf("neutral", "info", "success", "warning", "danger")

/** Chart and swatch palette, readable in light and dark. */
private val PALETTE = listOf(
    Color(0xFF6E8CF7), Color(0xFFF0965A), Color(0xFF3DBE7A), Color(0xFFB285F2),
    Color(0xFFE5737B), Color(0xFF4FC1B6), Color(0xFFE2B93B), Color(0xFF8BC34A)
)

internal object OroBadge : A2uiComponent {
    override val name = "Badge"
    override val description = "A small coloured label, e.g. a status or a tag."
    private val TextP = A2uiProperty.dynamicString("text", required = true)
    private val Tone = A2uiProperty.stringEnum("tone", TONES)
    override val properties = listOf(TextP, Tone, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(TextP) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val c = toneColor(properties[Tone])
        Surface(shape = OroCorners.pill, color = c.copy(alpha = 0.14f), modifier = modifier) {
            Text(
                properties.bind(TextP).orEmpty(), style = MaterialTheme.typography.labelMedium, color = c,
                modifier = Modifier.padding(horizontal = Space.sm + Space.xxs, vertical = Space.xxs)
            )
        }
    }
}

internal object OroCallout : A2uiComponent {
    override val name = "Callout"
    override val description = "A highlighted note: a tip, a success, a warning or a problem."
    private val Title = A2uiProperty.dynamicString("title")
    private val TextP = A2uiProperty.dynamicString("text", required = true)
    private val Tone = A2uiProperty.stringEnum("tone", TONES)
    override val properties = listOf(Title, TextP, Tone, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(TextP) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val tone = properties[Tone] ?: "info"
        val c = toneColor(tone)
        val icon = when (tone) {
            "success" -> Icons.Outlined.CheckCircle
            "warning" -> Icons.Outlined.WarningAmber
            "danger" -> Icons.Outlined.ErrorOutline
            else -> Icons.Outlined.Info
        }
        Surface(shape = OroCorners.large, color = c.copy(alpha = 0.10f), modifier = modifier.fillMaxWidth()) {
            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Top) {
                Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
                Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    properties.bind(Title)?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
                    Text(properties.bind(TextP).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

internal object OroStat : A2uiComponent {
    override val name = "Stat"
    override val description = "A key number with a label and an optional change, e.g. 'Load time 1.2 s, -30%'."
    private val Label = A2uiProperty.dynamicString("label", required = true)
    private val Value = A2uiProperty.dynamicValue("value", required = true)
    private val Delta = A2uiProperty.dynamicString("delta")
    private val Tone = A2uiProperty.stringEnum("tone", TONES)
    override val properties = listOf(Label, Value, Delta, Tone, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Label) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val value = properties.bind(Value)?.let { if (it is Number) formatNumber(it.toFloat()) else it.toString() } ?: "–"
        Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
            Column(Modifier.padding(Space.md).widthIn(min = 96.dp)) {
                Text(properties.bind(Label).orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                properties.bind(Delta)?.let { d ->
                    val tone = properties[Tone] ?: if (d.trim().startsWith("-")) "danger" else if (d.trim().startsWith("+")) "success" else "neutral"
                    Text(d, style = MaterialTheme.typography.labelMedium, color = toneColor(tone))
                }
            }
        }
    }
}

internal object OroTable : A2uiComponent {
    override val name = "Table"
    override val description = "Rows and columns of data. rows is a list of lists (or of objects keyed by column)."
    private val Columns = A2uiProperty.dynamicStringList("columns", required = true)
    private val Rows = A2uiProperty.dynamicValue("rows", required = true)
    override val properties = listOf(Columns, Rows, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Columns) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val columns = properties.bind(Columns).orEmpty()
        val raw = properties.bind(Rows) as? List<*> ?: emptyList<Any?>()
        val rows = remember(raw, columns) {
            raw.take(200).map { r ->
                when (r) {
                    is List<*> -> r.map { cell(it) }
                    is Map<*, *> -> columns.map { c -> cell(r[c]) }
                    else -> listOf(cell(r))
                }
            }
        }
        val widths = remember(rows, columns) {
            columns.indices.map { i ->
                val longest = maxOf(columns[i].length, rows.maxOfOrNull { it.getOrNull(i)?.length ?: 0 } ?: 0)
                (longest * 7 + 24).coerceIn(64, 220).dp
            }
        }
        val scheme = MaterialTheme.colorScheme
        Surface(shape = OroCorners.large, color = scheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(scheme.surfaceContainerHigh)) {
                    columns.forEachIndexed { i, c ->
                        Text(
                            c, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(widths[i]).padding(horizontal = Space.md, vertical = Space.sm)
                        )
                    }
                }
                rows.forEachIndexed { ri, r ->
                    Row(Modifier.background(if (ri % 2 == 1) scheme.surfaceContainer.copy(alpha = 0.5f) else Color.Transparent)) {
                        columns.indices.forEach { i ->
                            Text(
                                r.getOrNull(i).orEmpty(), style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(widths[i]).padding(horizontal = Space.md, vertical = Space.sm),
                                maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    private fun cell(v: Any?): String = when (v) {
        null -> ""
        is Number -> formatNumber(v.toFloat())
        else -> v.toString()
    }
}

internal object OroChart : A2uiComponent {
    override val name = "Chart"
    override val description = "A bar, line or pie chart of one series: labels and numeric values."
    private val Kind = A2uiProperty.stringEnum("kind", listOf("bar", "line", "pie"))
    private val Title = A2uiProperty.dynamicString("title")
    private val Labels = A2uiProperty.dynamicStringList("labels", required = true)
    private val Values = A2uiProperty.dynamicValue("values", required = true)
    override val properties = listOf(Kind, Title, Labels, Values, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Labels) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val labels = properties.bind(Labels).orEmpty()
        val values = (properties.bind(Values) as? List<*>).orEmpty().map { (it as? Number)?.toFloat() ?: it.toString().toFloatOrNull() ?: 0f }
        val n = minOf(labels.size, values.size)
        val kind = properties[Kind] ?: "bar"
        val scheme = MaterialTheme.colorScheme
        val grid = scheme.outlineVariant
        val accent = scheme.primary
        Surface(shape = OroCorners.large, color = scheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                properties.bind(Title)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                if (n == 0) {
                    Text("No data", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    return@Column
                }
                val vs = values.take(n)
                val maxV = (vs.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
                when (kind) {
                    "pie" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        val total = vs.sum().coerceAtLeast(0.0001f)
                        Canvas(Modifier.size(132.dp)) {
                            var start = -90f
                            vs.forEachIndexed { i, v ->
                                val sweep = 360f * v / total
                                drawArc(PALETTE[i % PALETTE.size], start, sweep, useCenter = false, style = Stroke(width = size.minDimension * 0.22f))
                                start += sweep
                            }
                        }
                        Spacer(Modifier.width(Space.md))
                        Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            labels.take(n).forEachIndexed { i, l ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(PALETTE[i % PALETTE.size]))
                                    Spacer(Modifier.width(Space.xs))
                                    Text("$l  ${Math.round(100 * vs[i] / total)}%", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                    else -> {
                        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                            val h = size.height
                            for (g in 1..3) drawLine(grid, Offset(0f, h * g / 4f), Offset(size.width, h * g / 4f), strokeWidth = 1f)
                            val step = size.width / n
                            if (kind == "line") {
                                val path = Path()
                                vs.forEachIndexed { i, v ->
                                    val x = step * i + step / 2
                                    val y = h - h * 0.92f * (v / maxV)
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, accent, style = Stroke(width = 5f))
                                vs.forEachIndexed { i, v ->
                                    drawCircle(accent, 7f, Offset(step * i + step / 2, h - h * 0.92f * (v / maxV)))
                                }
                            } else {
                                vs.forEachIndexed { i, v ->
                                    val bh = h * 0.92f * (v / maxV)
                                    drawRoundRect(
                                        PALETTE[i % PALETTE.size], topLeft = Offset(step * i + step * 0.18f, h - bh),
                                        size = Size(step * 0.64f, bh), cornerRadius = CornerRadius(10f, 10f)
                                    )
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            labels.take(n).forEachIndexed { i, l ->
                                Text(
                                    "$l\n${formatNumber(vs[i])}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal object OroSteps : A2uiComponent {
    override val name = "Steps"
    override val description = "An ordered list of steps; steps before 'current' (0-based) show as done."
    private val Items = A2uiProperty.dynamicStringList("items", required = true)
    private val Current = A2uiProperty.dynamicNumber("current")
    override val properties = listOf(Items, Current, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Items) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val items = properties.bind(Items).orEmpty()
        val current = properties.bind(Current)?.toInt() ?: -1
        val scheme = MaterialTheme.colorScheme
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            items.forEachIndexed { i, step ->
                val done = current >= 0 && i < current
                val active = i == current
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(if (done) scheme.primary else if (active) scheme.primaryContainer else scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) Icon(Icons.Outlined.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                        else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(Space.md))
                    Text(
                        step, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (done) scheme.onSurfaceVariant else scheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

internal object OroProgress : A2uiComponent {
    override val name = "Progress"
    override val description = "A progress bar; value from 0 to 1."
    private val Value = A2uiProperty.dynamicNumber("value", required = true)
    private val Label = A2uiProperty.dynamicString("label")
    override val properties = listOf(Value, Label, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val v = (properties.bind(Value)?.toFloat() ?: 0f).let { if (it > 1f) it / 100f else it }.coerceIn(0f, 1f)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Row {
                properties.bind(Label)?.let { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                Text("${Math.round(v * 100)}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { v }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal object OroSwatches : A2uiComponent {
    override val name = "Swatches"
    override val description = "A colour palette; tap a swatch to copy its hex code."
    private val Colors = A2uiProperty.dynamicStringList("colors", required = true)
    private val Labels = A2uiProperty.dynamicStringList("labels")
    override val properties = listOf(Colors, Labels, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Colors) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val colors = properties.bind(Colors).orEmpty()
        val labels = properties.bind(Labels).orEmpty()
        val clipboard = LocalClipboardManager.current
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            colors.forEachIndexed { i, hex ->
                val c = parseColor(hex)
                Column(
                    Modifier.width(72.dp).clip(OroCorners.medium).clickable { clipboard.setText(AnnotatedString(hex)) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(OroCorners.medium).background(c ?: Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, OroCorners.medium)
                    )
                    labels.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(hex, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }

    private fun parseColor(hex: String): Color? = runCatching {
        val h = hex.trim().removePrefix("#")
        val full = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("")
            else -> h
        }
        val v = full.toLong(16)
        if (full.length == 8) Color(v.toInt()) else Color(0xFF000000 or v)
    }.getOrNull()
}

internal object OroCode : A2uiComponent {
    override val name = "Code"
    override val description = "A short code snippet with a copy button."
    private val CodeP = A2uiProperty.dynamicString("code", required = true)
    private val Language = A2uiProperty.string("language")
    override val properties = listOf(CodeP, Language, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(CodeP) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val code = properties.bind(CodeP).orEmpty()
        val clipboard = LocalClipboardManager.current
        Surface(shape = OroCorners.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.padding(start = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(properties[Language].orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(Icons.Outlined.ContentCopy, "Copy", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = Space.md, end = Space.md, bottom = Space.md)
                )
            }
        }
    }
}

internal object OroLink : A2uiComponent {
    override val name = "Link"
    override val description = "A tappable link that opens a URL."
    private val TextP = A2uiProperty.dynamicString("text")
    private val Url = A2uiProperty.dynamicString("url", required = true)
    override val properties = listOf(TextP, Url, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Url) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val url = properties.bind(Url).orEmpty()
        val uri = LocalUriHandler.current
        Row(
            modifier.clip(OroCorners.small).clickable { runCatching { uri.openUri(url) } }.padding(vertical = Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                properties.bind(TextP) ?: url, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(Space.xxs))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal object OroSuggestions : A2uiComponent {
    override val name = "Suggestions"
    override val description = "Follow-up replies the user can tap; a tap sends that text as their next message."
    private val Items = A2uiProperty.dynamicStringList("items", required = true)
    override val properties = listOf(Items, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Items) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            properties.bind(Items).orEmpty().take(6).forEach { s ->
                AssistChip(
                    onClick = { dispatchAction(mapOf("event" to mapOf("name" to "say", "context" to mapOf("text" to s)))) },
                    label = { Text(s, maxLines = 2) },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                    shape = OroCorners.pill
                )
            }
        }
    }
}

internal val OroExtraComponents: List<A2uiComponent> = listOf(
    OroBadge, OroCallout, OroStat, OroTable, OroChart, OroSteps, OroProgress, OroSwatches, OroCode, OroLink, OroSuggestions
)
