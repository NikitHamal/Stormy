package com.oro.app.ui.screens.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oro.app.MainActivity
import com.oro.app.crash.CrashReports
import com.oro.app.ui.brand.OrobiIdle
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.OroTheme
import com.oro.app.ui.theme.Space
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Shown, in its own process, after Oro crashed.
 *
 * Leads with what a person can act on -- what broke, where, a plain-language
 * guess at why, and a restart -- and keeps the full trace one tab away for
 * whoever fixes it. Earlier crashes stay listed, so a crash that happened
 * twice is visible as a pattern.
 */
class CrashActivity : ComponentActivity() {

    private var crashLog: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Styled from the system's own dark-mode setting: this process has no in-app theme toggle to follow.
        val nightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val barStyle = if (nightMode) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        crashLog = intent.getStringExtra(EXTRA_CRASH_LOG)
            ?: CrashReports.list(this).firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }
            ?: "No crash report available."

        setContent {
            OroTheme(darkTheme = nightMode) {
                CrashScreen(
                    initialLog = crashLog,
                    onRestart = { restartApp() }
                )
            }
        }
    }

    private fun restartApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    companion object {
        const val EXTRA_CRASH_LOG = "extra_crash_log"
    }
}

private enum class CrashTab(val label: String) { Summary("Summary"), Trace("Stack trace"), Earlier("Earlier") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrashScreen(initialLog: String, onRestart: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var log by remember { mutableStateOf(initialLog) }
    var tab by remember { mutableStateOf(CrashTab.Summary) }
    val summary = remember(log) { CrashReports.summarize(log) }
    var earlier by remember { mutableStateOf(CrashReports.list(context)) }
    val device = remember(log) { deviceFacts(log) }

    Column(
        Modifier
            .fillMaxSize()
            .background(scheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- Hero ----------------------------------------------------------
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(72.dp).clip(ExpressiveShapes.cookie9()).background(scheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                OrobiIdle(modifier = Modifier.width(44.dp), look = false, showShadow = false, contentDescription = null)
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text("Oro hit a snag", style = MaterialTheme.typography.headlineSmallEmphasized, color = scheme.onSurface)
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "The app stopped unexpectedly. Your projects are saved on the device; restart to pick up where you left off.",
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant
                )
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Space.lg)) {
            CrashTab.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = tab == t, onClick = { tab = t },
                    shape = SegmentedButtonDefaults.itemShape(i, CrashTab.entries.size)
                ) {
                    Text(if (t == CrashTab.Earlier && earlier.isNotEmpty()) "${t.label} (${earlier.size})" else t.label, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(Space.md))

        // --- Body ----------------------------------------------------------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                CrashTab.Summary -> Column(
                    Modifier.fillMaxSize().padding(horizontal = Space.lg).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    Surface(shape = OroCorners.xLarge, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.BugReport, null, tint = scheme.error, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Space.sm))
                                Text(summary.type, style = MaterialTheme.typography.titleMediumEmphasized, color = scheme.error)
                            }
                            if (summary.message.isNotBlank()) SelectionContainer {
                                Text(
                                    summary.message, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = scheme.onSurface
                                )
                            }
                            summary.where?.let { Fact(Icons.Outlined.Place, "Where", it) }
                            summary.rootCause?.let { Fact(Icons.Outlined.BugReport, "Root cause", it) }
                        }
                    }
                    summary.hint?.let { hint ->
                        Surface(shape = OroCorners.large, color = scheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Outlined.Lightbulb, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Space.sm))
                                Text(hint, style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer)
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        device.forEach { fact ->
                            Surface(shape = OroCorners.pill, color = scheme.surfaceContainerHigh) {
                                Row(Modifier.padding(horizontal = Space.md, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.PhoneAndroid, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(Space.xs))
                                    Text(fact, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                }

                CrashTab.Trace -> TraceView(log)

                CrashTab.Earlier -> if (earlier.isEmpty()) {
                    Text(
                        "No earlier crashes.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.lg)
                    )
                } else LazyColumn(contentPadding = PaddingValues(horizontal = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    items(earlier, key = { it.name }) { f ->
                        val text = remember(f) { runCatching { f.readText() }.getOrDefault("") }
                        val s = remember(text) { CrashReports.summarize(text) }
                        Surface(
                            shape = OroCorners.large, color = if (text == log) scheme.secondaryContainer else scheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().clip(OroCorners.large).clickable { log = text; tab = CrashTab.Summary }
                        ) {
                            Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.History, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(Space.md))
                                Column(Modifier.weight(1f)) {
                                    Text(s.type, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(CrashReports.timeOf(f))) +
                                            (s.message.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    item(key = "clear") {
                        TextButton(onClick = { CrashReports.clear(context); earlier = emptyList() }) { Text("Clear crash history") }
                    }
                }
            }
        }

        // --- Actions -------------------------------------------------------
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
        Column(Modifier.fillMaxWidth().padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(52.dp), shape = OroCorners.pill) {
                Icon(Icons.Outlined.RestartAlt, null)
                Spacer(Modifier.width(Space.sm))
                Text("Restart Oro", style = MaterialTheme.typography.titleSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                ActionButton(Icons.Outlined.ContentCopy, "Copy", Modifier.weight(1f)) { copy(context, log) }
                ActionButton(Icons.Outlined.Share, "Share", Modifier.weight(1f)) { share(context, log) }
                ActionButton(Icons.Outlined.BugReport, "Report", Modifier.weight(1f)) { report(context, log, summary) }
            }
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface, maxLines = 4, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, shape = OroCorners.pill, contentPadding = PaddingValues(horizontal = Space.sm)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.xs))
        Text(label, maxLines = 1)
    }
}

/** The trace with Oro's own frames highlighted and library frames dimmed; scrolls sideways. */
@Composable
private fun TraceView(log: String) {
    val scheme = MaterialTheme.colorScheme
    val lines = remember(log) { log.lines() }
    val hScroll = rememberScrollState()
    SelectionContainer {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = Space.lg).clip(OroCorners.large).background(scheme.surfaceContainerLowest),
            contentPadding = PaddingValues(Space.md)
        ) {
            items(lines.size) { i ->
                val line = lines[i]
                val t = line.trim()
                val (color, weight) = when {
                    t.startsWith("===") -> scheme.primary to FontWeight.Bold
                    t.startsWith("at com.oro.") -> scheme.onSurface to FontWeight.SemiBold
                    t.startsWith("at ") || t.startsWith("...") -> scheme.onSurfaceVariant.copy(alpha = 0.7f) to FontWeight.Normal
                    t.startsWith("Caused by") || i < 8 && t.contains("Exception") || t.contains("Error:") -> scheme.error to FontWeight.SemiBold
                    else -> scheme.onSurface to FontWeight.Normal
                }
                Text(
                    line.replace(Regex("""r8-map-id-[0-9a-f]+"""), "r8"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                    color = color, fontWeight = weight, softWrap = false,
                    modifier = Modifier.horizontalScroll(hScroll)
                )
            }
        }
    }
}

private fun deviceFacts(log: String): List<String> {
    fun field(name: String) = Regex("""(?m)^$name:\s*(.+)$""").find(log)?.groupValues?.get(1)?.trim()
    return listOfNotNull(
        field("Android")?.let { "Android $it" },
        field("Device"),
        field("App Version")?.let { "Oro $it" },
        field("Used")?.let { "Memory $it" }
    )
}

private fun copy(context: Context, log: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Oro crash report", log))
    Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, log: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Oro crash report").putExtra(Intent.EXTRA_TEXT, log)
    runCatching { context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** A prefilled GitHub issue; long traces are cut so the URL stays within what browsers accept. */
private fun report(context: Context, log: String, s: CrashReports.Summary) {
    val title = "Crash: ${s.type}" + (s.message.takeIf { it.isNotBlank() }?.let { ": " + it.take(80) } ?: "")
    val body = "**What happened**\n\n(Describe what you were doing.)\n\n**Report**\n\n```\n${log.take(5_500)}\n```"
    val url = "https://github.com/NikitHamal/Stormy/issues/new?title=" + Uri.encode(title) + "&body=" + Uri.encode(body)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { copy(context, log) }
}
