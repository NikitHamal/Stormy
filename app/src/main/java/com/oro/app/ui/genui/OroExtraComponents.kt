package com.oro.app.ui.genui

import androidx.a2ui.compose.runtime.A2uiComponentProperties
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiProperty
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.automirrored.outlined.StarHalf
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space

/*
 * Components beyond the A2UI basic catalog, for answers in a coding chat --
 * the "intelligent UI" set. Every one declares its properties, so the engine
 * validates what the model sent before anything is drawn, and every one is
 * built to read well at phone width: no chip soup, no wrapping mid-word,
 * values that animate in instead of popping.
 */

private val Weight get() = A2uiBasicCatalogV1.WeightProperty

internal val Success = Color(0xFF2E9E62)
internal val Warning = Color(0xFFD08A1F)

@Composable
internal fun toneColor(tone: String?): Color {
    val s = MaterialTheme.colorScheme
    return when (tone) {
        "success", "good", "positive" -> Success
        "warning", "caution" -> Warning
        "danger", "error", "bad", "negative" -> s.error
        "info", "primary" -> s.primary
        else -> s.onSurfaceVariant
    }
}

private val TONES = listOf("neutral", "info", "success", "warning", "danger")

/** Chart and swatch palette, readable on light and dark. */
private val PALETTE = listOf(
    Color(0xFF6E8CF7), Color(0xFFF0965A), Color(0xFF3DBE7A), Color(0xFFB285F2),
    Color(0xFFE5737B), Color(0xFF4FC1B6), Color(0xFFE2B93B), Color(0xFF8BC34A)
)

