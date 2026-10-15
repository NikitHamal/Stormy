package com.codex.stormy

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.codex.stormy.crash.CrashHandler
import com.codex.stormy.data.ai.GeminiModelService
import com.codex.stormy.data.ai.OpenAiCompatibleModelService
import com.codex.stormy.data.ai.context.ContextWindowManager
import com.codex.stormy.data.ai.learning.UserPreferencesLearner
import com.codex.stormy.data.ai.memory.SemanticMemorySystem
import com.codex.stormy.data.ai.tools.MemoryStorage
import com.codex.stormy.data.ai.tools.ToolExecutor
import com.codex.stormy.data.ai.undo.UndoRedoManager
import com.codex.stormy.data.git.GitCredentialsManager
import com.codex.stormy.data.git.GitHubActionsService
import com.codex.stormy.data.git.GitManager
import com.codex.stormy.data.local.database.CodeXDatabase
import com.codex.stormy.data.repository.AiModelRepository
import com.codex.stormy.data.repository.AiRepository
import com.codex.stormy.data.repository.ChatRepository
import com.codex.stormy.data.repository.PreferencesRepository
import com.codex.stormy.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CodeXApplication : Application(), SingletonImageLoader.Factory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: CodeXDatabase by lazy {
        CodeXDatabase.getInstance(this)
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), this)
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }

    val aiRepository: AiRepository by lazy {
        AiRepository(preferencesRepository)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(database.chatMessageDao(), this)
    }

    val geminiModelService: GeminiModelService by lazy {
        GeminiModelService()
    }

    val openAiCompatibleModelService: OpenAiCompatibleModelService by lazy {
        OpenAiCompatibleModelService()
    }

    // AI Model repository for managing model storage and preferences
    val aiModelRepository: AiModelRepository by lazy {
        AiModelRepository(
            aiModelDao = database.aiModelDao(),
            geminiModelService = geminiModelService,
            openAiCompatibleModelService = openAiCompatibleModelService
        )
    }

    val contextWindowManager: ContextWindowManager by lazy {
        ContextWindowManager(aiRepository)
    }

    val userPreferencesLearner: UserPreferencesLearner by lazy {
        UserPreferencesLearner(this)
    }

    val memoryStorage: MemoryStorage by lazy {
        MemoryStorage(this)
    }

    val semanticMemorySystem: SemanticMemorySystem by lazy {
        SemanticMemorySystem(this)
    }

    val undoRedoManager: UndoRedoManager by lazy {
        UndoRedoManager(projectRepository)
    }

    val toolExecutor: ToolExecutor by lazy {
        ToolExecutor(projectRepository, memoryStorage, gitManager, semanticMemorySystem)
    }

    // Git integration
    val gitCredentialsManager: GitCredentialsManager by lazy {
        GitCredentialsManager(this)
    }

    val gitManager: GitManager by lazy {
        GitManager(gitCredentialsManager)
    }

    val gitHubActionsService: GitHubActionsService by lazy {
        GitHubActionsService(gitCredentialsManager)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.initialize(this)

        // Initialize AI models on startup to ensure models are available
        applicationScope.launch {
            aiModelRepository.initializeModelsIfEmpty()
        }
    }

    /**
     * Coil 3 singleton loader. SVG decoding is registered explicitly; network
     * fetching comes from coil-network-okhttp, which reuses the shared OkHttp
     * client instead of standing up a second connection pool.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    companion object {
        @Volatile
        private var instance: CodeXApplication? = null

        fun getInstance(): CodeXApplication {
            return instance ?: throw IllegalStateException(
                "CodeXApplication not initialized. Ensure the application class is properly configured."
            )
        }
    }
}
