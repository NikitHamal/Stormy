package com.codex.stormy.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.ai.StreamEvent
import com.codex.stormy.data.ai.ToolCallResponse
import com.codex.stormy.data.ai.tools.MemoryStorage
import com.codex.stormy.data.ai.tools.StormyTools
import com.codex.stormy.data.ai.tools.ToolExecutor
import com.codex.stormy.data.local.entity.MessageStatus
import com.codex.stormy.data.repository.AiRepository
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

/**
 * Represents an open file tab in the editor
 */
data class OpenFileTab(
    val path: String,
    val name: String,
    val extension: String,
    val isModified: Boolean = false,
    val content: String = ""
)

data class EditorUiState(
    val project: Project? = null,
    val selectedTab: EditorTab = EditorTab.CHAT,
    val fileTree: List<FileTreeNode> = emptyList(),
    val expandedFolders: Set<String> = emptySet(),
    val openFiles: List<OpenFileTab> = emptyList(),
    val currentFileIndex: Int = -1,
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
    val error: String? = null,
    val agentMode: Boolean = true
)

class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiRepository: AiRepository,
    private val toolExecutor: ToolExecutor,
    private val memoryStorage: MemoryStorage
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _project = MutableStateFlow<Project?>(null)
    private val _selectedTab = MutableStateFlow(EditorTab.CHAT)
    private val _fileTree = MutableStateFlow<List<FileTreeNode>>(emptyList())
    private val _expandedFolders = MutableStateFlow<Set<String>>(emptySet())
    private val _openFiles = MutableStateFlow<List<OpenFileTab>>(emptyList())
    private val _currentFileIndex = MutableStateFlow(-1)
    private val _currentFile = MutableStateFlow<FileTreeNode.FileNode?>(null)
    private val _fileContent = MutableStateFlow("")
    private val _originalFileContent = MutableStateFlow("")
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _chatInput = MutableStateFlow("")
    private val _isAiProcessing = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _agentMode = MutableStateFlow(true)
    private val _currentModel = MutableStateFlow(DeepInfraModels.QWEN_2_5_CODER_32B)
    private val _messageHistory = mutableListOf<ChatRequestMessage>()
    private val _streamingContent = MutableStateFlow("")

    val uiState: StateFlow<EditorUiState> = combine(
        _project,
        _selectedTab,
        _fileTree,
        _expandedFolders,
        _openFiles,
        _currentFileIndex,
        _currentFile,
        _fileContent,
        _messages
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        EditorUiState(
            project = values[0] as Project?,
            selectedTab = values[1] as EditorTab,
            fileTree = values[2] as List<FileTreeNode>,
            expandedFolders = values[3] as Set<String>,
            openFiles = values[4] as List<OpenFileTab>,
            currentFileIndex = values[5] as Int,
            currentFile = values[6] as FileTreeNode.FileNode?,
            fileContent = values[7] as String,
            isFileModified = values[7] as String != _originalFileContent.value,
            messages = values[8] as List<ChatMessage>
        )
    }.combine(_chatInput) { state, chatInput ->
        state.copy(chatInput = chatInput)
    }.combine(_isAiProcessing) { state, isProcessing ->
        state.copy(isAiProcessing = isProcessing)
    }.combine(_agentMode) { state, agentMode ->
        state.copy(agentMode = agentMode)
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

            // Check if file is already open
            val existingIndex = _openFiles.value.indexOfFirst { it.path == relativePath }
            if (existingIndex >= 0) {
                // Switch to existing tab
                switchToFileTab(existingIndex)
                return@launch
            }

            projectRepository.readFile(projectId, relativePath)
                .onSuccess { content ->
                    val fileNode = findFileNode(relativePath)
                    if (fileNode != null) {
                        // Add to open files
                        val newTab = OpenFileTab(
                            path = relativePath,
                            name = fileNode.name,
                            extension = fileNode.extension,
                            isModified = false,
                            content = content
                        )
                        val updatedOpenFiles = _openFiles.value + newTab
                        _openFiles.value = updatedOpenFiles
                        _currentFileIndex.value = updatedOpenFiles.size - 1

                        _fileContent.value = content
                        _originalFileContent.value = content
                        _currentFile.value = fileNode
                        _selectedTab.value = EditorTab.CODE
                    }
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    fun switchToFileTab(index: Int) {
        if (index < 0 || index >= _openFiles.value.size) return

        viewModelScope.launch {
            saveCurrentFileIfModified()

            val targetFile = _openFiles.value[index]
            _currentFileIndex.value = index
            _fileContent.value = targetFile.content
            _originalFileContent.value = if (targetFile.isModified) "" else targetFile.content
            _currentFile.value = findFileNode(targetFile.path)
        }
    }

    fun closeFileTab(index: Int) {
        if (index < 0 || index >= _openFiles.value.size) return

        viewModelScope.launch {
            val closingFile = _openFiles.value[index]

            // Save if modified before closing
            if (closingFile.isModified) {
                projectRepository.writeFile(projectId, closingFile.path, closingFile.content)
            }

            val updatedOpenFiles = _openFiles.value.toMutableList()
            updatedOpenFiles.removeAt(index)
            _openFiles.value = updatedOpenFiles

            // Adjust current index
            when {
                updatedOpenFiles.isEmpty() -> {
                    _currentFileIndex.value = -1
                    _currentFile.value = null
                    _fileContent.value = ""
                    _originalFileContent.value = ""
                }
                index >= updatedOpenFiles.size -> {
                    switchToFileTab(updatedOpenFiles.size - 1)
                }
                index == _currentFileIndex.value -> {
                    switchToFileTab(index.coerceAtMost(updatedOpenFiles.size - 1))
                }
                index < _currentFileIndex.value -> {
                    _currentFileIndex.value = _currentFileIndex.value - 1
                }
            }
        }
    }

    fun closeOtherTabs(keepIndex: Int) {
        if (keepIndex < 0 || keepIndex >= _openFiles.value.size) return

        viewModelScope.launch {
            val keepFile = _openFiles.value[keepIndex]

            // Save all modified files before closing
            _openFiles.value.forEachIndexed { index, file ->
                if (index != keepIndex && file.isModified) {
                    projectRepository.writeFile(projectId, file.path, file.content)
                }
            }

            _openFiles.value = listOf(keepFile)
            _currentFileIndex.value = 0
        }
    }

    fun closeAllTabs() {
        viewModelScope.launch {
            // Save all modified files
            _openFiles.value.forEach { file ->
                if (file.isModified) {
                    projectRepository.writeFile(projectId, file.path, file.content)
                }
            }

            _openFiles.value = emptyList()
            _currentFileIndex.value = -1
            _currentFile.value = null
            _fileContent.value = ""
            _originalFileContent.value = ""
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

        // Update the content in open files list
        val index = _currentFileIndex.value
        if (index >= 0 && index < _openFiles.value.size) {
            val isModified = content != _originalFileContent.value
            val updatedFiles = _openFiles.value.toMutableList()
            updatedFiles[index] = updatedFiles[index].copy(
                content = content,
                isModified = isModified
            )
            _openFiles.value = updatedFiles
        }
    }

    fun saveCurrentFile() {
        val currentFile = _currentFile.value ?: return
        val content = _fileContent.value

        viewModelScope.launch {
            projectRepository.writeFile(projectId, currentFile.path, content)
                .onSuccess {
                    _originalFileContent.value = content

                    // Update modified flag in open files
                    val index = _currentFileIndex.value
                    if (index >= 0 && index < _openFiles.value.size) {
                        val updatedFiles = _openFiles.value.toMutableList()
                        updatedFiles[index] = updatedFiles[index].copy(isModified = false)
                        _openFiles.value = updatedFiles
                    }
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

                        // Update modified flag in open files
                        val index = _currentFileIndex.value
                        if (index >= 0 && index < _openFiles.value.size) {
                            val updatedFiles = _openFiles.value.toMutableList()
                            updatedFiles[index] = updatedFiles[index].copy(isModified = false)
                            _openFiles.value = updatedFiles
                        }
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
                    // Close tab if open
                    val tabIndex = _openFiles.value.indexOfFirst { it.path == path }
                    if (tabIndex >= 0) {
                        val updatedFiles = _openFiles.value.toMutableList()
                        updatedFiles.removeAt(tabIndex)
                        _openFiles.value = updatedFiles

                        // Adjust current index
                        if (_currentFileIndex.value >= updatedFiles.size) {
                            _currentFileIndex.value = (updatedFiles.size - 1).coerceAtLeast(-1)
                        }
                        if (_currentFileIndex.value >= 0) {
                            switchToFileTab(_currentFileIndex.value)
                        } else {
                            _currentFile.value = null
                            _fileContent.value = ""
                            _originalFileContent.value = ""
                        }
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
                    // Update open tab if exists
                    val tabIndex = _openFiles.value.indexOfFirst { it.path == oldPath }
                    if (tabIndex >= 0) {
                        val updatedFiles = _openFiles.value.toMutableList()
                        val extension = newName.substringAfterLast(".", "")
                        updatedFiles[tabIndex] = updatedFiles[tabIndex].copy(
                            path = newPath,
                            name = newName,
                            extension = extension
                        )
                        _openFiles.value = updatedFiles

                        if (_currentFileIndex.value == tabIndex) {
                            _currentFile.value = findFileNode(newPath)
                        }
                    }

                    loadFileTree()
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    fun toggleAgentMode() {
        _agentMode.value = !_agentMode.value
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

        // Add to message history for AI context
        _messageHistory.add(aiRepository.createUserMessage(content))

        viewModelScope.launch {
            sendAiRequest()
        }
    }

    private suspend fun sendAiRequest() {
        val model = _currentModel.value
        val isAgentMode = _agentMode.value

        // Build system message with project context and memories
        val projectName = _project.value?.name ?: "Unknown Project"
        val currentFileName = _currentFile.value?.name ?: ""
        val currentFileContent = if (_fileContent.value.isNotEmpty()) {
            "\n\nCurrently open file ($currentFileName):\n```\n${_fileContent.value}\n```"
        } else ""

        // Get memory context if in agent mode
        val memoryContext = if (isAgentMode) {
            memoryStorage.getContextString(projectId)
        } else ""

        val systemMessage = aiRepository.createSystemMessage(
            projectContext = "Project: $projectName$currentFileContent$memoryContext"
        )

        val messagesWithSystem = listOf(systemMessage) + _messageHistory

        _streamingContent.value = ""

        // Create placeholder assistant message for streaming
        val assistantMessage = ChatMessage.createAssistantMessage(
            projectId = projectId,
            content = "",
            status = MessageStatus.STREAMING
        )
        _messages.value = _messages.value + assistantMessage

        // Get tools based on mode
        val tools = if (isAgentMode) StormyTools.getAllTools() else null

        try {
            aiRepository.streamChat(
                model = model,
                messages = messagesWithSystem,
                tools = tools,
                temperature = 0.7f
            ).collect { event ->
                when (event) {
                    is StreamEvent.Started -> {
                        // Streaming started
                    }
                    is StreamEvent.ContentDelta -> {
                        _streamingContent.value += event.content
                        updateLastAssistantMessage(_streamingContent.value, MessageStatus.STREAMING)
                    }
                    is StreamEvent.ReasoningDelta -> {
                        // Handle reasoning for thinking models if needed
                    }
                    is StreamEvent.ToolCalls -> {
                        // Handle tool calls for agent mode
                        handleToolCalls(event.toolCalls)
                    }
                    is StreamEvent.Error -> {
                        updateLastAssistantMessage(
                            "Error: ${event.message}",
                            MessageStatus.ERROR
                        )
                        _isAiProcessing.value = false
                    }
                    is StreamEvent.Completed -> {
                        val finalContent = _streamingContent.value
                        updateLastAssistantMessage(finalContent, MessageStatus.SENT)

                        // Add assistant response to history
                        _messageHistory.add(
                            ChatRequestMessage(
                                role = "assistant",
                                content = finalContent
                            )
                        )

                        _isAiProcessing.value = false

                        // Refresh file tree after tool execution
                        loadFileTree()
                    }
                }
            }
        } catch (e: Exception) {
            updateLastAssistantMessage(
                "Failed to connect to AI: ${e.message}",
                MessageStatus.ERROR
            )
            _isAiProcessing.value = false
        }
    }

    private suspend fun handleToolCalls(toolCalls: List<ToolCallResponse>) {
        val toolResults = StringBuilder()

        for (toolCall in toolCalls) {
            val result = toolExecutor.execute(projectId, toolCall)

            // Add tool result to message
            toolResults.append("\n\n**Tool: ${toolCall.function.name}**\n")
            if (result.success) {
                toolResults.append("✅ ${result.output}")
            } else {
                toolResults.append("❌ ${result.error}")
            }

            // Add tool result to message history for context
            _messageHistory.add(
                aiRepository.createToolResultMessage(
                    toolCallId = toolCall.id,
                    result = if (result.success) result.output else "Error: ${result.error}"
                )
            )
        }

        // Append tool results to current streaming content
        _streamingContent.value += toolResults.toString()
        updateLastAssistantMessage(_streamingContent.value, MessageStatus.STREAMING)
    }

    private fun updateLastAssistantMessage(content: String, status: MessageStatus) {
        val currentMessages = _messages.value.toMutableList()
        if (currentMessages.isNotEmpty()) {
            val lastMessage = currentMessages.last()
            if (!lastMessage.isUser) {
                currentMessages[currentMessages.lastIndex] = lastMessage.copy(
                    content = content,
                    status = status
                )
                _messages.value = currentMessages
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _messageHistory.clear()
        _streamingContent.value = ""
    }

    fun setModel(model: AiModel) {
        _currentModel.value = model
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
                    preferencesRepository = application.preferencesRepository,
                    aiRepository = application.aiRepository,
                    toolExecutor = application.toolExecutor,
                    memoryStorage = application.memoryStorage
                ) as T
            }
        }
    }
}
