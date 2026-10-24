package com.oro.app.ui.genui

import android.content.Intent
import android.net.Uri
import androidx.a2ui.compose.runtime.A2uiComponentReference
import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.runtime.A2uiComponentState
import androidx.a2ui.compose.runtime.ProvideActionInterceptor
import androidx.a2ui.compose.ui.A2uiComponent
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.oro.app.ui.components.MarkdownText
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/*
 * Oro's implementation of the A2UI basic catalog: the eighteen standard
 * components drawn with Oro's Material 3 design system. The library ships
 * the protocol, data binding and schemas; how each component looks is ours.
 */

internal fun Modifier.a11y(a: A2uiBasicCatalogV1.AccessibilityAttributes?): Modifier =
    if (a?.label.isNullOrBlank()) this else semantics { contentDescription = a!!.label!! }

/** Renders a child by id: the routing every container needs. */
@Composable
internal fun A2uiComponentScope.Child(id: String, baseDataPath: String? = null, modifier: Modifier = Modifier) {
    when (val st = observeA2uiComponentState(id, baseDataPath)) {
        is A2uiComponentState.Success -> A2uiComponent(st.component, modifier)
        is A2uiComponentState.Error -> Text(
            "⚠ ${st.exception.message ?: "Could not show this part"}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = modifier
        )
        else -> Unit
    }
}

@Composable
private fun A2uiComponentScope.weightOf(ref: A2uiComponentReference): Float? =
    (observeA2uiComponentState(ref.id, ref.baseDataPath) as? A2uiComponentState.Success)
        ?.component?.properties?.get(A2uiBasicCatalogV1.WeightProperty)?.toFloat()?.takeIf { it > 0f }

internal object OroText : A2uiBasicCatalogV1.Text {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        text: String,
        variant: A2uiBasicCatalogV1.Text.Variant,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val t = MaterialTheme.typography
        val style = when (variant) {
            A2uiBasicCatalogV1.Text.Variant.H1 -> t.headlineMediumEmphasized
            A2uiBasicCatalogV1.Text.Variant.H2 -> t.titleLargeEmphasized
            A2uiBasicCatalogV1.Text.Variant.H3 -> t.titleMediumEmphasized
            A2uiBasicCatalogV1.Text.Variant.H4 -> t.titleSmall
            A2uiBasicCatalogV1.Text.Variant.H5 -> t.labelLarge
            A2uiBasicCatalogV1.Text.Variant.Caption -> t.bodySmall
            A2uiBasicCatalogV1.Text.Variant.Body -> t.bodyMedium
        }
        // Unspecified so text inside a filled button takes the button's content colour.
        val color = if (variant == A2uiBasicCatalogV1.Text.Variant.Caption) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
        if (variant == A2uiBasicCatalogV1.Text.Variant.Body && MARKDOWNISH.containsMatchIn(text)) {
            MarkdownText(markdown = text, modifier = modifier.a11y(accessibility), textColor = LocalContentColor.current)
        } else {
            Text(text, style = style, color = color, modifier = modifier.a11y(accessibility))
        }
    }
}

private val MARKDOWNISH = Regex("""[*_`\[#]|^\s*[-•]\s""", RegexOption.MULTILINE)

