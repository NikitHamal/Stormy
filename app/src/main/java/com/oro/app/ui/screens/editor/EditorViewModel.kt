package com.oro.app.ui.screens.editor

import com.oro.app.data.ai.context.ContextCompactor
import com.oro.app.data.ai.context.ContextBreakdownBuilder
import com.oro.app.data.ai.context.ContextBreakdown
import com.oro.app.data.ai.ReasoningEffort
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import com.oro.app.ui.components.agent.PendingApproval
import kotlinx.serialization.json.jsonPrimitive
import com.oro.app.ui.components.agent.ToolPresentations
import com.oro.app.data.ai.tools.ToolResult
import com.oro.app.data.ai.agents.ToolCapability
import com.oro.app.data.ai.agents.AgentStep
import com.oro.app.data.ai.agents.AgentPhase
import com.oro.app.data.ai.agents.AgentSession
import com.oro.app.data.ai.agents.SubagentOrchestrator
import com.oro.app.data.ai.agents.DelegationTools
import com.oro.app.data.ai.agents.DelegationHandler
import androidx.compose.runtime.Immutable
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AssistantMessageWithToolCalls
import com.oro.app.data.ai.ChatRequestMessage
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.context.ContextUsageLevel
import com.oro.app.data.ai.context.ContextWindowManager
import com.oro.app.data.ai.learning.UserPreferencesLearner
import com.oro.app.data.ai.memory.SemanticMemorySystem
import com.oro.app.data.ai.tools.FileChangeType
import com.oro.app.data.ai.tools.MemoryStorage
import com.oro.app.data.ai.tools.OroTools
import com.oro.app.data.ai.tools.TodoItem
import com.oro.app.data.ai.tools.ToolExecutor
import com.oro.app.data.ai.tools.ToolInteractionCallback
import com.oro.app.data.ai.undo.UndoRedoManager
import com.oro.app.data.ai.undo.UndoRedoState
import com.oro.app.data.local.entity.MessageStatus
import com.oro.app.data.repository.AiModelRepository
import com.oro.app.data.repository.AiRepository
import com.oro.app.data.repository.ChatRepository
import com.oro.app.data.repository.PreferencesRepository
import com.oro.app.data.repository.ProjectRepository
import com.oro.app.domain.model.ChatMessage
import com.oro.app.domain.model.FileTreeNode
import com.oro.app.domain.model.Project
import com.oro.app.utils.FileLoadingStrategy
import com.oro.app.utils.FileSizeThresholds
import com.oro.app.utils.FileUtils
import com.oro.app.utils.LargeFileHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.oro.app.data.ai.agent.AgentLoop
import com.oro.app.data.ai.agent.AgentLoopConfig
import com.oro.app.data.ai.agent.AgentLoopListener
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import com.oro.app.data.ai.agent.AgentRunResult
import com.oro.app.data.ai.agent.AgentStatus
import com.oro.app.data.ai.agent.AgentToolRunner
import com.oro.app.data.ai.agent.HistoryPruner
import com.oro.app.data.ai.agent.TokenEstimator
import com.oro.app.data.ai.tools.AgentTools
import com.oro.app.data.ai.tools.ToolAliases
import com.oro.app.data.ai.tools.QuestionOption
import com.oro.app.data.ai.tools.UserQuestion
import com.oro.app.data.ai.Tool
import com.oro.app.ui.components.agent.PendingQuestion
import com.oro.app.agent.AgentNotifications
import com.oro.app.agent.BackgroundRuns
import com.oro.app.ui.components.agent.RunError
import com.oro.app.ui.screens.editor.agent.RunTranscript
import com.oro.app.ui.screens.editor.sessions.ChatSessionsUiState
import com.oro.app.domain.model.DeletedSessionSnapshot
import com.oro.app.domain.model.SessionTitles
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Represents an open file tab in the editor
 */
data class OpenFileTab(
    val path: String,
    val name: String,
    val extension: String,
    val isModified: Boolean = false,
    val content: String = "",
    val fileSize: Long = 0,
    val isReadOnly: Boolean = false,
    val isLargeFile: Boolean = false,
    val loadProgress: Float = 1f
)

/**
 * Marked @Immutable because it holds List fields. Compose cannot prove a
 * kotlin.collections.List is immutable -- the interface permits a mutable
 * implementation -- so without this the whole state object counts as unstable
 * and every composable reading it recomposes on each emission, even when the
 * value it reads is unchanged. These lists are only ever replaced wholesale,
 * never mutated, so the annotation is true.
 */
@Immutable
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
    // AI Model selection - null means no model selected (show "Select Model")
    val currentModel: AiModel? = null,
    val availableModels: List<AiModel> = emptyList(),
    val isRefreshingModels: Boolean = false,
    // Context window management
    val contextTokenCount: Int = 0,
    val contextMaxTokens: Int = 8000,
    val contextUsageLevel: ContextUsageLevel = ContextUsageLevel.LOW,
    // Undo/Redo state
    val undoRedoState: UndoRedoState = UndoRedoState(),
    // Task planning
    val taskList: List<TodoItem> = emptyList(),
    // Large file handling
    val largeFileWarning: LargeFileWarning? = null,
    val fileLoadProgress: Float = 1f,
    val isCurrentFileReadOnly: Boolean = false,
    /**
     * The agent's published plan for the current request, or null when it has
     * not published one. Rendered above the conversation so that long
     * multi-step work stays legible while it happens.
     */
    val agentPlan: com.oro.app.data.ai.agents.AgentPlan? = null,
    /** Delegated work, live and recently finished. */
    val agentSessions: List<com.oro.app.data.ai.agents.AgentSession> = emptyList(),
    val reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
    /** Where the context window is going, for the context sheet. */
    val contextBreakdown: ContextBreakdown = ContextBreakdown.Empty,
    val isCompacting: Boolean = false,
    /** Percentage freed by the last compaction, for a one-line confirmation. */
    val lastCompactionSavedPercent: Int? = null,
    /** How many messages a compaction would fold into the summary. */
    val compactableMessageCount: Int = 0,
    /** What the running turn is doing; null when idle. */
    val runStatus: AgentStatus? = null,
    /** The agent's open ask_user question, while the run waits on it. */
    val pendingQuestion: PendingQuestion? = null,
    /** Why the last run stopped without finishing, with a retry affordance. */
    val runError: RunError? = null
)

/**
 * Warning shown when opening large files
 */
