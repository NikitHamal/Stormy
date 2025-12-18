package com.codex.stormy

import android.app.Application
import com.codex.stormy.crash.CrashHandler
import com.codex.stormy.data.local.database.CodeXDatabase
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
