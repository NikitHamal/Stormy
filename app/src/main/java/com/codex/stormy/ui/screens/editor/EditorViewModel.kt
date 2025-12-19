package com.codex.stormy.ui.screens.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.codex.stormy.CodeXApplication
import com.codex.stormy.data.ai.AiModel
import com.codex.stormy.data.ai.AssistantMessageWithToolCalls
import com.codex.stormy.data.ai.ChatRequestMessage
import com.codex.stormy.data.ai.DeepInfraModels
import com.codex.stormy.data.ai.StreamEvent
import com.codex.stormy.data.ai.ToolCallResponse
import com.codex.stormy.data.repository.AiModelRepository
import com.codex.stormy.data.ai.context.ContextUsageLevel
import com.codex.stormy.data.ai.context.ContextWindowManager
import com.codex.stormy.data.ai.learning.UserPreferencesLearner
import com.codex.stormy.data.ai.tools.FileChangeType
import com.codex.stormy.data.ai.tools.MemoryStorage
import com.codex.stormy.data.ai.tools.StormyTools
import com.codex.stormy.data.ai.tools.TodoItem
import com.codex.stormy.data.ai.tools.ToolExecutor
import com.codex.stormy.data.ai.tools.ToolInteractionCallback
import com.codex.stormy.data.ai.undo.UndoRedoManager
import com.codex.stormy.data.ai.undo.UndoRedoState
import com.codex.stormy.data.local.entity.MessageStatus
import com.codex.stormy.data.repository.AiRepository
import com.codex.stormy.data.repository.ChatRepository
import com.codex.stormy.data.repository.PreferencesRepository
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.ChatMessage
import com.codex.stormy.domain.model.FileTreeNode
import com.codex.stormy.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val agentMode: Boolean = true,
    // AI Model selection
    val currentModel: AiModel = DeepInfraModels.defaultModel,
    // Context window management
    val contextTokenCount: Int = 0,
    val contextMaxTokens: Int = 8000,
    val contextUsageLevel: ContextUsageLevel = ContextUsageLevel.LOW,
    // Undo/Redo state
    val undoRedoState: UndoRedoState = UndoRedoState(),
    // Task planning
    val taskList: List<TodoItem> = emptyList()
)

