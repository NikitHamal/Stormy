package com.codex.stormy.ui.components.expressive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.codex.stormy.ui.theme.CodeXCorners
import com.codex.stormy.ui.theme.ExpressiveShapes
import com.codex.stormy.ui.theme.Sizes
import com.codex.stormy.ui.theme.Space

/**
 * A search affordance that is an icon until it is needed.
 *
 * A permanently-mounted search field costs a full row of vertical space on
 * every visit, to serve a task the user performs rarely and only once a list
 * is long. Collapsing it to an icon returns that row to the content, which is
 * the scarcest thing on a phone.
 *
 * The expansion is a single continuous object, not a swap: the icon's own
 * container grows into the field, so the motion explains where the field came
 * from. The icon sits on a [MaterialShapes] silhouette so that the collapsed
 * state is still a deliberate mark rather than a bare glyph floating in the
 * app bar.
 */
@Composable
fun ExpandingSearchField(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    contentDescription: String = "Search"
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Focus and the keyboard follow the expansion rather than a tap on the
    // field: having opened it, the user has already said what they want.
    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    val container by animateColorAsState(
        if (expanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "searchContainer"
    )

    // The leading glyph rotates a quarter turn as it becomes a dismiss
    // affordance, so the same element reads as changing role rather than being
    // replaced by a different button.
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "searchIconRotation"
    )

    val interaction = remember { MutableInteractionSource() }

    Surface(
        onClick = {
            if (expanded) {
                // Collapsing clears the query: leaving a filter applied behind
                // a collapsed icon hides the reason the list looks short.
                onQueryChange("")
                onExpandedChange(false)
            } else {
                onExpandedChange(true)
            }
        },
        modifier = modifier
            .expressivePress(interaction)
            .then(if (expanded) Modifier.fillMaxWidth() else Modifier),
        // Round to pill: the collapsed mark carries a silhouette, the expanded
        // field is a plain pill so the text has a calm container.
        shape = if (expanded) CodeXCorners.pill else ExpressiveShapes.cookie9(),
        color = container,
        interactionSource = interaction
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (expanded) Space.lg else Space.sm,
                vertical = Space.sm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(Sizes.minTouchTarget - Space.lg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.Close else Icons.Outlined.Search,
                    contentDescription = if (expanded) "Close search" else contentDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(Sizes.icon)
                        .rotate(iconRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                // Grows horizontally out of the icon rather than fading in on
                // top of it, so the field visibly comes from the control that
                // was tapped.
                enter = expandHorizontally(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkHorizontally(
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
                ) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(Space.sm))
                    Box(Modifier.fillMaxWidth()) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.merge(
                                MaterialTheme.typography.bodyMedium
                            ).copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboard?.hide() }
                            )
                        )
                    }
                }
            }
        }
    }
}