/** The shared container look: soft surface, hairline border, generous radius. */
@Composable
internal fun Panel(modifier: Modifier = Modifier, tint: Color? = null, content: @Composable () -> Unit) {
    val s = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint?.copy(alpha = 0.08f) ?: s.surfaceContainerLow,
        border = BorderStroke(1.dp, (tint ?: s.outlineVariant).copy(alpha = if (tint != null) 0.25f else 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) { content() }
}

private fun cellText(v: Any?): String = when (v) {
    null -> ""
    is Number -> formatNumber(v.toFloat())
    else -> v.toString()
}

private fun List<*>.maps(): List<Map<*, *>> = mapNotNull { it as? Map<*, *> }

// ---------------------------------------------------------------- content

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
        Surface(shape = OroCorners.pill, color = c.copy(alpha = 0.13f), modifier = modifier) {
            Text(
                properties.bind(TextP).orEmpty(), style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal object OroTags : A2uiComponent {
    override val name = "Tags"
    override val description = "A row of small non-interactive tags (technologies, keywords)."
    private val Items = A2uiProperty.dynamicStringList("items", required = true)
    override val properties = listOf(Items, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Items) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val s = MaterialTheme.colorScheme
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            properties.bind(Items).orEmpty().take(24).forEach { t ->
                Surface(shape = RoundedCornerShape(8.dp), color = s.surfaceContainerHigh) {
                    Text(t, style = MaterialTheme.typography.labelMedium, color = s.onSurfaceVariant, maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
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
        Panel(modifier, tint = c) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(c))
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(c.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        properties.bind(Title)?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface) }
                        Text(properties.bind(TextP).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal object OroQuote : A2uiComponent {
    override val name = "Quote"
    override val description = "A quotation or a testimonial, with an optional author."
    private val TextP = A2uiProperty.dynamicString("text", required = true)
    private val Author = A2uiProperty.dynamicString("author")
    override val properties = listOf(TextP, Author, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(TextP) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val s = MaterialTheme.colorScheme
        Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(s.primary))
            Spacer(Modifier.width(14.dp))
            Column {
                Icon(Icons.Outlined.FormatQuote, null, tint = s.primary, modifier = Modifier.size(22.dp))
                Text(properties.bind(TextP).orEmpty(), style = MaterialTheme.typography.bodyLarge, color = s.onSurface)
                properties.bind(Author)?.let { Text("— $it", style = MaterialTheme.typography.labelLarge, color = s.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
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
        val s = MaterialTheme.colorScheme
        val value = properties.bind(Value)?.let { if (it is Number) formatNumber(it.toFloat()) else it.toString() } ?: "–"
        Panel(modifier.widthIn(min = 120.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(properties.bind(Label).orEmpty(), style = MaterialTheme.typography.labelLarge, color = s.onSurfaceVariant, maxLines = 2)
                Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, color = s.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                properties.bind(Delta)?.let { d ->
                    val t = d.trim()
                    val tone = properties[Tone] ?: if (t.startsWith("-") || t.startsWith("−")) "danger" else if (t.startsWith("+")) "success" else "neutral"
                    val c = toneColor(tone)
                    Surface(shape = OroCorners.pill, color = c.copy(alpha = 0.12f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (tone == "success" || tone == "danger") {
                                Icon(if (tone == "success") Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown, null, tint = c, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(d, style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** label/value pairs, e.g. a summary of settings or a spec sheet. */
internal object OroKeyValue : A2uiComponent {
    override val name = "KeyValue"
    override val description = "A list of label/value rows: items [{label, value}]."
    private val Title = A2uiProperty.dynamicString("title")
    private val Items = A2uiProperty.dynamicValue("items", required = true)
    override val properties = listOf(Title, Items, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val s = MaterialTheme.colorScheme
        val items = (properties.bind(Items) as? List<*>).orEmpty().maps()
        Panel(modifier) {
            Column(Modifier.padding(vertical = 6.dp)) {
                properties.bind(Title)?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = s.outlineVariant.copy(alpha = 0.5f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                        Text(cellText(m["label"] ?: m["key"]), style = MaterialTheme.typography.bodyMedium, color = s.onSurfaceVariant, modifier = Modifier.weight(0.42f))
                        Spacer(Modifier.width(12.dp))
                        Text(cellText(m["value"]), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = s.onSurface,
                            textAlign = TextAlign.End, modifier = Modifier.weight(0.58f))
                    }
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
                    is List<*> -> r.map { cellText(it) }
                    is Map<*, *> -> columns.map { c -> cellText(r[c]) }
                    else -> listOf(cellText(r))
                }
            }
        }
        val numeric = remember(rows, columns) {
            columns.indices.map { i -> rows.isNotEmpty() && rows.all { it.getOrNull(i)?.replace(Regex("[,%$€£\\s]"), "")?.toDoubleOrNull() != null } }
        }
        val widths = remember(rows, columns) {
            columns.indices.map { i ->
                val longest = maxOf(columns[i].length, rows.maxOfOrNull { it.getOrNull(i)?.length ?: 0 } ?: 0)
                (longest * 7 + 28).coerceIn(72, 200).dp
            }
        }
        val s = MaterialTheme.colorScheme
        Panel(modifier) {
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(Modifier.background(s.surfaceContainerHigh)) {
                    columns.forEachIndexed { i, c ->
                        Text(c, style = MaterialTheme.typography.labelLarge, color = s.onSurfaceVariant,
                            textAlign = if (numeric[i]) TextAlign.End else TextAlign.Start,
                            modifier = Modifier.width(widths[i]).padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
                rows.forEachIndexed { ri, r ->
                    if (ri > 0) HorizontalDivider(color = s.outlineVariant.copy(alpha = 0.4f))
                    Row {
                        columns.indices.forEach { i ->
                            Text(
                                r.getOrNull(i).orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = s.onSurface,
                                textAlign = if (numeric[i]) TextAlign.End else TextAlign.Start,
                                modifier = Modifier.width(widths[i]).padding(horizontal = 14.dp, vertical = 10.dp),
                                maxLines = 4, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

internal object OroProsCons : A2uiComponent {
    override val name = "ProsCons"
    override val description = "Two lists side by side: pros and cons of an option."
    private val Title = A2uiProperty.dynamicString("title")
    private val Pros = A2uiProperty.dynamicStringList("pros", required = true)
    private val Cons = A2uiProperty.dynamicStringList("cons", required = true)
    override val properties = listOf(Title, Pros, Cons, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            properties.bind(Title)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProsConsColumn("Pros", properties.bind(Pros).orEmpty(), Success, true, Modifier.weight(1f).fillMaxHeight())
                ProsConsColumn("Cons", properties.bind(Cons).orEmpty(), MaterialTheme.colorScheme.error, false, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }

    @Composable
    private fun ProsConsColumn(label: String, items: List<String>, c: Color, pro: Boolean, modifier: Modifier) {
        Panel(modifier, tint = c) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (pro) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbDown, null, tint = c, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = c)
                }
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (pro) Icons.Outlined.Check else Icons.Outlined.Close, null, tint = c, modifier = Modifier.size(14.dp).padding(top = 3.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(item, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

internal object OroRating : A2uiComponent {
    override val name = "Rating"
    override val description = "A star rating, e.g. a score out of 5."
    private val Value = A2uiProperty.dynamicNumber("value", required = true)
    private val Max = A2uiProperty.number("max")
    private val Label = A2uiProperty.dynamicString("label")
    override val properties = listOf(Value, Max, Label, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val max = (properties[Max]?.toInt() ?: 5).coerceIn(1, 10)
        val v = (properties.bind(Value)?.toFloat() ?: 0f).coerceIn(0f, max.toFloat())
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            properties.bind(Label)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp)) }
            repeat(max) { i ->
                val icon = when {
                    v >= i + 1 -> Icons.Outlined.Star
                    v > i + 0.25f -> Icons.AutoMirrored.Outlined.StarHalf
                    else -> Icons.Outlined.StarBorder
                }
                Icon(icon, null, tint = Color(0xFFE2B93B), modifier = Modifier.size(20.dp))
            }
            Text(" ${formatNumber(v)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- charts

internal object OroChart : A2uiComponent {
    override val name = "Chart"
    override val description = "A bar, line or pie chart of one series: labels and numeric values."
    private val Kind = A2uiProperty.stringEnum("kind", listOf("bar", "line", "pie"))
    private val Title = A2uiProperty.dynamicString("title")
    private val Labels = A2uiProperty.dynamicStringList("labels", required = true)
    private val Values = A2uiProperty.dynamicValue("values", required = true)
    private val UnitP = A2uiProperty.string("unit")
    override val properties = listOf(Kind, Title, Labels, Values, UnitP, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Labels) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val labels = properties.bind(Labels).orEmpty()
        val values = (properties.bind(Values) as? List<*>).orEmpty().map { (it as? Number)?.toFloat() ?: it.toString().toFloatOrNull() ?: 0f }
        val n = minOf(labels.size, values.size)
        val kind = properties[Kind] ?: "bar"
        val unit = properties[UnitP].orEmpty()
        val s = MaterialTheme.colorScheme
        // Grow in once, the first time the chart is shown.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val progress by animateFloatAsState(if (shown) 1f else 0f, tween(700), label = "chart")
        Panel(modifier) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                properties.bind(Title)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                if (n == 0) {
                    Text("No data", style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant)
                    return@Column
                }
                val vs = values.take(n)
                val maxV = (vs.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)
                when (kind) {
                    "pie" -> Row(verticalAlignment = Alignment.CenterVertically) {
                        val total = vs.sum().coerceAtLeast(0.0001f)
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(128.dp)) {
                                var start = -90f
                                val stroke = size.minDimension * 0.2f
                                vs.forEachIndexed { i, v ->
                                    val sweep = 360f * v / total * progress
                                    drawArc(PALETTE[i % PALETTE.size], start + 1f, (sweep - 2f).coerceAtLeast(0f), useCenter = false,
                                        topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
                                        style = Stroke(width = stroke, cap = StrokeCap.Butt))
                                    start += 360f * v / total
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatNumber(total) + unit, style = MaterialTheme.typography.titleMedium)
                                Text("total", style = MaterialTheme.typography.labelSmall, color = s.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            labels.take(n).forEachIndexed { i, l ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(PALETTE[i % PALETTE.size]))
                                    Spacer(Modifier.width(8.dp))
                                    Text(l, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${Math.round(100 * vs[i] / total)}%", style = MaterialTheme.typography.labelMedium, color = s.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    "line" -> {
                        val accent = s.primary
                        val grid = s.outlineVariant.copy(alpha = 0.5f)
                        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                            val h = size.height
                            for (g in 0..3) drawLine(grid, Offset(0f, h * g / 3f), Offset(size.width, h * g / 3f), strokeWidth = 1f)
                            val step = if (n > 1) size.width / (n - 1) else size.width
                            val pts = vs.mapIndexed { i, v -> Offset(if (n > 1) step * i else size.width / 2, h - h * 0.9f * (v / maxV) * progress) }
                            val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                            val area = Path().apply { addPath(line); lineTo(pts.last().x, h); lineTo(pts.first().x, h); close() }
                            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent)))
                            drawPath(line, accent, style = Stroke(width = 5f, cap = StrokeCap.Round))
                            pts.forEach { drawCircle(Color.White, 7f, it); drawCircle(accent, 5f, it) }
                        }
                        AxisLabels(labels.take(n), vs, unit)
                    }
                    else -> {
                        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                            val h = size.height
                            val step = size.width / n
                            vs.forEachIndexed { i, v ->
                                val bh = (h * 0.92f * (v / maxV) * progress).coerceAtLeast(4f)
                                drawRoundRect(
                                    PALETTE[i % PALETTE.size], topLeft = Offset(step * i + step * 0.2f, h - bh),
                                    size = Size(step * 0.6f, bh), cornerRadius = CornerRadius(14f, 14f)
                                )
                            }
                        }
                        AxisLabels(labels.take(n), vs, unit)
                    }
                }
            }
        }
    }

    @Composable
    private fun AxisLabels(labels: List<String>, values: List<Float>, unit: String) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatNumber(values[i]) + unit, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

// --------------------------------------------------------------- progress

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
        val s = MaterialTheme.colorScheme
        Column(modifier.fillMaxWidth()) {
            items.forEachIndexed { i, step ->
                val done = current >= 0 && i < current
                val active = i == current
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape)
                                .background(if (done) s.primary else if (active) s.primaryContainer else s.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (done) Icon(Icons.Outlined.Check, null, tint = s.onPrimary, modifier = Modifier.size(15.dp))
                            else Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (active) s.onPrimaryContainer else s.onSurfaceVariant)
                        }
                        if (i < items.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(if (done) s.primary else s.outlineVariant))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        step, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (done) s.onSurfaceVariant else s.onSurface,
                        modifier = Modifier.padding(top = 3.dp, bottom = if (i < items.lastIndex) 16.dp else 0.dp)
                    )
                }
            }
        }
    }
}

internal object OroTimeline : A2uiComponent {
    override val name = "Timeline"
    override val description = "Dated events in order: items [{time, title, text}]."
    private val Items = A2uiProperty.dynamicValue("items", required = true)
    override val properties = listOf(Items, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val items = (properties.bind(Items) as? List<*>).orEmpty().maps()
        val s = MaterialTheme.colorScheme
        Column(modifier.fillMaxWidth()) {
            items.forEachIndexed { i, m ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp)) {
                        Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(s.primary))
                        if (i < items.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(s.outlineVariant))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.padding(bottom = if (i < items.lastIndex) 16.dp else 0.dp)) {
                        m["time"]?.let { Text(cellText(it), style = MaterialTheme.typography.labelMedium, color = s.primary) }
                        m["title"]?.let { Text(cellText(it), style = MaterialTheme.typography.titleSmall) }
                        m["text"]?.let { Text(cellText(it), style = MaterialTheme.typography.bodySmall, color = s.onSurfaceVariant) }
                    }
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
    private val Tone = A2uiProperty.stringEnum("tone", TONES)
    override val properties = listOf(Value, Label, Tone, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val target = (properties.bind(Value)?.toFloat() ?: 0f).let { if (it > 1f) it / 100f else it }.coerceIn(0f, 1f)
        val v by animateFloatAsState(target, tween(600), label = "progress")
        val s = MaterialTheme.colorScheme
        val c = properties[Tone]?.let { toneColor(it) } ?: s.primary
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(properties.bind(Label).orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${Math.round(target * 100)}%", style = MaterialTheme.typography.titleSmall, color = c)
            }
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(s.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(v).height(10.dp).clip(CircleShape).background(c))
            }
        }
    }
}

// ----------------------------------------------------------- interactive

internal object OroAccordion : A2uiComponent {
    override val name = "Accordion"
    override val description = "A collapsible section: a title that opens to show text or a child component."
    private val Title = A2uiProperty.dynamicString("title", required = true)
    private val TextP = A2uiProperty.dynamicString("text")
    private val ChildP = A2uiProperty.componentId("child")
    private val Open = A2uiProperty.boolean("open")
    override val properties = listOf(Title, TextP, ChildP, Open, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Title) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        var open by remember { mutableStateOf(properties[Open] ?: false) }
        val rotation by animateFloatAsState(if (open) 180f else 0f, label = "accordion")
        val s = MaterialTheme.colorScheme
        Panel(modifier) {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(properties.bind(Title).orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.ExpandMore, null, tint = s.onSurfaceVariant, modifier = Modifier.rotate(rotation))
                }
                AnimatedVisibility(open, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        properties.bind(TextP)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = s.onSurfaceVariant) }
                        properties[ChildP]?.let { Child(it) }
                    }
                }
            }
        }
    }
}

/** A uniform grid of children, e.g. feature cards; two columns on a phone. */
internal object OroGrid : A2uiComponent {
    override val name = "Grid"
    override val description = "Children in a grid of equal cells (columns, default 2)."
    private val Children = A2uiProperty.childList("children", required = true)
    private val Columns = A2uiProperty.number("columns")
    override val properties = listOf(Children, Columns, Weight)

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val refs = properties.bindChildReferences(Children).orEmpty()
        val cols = (properties[Columns]?.toInt() ?: 2).coerceIn(1, 3)
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            refs.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { Box(Modifier.weight(1f).fillMaxHeight()) { Child(it.id, it.baseDataPath, Modifier.fillMaxHeight()) } }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
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
        var copied by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(copied) { if (copied != null) { kotlinx.coroutines.delay(1_200); copied = null } }
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            colors.forEachIndexed { i, hex ->
                val c = parseColor(hex)
                Column(
                    Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { clipboard.setText(AnnotatedString(hex)); copied = hex },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(c ?: Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (copied == hex) Icon(Icons.Outlined.Check, "Copied", tint = if ((c?.let { it.red * 0.3f + it.green * 0.59f + it.blue * 0.11f } ?: 1f) > 0.6f) Color.Black else Color.White)
                    }
                    Spacer(Modifier.height(4.dp))
                    labels.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Text(hex.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }

    private fun parseColor(hex: String): Color? = runCatching {
        val h = hex.trim().removePrefix("#")
        val full = if (h.length == 3) h.map { "$it$it" }.joinToString("") else h
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
        Panel(modifier) {
            Column {
                Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(properties[Language].orEmpty().ifBlank { "code" }, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(Icons.Outlined.ContentCopy, "Copy", modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    code, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
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
            Text(properties.bind(TextP) ?: url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Follow-ups as full-width rows, not chips: chips wrapped mid-sentence and
 * left ragged gaps. A tapped row marks itself sent and the rest dim, so a
 * second tap cannot send a second message by accident.
 */
internal object OroSuggestions : A2uiComponent {
    override val name = "Suggestions"
    override val description = "Follow-up replies the user can tap; a tap sends that text as their next message."
    private val Items = A2uiProperty.dynamicStringList("items", required = true)
    private val Title = A2uiProperty.dynamicString("title")
    override val properties = listOf(Items, Title, Weight)

    @Composable
    override fun A2uiComponentScope.isReady(properties: A2uiComponentProperties) = properties.bind(Items) != null

    @Composable
    override fun A2uiComponentScope.Content(properties: A2uiComponentProperties, modifier: Modifier) {
        val s = MaterialTheme.colorScheme
        var sent by remember { mutableStateOf<String?>(null) }
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = s.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(properties.bind(Title) ?: "Suggested next", style = MaterialTheme.typography.labelLarge, color = s.onSurfaceVariant)
            }
            properties.bind(Items).orEmpty().take(6).forEach { item ->
                val isSent = sent == item
                val dimmed = sent != null && !isSent
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSent) s.secondaryContainer else s.surfaceContainerLow,
                    border = BorderStroke(1.dp, s.outlineVariant.copy(alpha = if (isSent) 0f else 0.6f)),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = sent == null) {
                        sent = item
                        dispatchAction(mapOf("event" to mapOf("name" to "say", "context" to mapOf("text" to item))))
                    }
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item, style = MaterialTheme.typography.bodyMedium,
                            color = if (dimmed) s.onSurfaceVariant.copy(alpha = 0.6f) else s.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(if (isSent) Icons.Outlined.Check else Icons.Outlined.NorthEast, null,
                            tint = if (isSent) s.onSecondaryContainer else s.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

internal val OroExtraComponents: List<A2uiComponent> = listOf(
    OroBadge, OroTags, OroCallout, OroQuote, OroStat, OroKeyValue, OroTable, OroProsCons, OroRating,
    OroChart, OroSteps, OroTimeline, OroProgress, OroAccordion, OroGrid, OroSwatches, OroCode, OroLink, OroSuggestions
)
