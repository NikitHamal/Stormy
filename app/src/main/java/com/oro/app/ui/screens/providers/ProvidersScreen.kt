package com.oro.app.ui.screens.providers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.ApiKeyRequirement
import com.oro.app.ui.components.agent.AgentVisuals
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.ExpressiveShapes
import com.oro.app.ui.theme.Sizes
import com.oro.app.ui.theme.Space

/**
 * AI providers and their credentials.
 *
 * Replaces four stacked password fields buried in Settings. Those gave no
 * indication of which providers were usable, which needed a key, or where a key
 * came from -- the user had to already know. This screen's job is to answer
 * "what can I use right now, and what would it take to use the rest", so each
 * provider is a row that states its own status and opens to reveal its field.
 *
 * Only one row is expanded at a time. Several open password fields on a phone
 * is a scrolling mess, and the user is only ever pasting one key.
 */
@Composable
fun ProvidersScreen(
    apiKeys: Map<AiProvider, String>,
    customBaseUrl: String,
    onKeyChange: (AiProvider, String) -> Unit,
    onCustomBaseUrlChange: (String) -> Unit,
    onBackClick: () -> Unit
) {
    var expandedProvider by remember { mutableStateOf<AiProvider?>(null) }

    // Ready-to-use providers first. A fresh install should see what already
    // works before it sees a list of things it has to go and sign up for.
    val ordered = remember(apiKeys) {
        AiProvider.entries.sortedWith(
            compareBy(
                { apiKeys[it].orEmpty().isBlank() && it.requiresApiKey },
                { it.ordinal }
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI providers",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Space.lg,
                end = Space.lg,
                bottom = Space.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item {
                Text(
                    text = "Every provider below is free to sign up for without " +
                        "billing details. Keys are stored on this device only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.md)
                )
            }

            items(ordered, key = { it.name }) { provider ->
                ProviderRow(
                    provider = provider,
                    apiKey = apiKeys[provider].orEmpty(),
                    customBaseUrl = customBaseUrl,
                    expanded = expandedProvider == provider,
                    onToggle = {
                        expandedProvider = if (expandedProvider == provider) null else provider
                    },
                    onKeyChange = { onKeyChange(provider, it) },
                    onCustomBaseUrlChange = onCustomBaseUrlChange
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    provider: AiProvider,
    apiKey: String,
    customBaseUrl: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onKeyChange: (String) -> Unit,
    onCustomBaseUrlChange: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(false) }

    val isReady = !provider.requiresApiKey || apiKey.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OroCorners.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val accent = AgentVisuals.accent(provider.ordinal)
                Surface(
                    modifier = Modifier.size(Sizes.avatar),
                    // Same device as the project list: a silhouette per
                    // provider, so the list is scannable without reading.
                    shape = ExpressiveShapes.forIndex(provider.ordinal),
                    color = if (isReady) accent.container
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = provider.displayName.first().uppercase(),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = if (isReady) accent.onContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(Space.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.displayName,
                        style = MaterialTheme.typography.bodyLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = provider.statusLine(apiKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isReady) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = "Ready to use",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(Sizes.icon)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = Space.lg,
                        end = Space.lg,
                        bottom = Space.lg
                    )
                ) {
                    if (provider.keyNote.isNotBlank()) {
                        Text(
                            text = provider.keyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = Space.md)
                        )
                    }

                    if (provider.apiKeyRequirement != ApiKeyRequirement.NONE) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = onKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API key") },
                            singleLine = true,
                            shape = OroCorners.medium,
                            visualTransformation = if (revealed) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            trailingIcon = {
                                Row {
                                    // Paste is explicit because a key is almost
                                    // always arriving from the clipboard, and
                                    // long-pressing a password field to reach
                                    // the system menu is a poor path on a phone.
                                    IconButton(onClick = {
                                        clipboard.getText()?.text
                                            ?.trim()
                                            ?.takeIf { it.isNotEmpty() }
                                            ?.let(onKeyChange)
                                    }) {
                                        Icon(
                                            Icons.Outlined.ContentPaste,
                                            contentDescription = "Paste key",
                                            modifier = Modifier.size(Sizes.icon)
                                        )
                                    }
                                    IconButton(onClick = { revealed = !revealed }) {
                                        Icon(
                                            if (revealed) Icons.Outlined.VisibilityOff
                                            else Icons.Outlined.Visibility,
                                            contentDescription = if (revealed) {
                                                "Hide key"
                                            } else {
                                                "Show key"
                                            },
                                            modifier = Modifier.size(Sizes.icon)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    if (provider == AiProvider.CUSTOM) {
                        Spacer(Modifier.height(Space.md))
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = onCustomBaseUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Base URL") },
                            placeholder = { Text("http://192.168.1.10:11434/v1") },
                            singleLine = true,
                            shape = OroCorners.medium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                        )
                    }

                    if (provider.apiKeyUrl.isNotBlank()) {
                        Spacer(Modifier.height(Space.xs))
                        TextButton(onClick = { uriHandler.openUri(provider.apiKeyUrl) }) {
                            Text("Get a key")
                            Spacer(Modifier.width(Space.sm))
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(Sizes.iconSmall)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One line stating whether this provider can be used right now.
 *
 * "Needs a key" rather than "not configured": the former says what to do, the
 * latter only says something is wrong.
 */
private fun AiProvider.statusLine(apiKey: String): String = when {
    apiKeyRequirement == ApiKeyRequirement.NONE -> "Ready — no key needed"
    apiKey.isNotBlank() -> "Key saved"
    this == AiProvider.CUSTOM -> "Optional key — set a base URL"
    else -> "Needs a key"
}