internal object OroImage : A2uiBasicCatalogV1.Image {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        url: String,
        description: String?,
        fit: A2uiBasicCatalogV1.Image.Fit,
        variant: A2uiBasicCatalogV1.Image.Variant,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val scale = when (fit) {
            A2uiBasicCatalogV1.Image.Fit.Contain -> ContentScale.Fit
            A2uiBasicCatalogV1.Image.Fit.Cover -> ContentScale.Crop
            A2uiBasicCatalogV1.Image.Fit.Fill -> ContentScale.FillBounds
            A2uiBasicCatalogV1.Image.Fit.None -> ContentScale.None
            A2uiBasicCatalogV1.Image.Fit.ScaleDown -> ContentScale.Inside
        }
        val sized = when (variant) {
            A2uiBasicCatalogV1.Image.Variant.Icon -> Modifier.size(24.dp)
            A2uiBasicCatalogV1.Image.Variant.Avatar -> Modifier.size(44.dp).clip(CircleShape)
            A2uiBasicCatalogV1.Image.Variant.SmallFeature -> Modifier.size(96.dp).clip(OroCorners.medium)
            A2uiBasicCatalogV1.Image.Variant.MediumFeature -> Modifier.fillMaxWidth().height(168.dp).clip(OroCorners.large)
            A2uiBasicCatalogV1.Image.Variant.LargeFeature -> Modifier.fillMaxWidth().height(240.dp).clip(OroCorners.large)
            A2uiBasicCatalogV1.Image.Variant.Header -> Modifier.fillMaxWidth().height(180.dp).clip(OroCorners.xLarge)
        }
        AsyncImage(
            model = url, contentDescription = description ?: accessibility?.label,
            contentScale = if (fit == A2uiBasicCatalogV1.Image.Fit.Fill && variant != A2uiBasicCatalogV1.Image.Variant.Icon) ContentScale.Crop else scale,
            modifier = modifier.then(sized)
        )
    }
}

internal object OroIcon : A2uiBasicCatalogV1.Icon {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        source: A2uiBasicCatalogV1.Icon.Source,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val vector = when (source) {
            is A2uiBasicCatalogV1.Icon.BuiltIn -> builtInIcon(source)
            is A2uiBasicCatalogV1.Icon.SvgPath -> remember(source.svgPath) {
                runCatching {
                    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                        .addPath(pathData = addPathNodes(source.svgPath), fill = SolidColor(Color.Black)).build()
                }.getOrNull()
            } ?: Icons.Outlined.Circle
            else -> iconByName((source as? A2uiBasicCatalogV1.Icon.Unrecognized)?.name.orEmpty())
        }
        Icon(vector, contentDescription = accessibility?.label, tint = MaterialTheme.colorScheme.primary, modifier = modifier.size(22.dp))
    }
}

internal fun iconByName(name: String): ImageVector =
    A2uiBasicCatalogV1.Icon.BuiltIn.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.let { builtInIcon(it) }
        ?: Icons.Outlined.Circle

