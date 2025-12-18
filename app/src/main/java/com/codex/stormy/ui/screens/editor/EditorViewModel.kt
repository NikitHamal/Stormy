package com.codex.stormy.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.local.entity.MessageStatus
import com.codex.stormy.data.repository.PreferencesRepository
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.ChatMessage
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorUiState(
    val project: Project? = null,
    val selectedTab: EditorTab = EditorTab.CHAT,
    val fileTree: List<FileTreeNode> = emptyList(),
    val expandedFolders: Set<String> = emptySet(),
    val currentFile: FileTreeNode.FileNode? = null,
    val fileContent: String = "",
    val isFileModified: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val chatInput: String = "",
    val isAiProcessing: Boolean = false,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val fontSize: Float = 14f,
    val isLoading: Boolean = false,
    val error: String? = null
)

class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _project = MutableStateFlow<Project?>(null)
    private val _selectedTab = MutableStateFlow(EditorTab.CHAT)
    private val _fileTree = MutableStateFlow<List<FileTreeNode>>(emptyList())
    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _currentFile = MutableStateFlow<FileTreeNode.FileNode?>(null)
    private val _fileContent = MutableStateFlow("")
    private val _originalFileContent = MutableStateFlow("")
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _chatInput = MutableStateFlow("")
    private val _isAiProcessing = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EditorUiState> = combine(
        _project,
        _selectedTab,
        _fileTree,
        _expandedFolders,
        _currentFile,
        _fileContent,
        _messages,
        _chatInput,
        _isAiProcessing
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        EditorUiState(
            project = values[0] as Project?,
            selectedTab = values[1] as EditorTab,
            fileTree = values[2] as List<FileTreeNode>,
            expandedFolders = values[3] as Set<String>,
            currentFile = values[4] as FileTreeNode.FileNode?,
            fileContent = values[5] as String,
            isFileModified = values[5] as String != _originalFileContent.value,
            messages = values[6] as List<ChatMessage>,
            chatInput = values[7] as String,
            isAiProcessing = values[8] as Boolean
        )
    }.combine(preferencesRepository.lineNumbers) { state, lineNumbers ->
        state.copy(showLineNumbers = lineNumbers)
    }.combine(preferencesRepository.wordWrap) { state, wordWrap ->
        state.copy(wordWrap = wordWrap)
    }.combine(preferencesRepository.fontSize) { state, fontSize ->
        state.copy(fontSize = fontSize)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true

            projectRepository.observeProjectById(projectId).collect { project ->
                _project.value = project
                if (project != null) {
                    loadFileTree()
                }
            }
        }
    }

    private suspend fun loadFileTree() {
        val tree = projectRepository.getFileTree(projectId)
        _fileTree.value = tree

        if (_currentFile.value == null && tree.isNotEmpty()) {
            findFirstFile(tree)?.let { file ->
                openFile(file.path)
            }
        }
    }

    private fun findFirstFile(nodes: List<FileTreeNode>): FileTreeNode.FileNode? {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> return node
                is FileTreeNode.FolderNode -> {
                    findFirstFile(node.children)?.let { return it }
                }
            }
        }
        return null
    }

    fun selectTab(tab: EditorTab) {
        _selectedTab.value = tab
    }

    fun toggleFolder(folderPath: String) {
        _expandedFolders.value = if (folderPath in _expandedFolders.value) {
            _expandedFolders.value - folderPath
        } else {
            _expandedFolders.value + folderPath
        }
    }

    fun openFile(relativePath: String) {
        viewModelScope.launch {
            saveCurrentFileIfModified()

            projectRepository.readFile(projectId, relativePath)
                .onSuccess { content ->
                    _fileContent.value = content
                    _originalFileContent.value = content
                    _currentFile.value = findFileNode(relativePath)
                    _selectedTab.value = EditorTab.CODE
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    private fun findFileNode(path: String): FileTreeNode.FileNode? {
        return findFileNodeInTree(_fileTree.value, path)
    }

    private fun findFileNodeInTree(nodes: List<FileTreeNode>, path: String): FileTreeNode.FileNode? {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (node.path == path) return node
                }
                is FileTreeNode.FolderNode -> {
                    findFileNodeInTree(node.children, path)?.let { return it }
                }
            }
        }
        return null
    }

    fun updateFileContent(content: String) {
        _fileContent.value = content
    }

    fun saveCurrentFile() {
        val currentFile = _currentFile.value ?: return
        val content = _fileContent.value

        viewModelScope.launch {
            projectRepository.writeFile(projectId, currentFile.path, content)
                .onSuccess {
                    _originalFileContent.value = content
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    private suspend fun saveCurrentFileIfModified() {
        if (_fileContent.value != _originalFileContent.value) {
            _currentFile.value?.let { file ->
                projectRepository.writeFile(projectId, file.path, _fileContent.value)
                    .onSuccess {
                        _originalFileContent.value = _fileContent.value
                    }
            }
        }
    }

    fun createFile(parentPath: String, fileName: String) {
        viewModelScope.launch {
            val fullPath = if (parentPath.isEmpty()) fileName else "$parentPath/$fileName"
            projectRepository.createFile(projectId, fullPath)
                .onSuccess {
                    loadFileTree()
                    openFile(fullPath)
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    fun createFolder(parentPath: String, folderName: String) {
        viewModelScope.launch {
            val fullPath = if (parentPath.isEmpty()) folderName else "$parentPath/$folderName"
            projectRepository.createFolder(projectId, fullPath)
                .onSuccess {
                    loadFileTree()
                    _expandedFolders.value = _expandedFolders.value + fullPath
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch {
            projectRepository.deleteFile(projectId, path)
                .onSuccess {
                    if (_currentFile.value?.path == path) {
                        _currentFile.value = null
                        _fileContent.value = ""
                        _originalFileContent.value = ""
                    }
                    loadFileTree()
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    fun renameFile(oldPath: String, newName: String) {
        viewModelScope.launch {
            val parentPath = oldPath.substringBeforeLast("/", "")
            val newPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"

            projectRepository.renameFile(projectId, oldPath, newPath)
                .onSuccess {
                    if (_currentFile.value?.path == oldPath) {
                        openFile(newPath)
                    }
                    loadFileTree()
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    fun updateChatInput(input: String) {
        _chatInput.value = input
    }

    fun sendMessage() {
        val content = _chatInput.value.trim()
        if (content.isEmpty() || _isAiProcessing.value) return

        val userMessage = ChatMessage.createUserMessage(projectId, content)
        _messages.value = _messages.value + userMessage
        _chatInput.value = ""
        _isAiProcessing.value = true

        viewModelScope.launch {
            simulateAiResponse(content)
        }
    }

    private suspend fun simulateAiResponse(userMessage: String) {
        kotlinx.coroutines.delay(1500)

        val response = ChatMessage.createAssistantMessage(
            projectId = projectId,
            content = "I understand you want to: \"$userMessage\"\n\n" +
                "AI integration will be added in a future update. For now, you can:\n\n" +
                "1. Use the Editor tab to manually write code\n" +
                "2. Preview your changes with the preview button\n" +
                "3. Manage files through the file drawer\n\n" +
                "Stay tuned for Stormy AI features!"
        )

        _messages.value = _messages.value + response
        _isAiProcessing.value = false
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = CodeXApplication.getInstance()
                val savedStateHandle = extras.createSavedStateHandle()
                return EditorViewModel(
                    savedStateHandle = savedStateHandle,
                    projectRepository = application.projectRepository,
                    preferencesRepository = application.preferencesRepository
                ) as T
            }
        }
    }
}