data class LargeFileWarning(
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val formattedSize: String,
    val lineCount: Long,
    val warningMessage: String,
    val estimatedLoadTime: String,
    val canEdit: Boolean,
    val suggestReadOnly: Boolean
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
    private val undoRedoManager: UndoRedoManager,
    private val semanticMemorySystem: SemanticMemorySystem
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    /**
     * Delegation state for this editor session.
     *
     * The orchestrator owns subagent lifecycle; the handler translates the
     * model's delegation tool calls into it. The runner below is what actually
     * drives a subagent -- it lives here because the model loop does.
     */
    private val subagentOrchestrator = SubagentOrchestrator(viewModelScope)

    /**
     * Approval the main conversation is blocked on, or null.
     *
     * Held as a CompletableDeferred rather than a callback so the tool call can
     * simply *suspend* until the user answers. The agent loop then needs no
     * resume path or saved continuation -- it is still sitting inside the tool
     * call where it asked.
     */
    private val _pendingApproval = MutableStateFlow<PendingApproval?>(null)
    val pendingApproval: StateFlow<PendingApproval?> = _pendingApproval.asStateFlow()

    private var approvalAnswer: CompletableDeferred<Boolean>? = null

    private val delegationHandler = DelegationHandler(
        orchestrator = subagentOrchestrator,
        runner = DelegationHandler.SubagentRunner { session -> launchSubagent(session) }
    )

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
    private val _currentModel = MutableStateFlow<AiModel?>(null)
    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    private val _isRefreshingModels = MutableStateFlow(false)
    private val _messageHistory = mutableListOf<ChatRequestMessage>()

    // Context window tracking
    private val _contextTokenCount = MutableStateFlow(0)
    private val _contextMaxTokens = MutableStateFlow(8000) // Default, will be updated when model is selected
    private val _contextUsageLevel = MutableStateFlow(ContextUsageLevel.LOW)
    private val _contextBreakdown = MutableStateFlow(ContextBreakdown.Empty)
    private val _isCompacting = MutableStateFlow(false)
    private val _lastCompactionSavedPercent = MutableStateFlow<Int?>(null)

    private val contextCompactor = ContextCompactor(contextWindowManager)
    private val breakdownBuilder = ContextBreakdownBuilder(contextWindowManager)

    // Task planning state
    private val _taskList = MutableStateFlow<List<TodoItem>>(emptyList())

    // Large file handling state
    private val _largeFileWarning = MutableStateFlow<LargeFileWarning?>(null)
    private val _fileLoadProgress = MutableStateFlow(1f)
    private val _isCurrentFileReadOnly = MutableStateFlow(false)

    // Agent run state
    private var _aiGenerationJob: Job? = null
    private val _runStatus = MutableStateFlow<AgentStatus?>(null)
    private val _pendingQuestion = MutableStateFlow<PendingQuestion?>(null)
    @Volatile private var questionAnswer: CompletableDeferred<List<String?>?>? = null
    private val _runError = MutableStateFlow<RunError?>(null)

    /** Transcript of the run in progress, rendered into the last assistant message. */
    @Volatile private var transcript: RunTranscript? = null
    @Volatile private var lastPushedRevision = -1L
    @Volatile private var lastPushAt = 0L
    private var pushJob: Job? = null

    /** System prompt and tools of the last request, for the context breakdown. */
    @Volatile private var lastSystemPrompt: String? = null
    @Volatile private var lastRequestTools: List<Tool>? = null

    /** Set when the provider rejected native tools; the run then uses text tool calls. */
    @Volatile private var forceTextTools = false

    // ---- Chat sessions ------------------------------------------------------
    //
    // A project has many chat sessions; exactly one is on screen. Everything
    // conversational -- _messages, _messageHistory (what the agent sees), the
    // run, clear and export -- is scoped to it.
    //
    // Switching is refused while a run is live rather than cancelling it or
    // letting it continue in the background. The run is bound to the screen,
    // not just to a session id: its streaming transcript is written into the
    // last on-screen message, and its ask_user question and approvals are
    // shown in this chat and suspend the run until answered. A background run
    // would block on a question nobody can see; a silent cancel would throw
    // away work the user did not ask to stop. So the switcher says a run is in
    // progress and offers Stop, and the user decides.

    /** The session on screen; null for a fresh chat not yet sent. */
    private val _currentSessionId = MutableStateFlow<String?>(null)

    /** User messages of the current chat whose run left a file checkpoint. */
    private val _checkpointMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val checkpointMessageIds: StateFlow<Set<String>> = _checkpointMessageIds

    private fun checkpoints(): com.oro.app.data.ai.checkpoint.Checkpoints? =
        _project.value?.rootPath?.let { com.oro.app.data.ai.checkpoint.Checkpoints(File(it)) }

    private fun refreshCheckpoints() {
        val sid = _currentSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            _checkpointMessageIds.value = if (sid == null) emptySet() else checkpoints()?.messageIds(sid).orEmpty()
        }
    }

    /** The user published the site; Orobi should know where it lives. */
    fun onDeployed(url: String) {
        if (_isAiProcessing.value) return
        _messageHistory += ChatRequestMessage(role = "user", content = "[The user published this project. It is live at $url]")
    }

    /** Put every file back to how it was before [messageId]'s run (and all later runs). */
    fun restoreCheckpoint(messageId: String) {
        val sid = _currentSessionId.value ?: return
        if (_isAiProcessing.value) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { checkpoints()?.restoreFrom(sid, messageId) }
            if (result != null && result.files.isNotEmpty()) {
                loadFileTree()
                // Refresh the open editor if it shows a restored file and holds no edits of the user's.
                val open = _currentFile.value
                val root = _project.value?.rootPath
                if (open != null && root != null && open.path in result.files &&
                    _fileContent.value == _originalFileContent.value
                ) {
                    val text = withContext(Dispatchers.IO) { File(root, open.path).takeIf { it.isFile }?.readText().orEmpty() }
                    _fileContent.value = text
                    _originalFileContent.value = text
                }
                val n = result.files.size
                _messageHistory += ChatRequestMessage(
                    role = "user",
                    content = "[The user restored $n file${if (n == 1) "" else "s"} to their state before an earlier " +
                        "message: ${result.files.take(12).joinToString(", ")}. Re-read files before editing them.]"
                )
            }
            refreshCheckpoints()
        }
    }

    // Token / cost meter, per chat session, persisted in the project.
    private var usageStore: com.oro.app.data.ai.usage.UsageStore? = null
    private val usageBySession = MutableStateFlow<Map<String, com.oro.app.data.ai.usage.SessionUsage>>(emptyMap())
    /** Usage of the chat on screen. */
    val sessionUsage: StateFlow<com.oro.app.data.ai.usage.SessionUsage> =
        combine(usageBySession, _currentSessionId) { all, id ->
            id?.let { all[it] } ?: com.oro.app.data.ai.usage.SessionUsage()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.oro.app.data.ai.usage.SessionUsage())
    private val _isSessionLoading = MutableStateFlow(true)
    /** False until the last-opened session has been restored at startup. */
    @Volatile private var sessionRestored = false
    /** Set on a switch so the context sheet is recomputed for the new history. */
    private var breakdownStale = false

    private class PendingUndo(val snapshot: DeletedSessionSnapshot, val wasCurrent: Boolean)
    private val _pendingUndo = MutableStateFlow<PendingUndo?>(null)
    private var undoExpiryJob: Job? = null

    /** Unsent input per session, so switching away does not lose a half-typed prompt. */
    private val inputDrafts = HashMap<String, String>()

    val sessionsState: StateFlow<ChatSessionsUiState> = combine(
        chatRepository.observeSessions(projectId),
        _currentSessionId,
        _isSessionLoading,
        _pendingUndo
    ) { sessions, currentId, loading, undo ->
        ChatSessionsUiState(
            sessions = sessions,
            currentSessionId = currentId,
            currentTitle = sessions.firstOrNull { it.id == currentId }?.title
                ?: SessionTitles.DEFAULT_TITLE,
            isLoadingSession = loading,
            undoTitle = undo?.snapshot?.title
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatSessionsUiState()
    )

    /**
     * Dirty flag, derived on its own: it used to be an O(file size) string
     * compare inside the state combine, so it ran on every streamed chat
     * token even though neither string had changed. Reference equality first
     * makes the common "nothing typed since load" case free.
     */
    private val _isFileModified = combine(_fileContent, _originalFileContent) { current, original ->
        current !== original && current != original
    }.distinctUntilChanged()

    /**
     * True while this screen's run is live, or a run this project started
     * from an earlier editor instance is still finishing in the background:
     * either way the composer must not start a second one.
     */
    private val _isProcessingAnywhere = combine(_isAiProcessing, BackgroundRuns.active) { own, runs ->
        own || (projectId in runs && _aiGenerationJob?.isActive != true)
    }.distinctUntilChanged()

    /** Recomputed only when the context breakdown changes, as before. */
    private val _compactableCount = _contextBreakdown.map {
        contextCompactor.partition(_messages.value).older.size
    }.distinctUntilChanged()

    /**
     * One combine over every source, building the state once per emission.
     *
     * This was a chain of ~30 `.combine` stages: every streamed chunk went
     * through each stage in turn -- a suspended coroutine hop and a full
     * EditorUiState copy per stage -- about thirty copies of a large object
     * per token on the main path. A single stage does it in one.
     */
    val uiState: StateFlow<EditorUiState> = combine(
        listOf<Flow<Any?>>(
            _project, _selectedTab, _fileTree, _expandedFolders, _openFiles, _currentFileIndex,
            _currentFile, _fileContent, _messages, _isFileModified, _chatInput, _isProcessingAnywhere,
            _agentMode, _currentModel, _availableModels, _isRefreshingModels,
            preferencesRepository.lineNumbers, preferencesRepository.wordWrap, preferencesRepository.fontSize,
            _contextTokenCount, _contextMaxTokens, _contextUsageLevel, delegationHandler.plan,
            subagentOrchestrator.sessions, preferencesRepository.reasoningEffort, _contextBreakdown,
            _compactableCount, _isCompacting, _lastCompactionSavedPercent, undoRedoManager.state,
            _taskList, _largeFileWarning, _fileLoadProgress, _isCurrentFileReadOnly, _runStatus,
            _pendingQuestion, _runError
        )
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        EditorUiState(
            project = v[0] as Project?,
            selectedTab = v[1] as EditorTab,
            fileTree = v[2] as List<FileTreeNode>,
            expandedFolders = v[3] as Set<String>,
            openFiles = v[4] as List<OpenFileTab>,
            currentFileIndex = v[5] as Int,
            currentFile = v[6] as FileTreeNode.FileNode?,
            fileContent = v[7] as String,
            messages = v[8] as List<ChatMessage>,
            isFileModified = v[9] as Boolean,
            chatInput = v[10] as String,
            isAiProcessing = v[11] as Boolean,
            agentMode = v[12] as Boolean,
            currentModel = v[13] as AiModel?,
            availableModels = v[14] as List<AiModel>,
            isRefreshingModels = v[15] as Boolean,
            showLineNumbers = v[16] as Boolean,
            wordWrap = v[17] as Boolean,
            fontSize = v[18] as Float,
            contextTokenCount = v[19] as Int,
            contextMaxTokens = v[20] as Int,
            contextUsageLevel = v[21] as ContextUsageLevel,
            // Null rather than an empty plan, so the UI can omit the strip.
            agentPlan = (v[22] as com.oro.app.data.ai.agents.AgentPlan).takeIf { !it.isEmpty },
            agentSessions = v[23] as List<com.oro.app.data.ai.agents.AgentSession>,
            reasoningEffort = v[24] as ReasoningEffort,
            contextBreakdown = v[25] as ContextBreakdown,
            compactableMessageCount = v[26] as Int,
            isCompacting = v[27] as Boolean,
            lastCompactionSavedPercent = v[28] as Int?,
            undoRedoState = v[29] as UndoRedoState,
            taskList = v[30] as List<TodoItem>,
            largeFileWarning = v[31] as LargeFileWarning?,
            fileLoadProgress = v[32] as Float,
            isCurrentFileReadOnly = v[33] as Boolean,
            runStatus = v[34] as AgentStatus?,
            pendingQuestion = v[35] as PendingQuestion?,
            runError = v[36] as RunError?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditorUiState()
    )

    // Debug logging state
    private val _debugLogsEnabled = MutableStateFlow(false)

    init {
        // Rewind markers follow the chat on screen (and appear once the project loads).
        viewModelScope.launch {
            combine(_currentSessionId, _project) { sid, project -> sid to project?.rootPath }
                .distinctUntilChanged()
                .collect { refreshCheckpoints() }
        }
        loadProject()
        loadChatHistory()
        loadAvailableModels()
        setupToolInteractionCallback()
        observeDebugLogsPreference()
    }

    private fun observeDebugLogsPreference() {
        viewModelScope.launch {
            preferencesRepository.debugLogsEnabled.collect { enabled ->
                _debugLogsEnabled.value = enabled
            }
        }
    }

    /**
     * Load available models from repository and resolve the initial model
     * Model resolution priority:
     * 1. Project's last used model (if exists)
     * 2. User's default model (if set)
     * 3. null (show "Select Model")
     */
    private fun loadAvailableModels() {
        viewModelScope.launch {
            // Observe enabled models from repository
            aiModelRepository.observeEnabledModels().collect { models ->
                _availableModels.value = models
                
                // Retry resolution if we have a project but no model yet
                // This handles the race condition where project loads before models
                val project = _project.value
                if (project != null && _currentModel.value == null) {
                    resolveInitialModel(project)
                }
            }
        }

        viewModelScope.launch {
            // Wait for project to load first
            _project.collect { project ->
                if (project != null && _currentModel.value == null) {
                    resolveInitialModel(project)
                }
            }
        }
    }

    /**
     * Resolve the initial model based on project and user preferences
     */
    private suspend fun resolveInitialModel(project: Project) {
        // Priority 1: Project's last used model
        val lastUsedModelId = project.lastUsedModelId
        if (!lastUsedModelId.isNullOrEmpty()) {
            val projectModel = aiModelRepository.getModelById(lastUsedModelId)
            if (projectModel != null) {
                _currentModel.value = projectModel
                updateContextMaxTokens(projectModel)
                return
            }
        }

        // Priority 2: User's global default model
        val defaultModelId = preferencesRepository.defaultModelId.first()
        if (defaultModelId.isNotEmpty()) {
            val defaultModel = aiModelRepository.getModelById(defaultModelId)
            if (defaultModel != null) {
                _currentModel.value = defaultModel
                updateContextMaxTokens(defaultModel)
                return
            }
        }

        // Priority 3: the keyless default if enabled, else the first enabled model
        val firstModel = _availableModels.value.firstOrNull { it.id == com.oro.app.data.ai.SeedModels.default.id }
            ?: _availableModels.value.firstOrNull()
        if (firstModel != null) {
            _currentModel.value = firstModel
            updateContextMaxTokens(firstModel)
            return
        }

        // Priority 4: null (no model selected - user must select one)
        _currentModel.value = null
    }

    /**
     * Update context max tokens when model changes
     */
    private fun updateContextMaxTokens(model: AiModel?) {
        if (model != null) {
            _contextMaxTokens.value = contextWindowManager.getAvailableTokens(model)
        }
    }

    private fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true

            projectRepository.observeProjectById(projectId).collect { project ->
                _project.value = project
                if (project != null) {
                    toolExecutor.setProjectPath(File(project.rootPath))
                    if (usageStore == null) {
                        val store = com.oro.app.data.ai.usage.UsageStore(File(project.rootPath))
                        usageStore = store
                        usageBySession.value = withContext(Dispatchers.IO) { store.load() }
                    }
                    loadFileTree()
                }
            }
        }
    }

    /**
     * Set up tool interaction callback for undo/redo tracking and task planning
     */
    private fun setupToolInteractionCallback() {
        toolExecutor.interactionCallback = object : ToolInteractionCallback {
            override suspend fun askUser(question: String, options: List<String>?): String? =
                awaitAnswers(
                    listOf(UserQuestion(question, options = options.orEmpty().map { QuestionOption(it) }))
                )?.firstOrNull()

            override suspend fun askQuestions(questions: List<UserQuestion>): List<String?>? =
                awaitAnswers(questions)

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
                // Keep the open editor in step with the agent's edits, unless
                // the user has unsaved changes of their own in it.
                if (path == _currentFile.value?.path &&
                    _fileContent.value == _originalFileContent.value &&
                    changeType != FileChangeType.MOVED && changeType != FileChangeType.RENAMED
                ) {
                    val fresh = if (changeType == FileChangeType.DELETED) "" else newContent
                    if (fresh != null) {
                        _fileContent.value = fresh
                        _originalFileContent.value = fresh
                    }
                }
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
     * Uses a simpler approach: database is always the source of truth when not streaming
     *
     * Restores the session the user was last in first, then follows whichever
     * session is current: a switch re-points the message Flow, so the
     * transcript and the agent's history are always those of one session.
     */
    private fun loadChatHistory() {
        viewModelScope.launch {
            val restored = runCatching { chatRepository.getLastOpenedSessionId(projectId) }.getOrNull()
            // compareAndSet: never override a choice made while this ran.
            _currentSessionId.compareAndSet(null, restored)
            sessionRestored = true

            _currentSessionId
                .flatMapLatest { sessionId ->
                    if (sessionId == null) {
                        flowOf(emptyList<ChatMessage>())
                    } else {
                        chatRepository.getMessagesForSession(sessionId)
                    }
                }
                .collect { dbMessages ->
                    _isSessionLoading.value = false
                    // Only update from database if we're not currently processing AI response
                    // During AI processing, the in-memory state is managed by streaming logic
                    if (!_isAiProcessing.value) {
                        // Database is the source of truth when not streaming
                        // Use distinctBy to prevent any duplicate IDs just in case
                        _messages.value = dbMessages.distinctBy { it.id }

                        // Rebuild message history for AI context
                        rebuildMessageHistoryFromMessages(_messages.value)
                        if (breakdownStale) {
                            breakdownStale = false
                            refreshContextBreakdown()
                        }
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
                    // Reasoning and tool-card markup are display-only; sending
                    // them back wastes context and teaches the model to write
                    // fake tool cards.
                    val text = RunTranscript.toHistoryText(message.content)
                    if (text.isNotBlank()) {
                        _messageHistory.add(ChatRequestMessage(role = "assistant", content = text))
                    }
                }
            }
        }
    }

    private suspend fun loadFileTree() {
        val tree = projectRepository.getFileTree(projectId)
        _fileTree.value = tree
        _isLoading.value = false
        // Don't auto-open first file - let user choose from file tree
        // Chat tab is the default and should remain visible
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

    /**
     * Public method to refresh the file tree
     * Called when external changes are made (e.g., asset manager adds/deletes files)
     */
    fun refreshFileTree() {
        viewModelScope.launch {
            loadFileTree()
        }
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

            val fileNode = findFileNode(relativePath)
            if (fileNode == null) {
                _error.value = "File not found: $relativePath"
                return@launch
            }

            // Check file size and determine loading strategy
            val project = _project.value ?: return@launch
            val file = File(project.rootPath, relativePath)
            val fileSize = file.length()
            val strategy = LargeFileHandler.determineStrategy(fileSize)

            when (strategy) {
                FileLoadingStrategy.UNSUPPORTED -> {
                    // File too large - show error
                    _error.value = "File is too large to open (${FileUtils.formatFileSize(fileSize)}). Maximum supported size is 10 MB."
                    return@launch
                }
                FileLoadingStrategy.READ_ONLY_PREVIEW -> {
                    // Show warning for very large files
                    val analysis = LargeFileHandler.analyzeFile(file)
                    _largeFileWarning.value = LargeFileWarning(
                        filePath = relativePath,
                        fileName = fileNode.name,
                        fileSize = fileSize,
                        formattedSize = analysis.sizeFormatted,
                        lineCount = analysis.lineCount,
                        warningMessage = analysis.warningMessage ?: "",
                        estimatedLoadTime = analysis.estimatedLoadTime,
                        canEdit = false,
                        suggestReadOnly = true
                    )
                    return@launch
                }
                FileLoadingStrategy.PAGINATED_LOAD -> {
                    // Show warning for large files
                    val analysis = LargeFileHandler.analyzeFile(file)
                    _largeFileWarning.value = LargeFileWarning(
                        filePath = relativePath,
                        fileName = fileNode.name,
                        fileSize = fileSize,
                        formattedSize = analysis.sizeFormatted,
                        lineCount = analysis.lineCount,
                        warningMessage = analysis.warningMessage ?: "",
                        estimatedLoadTime = analysis.estimatedLoadTime,
                        canEdit = true,
                        suggestReadOnly = false
                    )
                    return@launch
                }
                FileLoadingStrategy.CHUNKED_LOAD -> {
                    // Load with progress for medium-large files
                    openFileWithProgress(relativePath, fileNode, fileSize)
                }
                FileLoadingStrategy.FULL_LOAD -> {
                    // Normal loading for small files
                    openFileNormal(relativePath, fileNode, fileSize)
                }
            }
        }
    }

    /**
     * Open file with progress indicator for larger files
     */
    private suspend fun openFileWithProgress(
        relativePath: String,
        fileNode: FileTreeNode.FileNode,
        fileSize: Long
    ) {
        val project = _project.value ?: return
        val file = File(project.rootPath, relativePath)

        _fileLoadProgress.value = 0f

        LargeFileHandler.readFileWithProgress(file) { progress ->
            _fileLoadProgress.value = progress
        }.onSuccess { content ->
            val newTab = OpenFileTab(
                path = relativePath,
                name = fileNode.name,
                extension = fileNode.extension,
                isModified = false,
                content = content,
                fileSize = fileSize,
                isLargeFile = true
            )
            val updatedOpenFiles = _openFiles.value + newTab
            _openFiles.value = updatedOpenFiles
            _currentFileIndex.value = updatedOpenFiles.size - 1

            _fileContent.value = content
            _originalFileContent.value = content
            _currentFile.value = fileNode
            _fileLoadProgress.value = 1f
            _selectedTab.value = EditorTab.CODE
        }.onFailure { error ->
            _error.value = error.message
            _fileLoadProgress.value = 1f
        }
    }

    /**
     * Normal file loading for small files
     */
    private suspend fun openFileNormal(
        relativePath: String,
        fileNode: FileTreeNode.FileNode,
        fileSize: Long
    ) {
        projectRepository.readFile(projectId, relativePath)
            .onSuccess { content ->
                val newTab = OpenFileTab(
                    path = relativePath,
                    name = fileNode.name,
                    extension = fileNode.extension,
                    isModified = false,
                    content = content,
                    fileSize = fileSize
                )
                val updatedOpenFiles = _openFiles.value + newTab
                _openFiles.value = updatedOpenFiles
                _currentFileIndex.value = updatedOpenFiles.size - 1

                _fileContent.value = content
                _originalFileContent.value = content
                _currentFile.value = fileNode
                _selectedTab.value = EditorTab.CODE
            }
            .onFailure { error ->
                _error.value = error.message
            }
    }

    /**
     * Called when user confirms they want to open a large file
     */
    fun confirmOpenLargeFile(readOnly: Boolean = false) {
        val warning = _largeFileWarning.value ?: return
        _largeFileWarning.value = null

        viewModelScope.launch {
            val fileNode = findFileNode(warning.filePath) ?: return@launch

            if (readOnly) {
                // Open in read-only mode with preview
                openFileReadOnly(warning.filePath, fileNode, warning.fileSize)
            } else {
                // Open normally with progress
                openFileWithProgress(warning.filePath, fileNode, warning.fileSize)
            }
        }
    }

    /**
     * Open file in read-only mode (for very large files)
     */
    private suspend fun openFileReadOnly(
        relativePath: String,
        fileNode: FileTreeNode.FileNode,
        fileSize: Long
    ) {
        val project = _project.value ?: return
        val file = File(project.rootPath, relativePath)

        LargeFileHandler.readPreview(file).onSuccess { previewContent ->
            val newTab = OpenFileTab(
                path = relativePath,
                name = fileNode.name,
                extension = fileNode.extension,
                isModified = false,
                content = previewContent + "\n\n// ... (truncated - file too large to edit fully)",
                fileSize = fileSize,
                isReadOnly = true,
                isLargeFile = true
            )
            val updatedOpenFiles = _openFiles.value + newTab
            _openFiles.value = updatedOpenFiles
            _currentFileIndex.value = updatedOpenFiles.size - 1

            _fileContent.value = newTab.content
            _originalFileContent.value = newTab.content
            _currentFile.value = fileNode
            _isCurrentFileReadOnly.value = true
            _selectedTab.value = EditorTab.CODE
        }.onFailure { error ->
            _error.value = error.message
        }
    }

    /**
     * Dismiss large file warning without opening
     */
    fun dismissLargeFileWarning() {
        _largeFileWarning.value = null
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

    /**
     * Import a file from device storage into the project
     * @param fileUri URI of the file to import
     * @param targetPath Target folder path within the project (empty string for root)
     */
    fun importFile(fileUri: Uri, targetPath: String) {
        viewModelScope.launch {
            projectRepository.importFileToProject(projectId, fileUri, targetPath)
                .onSuccess { importedPath ->
                    loadFileTree()
                    // Optionally open the imported file
                    openFile(importedPath)
                }
                .onFailure { error ->
                    _error.value = error.message ?: "Failed to import file"
                }
        }
    }

    /**
     * Import a folder from device storage into the project
     * @param folderUri URI of the folder to import (document tree URI)
     * @param targetPath Target folder path within the project (empty string for root)
     */
    fun importFolder(folderUri: Uri, targetPath: String) {
        viewModelScope.launch {
            projectRepository.importFolderToProject(projectId, folderUri, targetPath)
                .onSuccess { filesImported ->
                    loadFileTree()
                }
                .onFailure { error ->
                    _error.value = error.message ?: "Failed to import folder"
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
        // Before the last session is restored there is no telling which
        // session this would belong to; that window is a few milliseconds.
        if (content.isEmpty() || _isAiProcessing.value || !sessionRestored) return
        if (projectId in BackgroundRuns.active.value) return

        // The first message of a fresh chat creates its session, titled from
        // that message, so an abandoned "new chat" never leaves an empty row.
        val existingSessionId = _currentSessionId.value
        val sessionId = existingSessionId ?: java.util.UUID.randomUUID().toString()
        val userMessage = ChatMessage.createUserMessage(projectId, content, sessionId = sessionId)
        _messages.value = _messages.value + userMessage
        _chatInput.value = ""
        inputDrafts.remove(existingSessionId ?: DRAFT_KEY)
        _messageHistory.add(aiRepository.createUserMessage(content))
        // A new request gets a new plan; the previous one is finished business.
        delegationHandler.resetPlan()

        // Persisted independently of the run, so the message is kept even if
        // the run never starts (no model selected).
        val persisted = viewModelScope.launch {
            try {
                if (existingSessionId == null) {
                    chatRepository.createSession(sessionId, projectId, content)
                }
                chatRepository.saveMessage(userMessage)
                // Only now does the draft become the session. Switching
                // earlier would point the message Flow at a session with no
                // rows yet and blank the message just shown.
                if (existingSessionId == null) {
                    _currentSessionId.compareAndSet(null, sessionId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("EditorViewModel", "Could not save message", e)
            }
        }

        startRun(sessionId) {
            persisted.join()
            if (_agentMode.value) {
                // Learning is best-effort and must never block or break a run.
                runCatching {
                    semanticMemorySystem.learnFromUserMessage(projectId, content, _currentFile.value?.path)
                }
            }
        }
    }

    /** Resume after a failed or limited run, from the history as it stands. */
    fun retryRun() {
        if (_isAiProcessing.value) return
        val sessionId = _currentSessionId.value ?: return
        if (_messageHistory.none { it.role == "user" }) return
        // After a run the history is rebuilt from the saved transcript and
        // ends with Orobi's partial reply; ask it to pick up from there.
        if (_messageHistory.lastOrNull()?.role != "user") {
            _messageHistory.add(
                aiRepository.createUserMessage(
                    "Continue from where you stopped. Check the current state of the files first if unsure."
                )
            )
        }
        startRun(sessionId)
    }

    fun dismissRunError() {
        _runError.value = null
    }

    /** The user answered the agent's ask_user questions (null = skipped). */
    fun answerQuestion(answers: List<String?>) {
        val pending = questionAnswer ?: return
        _pendingQuestion.value = null
        questionAnswer = null
        pending.complete(answers)
    }

    /** The user declined to answer; the agent proceeds on its own judgement. */
    fun skipQuestion() {
        val pending = questionAnswer ?: return
        _pendingQuestion.value = null
        questionAnswer = null
        pending.complete(null)
    }

    /**
     * Show the agent's question and suspend until the user answers.
     *
     * Previously the callback returned null immediately, the tool reported
     * success with "Waiting for user response...", and the run ended -- so the
     * user saw a green "Ask User" card and was never asked anything.
     */
    private suspend fun awaitAnswers(questions: List<UserQuestion>): List<String?>? {
        // One call at a time; a second concurrent ask is answered as skipped.
        if (questionAnswer != null) return null
        val deferred = CompletableDeferred<List<String?>?>()
        questionAnswer = deferred
        _pendingQuestion.value = PendingQuestion(
            id = java.util.UUID.randomUUID().toString(),
            questions = questions
        )
        AgentNotifications.update(
            com.oro.app.OroApplication.getInstance(), projectId,
            "Orobi has a question",
            questions.first().question + if (questions.size > 1) " (+${questions.size - 1} more)" else ""
        )
        return try {
            deferred.await()
        } finally {
            // Cancelled (Stop) or answered: either way the card goes.
            if (questionAnswer === deferred) questionAnswer = null
            _pendingQuestion.value = null
        }
    }

    /**
     * Stop the current run: cancels the stream, any running tool, and any
     * open question or approval.
     */
    fun stopGeneration() {
        if (!_isAiProcessing.value) {
            // A run this screen did not start (the editor was reopened while it ran).
            BackgroundRuns.cancel(projectId)
            return
        }
        _aiGenerationJob?.cancel()
        // The job's finally block records the stop in the transcript and
        // resets state; nothing else to do here.
    }

    /**
     * Start an agent (or chat) run on the current history.
     *
     * Runs off the main thread: the loop, stream parsing and transcript
     * rendering used to execute on Main, with a string concatenation per
     * token, which is what made long replies stutter.
     */
    private fun startRun(sessionId: String, beforeRun: suspend () -> Unit = {}) {
        val model = _currentModel.value
        if (model == null) {
            _runError.value = RunError("Select a model first (tap the model name below the input).", actionLabel = "Retry")
            return
        }
        _runError.value = null
        _isAiProcessing.value = true
        _runStatus.value = AgentStatus.Thinking
        forceTextTools = false

        val assistantMessage = ChatMessage.createAssistantMessage(
            projectId = projectId,
            content = "",
            status = MessageStatus.STREAMING,
            sessionId = sessionId
        )
        _messages.value = _messages.value + assistantMessage
        val run = RunTranscript()
        transcript = run
        lastPushedRevision = -1L

        // Application scope, not viewModelScope: leaving the editor must not
        // kill a run halfway through a multi-file edit. BackgroundRuns keeps a
        // foreground service up while it lives and notifies when it ends.
        val app = com.oro.app.OroApplication.getInstance()
        _aiGenerationJob = app.applicationScope.launch(Dispatchers.Default, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var finalStatus = MessageStatus.SENT
            var notice: Pair<String, String>? = null
            val projectName = _project.value?.name ?: "your project"
            try {
                beforeRun()
                HistoryPruner.closeDanglingToolCalls(_messageHistory)
                val result = runAgent(model, run)
                when (result) {
                    is AgentRunResult.Completed -> notice = "Orobi finished in $projectName" to
                        (result.finalText.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "The run is done.")
                    is AgentRunResult.Failed -> {
                        notice = "Orobi stopped in $projectName" to result.message.take(160)
                        finalStatus = MessageStatus.ERROR
                        if (run.render().isBlank()) run.note("⚠️ ${result.message}")
                        _runError.value = RunError(
                            message = result.message +
                                if (result.retryable) "" else "\nCheck the provider settings or pick another model, then retry.",
                            actionLabel = "Retry"
                        )
                    }
                    is AgentRunResult.IterationLimit -> {
                        run.note("_Paused after ${result.iterations} steps._")
                        _runError.value = RunError(
                            message = "Orobi paused after ${result.iterations} steps to check in. Continue to let it keep working.",
                            actionLabel = "Continue",
                            isNotice = true
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                run.closeThinking()
                run.note("_Stopped._")
                throw cancelled
            } catch (e: Exception) {
                finalStatus = MessageStatus.ERROR
                run.note("⚠️ ${e.message ?: "Something went wrong"}")
                _runError.value = RunError("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                withContext(NonCancellable) {
                    run.closeThinking()
                    pushJob?.cancel()
                    updateLastAssistantMessage(run.render(), finalStatus)
                    transcript = null
                    _runStatus.value = null
                    _pendingQuestion.value = null
                    questionAnswer?.cancel()
                    questionAnswer = null
                    _isAiProcessing.value = false
                    loadFileTree()
                    BackgroundRuns.end(app, projectId)
                    notice?.let { (title, text) -> AgentNotifications.update(app, projectId, title, text) }
                }
            }
        }
        _aiGenerationJob?.let { job ->
            BackgroundRuns.begin(app, BackgroundRuns.Run(projectId, sessionId, _project.value?.name ?: "your project", job))
            job.start()
        }
    }

    /** One agent run through [AgentLoop]; returns how it ended. */
    private suspend fun runAgent(model: AiModel, run: RunTranscript): AgentRunResult {
        val isAgentMode = _agentMode.value
        val nativeTools = model.supportsToolCalls
        // MCP servers' tools join the built-ins for this run (listing is cached).
        val mcp = if (isAgentMode) preferencesRepository.mcpSettings.first() else com.oro.app.data.ai.mcp.McpSettings()
        toolExecutor.mcpSettings = mcp
        val mcpTools = if (mcp.servers.any { it.enabled }) {
            runCatching {
                kotlinx.coroutines.withTimeout(15_000) { com.oro.app.data.ai.mcp.McpRegistry.tools(mcp) }
            }.getOrDefault(emptyList()).map { it.asTool() }
        } else emptyList()
        val offeredTools = if (isAgentMode) AgentTools.forMainAgent() + mcpTools else emptyList()
        val knownTools = offeredTools.map { it.function.name }.toSet()

        // Context that does not change during a run, computed once.
        val memoryContext = if (isAgentMode) memoryStorage.getContextString(projectId) else ""
        val semanticContext = if (isAgentMode) {
            runCatching {
                semanticMemorySystem.buildContextString(
                    projectId = projectId,
                    currentFiles = _currentFile.value?.path?.let { listOf(it) } ?: emptyList(),
                    queryTags = emptyList(),
                    maxTokens = 2000
                )
            }.getOrDefault("")
        } else ""
        val preferencesContext = userPreferencesLearner.getPreferencesContext(projectId)
        val instructions = _project.value?.rootPath?.let { root ->
            withContext(Dispatchers.IO) {
                com.oro.app.data.ai.context.ProjectInstructions.section(
                    com.oro.app.data.ai.context.ProjectInstructions.load(File(root))
                )
            }
        }.orEmpty() +
            // Errors the live preview hit since the last run: the loop that lets
            // Orobi fix what it broke without the user copying stack traces.
            // Taken once per run so the system prompt stays stable across turns.
            if (isAgentMode) com.oro.app.data.preview.PreviewDiagnostics.takeContextSummary(projectId) else ""

        val prepare: suspend (List<ChatRequestMessage>, Int) -> AgentRequest = { history, pressure ->
            val textMode = !nativeTools || forceTextTools
            val system = aiRepository.createSystemMessage(
                projectContext = buildProjectContext(isAgentMode, memoryContext, semanticContext, preferencesContext) + instructions,
                agentMode = isAgentMode,
                nativeTools = !textMode,
                toolList = offeredTools
            )
            val tools = if (isAgentMode && !textMode) offeredTools else null
            val window = contextWindowManager.getAvailableTokens(model)
            val fixed = TokenEstimator.estimate(system) + toolTokens(tools)
            val share = when (pressure) { 0 -> 0.9; 1 -> 0.6; else -> 0.35 }
            val budget = ((window * share).toInt() - fixed).coerceAtLeast(2_000)
            val pruned = HistoryPruner.prune(history, budget)
            val messages = listOf(system) + pruned
            lastSystemPrompt = system.content
            lastRequestTools = tools
            updateContextStats(messages)
            AgentRequest(messages, tools)
        }

        val pacingStatus = AgentStatus(AgentStatus.Kind.RETRYING, "Waiting for ${model.provider.displayName}'s rate limit")
        val onRateLimitWait: suspend () -> Unit = { _runStatus.value = pacingStatus }
        val client = object : AgentModelClient {
            override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
                val stream = aiRepository.streamChat(
                    model = model,
                    messages = request.messages,
                    tools = request.tools,
                    temperature = 0.5f,
                    onRateLimitWait = onRateLimitWait
                )
                return if (request.tools == null) stream else detectToolRejection(stream)
            }
        }

        val runner = AgentToolRunner { call -> runTool(call) }

        val listener = object : AgentLoopListener {
            override fun onStatus(status: AgentStatus) {
                _runStatus.value = status
            }
            override fun onModelCallStarted(iteration: Int, attempt: Int) {
                run.beginModelCall()
                pushTranscript(force = true)
            }
            override fun onReasoning(delta: String) {
                run.appendReasoning(delta)
                pushTranscript()
            }
            override fun onText(delta: String) {
                run.appendText(delta)
                pushTranscript()
            }
            override fun onAttemptDiscarded(reason: String) {
                run.discardCurrentCall()
                pushTranscript(force = true)
                logDebugToProject("RETRY", reason)
            }
            override fun onTurnComplete(visibleText: String, toolCalls: List<ToolCallResponse>) {
                run.setCurrentCallText(visibleText)
                pushTranscript(force = true)
            }
            override fun onToolStarted(call: ToolCallResponse) {
                run.toolStarted(call)
                pushTranscript(force = true)
            }
            override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                run.toolFinished(call, result)
                pushTranscript(force = true)
                if (ToolAliases.canonicalName(call.function.name) !in AgentTools.readOnly) {
                    viewModelScope.launch { loadFileTree() }
                }
            }
            override fun onUsage(promptTokens: Int, completionTokens: Int) {
                // The provider's own count beats the estimate when it gives one.
                if (promptTokens > 0) _contextTokenCount.value = promptTokens
            }

            override fun onUsage(promptTokens: Int, completionTokens: Int, cachedTokens: Int) {
                onUsage(promptTokens, completionTokens)
                val session = _currentSessionId.value ?: return
                usageBySession.update { all ->
                    all + (session to (all[session] ?: com.oro.app.data.ai.usage.SessionUsage())
                        .plus(model, promptTokens, completionTokens, cachedTokens))
                }
            }
        }

        logDebugToProject("RUN", "Model: ${model.id} (${model.provider.displayName}) agent=$isAgentMode nativeTools=$nativeTools")
        val loop = AgentLoop(
            client = client,
            tools = runner,
            config = AgentLoopConfig(maxIterations = MAX_AGENT_ITERATIONS)
        )
        toolExecutor.checkpoint = run {
            val sid = _currentSessionId.value
            val userMsg = _messages.value.lastOrNull { it.isUser }
            if (sid != null && userMsg != null) checkpoints()?.begin(sid, userMsg.id, userMsg.content) else null
        }
        val result = try {
            loop.run(_messageHistory, knownTools, listener, prepare)
        } finally {
            val recorder = toolExecutor.checkpoint
            toolExecutor.checkpoint = null
            if (recorder != null) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { runCatching { recorder.finish() } }
                refreshCheckpoints()
            }
            // Persist the meter once per run, even when it was stopped.
            usageStore?.let { store ->
                val snapshot = usageBySession.value
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { store.save(snapshot) }
            }
        }
        logDebugToProject("RUN_END", result.toString().take(500))
        refreshContextBreakdown()
        return result
    }

    /**
     * Some providers answer 400 when sent tools for a model they serve without
     * tool support. Switch the run to text tool calls and let the loop retry,
     * rather than failing the run over a capability the harness can emulate.
     */
    private fun detectToolRejection(stream: Flow<StreamEvent>): Flow<StreamEvent> = flow {
        stream.collect { event ->
            if (event is StreamEvent.Error && !forceTextTools && event.httpCode in setOf(400, 404, 422) &&
                Regex("tool|function", RegexOption.IGNORE_CASE).containsMatchIn(event.message)
            ) {
                forceTextTools = true
                emit(StreamEvent.Error("Model rejected native tools; switching to text tool calls", retryAfterMs = 500, retryable = true))
            } else {
                emit(event)
            }
        }
    }

    /** Execute one tool call for the main agent. */
    private suspend fun runTool(call: ToolCallResponse): ToolResult {
        val name = ToolAliases.canonicalName(call.function.name)
        return when {
            name == "request_approval" -> {
                val args = DelegationHandler.parseArgs(call.function.arguments)
                val action = args?.get("action")?.jsonPrimitive?.content
                if (action.isNullOrBlank()) {
                    ToolResult(false, "", "action is required")
                } else {
                    val detail = args["detail"]?.jsonPrimitive?.content
                    if (awaitApproval(action, detail)) {
                        ToolResult(true, "The user approved: $action")
                    } else {
                        ToolResult(
                            true,
                            "The user declined: $action. Do not attempt it, and do not ask again " +
                                "for the same action. Continue with what you can do, or explain what you cannot."
                        )
                    }
                }
            }
            name == "spawn_subagent" -> {
                val args = DelegationHandler.parseArgs(call.function.arguments)
                    ?: return ToolResult(false, "", "Could not parse the arguments for $name as JSON.")
                // Wait for the subagent and return its summary as the result.
                // The handler used to answer "it will report back" and nothing
                // ever did: the summary landed on a card the model never saw,
                // and the run often finished before the subagent did.
                val before = subagentOrchestrator.sessions.value.map { it.id }.toSet()
                val started = delegationHandler.execute(name, args)
                if (!started.success) return started
                val sessionId = subagentOrchestrator.sessions.value.firstOrNull { it.id !in before }?.id
                    ?: return started
                val done = subagentOrchestrator.sessions
                    .first { list -> list.none { it.id == sessionId } || list.first { it.id == sessionId }.let { !it.isLive } }
                    .firstOrNull { it.id == sessionId }
                when {
                    done == null -> ToolResult(false, "", "The subagent was removed before it finished.")
                    done.summary != null -> ToolResult(true, "Subagent \"${done.description}\" finished:\n${done.summary}")
                    done.error != null -> ToolResult(false, "", "Subagent \"${done.description}\" failed: ${done.error}")
                    else -> ToolResult(
                        false, "",
                        "Subagent \"${done.description}\" stopped (${done.phase.name.lowercase()}) without a summary. Do the work directly."
                    )
                }
            }
            delegationHandler.handles(name) -> {
                val args = DelegationHandler.parseArgs(call.function.arguments)
                    ?: return ToolResult(false, "", "Could not parse the arguments for $name as JSON.")
                delegationHandler.execute(name, args)
            }
            else -> {
                undoRedoManager.beginChangeGroup("AI: ${RunTranscript.titleFor(name)}")
                try {
                    toolExecutor.execute(projectId, call.copy(function = call.function.copy(name = name)))
                } finally {
                    undoRedoManager.endChangeGroup()
                }
            }
        }
    }

    private fun toolTokens(tools: List<Tool>?): Int = tools?.sumOf {
        TokenEstimator.estimate(it.function.name) + TokenEstimator.estimate(it.function.description) +
            TokenEstimator.estimate(it.function.parameters.toString())
    } ?: 0

    private fun buildProjectContext(
        isAgentMode: Boolean,
        memoryContext: String,
        semanticContext: String,
        preferencesContext: String
    ): String = buildString {
        append("Project: ").append(_project.value?.name ?: "Untitled")
        if (isAgentMode) append(buildFileTreeContext())
        val file = _currentFile.value
        val content = _fileContent.value
        if (file != null && content.isNotEmpty()) {
            append("\n\nThe user has ").append(file.path).append(" open in the editor")
            if (content.length <= OPEN_FILE_CONTEXT_CHARS) {
                append(":\n```\n").append(content).append("\n```")
            } else {
                append(" (").append(content.lines().size).append(" lines; read it with read_file if relevant).")
            }
        }
        append(memoryContext)
        append(semanticContext)
        append(preferencesContext)
    }

    /**
     * Push the transcript into the last assistant message, at most every
     * [STREAMING_UPDATE_INTERVAL_MS] unless [force]d (structural changes such
     * as a tool card appearing are shown at once).
     */
    private fun pushTranscript(force: Boolean = false) {
        val run = transcript ?: return
        if (run.revision == lastPushedRevision) return
        val now = System.currentTimeMillis()
        if (force || now - lastPushAt >= STREAMING_UPDATE_INTERVAL_MS) {
            pushJob?.cancel()
            lastPushAt = now
            lastPushedRevision = run.revision
            updateLastAssistantMessageDirect(run.render(), MessageStatus.STREAMING)
        } else if (pushJob?.isActive != true) {
            pushJob = viewModelScope.launch(Dispatchers.Default) {
                delay(STREAMING_UPDATE_INTERVAL_MS)
                transcript?.let {
                    lastPushAt = System.currentTimeMillis()
                    lastPushedRevision = it.revision
                    updateLastAssistantMessageDirect(it.render(), MessageStatus.STREAMING)
                }
            }
        }
    }

    /**
     * Build a compact file tree context string for the AI
     */
    private fun buildFileTreeContext(): String {
        val tree = _fileTree.value
        if (tree.isEmpty()) return "\n\nThe project is empty."

        val sb = StringBuilder("\n\nProject files:\n")
        buildFileTreeString(tree, sb, 0)
        return sb.toString().let { if (it.length > 6_000) it.take(6_000) + "\n… (use list_files for the rest)" else it }
    }

    private fun buildFileTreeString(nodes: List<FileTreeNode>, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        for (node in nodes) {
            if (!com.oro.app.data.ai.tools.AgentPathPolicy.isVisible(node.name)) continue
            when (node) {
                is FileTreeNode.FileNode -> sb.append("$indent${node.name}\n")
                is FileTreeNode.FolderNode -> {
                    sb.append("$indent${node.name}/\n")
                    if (node.name != "node_modules") buildFileTreeString(node.children, sb, depth + 1)
                }
            }
        }
    }

    /**
     * Update context window statistics for UI display
     */
    private fun updateContextStats(messages: List<ChatRequestMessage>) {
        val model = _currentModel.value ?: return
        val currentTokens = contextWindowManager.estimateTotalTokens(messages) + toolTokens(lastRequestTools)
        val maxTokens = contextWindowManager.getAvailableTokens(model)
        val usage = currentTokens.toFloat() / maxTokens

        _contextTokenCount.value = currentTokens
        _contextMaxTokens.value = maxTokens
        refreshContextBreakdown()
        _contextUsageLevel.value = when {
            usage < 0.5f -> ContextUsageLevel.LOW
            usage < 0.75f -> ContextUsageLevel.MEDIUM
            usage < 0.9f -> ContextUsageLevel.HIGH
            else -> ContextUsageLevel.CRITICAL
        }
    }

    private fun updateLastAssistantMessage(content: String, status: MessageStatus) {
        updateLastAssistantMessageDirect(content, status)
    }

    private fun updateLastAssistantMessageDirect(content: String, status: MessageStatus) {
        val currentMessages = _messages.value.toMutableList()
        if (currentMessages.isNotEmpty()) {
            val lastMessage = currentMessages.last()
            if (!lastMessage.isUser) {
                val finalContent = if (status != MessageStatus.STREAMING) {
                    sanitizeAiResponseContent(content)
                } else {
                    content
                }
                val updatedMessage = lastMessage.copy(content = finalContent, status = status)
                currentMessages[currentMessages.lastIndex] = updatedMessage
                _messages.value = currentMessages

                if (status != MessageStatus.STREAMING) {
                    viewModelScope.launch {
                        if (updatedMessage.content.isBlank()) {
                            // Nothing to keep: an empty assistant row would
                            // render as a blank bubble after reload.
                            _messages.value = _messages.value.filterNot { it.id == updatedMessage.id }
                            chatRepository.deleteMessage(updatedMessage)
                        } else {
                            chatRepository.saveMessage(updatedMessage)
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if content has any unclosed thinking/reasoning tags
     * Supports multiple tag formats used by different AI providers
     */
    private fun hasUnclosedThinkingTag(content: String): Boolean {
        val lowerContent = content.lowercase()
        val tagPairs = listOf(
            "<think>" to "</think>",
            "<thinking>" to "</thinking>",
            "<reasoning>" to "</reasoning>",
            "<reason>" to "</reason>"
        )

        for ((openTag, closeTag) in tagPairs) {
            val openCount = lowerContent.split(openTag).size - 1
            val closeCount = lowerContent.split(closeTag).size - 1
            if (openCount > closeCount) {
                return true
            }
        }
        return false
    }

    /**
     * Sanitize AI response content to ensure proper tag closure and remove polluted content
     * This handles various thinking/reasoning tag formats from different AI providers
     */
    private fun sanitizeAiResponseContent(content: String): String {
        var result = content

        // List of thinking/reasoning tag pairs to process
        val tagPairs = listOf(
            "<think>" to "</think>",
            "<thinking>" to "</thinking>",
            "<reasoning>" to "</reasoning>",
            "<reason>" to "</reason>"
        )

        // Close any unclosed thinking tags
        for ((openTag, closeTag) in tagPairs) {
            val openCount = result.lowercase().split(openTag.lowercase()).size - 1
            val closeCount = result.lowercase().split(closeTag.lowercase()).size - 1

            if (openCount > closeCount) {
                // Add missing closing tags
                repeat(openCount - closeCount) {
                    result = "$result$closeTag"
                }
            }
        }

        // Normalize all thinking tag variants to <think></think> for consistent parsing
        result = result
            .replace(Regex("<thinking>", RegexOption.IGNORE_CASE), "<think>")
            .replace(Regex("</thinking>", RegexOption.IGNORE_CASE), "</think>")
            .replace(Regex("<reasoning>", RegexOption.IGNORE_CASE), "<think>")
            .replace(Regex("</reasoning>", RegexOption.IGNORE_CASE), "</think>")
            .replace(Regex("<reason>", RegexOption.IGNORE_CASE), "<think>")
            .replace(Regex("</reason>", RegexOption.IGNORE_CASE), "</think>")

        // Remove any empty thinking blocks
        result = result.replace(Regex("<think>\\s*</think>", RegexOption.IGNORE_CASE), "")

        // Clean up excessive whitespace but preserve paragraph breaks
        result = result
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        return result
    }

    /** Clear the current session's messages; the session itself stays. */
    fun clearChat() {
        if (_isAiProcessing.value) stopGeneration()
        _messages.value = emptyList()
        _messageHistory.clear()
        _runError.value = null

        // Clear persisted messages
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            chatRepository.clearChatHistory(sessionId)
        }
    }

    /**
     * Export the current session to a markdown file
     */
    fun exportChatHistory(onResult: (Result<java.io.File>) -> Unit) {
        val projectName = _project.value?.name ?: "Unknown"
        val sessionId = _currentSessionId.value
        if (sessionId == null) {
            onResult(Result.failure(IllegalStateException("No messages to export")))
            return
        }
        val title = sessionsState.value.currentTitle
        viewModelScope.launch {
            val result = chatRepository.exportToMarkdown(sessionId, projectName, title)
            onResult(result)
        }
    }

    // ---- Chat sessions ------------------------------------------------------

    /** Switch to another session of this project. Refused while a run is live. */
    fun openSession(sessionId: String) {
        if (_isAiProcessing.value || sessionId == _currentSessionId.value) return
        switchToSession(sessionId)
        viewModelScope.launch { chatRepository.markSessionOpened(sessionId) }
    }

    /**
     * Start a fresh chat. Nothing is written until its first message is
     * sent, so tapping New chat and backing out leaves no empty session.
     */
    fun startNewChat() {
        if (_isAiProcessing.value) return
        if (_currentSessionId.value == null && _messages.value.isEmpty()) return
        switchToSession(null)
    }

    fun renameSession(sessionId: String, title: String) {
        viewModelScope.launch { chatRepository.renameSession(sessionId, title) }
    }

    fun setSessionPinned(sessionId: String, pinned: Boolean) {
        viewModelScope.launch { chatRepository.setSessionPinned(sessionId, pinned) }
    }

    /**
     * Delete a session, offering undo for [UNDO_WINDOW_MS]. Deleting the
     * session on screen lands in a fresh chat rather than in some other old
     * thread, which is predictable when tidying up several in a row.
     */
    fun deleteSession(sessionId: String) {
        val wasCurrent = sessionId == _currentSessionId.value
        if (wasCurrent && _isAiProcessing.value) return
        viewModelScope.launch {
            if (wasCurrent && _currentSessionId.value == sessionId) {
                switchToSession(null)
            }
            inputDrafts.remove(sessionId)
            val snapshot = chatRepository.deleteSession(sessionId) ?: return@launch
            _pendingUndo.value = PendingUndo(snapshot, wasCurrent)
            undoExpiryJob?.cancel()
            undoExpiryJob = viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                _pendingUndo.value = null
            }
        }
    }

    fun undoDeleteSession() {
        val pending = _pendingUndo.value ?: return
        _pendingUndo.value = null
        undoExpiryJob?.cancel()
        viewModelScope.launch {
            chatRepository.restoreSession(pending.snapshot)
            // Back into it, if the user was in it and has not moved on since.
            if (pending.wasCurrent &&
                !_isAiProcessing.value &&
                _currentSessionId.value == null &&
                _messages.value.isEmpty()
            ) {
                switchToSession(pending.snapshot.id)
            }
        }
    }

    fun dismissSessionUndo() {
        _pendingUndo.value = null
        undoExpiryJob?.cancel()
    }

    /**
     * Point the chat at [sessionId] (null: a fresh chat), resetting
     * everything that belonged to the previous conversation. The message
     * Flow follows [_currentSessionId] and refills the transcript and the
     * agent's history.
     */
    private fun switchToSession(sessionId: String?) {
        val leaving = _currentSessionId.value ?: DRAFT_KEY
        val unsent = _chatInput.value
        if (unsent.isNotBlank()) inputDrafts[leaving] = unsent else inputDrafts.remove(leaving)
        _chatInput.value = inputDrafts.remove(sessionId ?: DRAFT_KEY).orEmpty()

        // Blank rather than showing the previous conversation under the new
        // title; the chat shows neither it nor the welcome until rows arrive.
        _isSessionLoading.value = sessionId != null
        _messages.value = emptyList()
        _messageHistory.clear()
        _runError.value = null
        _taskList.value = emptyList()
        _lastCompactionSavedPercent.value = null
        delegationHandler.resetPlan()
        subagentOrchestrator.clearCompleted()
        breakdownStale = true
        _currentSessionId.value = sessionId
    }

    fun setModel(model: AiModel) {
        _currentModel.value = model
        updateContextMaxTokens(model)

        // Persist the last used model for this project
        viewModelScope.launch {
            projectRepository.updateLastUsedModelId(projectId, model.id)
            preferencesRepository.setAiModel(model.id)
            preferencesRepository.setAiProvider(model.provider.name)
            aiModelRepository.recordModelUsage(model.id)
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _isRefreshingModels.value = true
            try {
                val apiKeys = preferencesRepository.apiKeys.first()

                aiModelRepository.refreshModelsFromAllConfiguredProviders(apiKeys)
                    .onFailure { error ->
                        _error.value = "Failed to refresh models: ${error.message}"
                    }
            } finally {
                _isRefreshingModels.value = false
            }
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

    /**
     * Handle AI code edit request from text selection in the code editor
     * Creates a specialized prompt for the AI to edit the selected code
     */
    fun handleAiCodeEdit(request: com.oro.app.ui.screens.editor.code.AiCodeEditRequest) {
        // Switch to chat tab to show the AI response
        _selectedTab.value = EditorTab.CHAT

        // Create a specialized prompt for code editing
        val prompt = buildString {
            append("Please help me edit the following code from **${request.fileName}** ")
            append("(lines ${request.startLine + 1}-${request.endLine + 1}):\n\n")
            append("```\n${request.selectedText}\n```\n\n")
            append("What changes would you like me to make to this code?")
        }

        // Set the chat input with the prompt
        _chatInput.value = prompt
    }

    companion object {
        private const val MAX_AGENT_ITERATIONS = 40
        private const val OPEN_FILE_CONTEXT_CHARS = 12_000
        /** At most this often is a streaming transcript pushed to the UI. */
        private const val STREAMING_UPDATE_INTERVAL_MS = 80L
        /** How long a deleted session can be restored. */
        private const val UNDO_WINDOW_MS = 6_000L
        /** inputDrafts key for the fresh, unsaved chat. */
        private const val DRAFT_KEY = ""

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = OroApplication.getInstance()
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
                    undoRedoManager = application.undoRedoManager,
                    semanticMemorySystem = application.semanticMemorySystem
                ) as T
            }
        }
    }
    /**
     * Log raw SSE data to a separate raw log file.
     * This provides unformatted server responses for debugging.
     */
    private fun logDebugRawSSE(data: String) {
        if (!_debugLogsEnabled.value) return

        val project = _project.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logsDir = File(project.rootPath, ".oro")
                if (!logsDir.exists()) logsDir.mkdirs()

                // Use separate file for raw SSE data
                val rawLogFile = File(logsDir, "raw_sse.log")
                val timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                )

                // Append raw data with minimal formatting
                rawLogFile.appendText("[$timestamp] $data\n")
            } catch (e: Exception) {
                // Ignore logging errors
            }
        }
    }

    /**
     * Helper to log debug info to .oro/logs.txt if enabled
     */
    private fun logDebugToProject(title: String, content: String) {
        if (!_debugLogsEnabled.value) return

        val project = _project.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logsDir = File(project.rootPath, ".oro")
                if (!logsDir.exists()) logsDir.mkdirs()

                val logFile = File(logsDir, "logs.txt")
                val timestamp = java.time.LocalDateTime.now().toString()

                val output = buildString {
                    append("\n")
                    append("=".repeat(20))
                    append(" $title [$timestamp] ")
                    append("=".repeat(20))
                    append("\n")
                    append(content)
                    append("\n")
                }

                logFile.appendText(output)
            } catch (e: Exception) {
                // Ignore logging errors to prevent detailed crashes
            }
        }
    }
    /**
     * Drive a subagent to completion.
     *
     * The subagent gets its own message list seeded from its definition's
     * system prompt and its brief -- deliberately *not* the parent
     * conversation. That isolation is the point of delegation: the subagent
     * works in a clean context, and only its final summary comes back, so the
     * verbose middle never crowds the orchestrator's window.
     *
     * Tools are filtered to the definition's capabilities, enforced here rather
     * than described in the prompt, so a read-only agent cannot write even if
     * it decides it should.
     */
    private fun launchSubagent(session: AgentSession) {
        val job = viewModelScope.launch {
            try {
                subagentOrchestrator.withSlot(session.id) {
                    runSubagentLoop(session)
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                // The user pressed stop. The orchestrator has already recorded
                // CANCELLED; rethrow so the scope unwinds normally.
                throw cancellation
            } catch (e: Exception) {
                subagentOrchestrator.fail(session.id, e.message ?: "Subagent failed")
            }
        }
        subagentOrchestrator.attachJob(session.id, job)
    }

    private suspend fun runSubagentLoop(session: AgentSession) {
        val definition = session.definition
        val model = _currentModel.value ?: run {
            subagentOrchestrator.fail(session.id, "No model selected")
            return
        }

        val allowedTools = AgentTools.all.filter { tool ->
            val capability = capabilityFor(tool.function.name)
            capability != null && definition.allows(capability)
        }
        val nativeTools = model.supportsToolCalls
        val textToolProtocol = if (!nativeTools && allowedTools.isNotEmpty()) {
            "\n\n" + (aiRepository.createSystemMessage(
                agentMode = true, nativeTools = false, toolList = allowedTools
            ).content.orEmpty().substringAfter("# Calling tools", "").let { if (it.isBlank()) "" else "# Calling tools$it" }
                .substringBefore("\n# Web guidelines"))
        } else ""
        val system = ChatRequestMessage(role = "system", content = definition.systemPrompt + textToolProtocol)
        val history = mutableListOf(ChatRequestMessage(role = "user", content = session.prompt))
        var turns = 0

        val loop = AgentLoop(
            client = { request ->
                aiRepository.streamChat(model = model, messages = request.messages, tools = request.tools, temperature = 0.4f)
            },
            // Out-of-capability calls are refused, not executed, and the
            // refusal goes back as the result so the agent can adjust.
            tools = { call ->
                val name = ToolAliases.canonicalName(call.function.name)
                val capability = capabilityFor(name)
                if (capability == null || !definition.allows(capability)) {
                    ToolResult(false, "", "Refused: a ${definition.label} agent cannot call ${call.function.name}.")
                } else {
                    toolExecutor.execute(projectId, call.copy(function = call.function.copy(name = name)))
                }
            },
            config = AgentLoopConfig(maxIterations = definition.maxTurns)
        )

        val listener = object : AgentLoopListener {
            private val started = mutableMapOf<String, Long>()
            override fun onModelCallStarted(iteration: Int, attempt: Int) {
                turns = iteration
                subagentOrchestrator.advance(session.id, AgentPhase.THINKING, turnsUsed = iteration)
            }
            override fun onToolStarted(call: ToolCallResponse) {
                started[call.id] = System.currentTimeMillis()
                capabilityFor(ToolAliases.canonicalName(call.function.name))?.let {
                    subagentOrchestrator.advance(session.id, phaseFor(it))
                }
            }
            override fun onToolFinished(call: ToolCallResponse, result: ToolResult) {
                val name = ToolAliases.canonicalName(call.function.name)
                val phase = capabilityFor(name)?.let { phaseFor(it) } ?: AgentPhase.THINKING
                subagentOrchestrator.advance(
                    session.id,
                    phase,
                    AgentStep(
                        index = subagentOrchestrator.session(session.id)?.steps?.size ?: 0,
                        phase = phase,
                        label = ToolPresentations.describe(name, subjectOf(call.function.arguments), isRunning = false),
                        startedAt = started[call.id] ?: System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(),
                        isError = !result.success
                    )
                )
            }
        }

        val knownTools = allowedTools.map { it.function.name }.toSet()
        when (val result = loop.run(history, knownTools, listener) { h, pressure ->
            val window = contextWindowManager.getAvailableTokens(model)
            val share = if (pressure == 0) 0.9 else 0.5
            val tools = if (nativeTools) allowedTools.takeIf { it.isNotEmpty() } else null
            AgentRequest(
                listOf(system) + HistoryPruner.prune(h, (window * share).toInt() - TokenEstimator.estimate(system) - toolTokens(tools)),
                tools
            )
        }) {
            is AgentRunResult.Completed -> {
                subagentOrchestrator.advance(session.id, AgentPhase.SUMMARISING)
                subagentOrchestrator.complete(session.id, result.finalText.ifBlank { "Finished without a summary." })
            }
            is AgentRunResult.Failed -> subagentOrchestrator.fail(session.id, result.message)
            // Out of turns. Almost always a loop rather than a task that
            // genuinely needed more steps, so reported as such.
            is AgentRunResult.IterationLimit ->
                subagentOrchestrator.advance(session.id, AgentPhase.EXHAUSTED, turnsUsed = turns)
        }
    }

    /** Which capability a tool needs, or null if it is not a known tool. */
    private fun capabilityFor(toolName: String): ToolCapability? = AgentTools.capabilityOf(toolName)

    private fun phaseFor(capability: ToolCapability): AgentPhase = when (capability) {
        ToolCapability.READ -> AgentPhase.READING
        ToolCapability.WRITE -> AgentPhase.EDITING
        ToolCapability.EXECUTE -> AgentPhase.EXECUTING
        else -> AgentPhase.THINKING
    }

    /** Best-effort subject for a step label: the path or command, if present. */
    private fun subjectOf(rawArguments: String): String? {
        val args = DelegationHandler.parseArgs(rawArguments) ?: return null
        for (key in listOf("path", "file_path", "command", "query", "directory")) {
            val value = runCatching {
                args[key]?.jsonPrimitive?.content
            }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** Stop one delegated agent. */
    fun stopSubagent(sessionId: String) = subagentOrchestrator.cancel(sessionId)

    /** Stop every delegated agent still running. */
    fun stopAllSubagents() = subagentOrchestrator.cancelAll()

    /** Answer a subagent's pending approval request. */
    fun resolveSubagentApproval(sessionId: String, approved: Boolean) =
        subagentOrchestrator.resolveApproval(sessionId, approved)

    /** Drop finished agent cards from the transcript. */
    fun clearFinishedSubagents() = subagentOrchestrator.clearCompleted()

    /**
     * Answer the conversation-level approval.
     *
     * Dismissing the sheet calls this with false; there is no third state,
     * because the agent is suspended inside the tool call until it has an
     * answer and leaving it unanswered would hang the turn.
     */
    fun resolveApproval(approved: Boolean) {
        _pendingApproval.value = null
        approvalAnswer?.complete(approved)
        approvalAnswer = null
    }

    /**
     * Ask the user, and suspend until they answer.
     *
     * Not timed and not auto-dismissing: an approval that expires into a
     * default is worse than no gate, because afterwards the user cannot tell
     * whether they agreed or simply looked away.
     */
    private suspend fun awaitApproval(action: String, detail: String?): Boolean {
        // A second request while one is open would orphan the first agent.
        approvalAnswer?.let { return false }

        val deferred = CompletableDeferred<Boolean>()
        approvalAnswer = deferred
        _pendingApproval.value = PendingApproval(
            action = action,
            detail = detail,
            detailIsCode = detail?.contains('\n') == true
        )
        return try {
            deferred.await()
        } catch (cancellation: CancellationException) {
            // Generation was stopped while the sheet was open: clear it so the
            // next turn does not inherit a stale question.
            _pendingApproval.value = null
            approvalAnswer = null
            throw cancellation
        }
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        viewModelScope.launch { preferencesRepository.setReasoningEffort(effort) }
    }

    /**
     * Recompute the context breakdown.
     *
     * Called after each turn rather than on every token: the numbers are
     * estimates and the sheet is not open during streaming, so recomputing per
     * delta would burn CPU to animate a figure nobody is reading.
     */
    private fun refreshContextBreakdown() {
        val model = _currentModel.value ?: return
        // The system prompt is built per request and never stored in the
        // history, so it is taken from the last request (it used to be looked
        // for in the history and was therefore always missing from the sheet).
        val systemPrompt = lastSystemPrompt
        val tools = lastRequestTools
        _contextBreakdown.value = breakdownBuilder.build(
            systemPrompt = systemPrompt,
            tools = tools,
            messages = _messageHistory.toList(),
            maxTokens = contextWindowManager.getAvailableTokens(model)
        )
    }

    /**
     * Summarise the older part of the conversation and replace it in place.
     *
     * Runs through the selected model with OpenCode's anchored-summary prompt.
     * A failed compaction leaves the conversation untouched -- a half-applied
     * compaction that dropped messages without producing a summary would lose
     * work irrecoverably, so nothing is removed until a usable summary is in
     * hand.
     */
    fun compactContext() {
        val model = _currentModel.value ?: return
        if (_isCompacting.value) return

        val partition = contextCompactor.partition(_messages.value)
        if (!partition.canCompact) return

        val sessionAtStart = _currentSessionId.value
        viewModelScope.launch {
            _isCompacting.value = true
            try {
                val previous = contextCompactor.extractPreviousSummary(_messageHistory)
                val request = contextCompactor.buildSummaryRequest(partition.older, previous)

                val completion = aiRepository.chat(
                    model = model,
                    messages = request,
                    maxTokens = ContextCompactor.SUMMARY_OUTPUT_TOKENS
                ).getOrElse { error ->
                    _error.value = "Could not compact: ${error.message}"
                    return@launch
                }

                val summary = completion.choices.firstOrNull()?.message?.content.orEmpty()
                // The user switched chats while this ran: the summary is of a
                // conversation that is no longer the agent's history.
                if (_currentSessionId.value != sessionAtStart) return@launch
                val applied = contextCompactor.applySummary(_messages.value, summary)
                if (applied == null) {
                    _error.value = "Compaction produced no summary; nothing was changed."
                    return@launch
                }

                val (compactedHistory, result) = applied
                _messageHistory.clear()
                _messageHistory.addAll(compactedHistory)
                _lastCompactionSavedPercent.value = result.savedPercent
                refreshContextBreakdown()
            } finally {
                _isCompacting.value = false
            }
        }
    }

}