internal fun builtInIcon(b: A2uiBasicCatalogV1.Icon.BuiltIn): ImageVector = when (b) {
    A2uiBasicCatalogV1.Icon.BuiltIn.AccountCircle -> Icons.Outlined.AccountCircle
    A2uiBasicCatalogV1.Icon.BuiltIn.Add -> Icons.Outlined.Add
    A2uiBasicCatalogV1.Icon.BuiltIn.ArrowBack -> Icons.AutoMirrored.Outlined.ArrowBack
    A2uiBasicCatalogV1.Icon.BuiltIn.ArrowForward -> Icons.AutoMirrored.Outlined.ArrowForward
    A2uiBasicCatalogV1.Icon.BuiltIn.AttachFile -> Icons.Outlined.AttachFile
    A2uiBasicCatalogV1.Icon.BuiltIn.CalendarToday -> Icons.Outlined.CalendarToday
    A2uiBasicCatalogV1.Icon.BuiltIn.Call -> Icons.Outlined.Call
    A2uiBasicCatalogV1.Icon.BuiltIn.Camera -> Icons.Outlined.PhotoCamera
    A2uiBasicCatalogV1.Icon.BuiltIn.Check -> Icons.Outlined.Check
    A2uiBasicCatalogV1.Icon.BuiltIn.Close -> Icons.Outlined.Close
    A2uiBasicCatalogV1.Icon.BuiltIn.Delete -> Icons.Outlined.Delete
    A2uiBasicCatalogV1.Icon.BuiltIn.Download -> Icons.Outlined.Download
    A2uiBasicCatalogV1.Icon.BuiltIn.Edit -> Icons.Outlined.Edit
    A2uiBasicCatalogV1.Icon.BuiltIn.Error -> Icons.Outlined.ErrorOutline
    A2uiBasicCatalogV1.Icon.BuiltIn.Event -> Icons.Outlined.Event
    A2uiBasicCatalogV1.Icon.BuiltIn.FastForward -> Icons.Outlined.FastForward
    A2uiBasicCatalogV1.Icon.BuiltIn.Favorite -> Icons.Outlined.Favorite
    A2uiBasicCatalogV1.Icon.BuiltIn.FavoriteOff -> Icons.Outlined.FavoriteBorder
    A2uiBasicCatalogV1.Icon.BuiltIn.Folder -> Icons.Outlined.Folder
    A2uiBasicCatalogV1.Icon.BuiltIn.Help -> Icons.AutoMirrored.Outlined.HelpOutline
    A2uiBasicCatalogV1.Icon.BuiltIn.Home -> Icons.Outlined.Home
    A2uiBasicCatalogV1.Icon.BuiltIn.Info -> Icons.Outlined.Info
    A2uiBasicCatalogV1.Icon.BuiltIn.LocationOn -> Icons.Outlined.LocationOn
    A2uiBasicCatalogV1.Icon.BuiltIn.Lock -> Icons.Outlined.Lock
    A2uiBasicCatalogV1.Icon.BuiltIn.LockOpen -> Icons.Outlined.LockOpen
    A2uiBasicCatalogV1.Icon.BuiltIn.Mail -> Icons.Outlined.Mail
    A2uiBasicCatalogV1.Icon.BuiltIn.Menu -> Icons.Outlined.Menu
    A2uiBasicCatalogV1.Icon.BuiltIn.MoreHoriz -> Icons.Outlined.MoreHoriz
    A2uiBasicCatalogV1.Icon.BuiltIn.MoreVert -> Icons.Outlined.MoreVert
    A2uiBasicCatalogV1.Icon.BuiltIn.Notifications -> Icons.Outlined.Notifications
    A2uiBasicCatalogV1.Icon.BuiltIn.NotificationsOff -> Icons.Outlined.NotificationsOff
    A2uiBasicCatalogV1.Icon.BuiltIn.Pause -> Icons.Outlined.Pause
    A2uiBasicCatalogV1.Icon.BuiltIn.Payment -> Icons.Outlined.Payment
    A2uiBasicCatalogV1.Icon.BuiltIn.Person -> Icons.Outlined.Person
    A2uiBasicCatalogV1.Icon.BuiltIn.Phone -> Icons.Outlined.Phone
    A2uiBasicCatalogV1.Icon.BuiltIn.Photo -> Icons.Outlined.Photo
    A2uiBasicCatalogV1.Icon.BuiltIn.Play -> Icons.Outlined.PlayArrow
    A2uiBasicCatalogV1.Icon.BuiltIn.Print -> Icons.Outlined.Print
    A2uiBasicCatalogV1.Icon.BuiltIn.Refresh -> Icons.Outlined.Refresh
    A2uiBasicCatalogV1.Icon.BuiltIn.Rewind -> Icons.Outlined.FastRewind
    A2uiBasicCatalogV1.Icon.BuiltIn.Search -> Icons.Outlined.Search
    A2uiBasicCatalogV1.Icon.BuiltIn.Send -> Icons.AutoMirrored.Outlined.Send
    A2uiBasicCatalogV1.Icon.BuiltIn.Settings -> Icons.Outlined.Settings
    A2uiBasicCatalogV1.Icon.BuiltIn.Share -> Icons.Outlined.Share
    A2uiBasicCatalogV1.Icon.BuiltIn.ShoppingCart -> Icons.Outlined.ShoppingCart
    A2uiBasicCatalogV1.Icon.BuiltIn.SkipNext -> Icons.Outlined.SkipNext
    A2uiBasicCatalogV1.Icon.BuiltIn.SkipPrevious -> Icons.Outlined.SkipPrevious
    A2uiBasicCatalogV1.Icon.BuiltIn.Star -> Icons.Outlined.Star
    A2uiBasicCatalogV1.Icon.BuiltIn.StarHalf -> Icons.AutoMirrored.Outlined.StarHalf
    A2uiBasicCatalogV1.Icon.BuiltIn.StarOff -> Icons.Outlined.StarBorder
    A2uiBasicCatalogV1.Icon.BuiltIn.Stop -> Icons.Outlined.Stop
    A2uiBasicCatalogV1.Icon.BuiltIn.Upload -> Icons.Outlined.Upload
    A2uiBasicCatalogV1.Icon.BuiltIn.Visibility -> Icons.Outlined.Visibility
    A2uiBasicCatalogV1.Icon.BuiltIn.VisibilityOff -> Icons.Outlined.VisibilityOff
    A2uiBasicCatalogV1.Icon.BuiltIn.VolumeDown -> Icons.AutoMirrored.Outlined.VolumeDown
    A2uiBasicCatalogV1.Icon.BuiltIn.VolumeMute -> Icons.AutoMirrored.Outlined.VolumeMute
    A2uiBasicCatalogV1.Icon.BuiltIn.VolumeOff -> Icons.AutoMirrored.Outlined.VolumeOff
    A2uiBasicCatalogV1.Icon.BuiltIn.VolumeUp -> Icons.AutoMirrored.Outlined.VolumeUp
    A2uiBasicCatalogV1.Icon.BuiltIn.Warning -> Icons.Outlined.WarningAmber
}

