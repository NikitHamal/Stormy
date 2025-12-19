package com.codex.stormy

import android.app.Application
import com.codex.stormy.crash.CrashHandler
import com.codex.stormy.data.ai.context.ContextWindowManager
import com.codex.stormy.data.ai.learning.UserPreferencesLearner
import com.codex.stormy.data.ai.tools.MemoryStorage
import com.codex.stormy.data.ai.tools.ToolExecutor
import com.codex.stormy.data.ai.undo.UndoRedoManager
import com.codex.stormy.data.local.database.CodeXDatabase
import com.codex.stormy.data.repository.AiRepository
import com.codex.stormy.data.repository.ChatRepository
import com.codex.stormy.data.repository.PreferencesRepository
import com.codex.stormy.data.repository.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CodeXApplication : Application() {

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

    val contextWindowManager: ContextWindowManager by lazy {
        ContextWindowManager(aiRepository)
    }

    val userPreferencesLearner: UserPreferencesLearner by lazy {
        UserPreferencesLearner(this)
    }

    val memoryStorage: MemoryStorage by lazy {
        MemoryStorage(this)
    }

    val undoRedoManager: UndoRedoManager by lazy {
        UndoRedoManager(projectRepository)
    }

    val toolExecutor: ToolExecutor by lazy {
        ToolExecutor(projectRepository, memoryStorage)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashHandler.initialize(this)
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
