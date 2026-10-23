package com.oro.app.ui.components.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.ReasoningControls
import com.oro.app.ui.components.expressive.PickerOption
import com.oro.app.ui.components.expressive.SegmentedPicker

/**
 * The selected model's own reasoning control: its provider's levels, named
 * the way the provider names them (Instant…High on Mercury, None…XHigh on
 * GPT-5.5, Off/On for Qwen Chat's thinking). Nothing for a model without one.
 *
 * [levels] is the saved choice per model id; [onSelect] stores one.
 */
@Composable
fun ReasoningPicker(
    model: AiModel,
    levels: Map<String, String>,
    onSelect: (modelId: String, level: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val control = remember(model.provider, model.id) { model.reasoningControl } ?: return
    val options = remember(control) { control.levels.map { PickerOption(it.wire, it.label) } }
    SegmentedPicker(
        label = control.title,
        options = options,
        selected = ReasoningControls.chosen(control, model.id, levels) ?: control.initial,
        onSelect = { onSelect(model.id, it) },
        modifier = modifier
    )
}