/** Video and audio open in the system player: an in-app player would cost megabytes of APK. */
@Composable
private fun MediaCard(url: String, label: String, icon: ImageVector, mime: String, modifier: Modifier) {
    val context = LocalContext.current
    Surface(
        shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().clip(OroCorners.large).clickable {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(Uri.parse(url).host ?: url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

internal object OroVideo : A2uiBasicCatalogV1.Video {
    @Composable
    override fun A2uiComponentScope.TypedContent(url: String, accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?, modifier: Modifier) =
        MediaCard(url, accessibility?.label ?: "Play video", Icons.Outlined.PlayCircle, "video/*", modifier)
}

internal object OroAudio : A2uiBasicCatalogV1.AudioPlayer {
    @Composable
    override fun A2uiComponentScope.TypedContent(url: String, description: String?, accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?, modifier: Modifier) =
        MediaCard(url, description ?: accessibility?.label ?: "Play audio", Icons.Outlined.Headphones, "audio/*", modifier)
}

internal object OroRow : A2uiBasicCatalogV1.Row {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: kotlin.collections.List<A2uiComponentReference>,
        justify: A2uiBasicCatalogV1.Row.Justify,
        align: A2uiBasicCatalogV1.Row.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val gap = Space.sm
        val arrangement = when (justify) {
            A2uiBasicCatalogV1.Row.Justify.Start, A2uiBasicCatalogV1.Row.Justify.Stretch -> Arrangement.spacedBy(gap)
            A2uiBasicCatalogV1.Row.Justify.Center -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
            A2uiBasicCatalogV1.Row.Justify.End -> Arrangement.spacedBy(gap, Alignment.End)
            A2uiBasicCatalogV1.Row.Justify.SpaceBetween -> Arrangement.SpaceBetween
            A2uiBasicCatalogV1.Row.Justify.SpaceAround -> Arrangement.SpaceAround
            A2uiBasicCatalogV1.Row.Justify.SpaceEvenly -> Arrangement.SpaceEvenly
        }
        val vertical = when (align) {
            A2uiBasicCatalogV1.Row.Align.Start -> Alignment.Top
            A2uiBasicCatalogV1.Row.Align.End -> Alignment.Bottom
            else -> Alignment.CenterVertically
        }
        Row(modifier.fillMaxWidth().a11y(accessibility), horizontalArrangement = arrangement, verticalAlignment = vertical) {
            children.forEach { ref ->
                val w = weightOf(ref) ?: if (justify == A2uiBasicCatalogV1.Row.Justify.Stretch) 1f else null
                Child(ref.id, ref.baseDataPath, if (w != null) Modifier.weight(w) else Modifier)
            }
        }
    }
}

internal object OroColumn : A2uiBasicCatalogV1.Column {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: kotlin.collections.List<A2uiComponentReference>,
        justify: A2uiBasicCatalogV1.Column.Justify,
        align: A2uiBasicCatalogV1.Column.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        val gap = 12.dp
        val arrangement = when (justify) {
            A2uiBasicCatalogV1.Column.Justify.Center -> Arrangement.spacedBy(gap, Alignment.CenterVertically)
            A2uiBasicCatalogV1.Column.Justify.End -> Arrangement.spacedBy(gap, Alignment.Bottom)
            A2uiBasicCatalogV1.Column.Justify.SpaceBetween -> Arrangement.SpaceBetween
            A2uiBasicCatalogV1.Column.Justify.SpaceAround -> Arrangement.SpaceAround
            A2uiBasicCatalogV1.Column.Justify.SpaceEvenly -> Arrangement.SpaceEvenly
            else -> Arrangement.spacedBy(gap)
        }
        val horizontal = when (align) {
            A2uiBasicCatalogV1.Column.Align.Center -> Alignment.CenterHorizontally
            A2uiBasicCatalogV1.Column.Align.End -> Alignment.End
            else -> Alignment.Start
        }
        Column(modifier.fillMaxWidth().a11y(accessibility), verticalArrangement = arrangement, horizontalAlignment = horizontal) {
            children.forEach { ref ->
                Child(ref.id, ref.baseDataPath, if (align == A2uiBasicCatalogV1.Column.Align.Stretch) Modifier.fillMaxWidth() else Modifier)
            }
        }
    }
}

/** Lists render eagerly: they sit inside the chat's own lazy list, which cannot nest another vertical lazy list. */
internal object OroList : A2uiBasicCatalogV1.List {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        children: kotlin.collections.List<A2uiComponentReference>,
        direction: A2uiBasicCatalogV1.List.Direction,
        align: A2uiBasicCatalogV1.List.Align,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        if (direction == A2uiBasicCatalogV1.List.Direction.Horizontal) {
            Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).a11y(accessibility), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                children.forEach { Child(it.id, it.baseDataPath) }
            }
        } else {
            val horizontal = when (align) {
                A2uiBasicCatalogV1.List.Align.Center -> Alignment.CenterHorizontally
                A2uiBasicCatalogV1.List.Align.End -> Alignment.End
                else -> Alignment.Start
            }
            Column(modifier.fillMaxWidth().a11y(accessibility), verticalArrangement = Arrangement.spacedBy(Space.sm), horizontalAlignment = horizontal) {
                children.take(200).forEach {
                    Child(it.id, it.baseDataPath, if (align == A2uiBasicCatalogV1.List.Align.Stretch) Modifier.fillMaxWidth() else Modifier)
                }
            }
        }
    }
}

