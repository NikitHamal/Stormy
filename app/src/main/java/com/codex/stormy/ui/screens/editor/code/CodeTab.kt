package com.codex.stormy.ui.screens.editor.code

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.stormy.R
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.ui.theme.CodeXTheme

@Composable
fun CodeTab(
    currentFile: FileTreeNode.FileNode?,
    fileContent: String,
    isModified: Boolean,
    lineNumbers: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val extendedColors = CodeXTheme.extendedColors

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var showReplace by remember { mutableStateOf(false) }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var searchMatches by remember { mutableStateOf<List<IntRange>>(emptyList()) }

    // Update search matches when query or content changes
    LaunchedEffect(searchQuery, fileContent) {
        searchMatches = if (searchQuery.isNotEmpty()) {
            findAllMatches(fileContent, searchQuery)
        } else {
            emptyList()
        }
        if (searchMatches.isNotEmpty() && currentMatchIndex >= searchMatches.size) {
            currentMatchIndex = 0
        }
    }

    if (currentFile == null) {
        EmptyEditorState(modifier = Modifier.fillMaxSize())
    } else {
        Scaffold(
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search FAB
                    SmallFloatingActionButton(
                        onClick = { showSearch = !showSearch },
                        containerColor = if (showSearch) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (showSearch) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search"
                        )
                    }

                    // Save FAB
                    if (isModified) {
                        SmallFloatingActionButton(
                            onClick = onSave,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Save,
                                contentDescription = context.getString(R.string.action_save)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Search bar
                AnimatedVisibility(
                    visible = showSearch,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    SearchReplaceBar(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        replaceText = replaceText,
                        onReplaceTextChange = { replaceText = it },
                        showReplace = showReplace,
                        onToggleReplace = { showReplace = !showReplace },
                        matchCount = searchMatches.size,
                        currentMatch = if (searchMatches.isNotEmpty()) currentMatchIndex + 1 else 0,
                        onPrevious = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.size - 1
                            }
                        },
                        onNext = {
                            if (searchMatches.isNotEmpty()) {
                                currentMatchIndex = (currentMatchIndex + 1) % searchMatches.size
                            }
                        },
                        onReplace = {
                            if (searchMatches.isNotEmpty() && currentMatchIndex < searchMatches.size) {
                                val match = searchMatches[currentMatchIndex]
                                val newContent = fileContent.replaceRange(match, replaceText)
                                onContentChange(newContent)
                            }
                        },
                        onReplaceAll = {
                            if (searchQuery.isNotEmpty()) {
                                val newContent = fileContent.replace(searchQuery, replaceText, ignoreCase = true)
                                onContentChange(newContent)
                            }
                        },
                        onClose = {
                            showSearch = false
                            searchQuery = ""
                            replaceText = ""
                        }
                    )
                }

                // Sora Code Editor
                SoraCodeEditorView(
                    content = fileContent,
                    onContentChange = onContentChange,
                    fileExtension = currentFile.extension,
                    fileName = currentFile.name,
                    filePath = currentFile.path,
                    showLineNumbers = lineNumbers,
                    wordWrap = wordWrap,
                    fontSize = fontSize,
                    searchQuery = searchQuery,
                    currentMatchIndex = currentMatchIndex,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(extendedColors.editorBackground)
                )
            }
        }
    }
}

/**
 * Find all occurrences of a search query in the text
 */
private fun findAllMatches(text: String, query: String): List<IntRange> {
    if (query.isEmpty()) return emptyList()

    val matches = mutableListOf<IntRange>()
    var startIndex = 0

    while (startIndex < text.length) {
        val index = text.indexOf(query, startIndex, ignoreCase = true)
        if (index == -1) break
        matches.add(index until (index + query.length))
        startIndex = index + 1
    }

    return matches
}

/**
 * Search and replace bar component
 */
@Composable
private fun SearchReplaceBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    showReplace: Boolean,
    onToggleReplace: () -> Unit,
    matchCount: Int,
    currentMatch: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Search row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Search input
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search...",
                                    style = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Match count
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = if (matchCount > 0) "$currentMatch/$matchCount" else "0 results",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                // Navigation buttons
                IconButton(
                    onClick = onPrevious,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "Previous",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onNext,
                    enabled = matchCount > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Next",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Toggle replace
                TextButton(
                    onClick = onToggleReplace
                ) {
                    Text(
                        text = if (showReplace) "−" else "+",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Replace row
            AnimatedVisibility(visible = showReplace) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BasicTextField(
                        value = replaceText,
                        onValueChange = onReplaceTextChange,
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (replaceText.isEmpty()) {
                                    Text(
                                        text = "Replace with...",
                                        style = TextStyle(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    TextButton(
                        onClick = onReplace,
                        enabled = matchCount > 0
                    ) {
                        Text("Replace")
                    }

                    TextButton(
                        onClick = onReplaceAll,
                        enabled = matchCount > 0
                    ) {
                        Text("All")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyEditorState(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Code,
            contentDescription = null,
            modifier = Modifier.padding(16.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )

        Text(
            text = context.getString(R.string.editor_no_file),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
