package com.codex.stormy.ui.screens.home

import com.codex.stormy.ui.theme.CodeXCorners
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Games
import androidx.compose.material.icons.outlined.Html
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.stormy.R
import com.codex.stormy.data.local.entity.ProjectTemplate
import com.codex.stormy.data.local.entity.TemplateCategory
import com.codex.stormy.ui.components.expressive.FilterChipItem
import com.codex.stormy.ui.components.expressive.FilterChipStrip
import com.codex.stormy.ui.components.expressive.FormSheet
import com.codex.stormy.ui.theme.Space

/**
 * Template option with icon for display
 */
/** Chip id for the unfiltered state; not a category, so it needs its own. */
private const val ALL_CATEGORIES = "__all__"

private data class TemplateOption(
    val template: ProjectTemplate,
    val icon: ImageVector
)

/**
 * Map each template to its appropriate icon
 */
private fun getTemplateIcon(template: ProjectTemplate): ImageVector = when (template) {
    ProjectTemplate.BLANK -> Icons.Outlined.Description
    ProjectTemplate.HTML_BASIC -> Icons.Outlined.Html
    ProjectTemplate.TAILWIND -> Icons.Outlined.Palette
    ProjectTemplate.LANDING_PAGE -> Icons.Outlined.Web
    ProjectTemplate.REACT -> Icons.Outlined.Code
    ProjectTemplate.VUE -> Icons.Outlined.Code
    ProjectTemplate.SVELTE -> Icons.Outlined.Code
    ProjectTemplate.NEXT_JS -> Icons.Outlined.Code
    ProjectTemplate.ANDROID_APP -> Icons.Outlined.Android
    ProjectTemplate.PWA -> Icons.Outlined.InstallMobile
    ProjectTemplate.PHASER -> Icons.Outlined.Games
    ProjectTemplate.THREE_JS -> Icons.Outlined.ViewInAr
    ProjectTemplate.EXPRESS_API -> Icons.Outlined.Api
    ProjectTemplate.PORTFOLIO -> Icons.Outlined.Person
    ProjectTemplate.BLOG -> Icons.Outlined.Article
    ProjectTemplate.DASHBOARD -> Icons.Outlined.Dashboard
}

/**
 * Get all templates as options
 */
private val allTemplateOptions = ProjectTemplate.entries.map { template ->
    TemplateOption(template, getTemplateIcon(template))
}

/**
 * Create Project Dialog with comprehensive template selection.
 * Shows templates grouped by category with filtering support.
 */
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, template: ProjectTemplate) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.HTML_BASIC) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<TemplateCategory?>(null) }

    val categoryChips = remember {
        buildList {
            add(FilterChipItem(ALL_CATEGORIES, "All"))
            TemplateCategory.entries.forEach { add(FilterChipItem(it.name, it.displayName)) }
        }
    }

    val filteredTemplates = remember(selectedCategory) {
        if (selectedCategory == null) {
            allTemplateOptions
        } else {
            allTemplateOptions.filter { it.template.category == selectedCategory }
        }
    }

    FormSheet(
        title = context.getString(R.string.create_project_title),
        icon = Icons.Outlined.RocketLaunch,
        onDismiss = onDismiss,
        confirmLabel = context.getString(R.string.action_create),
        onConfirm = {
            if (name.isBlank()) {
                nameError = context.getString(R.string.create_project_error_empty_name)
            } else {
                onConfirm(name.trim(), description.trim(), selectedTemplate)
            }
        },
        cancelLabel = context.getString(R.string.action_cancel)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Bounded, then scrollable: the template picker makes this the
                // tallest form in the app, and an unbounded sheet would push
                // its own action row off the bottom of the screen.
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = { Text(context.getString(R.string.create_project_name_label)) },
                placeholder = { Text(context.getString(R.string.create_project_name_hint)) },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = CodeXCorners.large
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(context.getString(R.string.create_project_description_label)) },
                placeholder = { Text(context.getString(R.string.create_project_description_hint)) },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                shape = CodeXCorners.large
            )

            Column {
                Text(
                    text = context.getString(R.string.create_project_template_label),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Space.sm))

                // One chip vocabulary app-wide; the stock FilterChip was the
                // second kind of chip the user called out.
                FilterChipStrip(
                    items = categoryChips,
                    selectedId = selectedCategory?.name ?: ALL_CATEGORIES,
                    onSelect = { id ->
                        selectedCategory = TemplateCategory.entries
                            .firstOrNull { it.name == id }
                    },
                    modifier = Modifier.padding(horizontal = 0.dp)
                )

                Spacer(modifier = Modifier.height(Space.md))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    contentPadding = PaddingValues(vertical = Space.xs)
                ) {
                    items(filteredTemplates, key = { it.template.name }) { option ->
                        TemplateCard(
                            template = option.template,
                            icon = option.icon,
                            isSelected = selectedTemplate == option.template,
                            onClick = { selectedTemplate = option.template }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Space.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CodeXCorners.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(Space.md)
                ) {
                    Column {
                        Text(
                            text = selectedTemplate.displayName,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Space.xxs))
                        Text(
                            text = selectedTemplate.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (selectedTemplate == ProjectTemplate.ANDROID_APP) {
                            Spacer(modifier = Modifier.height(Space.sm))
                            Text(
                                text = "Includes a GitHub Actions workflow for APK builds",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual template card with icon, name, and selection state
 */
@Composable
private fun TemplateCard(
    template: ProjectTemplate,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(90.dp)
            .clip(CodeXCorners.large)
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = CodeXCorners.large
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = template.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