internal object OroCard : A2uiBasicCatalogV1.Card {
    @Composable
    override fun A2uiComponentScope.TypedContent(childId: String, accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?, modifier: Modifier) {
        Panel(modifier.a11y(accessibility)) {
            Box(Modifier.padding(16.dp)) { Child(childId) }
        }
    }
}

internal object OroTabs : A2uiBasicCatalogV1.Tabs {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        tabs: kotlin.collections.List<A2uiBasicCatalogV1.Tabs.Tab>,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        if (tabs.isEmpty()) return
        var selected by remember { mutableIntStateOf(0) }
        val index = selected.coerceIn(0, tabs.lastIndex)
        Column(modifier.fillMaxWidth().a11y(accessibility)) {
            PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 0.dp, containerColor = Color.Transparent) {
                tabs.forEachIndexed { i, tab ->
                    Tab(selected = i == index, onClick = { selected = i }, text = { Text(tab.title, maxLines = 1) })
                }
            }
            Spacer(Modifier.height(Space.md))
            Child(tabs[index].childId)
        }
    }
}

/** A modal opens as a bottom sheet: the trigger's own action is intercepted to open it. */
internal object OroModal : A2uiBasicCatalogV1.Modal {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        triggerId: String,
        contentId: String,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        modifier: Modifier,
    ) {
        var open by remember { mutableStateOf(false) }
        ProvideActionInterceptor(onIntercept = { open = true; true }) {
            Box(modifier.a11y(accessibility)) { Child(triggerId) }
        }
        if (open) {
            ModalBottomSheet(
                onDismissRequest = { open = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.lg, vertical = Space.md)) {
                    Child(contentId)
                }
            }
        }
    }
}

