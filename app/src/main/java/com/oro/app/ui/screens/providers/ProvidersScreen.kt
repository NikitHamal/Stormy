package com.oro.app.ui.screens.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ApiKeyRequirement
import com.oro.app.data.ai.providers.KeyHealth
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/** One row of the provider list, precomputed so the list does no work per frame. */
@Immutable
data class ProviderRowUi(
    val ref: ProviderRef,
    val title: String,
    val subtitle: String,
    val status: String,
    val ready: Boolean,
    val warning: Boolean,
    val shapeIndex: Int
)

/**
 * AI providers: the ones you added, then the built-ins.
 *
 * Each row states its own status ("2 keys · 1 cooling", "48 models",
 * "Needs a key") and opens a sheet with its keys -- several per provider, with
 * live health, order and rotation -- and, for your own providers, its models.
 * "Add provider" starts from a preset (OpenAI, Anthropic, DeepSeek, local
 * Ollama...) or from scratch, for either protocol.
 */
@Composable
fun ProvidersScreen(
    viewModel: ProvidersViewModel,
    onBackClick: () -> Unit,
    /** Open the editor: [ProviderRoute.NEW] or a provider's route. */
    onOpenEditor: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val counts by viewModel.customModelCounts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    // Shipped vendors (seeded presets) list with the built-ins; "Your
    // providers" is only what the user added themselves.
    val (seededConfigs, ownConfigs) = remember(state.settings.custom) {
        state.settings.custom.partition { it.presetId?.startsWith("custom-") == false }
    }
    fun customRow(i: Int, c: com.oro.app.data.ai.providers.CustomProviderConfig): ProviderRowUi {
        val ref = ProviderRef.Custom(c.id)
        val keys = state.keysOf(ref)
        val models = counts[c.id] ?: 0
        val needsKey = c.presetId?.let { com.oro.app.data.ai.providers.ProviderPresets.byId(it)?.needsKey } ?: false
        return ProviderRowUi(
            ref = ref,
            title = c.name,
            subtitle = c.normalizedBaseUrl.removePrefix("https://").removePrefix("http://"),
            status = when {
                !c.enabled -> "Off"
                needsKey && keys.isEmpty() -> "Needs a key"
                else -> listOfNotNull(
                    "$models models".takeIf { models > 0 },
                    keySummary(keys).takeIf { it.isNotEmpty() }
                ).joinToString(" · ").ifEmpty { "Ready · fetch models" }
            },
            ready = c.enabled && (!needsKey || keys.isNotEmpty()),
            warning = keys.isNotEmpty() && keys.none { it.entry.enabled && it.state.health == KeyHealth.OK },
            shapeIndex = i + 3
        )
    }
    val seededRows = remember(seededConfigs, state.health, counts) { seededConfigs.mapIndexed { i, c -> customRow(i + 20, c) } }
    val customRows = remember(ownConfigs, state.health, counts) {
        ownConfigs.mapIndexed { i, c -> customRow(i, c) }
    }
    val builtInRows = remember(state.settings.builtInKeys, state.health, seededRows) {
        AiProvider.entries.filter { it != AiProvider.CUSTOM }.map { p ->
            val ref = ProviderRef.BuiltIn(p)
            val keys = state.keysOf(ref)
            val hasKey = keys.any { it.entry.enabled && it.entry.key.isNotBlank() }
            ProviderRowUi(
                ref = ref,
                title = p.displayName,
                subtitle = p.keyNote,
                status = when {
                    keys.isNotEmpty() -> keySummary(keys)
                    p.apiKeyRequirement == ApiKeyRequirement.NONE -> "Ready · no key needed"
                    p.apiKeyRequirement == ApiKeyRequirement.OPTIONAL -> "Ready · key optional"
                    else -> "Needs a key"
                },
                ready = hasKey || p.worksWithoutKey,
                warning = keys.isNotEmpty() && keys.none { it.entry.enabled && it.state.health == KeyHealth.OK },
                shapeIndex = p.ordinal
            )
        }.plus(seededRows).sortedBy { !it.ready }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI providers", style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenEditor(ProviderRoute.NEW) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add provider") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item(key = "intro", contentType = "text") {
                Text(
                    text = "Add as many keys as you like to any provider: when one hits a rate limit or runs " +
                        "out of credits, Oro moves to the next without interrupting your run. Keys stay on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.md)
                )
            }
            if (customRows.isNotEmpty()) {
                item(key = "h-custom", contentType = "header") { SectionLabel("Your providers") }
                items(customRows, key = { "c-" + it.ref.poolId }, contentType = { "row" }) { row ->
                    ProviderListRow(row, onClick = { onOpenEditor(ProviderRoute.of(row.ref)) })
                }
            }
            item(key = "h-builtin", contentType = "header") { SectionLabel("Built in") }
            items(builtInRows, key = { "b-" + it.ref.poolId }, contentType = { "row" }) { row ->
                ProviderListRow(row, onClick = { onOpenEditor(ProviderRoute.of(row.ref)) })
            }
        }
    }

}

/** "2 keys · 1 cooling". */
internal fun keySummary(keys: List<KeyUi>): String {
    if (keys.isEmpty()) return ""
    val enabled = keys.filter { it.entry.enabled }
    val down = enabled.count { it.state.health != KeyHealth.OK }
    val base = if (keys.size == 1) "1 key" else "${keys.size} keys"
    return when {
        enabled.isEmpty() -> "$base · all off"
        down == 0 -> base
        down == enabled.size -> "$base · none usable"
        else -> "$base · $down resting"
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Space.md, bottom = Space.xs, start = Space.xs)
    )
}

@Composable
private fun ProviderListRow(row: ProviderRowUi, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = scheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(Sizes.avatar),
                shape = ExpressiveShapes.forIndex(row.shapeIndex),
                color = if (row.ready) scheme.primaryContainer else scheme.surfaceContainerHighest
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = row.title.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = if (row.ready) scheme.onPrimaryContainer else scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    row.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (row.warning) scheme.error else if (row.ready) scheme.primary else scheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (row.subtitle.isNotBlank()) {
                    Text(
                        row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant
            )
        }
    }
}
