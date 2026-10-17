package com.oro.app

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.oro.app.crash.CrashHandler
import com.oro.app.data.ai.GeminiModelService
import com.oro.app.data.ai.OpenAiCompatibleModelService
import com.oro.app.data.ai.context.ContextWindowManager
import com.oro.app.data.ai.learning.UserPreferencesLearner
import com.oro.app.data.ai.memory.SemanticMemorySystem
import com.oro.app.data.ai.tools.MemoryStorage
import com.oro.app.data.ai.tools.ToolExecutor
import com.oro.app.data.ai.undo.UndoRedoManager
import com.oro.app.data.git.GitCredentialsManager
import com.oro.app.data.git.GitHubActionsService
import com.oro.app.data.git.GitManager
import com.oro.app.data.local.database.OroDatabase
import com.oro.app.data.repository.AiModelRepository
import com.oro.app.data.repository.AiRepository
import com.oro.app.data.repository.ChatRepository
import com.oro.app.data.repository.PreferencesRepository
import com.oro.app.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OroApplication : Application(), SingletonImageLoader.Factory {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: OroDatabase by lazy {
        OroDatabase.getInstance(this)
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
        ChatRepository(database.chatMessageDao(), database.chatSessionDao(), this)
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

        // CrashActivity runs in its own ":crash_handler" process, which also
        // creates an OroApplication. It only shows a stack trace, so it must
        // not open the database or seed models -- that is wasted work and
        // extra memory pressure at exactly the moment the device just crashed.
        if (isCrashProcess()) return

        // Initialize AI models on startup to ensure models are available
        applicationScope.launch {
            aiModelRepository.initializeModelsIfEmpty()
        }
    }

    private fun isCrashProcess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Application.getProcessName().endsWith(":crash_handler")

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
        private var instance: OroApplication? = null

        fun getInstance(): OroApplication {
            return instance ?: throw IllegalStateException(
                "OroApplication not initialized. Ensure the application class is properly configured."
            )
        }
    }
}