internal object OroDivider : A2uiBasicCatalogV1.Divider {
    @Composable
    override fun A2uiComponentScope.TypedContent(axis: A2uiBasicCatalogV1.Divider.Axis, accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?, modifier: Modifier) {
        if (axis == A2uiBasicCatalogV1.Divider.Axis.Vertical) {
            VerticalDivider(modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        } else {
            HorizontalDivider(modifier.padding(vertical = Space.xs), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

internal object OroButton : A2uiBasicCatalogV1.Button {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        childId: String,
        variant: A2uiBasicCatalogV1.Button.Variant,
        action: Map<String, Any?>,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: kotlin.collections.List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        val failing = checks.firstOrNull { !it.condition }
        val onClick = { dispatchAction(action) }
        val m = modifier.a11y(accessibility)
        Column {
            when (variant) {
                A2uiBasicCatalogV1.Button.Variant.Primary ->
                    Button(onClick = onClick, enabled = failing == null, modifier = m, shape = OroCorners.pill) { Child(childId) }
                A2uiBasicCatalogV1.Button.Variant.Borderless ->
                    TextButton(onClick = onClick, enabled = failing == null, modifier = m) { Child(childId) }
                else ->
                    FilledTonalButton(onClick = onClick, enabled = failing == null, modifier = m, shape = OroCorners.pill) { Child(childId) }
            }
            failing?.let { Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

internal object OroTextField : A2uiBasicCatalogV1.TextField {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String,
        value: String?,
        variant: A2uiBasicCatalogV1.TextField.Variant,
        validationRegexp: String?,
        onValueChange: (String) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: kotlin.collections.List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        val text = value.orEmpty()
        val regexFails = remember(validationRegexp, text) {
            text.isNotEmpty() && validationRegexp != null && runCatching { !Regex(validationRegexp).matches(text) }.getOrDefault(false)
        }
        val failing = checks.firstOrNull { !it.condition }?.message ?: if (regexFails) "Check the format" else null
        OutlinedTextField(
            value = text, onValueChange = onValueChange, enabled = enabled,
            label = { Text(label) },
            singleLine = variant != A2uiBasicCatalogV1.TextField.Variant.LongText,
            minLines = if (variant == A2uiBasicCatalogV1.TextField.Variant.LongText) 3 else 1,
            isError = failing != null,
            supportingText = if (failing != null) { { Text(failing) } } else null,
            visualTransformation = if (variant == A2uiBasicCatalogV1.TextField.Variant.Obscured) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when (variant) {
                    A2uiBasicCatalogV1.TextField.Variant.Number -> KeyboardType.Number
                    A2uiBasicCatalogV1.TextField.Variant.Obscured -> KeyboardType.Password
                    else -> KeyboardType.Text
                }
            ),
            shape = OroCorners.large,
            modifier = modifier.fillMaxWidth().a11y(accessibility)
        )
    }
}

internal object OroCheckBox : A2uiBasicCatalogV1.CheckBox {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String,
        value: Boolean,
        onValueChange: (Boolean) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: kotlin.collections.List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        Column(modifier.a11y(accessibility)) {
            Row(
                Modifier.clip(OroCorners.medium).clickable(enabled = enabled) { onValueChange(!value) }.padding(end = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = value, onCheckedChange = onValueChange, enabled = enabled)
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
            checks.firstOrNull { !it.condition }?.let {
                Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Space.md))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal object OroChoicePicker : A2uiBasicCatalogV1.ChoicePicker {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String?,
        options: kotlin.collections.List<A2uiBasicCatalogV1.ChoicePicker.Option>,
        value: kotlin.collections.List<String>,
        variant: A2uiBasicCatalogV1.ChoicePicker.Variant,
        displayStyle: A2uiBasicCatalogV1.ChoicePicker.DisplayStyle,
        filterable: Boolean,
        onValueChange: (kotlin.collections.List<String>) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: kotlin.collections.List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        val single = variant == A2uiBasicCatalogV1.ChoicePicker.Variant.MutuallyExclusive
        var filter by remember { mutableStateOf("") }
        val shown = if (filter.isBlank()) options else options.filter { it.label.contains(filter, ignoreCase = true) }
        fun toggle(v: String) = onValueChange(
            if (single) listOf(v) else if (v in value) value - v else value + v
        )
        Column(modifier.fillMaxWidth().a11y(accessibility), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            label?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            if (filterable) OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true, placeholder = { Text("Filter") },
                shape = OroCorners.large, modifier = Modifier.fillMaxWidth()
            )
            if (displayStyle == A2uiBasicCatalogV1.ChoicePicker.DisplayStyle.Chips) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    shown.forEach { o ->
                        FilterChip(selected = o.value in value, onClick = { toggle(o.value) }, enabled = enabled, label = { Text(o.label) })
                    }
                }
            } else {
                shown.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clip(OroCorners.medium).clickable(enabled = enabled) { toggle(o.value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (single) RadioButton(selected = o.value in value, onClick = { toggle(o.value) }, enabled = enabled)
                        else Checkbox(checked = o.value in value, onCheckedChange = { toggle(o.value) }, enabled = enabled)
                        Text(o.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            checks.firstOrNull { !it.condition }?.let { Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

internal object OroSlider : A2uiBasicCatalogV1.Slider {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String?,
        min: Float,
        max: Float,
        value: Float,
        onValueChange: (Float) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: kotlin.collections.List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        val lo = minOf(min, max)
        val hi = maxOf(min, max).let { if (it == lo) lo + 1f else it }
        Column(modifier.fillMaxWidth().a11y(accessibility)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                label?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)) } ?: Spacer(Modifier.weight(1f))
                Text(formatNumber(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(value = value.coerceIn(lo, hi), onValueChange = onValueChange, valueRange = lo..hi, enabled = enabled)
        }
    }
}

internal fun formatNumber(v: Float): String =
    if (v == Math.round(v).toFloat()) Math.round(v).toString() else String.format(java.util.Locale.getDefault(), "%.2f", v)

internal object OroDateTimeInput : A2uiBasicCatalogV1.DateTimeInput {
    @Composable
    override fun A2uiComponentScope.TypedContent(
        value: Long?,
        onValueChange: ((Long?) -> Unit)?,
        enableDate: Boolean,
        enableTime: Boolean,
        min: Long?,
        max: Long?,
        label: String?,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: kotlin.collections.List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        var step by remember { mutableIntStateOf(0) } // 0 closed, 1 date, 2 time
        var pendingDate by remember { mutableStateOf<Long?>(null) }
        val shown = value?.let {
            val f = when {
                enableDate && enableTime -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                enableTime -> DateFormat.getTimeInstance(DateFormat.SHORT)
                else -> DateFormat.getDateInstance(DateFormat.MEDIUM)
            }
            f.timeZone = TimeZone.getTimeZone("UTC")
            f.format(Date(it))
        }
        Column(modifier.a11y(accessibility)) {
            label?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            OutlinedButton(
                onClick = { step = if (enableDate || !enableTime) 1 else 2 },
                enabled = onValueChange != null, shape = OroCorners.pill
            ) {
                Icon(if (enableDate || !enableTime) Icons.Outlined.CalendarToday else Icons.Outlined.Schedule, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.sm))
                Text(shown ?: "Choose")
            }
        }
        if (step == 1) {
            val state = rememberDatePickerState(initialSelectedDateMillis = value)
            DatePickerDialog(
                onDismissRequest = { step = 0 },
                confirmButton = {
                    TextButton(onClick = {
                        val picked = state.selectedDateMillis
                        if (enableTime && picked != null) { pendingDate = picked; step = 2 } else { onValueChange?.invoke(picked); step = 0 }
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } }
            ) { DatePicker(state = state) }
        }
        if (step == 2) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = value ?: System.currentTimeMillis() }
            val state = rememberTimePickerState(initialHour = cal.get(Calendar.HOUR_OF_DAY), initialMinute = cal.get(Calendar.MINUTE))
            AlertDialog(
                onDismissRequest = { step = 0 },
                confirmButton = {
                    TextButton(onClick = {
                        val base = pendingDate ?: if (enableDate) (value ?: 0L) else 0L
                        val day = base - Math.floorMod(base, 86_400_000L)
                        onValueChange?.invoke(day + state.hour * 3_600_000L + state.minute * 60_000L)
                        step = 0
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { step = 0 }) { Text("Cancel") } },
                text = { TimePicker(state = state) }
            )
        }
    }
}
