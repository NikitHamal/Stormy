package com.codex.stormy.ui.components.expressive

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space
import kotlinx.coroutines.delay

/**
 * A top bar that cross-fades between a title row and a search pill.
 *
 * Two layers occupying the same box, not a field that grows out of an icon.
 * The difference matters: search here replaces the whole bar, so the title and
 * the actions have somewhere to *go* -- the default layer lifts and shrinks
 * out while the pill rises in from below. A field expanding sideways out of an
 * icon leaves the title sitting next to it, fighting for the same row.
 *
 * Both halves move on the theme's spatial spring, so the exit and entrance
 * overlap rather than running in sequence; that overlap is what makes it read
 * as one bar changing rather than two bars swapping.
 *
 * Reusable by design -- this is the only search affordance in the app, so
 * every screen with a search gets the same motion and the same pill.
 */
@Composable
fun ExpressiveSearchTopBar(
    searchActive: Boolean,
    onSearchActiveChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    /** The title row shown when search is closed. */
    title: @Composable () -> Unit,
    /** Actions shown beside the title. The search trigger is added for you. */
    actions: @Composable () -> Unit = {},
    /** Extra control inside the pill, e.g. a filter button. */
    searchTrailing: @Composable (() -> Unit)? = null,
    height: androidx.compose.ui.unit.Dp = 72.dp
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchActive) {
        if (searchActive) {
            // Matches the reference's 120ms: focusing before the pill has
            // arrived makes the keyboard appear under a bar that is still
            // moving, which reads as two unrelated events.
            delay(120)
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        // --- Default layer: title + actions -------------------------------
        AnimatedVisibility(
            visible = !searchActive,
            modifier = Modifier.fillMaxSize(),
            // Exits upward: the bar gives way to the pill coming from below.
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { -it / 6 } +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.98f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { -it / 6 } +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.98f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.xl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) { title() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    actions()
                }
            }
        }

        // --- Search layer: the pill ---------------------------------------
        AnimatedVisibility(
            visible = searchActive,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 5 } +
                scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.96f),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 5 } +
                scaleOut(MaterialTheme.motionScheme.fastSpatialSpec(), targetScale = 0.96f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Space.lg),
                contentAlignment = Alignment.Center
            ) {
                SearchPill(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = placeholder,
                    focusRequester = focusRequester,
                    onClose = {
                        // Closing clears the query. A filter left applied
                        // behind a closed search hides the reason the list
                        // looks short.
                        onQueryChange("")
                        onSearchActiveChange(false)
                    },
                    trailing = searchTrailing
                )
            }
        }
    }
}

/**
 * The search field itself.
 *
 * Exposed separately so screens that are *only* a search (a picker sheet, say)
 * can use the same pill without the cross-fading bar around it.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailing: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }

    val container by animateColorAsState(
        if (focused) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pillContainer"
    )
    // A focus ring rather than a colour change alone: on a monochrome palette
    // the container step between states is subtle, and the ring is what makes
    // the focused state unambiguous.
    val border by animateColorAsState(
        if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pillBorder"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = CodeXCorners.pill,
        color = container,
        border = androidx.compose.foundation.BorderStroke(Sizes.hairline, border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Close search",
                    modifier = Modifier.size(Sizes.iconLarge)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = LocalTextStyle.current
                        .merge(MaterialTheme.typography.bodyLarge)
                        .copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { })
                )
            }

            // Clear appears only with text, so the pill is not carrying a dead
            // control the whole time it is empty.
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.7f),
                exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.7f)
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search text",
                        modifier = Modifier.size(Sizes.icon),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            trailing?.invoke()
        }
    }
}
