package com.codex.stormy.data.preview

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Server state for preview
 */
sealed class PreviewServerState {
    data object Stopped : PreviewServerState()
    data object Starting : PreviewServerState()
    data class Running(
        val port: Int,
        val baseUrl: String,
        val frameworkType: FrameworkType
    ) : PreviewServerState()
    data class Error(val message: String) : PreviewServerState()
}

/**
 * Manages the lifecycle of embedded dev servers for project preview.
 *
 * Features:
 * - Automatic framework detection
 * - Server lifecycle management
 * - Hot reload coordination
 * - Multi-project support (one server per project)
 */
class PreviewServerManager {

    companion object {
        private const val TAG = "PreviewServerManager"

        @Volatile
        private var instance: PreviewServerManager? = null

        fun getInstance(): PreviewServerManager {
            return instance ?: synchronized(this) {
                instance ?: PreviewServerManager().also { instance = it }
            }
        }
    }

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active servers keyed by project path
    private val activeServers = mutableMapOf<String, EmbeddedDevServer>()

    private val _serverState = MutableStateFlow<PreviewServerState>(PreviewServerState.Stopped)
    val serverState: StateFlow<PreviewServerState> = _serverState

    private val _currentProjectPath = MutableStateFlow<String?>(null)
    val currentProjectPath: StateFlow<String?> = _currentProjectPath

    /**
     * Start or get server for a project
     * Returns the server URL or null if failed
     */
    suspend fun startServerForProject(projectPath: String): String? = withContext(Dispatchers.IO) {
        val projectRoot = File(projectPath)
        if (!projectRoot.exists() || !projectRoot.isDirectory) {
            Log.e(TAG, "Invalid project path: $projectPath")
            _serverState.value = PreviewServerState.Error("Invalid project path")
            return@withContext null
        }

        // Check if server already running for this project
        activeServers[projectPath]?.let { existingServer ->
            if (existingServer.port > 0) {
                Log.d(TAG, "Server already running for $projectPath on port ${existingServer.port}")
                _serverState.value = PreviewServerState.Running(
                    port = existingServer.port,
                    baseUrl = existingServer.baseUrl,
                    frameworkType = FrameworkType.detect(projectRoot)
                )
                _currentProjectPath.value = projectPath
                return@withContext existingServer.baseUrl
            }
        }

        _serverState.value = PreviewServerState.Starting

        try {
            // Detect framework type
            val frameworkType = FrameworkType.detect(projectRoot)
            Log.i(TAG, "Detected framework: ${frameworkType.displayName} for $projectPath")

            // Create and start server
            val server = EmbeddedDevServer(projectRoot, frameworkType)
            val port = server.start()

            if (port > 0) {
                activeServers[projectPath] = server
                _currentProjectPath.value = projectPath
                _serverState.value = PreviewServerState.Running(
                    port = port,
                    baseUrl = server.baseUrl,
                    frameworkType = frameworkType
                )
                Log.i(TAG, "Server started successfully: ${server.baseUrl}")
                return@withContext server.baseUrl
            } else {
                _serverState.value = PreviewServerState.Error("Failed to start server - no port available")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting server", e)
            _serverState.value = PreviewServerState.Error(e.message ?: "Unknown error")
            return@withContext null
        }
    }

    /**
     * Stop server for a specific project
     */
    fun stopServerForProject(projectPath: String) {
        activeServers[projectPath]?.let { server ->
            server.stop()
            activeServers.remove(projectPath)
            Log.i(TAG, "Server stopped for $projectPath")
        }

        if (projectPath == _currentProjectPath.value) {
            _currentProjectPath.value = null
            _serverState.value = PreviewServerState.Stopped
        }
    }

    /**
     * Stop all running servers
     */
    fun stopAllServers() {
        activeServers.values.forEach { it.stop() }
        activeServers.clear()
        _currentProjectPath.value = null
        _serverState.value = PreviewServerState.Stopped
        Log.i(TAG, "All servers stopped")
    }

    /**
     * Get the URL for a specific project's server
     */
    fun getServerUrl(projectPath: String): String? {
        return activeServers[projectPath]?.baseUrl
    }

    /**
     * Check if a project needs HTTP server (framework project)
     */
    fun needsHttpServer(projectPath: String): Boolean {
        val projectRoot = File(projectPath)
        if (!projectRoot.exists()) return false

        val frameworkType = FrameworkType.detect(projectRoot)
        return frameworkType != FrameworkType.VANILLA
    }

    /**
     * Get the appropriate preview URL for a project
     * Returns HTTP URL for framework projects, file:// URL for vanilla HTML
     */
    suspend fun getPreviewUrl(projectPath: String): String {
        val projectRoot = File(projectPath)
        val frameworkType = FrameworkType.detect(projectRoot)

        // For framework projects, use HTTP server
        if (frameworkType != FrameworkType.VANILLA) {
            val serverUrl = startServerForProject(projectPath)
            if (serverUrl != null) {
                return serverUrl
            }
        }

        // Fallback to file:// URL for vanilla HTML
        val indexFile = File(projectPath, "index.html")
        return if (indexFile.exists()) {
            "file://${indexFile.absolutePath}"
        } else {
            "about:blank"
        }
    }

    /**
     * Trigger a manual refresh/reload
     */
    fun triggerReload(projectPath: String) {
        activeServers[projectPath]?.checkForChanges()
    }

    /**
     * Get detected framework type for a project
     */
    fun getFrameworkType(projectPath: String): FrameworkType {
        return FrameworkType.detect(File(projectPath))
    }

    /**
     * Check if server is running for a project
     */
    fun isServerRunning(projectPath: String): Boolean {
        return activeServers[projectPath]?.port?.let { it > 0 } ?: false
    }

    /**
     * Release all resources
     */
    fun destroy() {
        stopAllServers()
        activeServers.values.forEach { it.destroy() }
    }
}

/**
 * Extension function to determine if project uses modules
 */
fun File.usesEsModules(): Boolean {
    val indexHtml = File(this, "index.html")
    if (indexHtml.exists()) {
        val content = indexHtml.readText()
        if (content.contains("type=\"module\"")) {
            return true
        }
    }

    val packageJson = File(this, "package.json")
    if (packageJson.exists()) {
        val content = packageJson.readText()
        if (content.contains("\"type\": \"module\"") || content.contains("\"type\":\"module\"")) {
            return true
        }
    }

    // Check for module syntax in JS files
    return walkTopDown()
        .filter { it.extension in listOf("js", "mjs", "jsx") }
        .take(10) // Sample first 10 files
        .any { file ->
            try {
                val content = file.readText()
                content.contains("import ") || content.contains("export ")
            } catch (e: Exception) {
                false
            }
        }
}
