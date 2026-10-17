package com.oro.app.ui.screens.skills

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oro.app.data.ai.skills.Skill
import com.oro.app.data.ai.skills.Skills
import com.oro.app.ui.components.expressive.FormSheet
import com.oro.app.ui.theme.OroCorners
import com.oro.app.ui.theme.Space
import java.io.File

/**
 * Settings > Skills: the playbooks Orobi can load. Built-ins ship with the
 * app; "Mine" are yours across projects; a project can carry its own in
 * `.oro/skills`. Each is a Markdown file you can share and paste in.
 */
@Composable
fun SkillsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dir = remember { File(context.filesDir, "skills") }
    var version by remember { mutableIntStateOf(0) }
    val skills = remember(version) { Skills.all(dir, null) }
    var editing by remember { mutableStateOf<Skill?>(null) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills", style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let { Skills.parse(it, Skill.Source.MINE) }?.let {
                            Skills.save(dir, it); version++
                        }
                    }) { Icon(Icons.Outlined.ContentPaste, "Paste a skill") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New skill") })
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item(key = "intro") {
                Text(
                    "Orobi sees each skill's name and description and loads the full playbook when a task matches. " +
                        "Share one as text; paste a shared skill with the clipboard button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Space.md)
                )
            }
            items(skills, key = { it.name }) { skill ->
                Surface(
                    onClick = { if (skill.source == Skill.Source.MINE) editing = skill },
                    shape = OroCorners.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(skill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                skill.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                (if (skill.source == Skill.Source.BUILT_IN) "Built in" else "Mine") +
                                    if (skill.files.isEmpty()) "" else " · ${skill.files.size} starter file" + if (skill.files.size == 1) "" else "s",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/markdown").putExtra(Intent.EXTRA_TEXT, skill.toMarkdown())
                            context.startActivity(Intent.createChooser(send, "Share ${skill.name}"))
                        }) { Icon(Icons.Outlined.Share, "Share") }
                        if (skill.source == Skill.Source.MINE) {
                            IconButton(onClick = { Skills.delete(dir, skill.name); version++ }) { Icon(Icons.Outlined.DeleteOutline, "Delete") }
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val base = editing
        var name by remember(base) { mutableStateOf(base?.name.orEmpty()) }
        var description by remember(base) { mutableStateOf(base?.description.orEmpty()) }
        var body by remember(base) { mutableStateOf(base?.body.orEmpty()) }
        FormSheet(
            title = if (base == null) "New skill" else "Edit skill",
            icon = Icons.Outlined.AutoAwesome,
            onDismiss = { creating = false; editing = null },
            confirmLabel = "Save",
            confirmEnabled = name.isNotBlank() && body.isNotBlank(),
            onConfirm = {
                base?.let { Skills.delete(dir, it.name) }
                Skills.save(dir, Skill(Skills.slug(name), description.trim(), body.trim(), Skill.Source.MINE, base?.files.orEmpty()))
                version++
                creating = false; editing = null
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true, shape = OroCorners.medium)
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("When to use it") }, singleLine = true, shape = OroCorners.medium)
                OutlinedTextField(
                    body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    label = { Text("Steps") }, shape = OroCorners.medium
                )
            }
        }
    }
}
