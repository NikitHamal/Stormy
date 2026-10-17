package com.oro.app.ui.screens.home

import com.oro.app.ui.theme.Space
import com.oro.app.ui.components.expressive.FormSheet
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.Icons
import com.oro.app.ui.theme.OroCorners
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.oro.app.R
import com.oro.app.domain.model.Project

/**
 * Confirm deleting a project.
 *
 * A sheet rather than an AlertDialog: a dialog floats centred, which puts a
 * destructive confirm button in the middle of the screen where a mis-tap is
 * easiest, while a sheet keeps it at the bottom edge next to Cancel.
 */
@Composable
internal fun DeleteProjectDialog(
    projectName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current

    FormSheet(
        title = context.getString(R.string.home_delete_project_title),
        icon = Icons.Outlined.DeleteOutline,
        destructive = true,
        confirmLabel = context.getString(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss
    ) {
        Text(
            text = context.getString(R.string.home_delete_project_message, projectName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Rename a project and edit its description.
 */
@Composable
internal fun EditProjectDialog(
    project: Project,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(project.name) }
    var description by remember { mutableStateOf(project.description) }
    val focusManager = LocalFocusManager.current

    FormSheet(
        title = "Rename project",
        icon = Icons.Outlined.DriveFileRenameOutline,
        confirmLabel = "Save",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name.trim(), description.trim()) },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = OroCorners.large
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                shape = OroCorners.large
            )
        }
    }
}
