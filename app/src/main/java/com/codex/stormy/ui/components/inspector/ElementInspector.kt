package com.codex.stormy.ui.components.inspector

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * DEPRECATED: This file is kept for backward compatibility.
 *
 * The ElementInspector has been modularized into separate files:
 * - InspectorModels.kt - Data models (StyleChangeRequest, TextChangeRequest, etc.)
 * - InspectorUtils.kt - Utility functions (buildSelector, parseColor, etc.)
 * - InspectorSection.kt - Collapsible section component
 * - PropertyEditors.kt - Property editor components (ColorPropertyEditor, SliderPropertyEditor, etc.)
 * - VisualElementInspector.kt - Main inspector composable
 * - tabs/DesignTab.kt - Design tab (colors, borders, effects)
 * - tabs/LayoutTab.kt - Layout tab (size, spacing, display)
 * - tabs/TextTab.kt - Text tab (typography, content)
 * - tabs/ImageTab.kt - Image tab (source, accessibility)
 *
 * Import from the specific files above for new code.
 * This file provides a compatibility wrapper that delegates to the new modular implementation.
 */

/**
 * Backward-compatible wrapper for VisualElementInspector.
 * Delegates to the new modular implementation in VisualElementInspector.kt
 *
 * @see VisualElementInspector
 */
@Deprecated(
    message = "Import VisualElementInspector from VisualElementInspector.kt instead",
    replaceWith = ReplaceWith(
        "VisualElementInspector(element, onClose, onStyleChange, onTextChange, onAiEditRequest, onImageChange, onPickImage, enableAiFeatures, hasValidModel, modifier)",
        "com.codex.stormy.ui.components.inspector.VisualElementInspector"
    )
)
@Composable
fun ElementInspector(
    element: InspectorElementData,
    onClose: () -> Unit,
    onStyleChange: (StyleChangeRequest) -> Unit,
    onTextChange: (TextChangeRequest) -> Unit,
    onAiEditRequest: ((String) -> Unit)? = null,
    onImageChange: ((ImageChangeRequest) -> Unit)? = null,
    onPickImage: (() -> Unit)? = null,
    enableAiFeatures: Boolean = true,
    hasValidModel: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Delegate to the new modular implementation
    VisualElementInspector(
        element = element,
        onClose = onClose,
        onStyleChange = onStyleChange,
        onTextChange = onTextChange,
        onAiEditRequest = onAiEditRequest,
        onImageChange = onImageChange,
        onPickImage = onPickImage,
        enableAiFeatures = enableAiFeatures,
        hasValidModel = hasValidModel,
        modifier = modifier
    )
}