class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val aiModelRepository: AiModelRepository,
    private val contextWindowManager: ContextWindowManager,
    private val userPreferencesLearner: UserPreferencesLearner,
    private val toolExecutor: ToolExecutor,
    private val memoryStorage: MemoryStorage,
    private val undoRedoManager: UndoRedoManager
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

    // Context window tracking
    private val _contextTokenCount = MutableStateFlow(0)
    private val _contextMaxTokens = MutableStateFlow(contextWindowManager.getAvailableTokens(_currentModel.value))
    private val _contextUsageLevel = MutableStateFlow(ContextUsageLevel.LOW)

    // Task planning state
    private val _taskList = MutableStateFlow<List<TodoItem>>(emptyList())

    // Agent loop state tracking
    private var _pendingToolCalls = mutableListOf<ToolCallResponse>()
    private var _shouldContinueAgentLoop = false
    private var _agentIterationCount = 0
    private var _taskCompleted = false

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
    }.combine(_currentModel) { state, currentModel ->
        state.copy(currentModel = currentModel)
    }.combine(preferencesRepository.lineNumbers) { state, lineNumbers ->
        state.copy(showLineNumbers = lineNumbers)
    }.combine(preferencesRepository.wordWrap) { state, wordWrap ->
        state.copy(wordWrap = wordWrap)
    }.combine(preferencesRepository.fontSize) { state, fontSize ->
        state.copy(fontSize = fontSize)
    }.combine(_contextTokenCount) { state, tokenCount ->
        state.copy(contextTokenCount = tokenCount)
    }.combine(_contextMaxTokens) { state, maxTokens ->
        state.copy(contextMaxTokens = maxTokens)
    }.combine(_contextUsageLevel) { state, usageLevel ->
        state.copy(contextUsageLevel = usageLevel)
    }.combine(undoRedoManager.state) { state, undoRedoState ->
        state.copy(undoRedoState = undoRedoState)
    }.combine(_taskList) { state, taskList ->
        state.copy(taskList = taskList)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )

    init {
        loadProject()
        loadChatHistory()
        setupToolInteractionCallback()
    }

    private fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true

            projectRepository.observeProjectById(projectId).collect { project ->
                _project.value = project
                if (project != null) {
                    loadFileTree()
                    // Load project's preferred model or fall back to global default
                    loadPreferredModel(project.preferredAiModelId)
                }
            }
        }
    }

    /**
     * Load the preferred AI model for this project
     * Falls back to global default if no project preference is set
     */
    private suspend fun loadPreferredModel(projectPreferredModelId: String?) {
        val modelId = projectPreferredModelId
            ?: preferencesRepository.defaultAiModel.first()

        // Find the model by ID from available models
        val model = aiModelRepository.getModelById(modelId)
        if (model != null) {
            _currentModel.value = model
            _contextMaxTokens.value = contextWindowManager.getAvailableTokens(model)
        }
    }

    /**
     * Set up tool interaction callback for undo/redo tracking and task planning
     */
    private fun setupToolInteractionCallback() {
        toolExecutor.interactionCallback = object : ToolInteractionCallback {
            override suspend fun askUser(question: String, options: List<String>?): String? {
                // For now, we'll display the question in the chat
                // A full implementation would show a dialog and wait for user input
                return null
            }

            override suspend fun onTaskFinished(summary: String) {
                // Clear task list when task is finished
                _taskList.value = emptyList()
            }

            override suspend fun onFileChanged(
                path: String,
                changeType: FileChangeType,
                oldContent: String?,
                newContent: String?
            ) {
                // Record the change for undo/redo
                undoRedoManager.recordChange(
                    path = path,
                    changeType = changeType,
                    oldContent = oldContent,
                    newContent = newContent
                )
            }

            override suspend fun onTodoCreated(todo: TodoItem) {
                // Add the new todo to the task list
                _taskList.value = _taskList.value + todo
            }

            override suspend fun onTodoUpdated(todo: TodoItem) {
                // Update the todo in the task list
                _taskList.value = _taskList.value.map {
                    if (it.id == todo.id) todo else it
                }
            }
        }
    }

    /**
     * Load persisted chat history from database
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            chatRepository.getMessagesForProject(projectId).collect { messages ->
                // Only update if we're not currently processing (to avoid overwriting streaming)
                if (!_isAiProcessing.value) {
                    _messages.value = messages

                    // Rebuild message history for AI context
                    rebuildMessageHistoryFromMessages(messages)
                }
            }
        }
    }

    /**
     * Rebuild the AI message history from persisted messages
     */
    private fun rebuildMessageHistoryFromMessages(messages: List<ChatMessage>) {
        _messageHistory.clear()
        for (message in messages) {
            when {
                message.isUser -> {
                    _messageHistory.add(aiRepository.createUserMessage(message.content))
                }
                message.isAssistant && message.status != MessageStatus.STREAMING -> {
                    _messageHistory.add(
                        ChatRequestMessage(
                            role = "assistant",
                            content = message.content
                        )
                    )
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
                        // Don't switch to CODE tab automatically - keep user on current tab
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

        // Reset agent loop state for new conversation turn
        _agentIterationCount = 0
        _taskCompleted = false
        _pendingToolCalls.clear()

        // Add to message history for AI context
        _messageHistory.add(aiRepository.createUserMessage(content))

        viewModelScope.launch {
            // Save user message to database
            chatRepository.saveMessage(userMessage)

            sendAiRequest()
        }
    }

    private suspend fun sendAiRequest() {
        val model = _currentModel.value
        val isAgentMode = _agentMode.value

        // Check iteration limit
        if (_agentIterationCount >= MAX_AGENT_ITERATIONS) {
            appendToLastAssistantMessage(
                "\n\n⚠️ Agent reached maximum iteration limit ($MAX_AGENT_ITERATIONS). Stopping to prevent infinite loop."
            )
            updateLastAssistantMessage(_streamingContent.value, MessageStatus.SENT)
            _isAiProcessing.value = false
            loadFileTree()
            return
        }

        _agentIterationCount++

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

        // Get file tree for context
        val fileTreeContext = if (isAgentMode) {
            buildFileTreeContext()
        } else ""

        // Get user preferences context
        val preferencesContext = userPreferencesLearner.getPreferencesContext(projectId)

        val systemMessage = aiRepository.createSystemMessage(
            projectContext = "Project: $projectName$fileTreeContext$currentFileContent$memoryContext$preferencesContext"
        )

        // Optimize message history if needed for context window
        val optimizedHistory = if (contextWindowManager.needsOptimization(_messageHistory, model)) {
            contextWindowManager.optimizeMessages(_messageHistory, model, systemMessage)
        } else {
            _messageHistory.toList()
        }

        val messagesWithSystem = listOf(systemMessage) + optimizedHistory

        // Update context window stats
        updateContextStats(messagesWithSystem)

        // Only create new assistant message if this is the first iteration
        if (_agentIterationCount == 1) {
            _streamingContent.value = ""
            val assistantMessage = ChatMessage.createAssistantMessage(
                projectId = projectId,
                content = "",
                status = MessageStatus.STREAMING
            )
            _messages.value = _messages.value + assistantMessage
        }

        // Get tools based on mode
        val tools = if (isAgentMode) StormyTools.getAllTools() else null

        // Track tool calls for this iteration
        var currentToolCalls = listOf<ToolCallResponse>()
        var hasToolCalls = false
        var finishedWithToolCalls = false

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
                        // Handle reasoning for thinking models - show in UI
                        _streamingContent.value += event.reasoning
                        updateLastAssistantMessage(_streamingContent.value, MessageStatus.STREAMING)
                    }
                    is StreamEvent.ToolCalls -> {
                        // Store tool calls for processing after stream completes
                        currentToolCalls = event.toolCalls
                        hasToolCalls = true
                    }
                    is StreamEvent.FinishReason -> {
                        // Track if finished due to tool calls
                        finishedWithToolCalls = event.reason == "tool_calls"
                    }
                    is StreamEvent.Error -> {
                        updateLastAssistantMessage(
                            _streamingContent.value + "\n\n❌ Error: ${event.message}",
                            MessageStatus.ERROR
                        )
                        _isAiProcessing.value = false
                    }
                    is StreamEvent.Completed -> {
                        // Handle completion based on whether we have tool calls
                        if (hasToolCalls && currentToolCalls.isNotEmpty()) {
                            // Process tool calls and continue the loop
                            val shouldContinue = handleToolCalls(currentToolCalls)

                            if (shouldContinue && _agentMode.value && !_taskCompleted) {
                                // Continue the agentic loop
                                sendAiRequest()
                            } else {
                                // Task completed or agent stopped
                                updateLastAssistantMessage(_streamingContent.value, MessageStatus.SENT)
                                _isAiProcessing.value = false
                                loadFileTree()
                            }
                        } else {
                            // No tool calls - conversation turn complete
                            val finalContent = _streamingContent.value
                            updateLastAssistantMessage(finalContent, MessageStatus.SENT)

                            // Add assistant response to history (without tool calls)
                            if (finalContent.isNotEmpty()) {
                                _messageHistory.add(
                                    ChatRequestMessage(
                                        role = "assistant",
                                        content = finalContent
                                    )
                                )
                            }

                            _isAiProcessing.value = false
                            loadFileTree()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            updateLastAssistantMessage(
                _streamingContent.value + "\n\n❌ Failed to connect to AI: ${e.message}",
                MessageStatus.ERROR
            )
            _isAiProcessing.value = false
        }
    }

    /**
     * Build a compact file tree context string for the AI
     */
    private fun buildFileTreeContext(): String {
        val tree = _fileTree.value
        if (tree.isEmpty()) return ""

        val sb = StringBuilder("\n\nProject file structure:\n")
        buildFileTreeString(tree, sb, 0)
        return sb.toString()
    }

    private fun buildFileTreeString(nodes: List<FileTreeNode>, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    sb.append("$indent- ${node.name}\n")
                }
                is FileTreeNode.FolderNode -> {
                    sb.append("$indent📁 ${node.name}/\n")
                    buildFileTreeString(node.children, sb, depth + 1)
                }
            }
        }
    }

    /**
     * Update context window statistics for UI display
     */
    private fun updateContextStats(messages: List<ChatRequestMessage>) {
        val model = _currentModel.value
        val currentTokens = contextWindowManager.estimateTotalTokens(messages)
        val maxTokens = contextWindowManager.getAvailableTokens(model)
        val usage = currentTokens.toFloat() / maxTokens

        _contextTokenCount.value = currentTokens
        _contextMaxTokens.value = maxTokens
        _contextUsageLevel.value = when {
            usage < 0.5f -> ContextUsageLevel.LOW
            usage < 0.75f -> ContextUsageLevel.MEDIUM
            usage < 0.9f -> ContextUsageLevel.HIGH
            else -> ContextUsageLevel.CRITICAL
        }
    }

    /**
     * Handle tool calls and return whether the agent should continue
     */
    private suspend fun handleToolCalls(toolCalls: List<ToolCallResponse>): Boolean {
        val toolResults = StringBuilder()
        var shouldContinue = true

        // First, add the assistant message with tool calls to history
        val currentContent = _streamingContent.value
        _messageHistory.add(
            AssistantMessageWithToolCalls(
                content = if (currentContent.isNotEmpty()) currentContent else null,
                toolCalls = toolCalls
            ).toChatRequestMessage()
        )

        // Begin a change group for undo/redo
        val toolNames = toolCalls.map { it.function.name }.distinct().joinToString(", ")
        undoRedoManager.beginChangeGroup("AI: $toolNames")

        for (toolCall in toolCalls) {
            val toolName = toolCall.function.name
            val result = toolExecutor.execute(projectId, toolCall)

            // Add visual feedback in the UI
            toolResults.append("\n\n🔧 **${formatToolName(toolName)}**\n")
            if (result.success) {
                val output = result.output.take(500) // Truncate long outputs in UI
                if (result.output.length > 500) {
                    toolResults.append("✅ ${output}...")
                } else {
                    toolResults.append("✅ $output")
                }
            } else {
                toolResults.append("❌ ${result.error}")
            }

            // Add tool result to message history for AI context
            _messageHistory.add(
                aiRepository.createToolResultMessage(
                    toolCallId = toolCall.id,
                    result = if (result.success) result.output else "Error: ${result.error}"
                )
            )

            // Check if this is a finish_task tool call
            if (toolName == "finish_task") {
                _taskCompleted = true
                shouldContinue = false
            }

            // Check if this is an ask_user tool call (requires user input)
            if (toolName == "ask_user") {
                shouldContinue = false
            }
        }

        // End the change group for undo/redo
        undoRedoManager.endChangeGroup()

        // Append tool results to streaming content
        _streamingContent.value += toolResults.toString()
        updateLastAssistantMessage(_streamingContent.value, MessageStatus.STREAMING)

        // Refresh file tree to reflect any changes
        loadFileTree()

        return shouldContinue
    }

    /**
     * Format tool name for display (convert snake_case to Title Case)
     */
    private fun formatToolName(name: String): String {
        return name.split("_").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    /**
     * Append text to the last assistant message
     */
    private fun appendToLastAssistantMessage(text: String) {
        _streamingContent.value += text
        updateLastAssistantMessage(_streamingContent.value, MessageStatus.STREAMING)
    }

    private fun updateLastAssistantMessage(content: String, status: MessageStatus) {
        val currentMessages = _messages.value.toMutableList()
        if (currentMessages.isNotEmpty()) {
            val lastMessage = currentMessages.last()
            if (!lastMessage.isUser) {
                val updatedMessage = lastMessage.copy(
                    content = content,
                    status = status
                )
                currentMessages[currentMessages.lastIndex] = updatedMessage
                _messages.value = currentMessages

                // Persist to database when message is complete (not streaming)
                if (status != MessageStatus.STREAMING) {
                    viewModelScope.launch {
                        chatRepository.saveMessage(updatedMessage)
                    }
                }
            }
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
        _messageHistory.clear()
        _streamingContent.value = ""

        // Clear persisted messages
        viewModelScope.launch {
            chatRepository.clearChatHistory(projectId)
        }
    }

    /**
     * Export chat history to markdown file
     */
    fun exportChatHistory(onResult: (Result<java.io.File>) -> Unit) {
        val projectName = _project.value?.name ?: "Unknown"
        viewModelScope.launch {
            val result = chatRepository.exportToMarkdown(projectId, projectName)
            onResult(result)
        }
    }

    fun setModel(model: AiModel) {
        _currentModel.value = model
        _contextMaxTokens.value = contextWindowManager.getAvailableTokens(model)
    }

    /**
     * Set the preferred AI model for this project
     * Also updates the current model
     */
    fun setProjectPreferredModel(model: AiModel) {
        _currentModel.value = model
        _contextMaxTokens.value = contextWindowManager.getAvailableTokens(model)

        viewModelScope.launch {
            projectRepository.setPreferredAiModel(projectId, model.id)
        }
    }

    /**
     * Clear the project's preferred model (use global default)
     */
    fun clearProjectPreferredModel() {
        viewModelScope.launch {
            projectRepository.setPreferredAiModel(projectId, null)
            // Reload with global default
            loadPreferredModel(null)
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Undo the last AI change
     */
    fun undo() {
        viewModelScope.launch {
            undoRedoManager.undo(projectId)
                .onSuccess { message ->
                    // Refresh file tree and current file
                    loadFileTree()
                    _currentFile.value?.let { file ->
                        projectRepository.readFile(projectId, file.path)
                            .onSuccess { content ->
                                _fileContent.value = content
                                _originalFileContent.value = content
                            }
                    }
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    /**
     * Redo the last undone AI change
     */
    fun redo() {
        viewModelScope.launch {
            undoRedoManager.redo(projectId)
                .onSuccess { message ->
                    // Refresh file tree and current file
                    loadFileTree()
                    _currentFile.value?.let { file ->
                        projectRepository.readFile(projectId, file.path)
                            .onSuccess { content ->
                                _fileContent.value = content
                                _originalFileContent.value = content
                            }
                    }
                }
                .onFailure { error ->
                    _error.value = error.message
                }
        }
    }

    /**
     * Clear undo/redo history
     */
    fun clearUndoHistory() {
        undoRedoManager.clearHistory()
    }

    companion object {
        private const val MAX_AGENT_ITERATIONS = 25 // Prevent infinite loops

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
                    chatRepository = application.chatRepository,
                    aiModelRepository = application.aiModelRepository,
                    contextWindowManager = application.contextWindowManager,
                    userPreferencesLearner = application.userPreferencesLearner,
                    toolExecutor = application.toolExecutor,
                    memoryStorage = application.memoryStorage,
                    undoRedoManager = application.undoRedoManager
                ) as T
            }
        }
    }
}
