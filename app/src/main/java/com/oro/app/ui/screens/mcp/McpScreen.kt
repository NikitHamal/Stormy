package com.oro.app.ui.screens.mcp

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oro.app.OroApplication
import com.oro.app.data.ai.mcp.McpRegistry
import com.oro.app.data.ai.mcp.McpServerConfig
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import kotlinx.coroutines.launch
import java.util.UUID

private data class McpPreset(val name: String, val url: String, val note: String, val tokenHint: String? = null)

private val PRESETS = listOf(
    McpPreset("Context7", "https://mcp.context7.com/mcp", "Up-to-date library docs. No key needed."),
    McpPreset("DeepWiki", "https://mcp.deepwiki.com/mcp", "Ask about any public GitHub repo. No key needed."),
    McpPreset("GitHub", "https://api.githubcopilot.com/mcp/", "Issues, PRs, code. Needs a GitHub personal access token.", "GitHub token (ghp_… or github_pat_…)"),
    McpPreset("Supabase", "https://mcp.supabase.com/mcp", "Your database and edge functions. Needs a Supabase access token.", "Supabase access token"),
    McpPreset("Figma", "https://mcp.figma.com/mcp", "Designs and components. Add it, then tap Sign in.", "Figma token (optional with sign-in)"),
    McpPreset("Custom", "", "Any remote MCP server (Streamable HTTP). Use a token, or add it and tap Sign in.", "Bearer token (optional)")
)

/**
 * Remote MCP servers whose tools Orobi can use.
 *
 * Each server is a URL and, usually, a token sent as `Authorization: Bearer`.
 * "Test" lists the server's tools, which is both the connection check and a
 * preview of what Orobi gains.
 */
@Composable
fun McpScreen(onBackClick: () -> Unit) {
    val prefs = remember { OroApplication.getInstance().preferencesRepository }
    val settings by prefs.mcpSettings.collectAsStateWithLifecycle(com.oro.app.data.ai.mcp.McpSettings())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var adding by rememberSaveable { mutableStateOf(false) }
    var testing by remember { mutableStateOf<String?>(null) }

    fun test(server: McpServerConfig) {
        testing = server.id
        scope.launch {
            val r = McpRegistry.test(server)
            testing = null
            snackbar.showSnackbar(r.fold({ "${server.name}: connected · $it tools" }, { "${server.name}: ${it.message}" }))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MCP servers", style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add server") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item(key = "intro") {
                Text(
                    "Connect remote MCP servers and Orobi can use their tools: GitHub issues, your Supabase " +
                        "database, library docs and more. Tokens stay on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.md)
                )
            }
            if (settings.servers.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No servers yet. Context7 and DeepWiki work without a key, a good first try.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(settings.servers, key = { it.id }) { server ->
                Surface(shape = OroCorners.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Hub, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(server.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                server.url, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            McpRegistry.lastErrors[server.id]?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                            }
                        }
                        IconButton(onClick = {
                            scope.launch {
                                com.oro.app.data.ai.mcp.McpOAuth.begin(server).fold(
                                    onSuccess = { url -> runCatching { uriHandler.openUri(url) } },
                                    onFailure = { snackbar.showSnackbar(it.message ?: "Sign-in not available") }
                                )
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Login,
                                if (server.oauth != null) "Signed in" else "Sign in",
                                tint = if (server.oauth != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { test(server) }, enabled = testing == null) {
                            if (testing == server.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Outlined.Verified, "Test")
                        }
                        IconButton(onClick = {
                            scope.launch { prefs.updateMcpSettings { s -> s.copy(servers = s.servers.filterNot { it.id == server.id }) } }
                        }) { Icon(Icons.Outlined.DeleteOutline, "Remove") }
                        Switch(checked = server.enabled, onCheckedChange = { on ->
                            scope.launch {
                                prefs.updateMcpSettings { s -> s.copy(servers = s.servers.map { if (it.id == server.id) it.copy(enabled = on) else it }) }
                            }
                        })
                    }
                }
            }
        }
    }

    if (adding) {
        var preset by remember { mutableStateOf(PRESETS.first()) }
        var name by remember(preset) { mutableStateOf(if (preset.name == "Custom") "" else preset.name) }
        var url by remember(preset) { mutableStateOf(preset.url) }
        var token by remember { mutableStateOf("") }
        FormSheet(
            title = "Add MCP server",
            icon = Icons.Outlined.Hub,
            onDismiss = { adding = false },
            confirmLabel = "Add and test",
            confirmEnabled = name.isNotBlank() && url.startsWith("https://"),
            onConfirm = {
                val server = McpServerConfig(
                    id = UUID.randomUUID().toString().take(8),
                    name = name.trim(),
                    url = url.trim(),
                    headers = token.trim().takeIf { it.isNotEmpty() }?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
                )
                adding = false
                scope.launch {
                    prefs.updateMcpSettings { s -> s.copy(servers = s.servers + server) }
                    test(server)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    items(PRESETS, key = { it.name }) { p ->
                        FilterChip(selected = p == preset, onClick = { preset = p }, label = { Text(p.name) })
                    }
                }
                Text(preset.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = OroCorners.medium)
                OutlinedTextField(
                    url, { url = it.trim() }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = OroCorners.medium
                )
                if (preset.tokenHint != null) {
                    OutlinedTextField(
                        token, { token = it }, Modifier.fillMaxWidth(), label = { Text(preset.tokenHint.orEmpty()) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = OroCorners.medium
                    )
                }
            }
        }
    }
}
