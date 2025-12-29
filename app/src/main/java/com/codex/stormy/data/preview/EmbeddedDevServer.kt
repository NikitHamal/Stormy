package com.codex.stormy.data.preview

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight embedded HTTP server for serving project files during preview.
 *
 * This server enables framework preview (React, Vue, Svelte, Next.js) by providing:
 * - HTTP protocol support (required for ES modules, CORS, etc.)
 * - Proper MIME type handling
 * - Framework-specific transformations (JSX, Vue SFC, Svelte)
 * - CDN-based module resolution (no local bundling needed)
 * - Hot reload support via WebSocket-like polling
 *
 * The server runs on localhost and binds to a random available port.
 */
class EmbeddedDevServer(
    private val projectRoot: File,
    private val frameworkType: FrameworkType = FrameworkType.VANILLA
) {
    companion object {
        private const val TAG = "EmbeddedDevServer"
        private const val DEFAULT_PORT_RANGE_START = 8080
        private const val DEFAULT_PORT_RANGE_END = 8180
        private const val HTTP_OK = 200
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SERVER_ERROR = 500

        // Pre-compiled regex patterns for performance
        // React/JS transformations
        private val REACT_IMPORT_REGEX = Regex("""from\s+['"]react['"]""")
        private val REACT_DOM_IMPORT_REGEX = Regex("""from\s+['"]react-dom['"]""")
        private val REACT_DOM_CLIENT_REGEX = Regex("""from\s+['"]react-dom/client['"]""")
        private val REACT_ROUTER_REGEX = Regex("""from\s+['"]react-router-dom['"]""")
        private val JSX_IMPORT_REGEX = Regex("""from\s+['"](\.[^'"]+)\.(jsx|tsx)['"]""")
        private val CSS_IMPORT_REGEX = Regex("""import\s+['"](\.[^'"]+\.css)['"]\s*;?""")

        // Vue transformations
        private val VUE_IMPORT_REGEX = Regex("""from\s+['"]vue['"]""")
        private val VUE_CREATE_APP_REGEX = Regex("""import\s*\{\s*createApp\s*\}\s*from\s*['"]vue['"]\s*;?""")
        private val VUE_COMPONENT_IMPORT_REGEX = Regex("""import\s+(\w+)\s+from\s+['"](\.[^'"]+\.vue)['"]\s*;?""")
        private val VUE_EXPORT_DEFAULT_REGEX = Regex("""export\s+default\s*\{""")

        // Svelte transformations
        private val SVELTE_COMPONENT_IMPORT_REGEX = Regex("""import\s+(\w+)\s+from\s+['"](\.[^'"]+\.svelte)['"]\s*;?""")
        private val SVELTE_VAR_DECL_REGEX = Regex("""let\s+(\w+)\s*=\s*([^;\n]+)""")
        private val SVELTE_FUNC_REGEX = Regex("""function\s+(\w+)\s*\(""")

        // HTML transformations
        private val HEAD_CLOSE_REGEX = Regex("</head>", RegexOption.IGNORE_CASE)
        private val BODY_CLOSE_REGEX = Regex("</body>", RegexOption.IGNORE_CASE)
        private val HTML_CLOSE_REGEX = Regex("</html>", RegexOption.IGNORE_CASE)
        private val MODULE_SCRIPT_JSX_REGEX = Regex("""<script\s+type=['"]module['"]\s+src=['"]([^'"]+\.jsx)['"]\s*>""", RegexOption.IGNORE_CASE)
        private val MODULE_SCRIPT_VUE_REGEX = Regex("""<script\s+type=['"]module['"]\s+src=['"]/?src/main\.js['"]\s*>\s*</script>""", RegexOption.IGNORE_CASE)

        // SFC parsing
        private val TEMPLATE_REGEX = Regex("""<template>([\s\S]*?)</template>""", RegexOption.IGNORE_CASE)
        private val SCRIPT_SETUP_REGEX = Regex("""<script\s+setup[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        private val SCRIPT_REGEX = Regex("""<script(?!\s+setup)[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
        private val STYLE_REGEX = Regex("""<style[^>]*>([\s\S]*?)</style>""", RegexOption.IGNORE_CASE)

        // Vue JS transformations
        private val VUE_CREATE_APP_IMPORT_ALT_REGEX = Regex("""import\s+\{\s*createApp\s*\}\s+from\s*['"]vue['"]\s*;?""")
        private val VUE_IMPORT_ALL_REGEX = Regex("""import\s*\{[^}]+\}\s*from\s*['"]vue['"]""")
        private val VUE_FILE_IMPORT_REGEX = Regex("""from\s+['"](\.[^'"]+)\.vue['"]""")

        // Svelte JS transformations
        private val SVELTE_FILE_IMPORT_REGEX = Regex("""from\s+['"](\.[^'"]+)\.svelte['"]""")

        // Next.js transformations
        private val NEXT_HEAD_IMPORT_REGEX = Regex("""from\s+['"]next/head['"]""")
        private val NEXT_LINK_IMPORT_REGEX = Regex("""from\s+['"]next/link['"]""")
        private val NEXT_IMAGE_IMPORT_REGEX = Regex("""from\s+['"]next/image['"]""")
        private val NEXT_ROUTER_IMPORT_REGEX = Regex("""from\s+['"]next/router['"]""")

        // File extension patterns
        private val JS_TSX_FILE_REGEX = Regex("jsx?|tsx?")

        // React import detection
        private val REACT_IMPORT_DETECT_REGEX = Regex("""import\s+React""")
        private val REACT_NAMED_IMPORT_DETECT_REGEX = Regex("""import\s*\{\s*.*\s*\}\s*from\s+['"].*react""")

        // Vue setup script extraction
        private val CONST_DECL_REGEX = Regex("""const\s+(\w+)\s*=""")
        private val LET_DECL_REGEX = Regex("""let\s+(\w+)\s*=""")
        private val FUNC_OR_CONST_DECL_REGEX = Regex("""(?:const|function)\s+(\w+)\s*[=(]""")
        private val VUE_BUILTINS_REGEX = Regex("ref|reactive|computed")

        // Svelte template transformations
        private val SVELTE_VAR_INTERPOLATION_REGEX = Regex("""\{(\w+)\}""")
        private val SVELTE_CLICK_HANDLER_REGEX = Regex("""on:click=\{(\w+)\}""")
        private val SVELTE_INCREMENT_REGEX = Regex("""(\w+)\s*\+=\s*1""")
        private val SVELTE_ASSIGNMENT_REGEX = Regex("""(\w+)\s*=\s*([^;]+)""")
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track file modification times for hot reload
    private val fileModificationTimes = mutableMapOf<String, Long>()
    private var lastChangeTimestamp = System.currentTimeMillis()

    // Response cache for static assets (cleared on file changes)
    private val responseCache = mutableMapOf<String, CachedResponse>()
    private data class CachedResponse(
        val content: ByteArray,
        val mimeType: String,
        val timestamp: Long
    )

    /**
     * Current server port, or -1 if not running
     */
    var port: Int = -1
        private set

    /**
     * Server base URL
     */
    val baseUrl: String
        get() = if (port > 0) "http://127.0.0.1:$port" else ""

    /**
     * Start the server
     * @return The port the server is running on, or -1 if failed
     */
    suspend fun start(): Int = withContext(Dispatchers.IO) {
        if (isRunning.get()) {
            Log.d(TAG, "Server already running on port $port")
            return@withContext port
        }

        try {
            // Find an available port
            val socket = findAvailablePort()
            if (socket == null) {
                Log.e(TAG, "No available port found")
                return@withContext -1
            }

            serverSocket = socket
            port = socket.localPort
            isRunning.set(true)

            // Index initial files for hot reload
            indexProjectFiles()

            // Start accepting connections
            serverJob = serverScope.launch {
                Log.i(TAG, "Dev server started on port $port for ${frameworkType.displayName}")
                acceptConnections()
            }

            Log.i(TAG, "Server URL: $baseUrl")
            port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            -1
        }
    }

    /**
     * Stop the server
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            serverJob?.cancel()
            serverSocket?.close()
            serverSocket = null
            port = -1
            Log.i(TAG, "Dev server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    /**
     * Check if files have changed since last check
     * Clears the response cache when changes are detected
     */
    fun checkForChanges(): Boolean {
        var hasChanges = false
        val currentTime = System.currentTimeMillis()
        val changedFiles = mutableListOf<String>()

        projectRoot.walkTopDown()
            .filter { it.isFile && isServableFile(it) }
            .forEach { file ->
                val relativePath = file.relativeTo(projectRoot).path
                val lastModified = file.lastModified()
                val previousModified = fileModificationTimes[relativePath]

                if (previousModified == null || lastModified > previousModified) {
                    fileModificationTimes[relativePath] = lastModified
                    changedFiles.add(relativePath)
                    hasChanges = true
                }
            }

        if (hasChanges) {
            lastChangeTimestamp = currentTime
            // Clear cache for changed files (or all if many changed)
            if (changedFiles.size > 5) {
                responseCache.clear()
            } else {
                changedFiles.forEach { path ->
                    responseCache.remove(path)
                    // Also clear files that might import changed files
                    if (path.endsWith(".css")) {
                        responseCache.keys.filter { it.endsWith(".js") || it.endsWith(".jsx") }
                            .forEach { responseCache.remove(it) }
                    }
                }
            }
        }

        return hasChanges
    }

    private fun findAvailablePort(): ServerSocket? {
        for (port in DEFAULT_PORT_RANGE_START..DEFAULT_PORT_RANGE_END) {
            try {
                return ServerSocket(port).also {
                    it.reuseAddress = true
                }
            } catch (e: Exception) {
                // Port in use, try next
            }
        }
        return null
    }

    private suspend fun acceptConnections() {
        val socket = serverSocket ?: return

        while (isRunning.get() && serverScope.isActive) {
            try {
                val clientSocket = socket.accept()
                serverScope.launch {
                    handleClient(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error accepting connection", e)
                }
            }
        }
    }

    private suspend fun handleClient(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            clientSocket.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val output = BufferedOutputStream(socket.getOutputStream())

                // Read request line
                val requestLine = reader.readLine() ?: return@use
                val parts = requestLine.split(" ")

                if (parts.size < 2) {
                    sendError(output, HTTP_SERVER_ERROR, "Invalid request")
                    return@use
                }

                val method = parts[0]
                var path = URLDecoder.decode(parts[1], "UTF-8")

                // Read headers
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                    val colonIndex = line!!.indexOf(':')
                    if (colonIndex > 0) {
                        headers[line!!.substring(0, colonIndex).trim().lowercase()] =
                            line!!.substring(colonIndex + 1).trim()
                    }
                }

                when {
                    path == "/__dev_changes__" -> handleChangesEndpoint(output)
                    path == "/__dev_reload__" -> handleReloadEndpoint(output)
                    method == "GET" -> handleGetRequest(path, output)
                    else -> sendError(output, HTTP_NOT_FOUND, "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        }
    }

    private fun handleGetRequest(path: String, output: BufferedOutputStream) {
        var normalizedPath = if (path.startsWith("/")) path.substring(1) else path

        // Remove query string
        val queryIndex = normalizedPath.indexOf('?')
        if (queryIndex >= 0) {
            normalizedPath = normalizedPath.substring(0, queryIndex)
        }

        // Handle root path
        if (normalizedPath.isEmpty() || normalizedPath == "/") {
            normalizedPath = "index.html"
        }

        val file = File(projectRoot, normalizedPath)

        // Security: Prevent directory traversal
        if (!file.canonicalPath.startsWith(projectRoot.canonicalPath)) {
            sendError(output, HTTP_NOT_FOUND, "Not Found")
            return
        }

        // Check if file exists, try index.html for directories
        val targetFile = when {
            file.isDirectory -> File(file, "index.html")
            file.exists() -> file
            // Try without extension for SPA routing
            !file.exists() && !normalizedPath.contains(".") -> File(projectRoot, "index.html")
            else -> null
        }

        if (targetFile == null || !targetFile.exists()) {
            sendError(output, HTTP_NOT_FOUND, "File not found: $normalizedPath")
            return
        }

        // Serve the file with transformation if needed
        serveFile(targetFile, normalizedPath, output)
    }

    private fun serveFile(file: File, requestPath: String, output: BufferedOutputStream) {
        try {
            val mimeType = getMimeType(file.extension)
            val relativePath = file.relativeTo(projectRoot).path
            val fileModTime = file.lastModified()

            // Check cache for static assets (images, fonts, etc. that don't need transformation)
            val isStaticAsset = file.extension in listOf("png", "jpg", "jpeg", "gif", "webp", "ico",
                "woff", "woff2", "ttf", "eot", "otf", "mp4", "webm", "mp3", "wav", "pdf")

            if (isStaticAsset) {
                val cached = responseCache[relativePath]
                if (cached != null && cached.timestamp >= fileModTime) {
                    sendResponse(output, HTTP_OK, cached.mimeType, cached.content)
                    return
                }
            }

            var content = file.readBytes()

            // Apply framework-specific transformations for JS/JSX/Vue/Svelte files
            if (frameworkType != FrameworkType.VANILLA && needsTransformation(file.extension)) {
                content = transformContent(file, String(content)).toByteArray()
            }

            // Transform HTML files for framework projects to use Babel standalone
            if ((file.extension == "html" || file.extension == "htm") && frameworkType != FrameworkType.VANILLA) {
                content = transformHtmlForFramework(String(content)).toByteArray()
            }

            // Inject hot reload script for HTML files
            if (file.extension == "html" || file.extension == "htm") {
                content = injectHotReload(String(content)).toByteArray()
            }

            // Cache static assets
            if (isStaticAsset) {
                responseCache[relativePath] = CachedResponse(
                    content = content,
                    mimeType = mimeType,
                    timestamp = fileModTime
                )
            }

            sendResponse(output, HTTP_OK, mimeType, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error serving file: ${file.path}", e)
            sendError(output, HTTP_SERVER_ERROR, "Error reading file")
        }
    }

    /**
     * Transform HTML for framework projects to enable browser-based JSX/Vue/Svelte compilation
     */
    private fun transformHtmlForFramework(html: String): String {
        var result = html

        // Add necessary runtime scripts based on framework type
        val runtimeScripts = when (frameworkType) {
            FrameworkType.REACT, FrameworkType.NEXTJS -> """
                |    <!-- React Runtime for Preview -->
                |    <script crossorigin src="https://unpkg.com/react@18.2.0/umd/react.development.js"></script>
                |    <script crossorigin src="https://unpkg.com/react-dom@18.2.0/umd/react-dom.development.js"></script>
                |    <script crossorigin src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
                |    <script>
                |      // Enable Babel to transform JSX in script tags
                |      Babel.registerPreset('jsx', {
                |        presets: [
                |          [Babel.availablePresets['react'], { runtime: 'classic' }]
                |        ]
                |      });
                |    </script>
            """.trimMargin()
            FrameworkType.VUE -> """
                |    <!-- Vue Runtime for Preview -->
                |    <script crossorigin src="https://unpkg.com/vue@3.4.21/dist/vue.global.js"></script>
            """.trimMargin()
            FrameworkType.SVELTE -> """
                |    <!-- Svelte projects are compiled to vanilla JS by the server -->
            """.trimMargin()
            else -> ""
        }

        // Insert runtime scripts before </head> (using pre-compiled regex)
        if (runtimeScripts.isNotEmpty() && result.contains("</head>", ignoreCase = true)) {
            result = result.replace(HEAD_CLOSE_REGEX, "$runtimeScripts\n</head>")
        }

        // Transform module script tags for JSX files to use Babel (using pre-compiled regex)
        if (frameworkType == FrameworkType.REACT || frameworkType == FrameworkType.NEXTJS) {
            // Change <script type="module" src="/src/main.jsx"> to <script type="text/babel" data-type="module" src="/src/main.jsx">
            result = result.replace(MODULE_SCRIPT_JSX_REGEX) { match ->
                val src = match.groupValues[1]
                """<script type="text/babel" data-presets="jsx" src="$src">"""
            }
        }

        // For Vue, we need to transform the main.js import to not use ES modules
        // since Vue 3 global build doesn't support ES module imports (using pre-compiled regex)
        if (frameworkType == FrameworkType.VUE) {
            // Vue global build exposes Vue on window, so we can inline a simple mount script
            if (result.contains("src=\"/src/main.js\"", ignoreCase = true) ||
                result.contains("src='/src/main.js'", ignoreCase = true)) {
                // Replace the module script with inline Vue mounting code
                result = result.replace(MODULE_SCRIPT_VUE_REGEX, """<script src="/src/main.js"></script>""")
            }
        }

        return result
    }

    private fun handleChangesEndpoint(output: BufferedOutputStream) {
        val hasChanges = checkForChanges()
        val json = """{"hasChanges":$hasChanges,"timestamp":$lastChangeTimestamp}"""
        sendResponse(output, HTTP_OK, "application/json", json.toByteArray())
    }

    private fun handleReloadEndpoint(output: BufferedOutputStream) {
        // Force timestamp update to trigger reload
        lastChangeTimestamp = System.currentTimeMillis()
        val json = """{"reloaded":true,"timestamp":$lastChangeTimestamp}"""
        sendResponse(output, HTTP_OK, "application/json", json.toByteArray())
    }

    private fun transformContent(file: File, content: String): String {
        return when (frameworkType) {
            FrameworkType.REACT -> transformReact(file, content)
            FrameworkType.VUE -> transformVueFiles(file, content)
            FrameworkType.SVELTE -> transformSvelteFiles(file, content)
            FrameworkType.NEXTJS -> transformNextJs(file, content)
            else -> content
        }
    }

    /**
     * Transform Vue-related files (both .vue SFCs and .js entry files)
     */
    private fun transformVueFiles(file: File, content: String): String {
        return when (file.extension) {
            "vue" -> transformVue(file, content)
            "js", "mjs" -> transformVueJs(file, content)
            else -> content
        }
    }

    /**
     * Transform Vue .js files (main.js, etc.)
     * Converts ES module syntax to work with Vue global build
     */
    private fun transformVueJs(file: File, content: String): String {
        var transformed = content

        // Check if this is a main entry file that uses createApp
        val isMainFile = content.contains("createApp") && content.contains("mount")

        if (isMainFile) {
            // For main.js, convert to use global Vue object
            // Remove import statements and use global Vue (using pre-compiled regex)
            transformed = transformed
                .replace(VUE_CREATE_APP_REGEX, "const { createApp } = Vue;")
                .replace(VUE_CREATE_APP_IMPORT_ALT_REGEX, "const { createApp } = Vue;")

            // Transform .vue imports to fetch the transformed component
            // import App from './App.vue' -> const App = (await import('./App.vue')).default
            transformed = transformed.replace(VUE_COMPONENT_IMPORT_REGEX) { match ->
                val componentName = match.groupValues[1]
                val path = match.groupValues[2]
                "const $componentName = (await import('$path')).default;"
            }

            // Wrap in async IIFE if we have async imports
            if (transformed.contains("await import")) {
                transformed = """
                    |(async function() {
                    |  $transformed
                    |})();
                """.trimMargin()
            }
        } else {
            // For other JS files, just transform Vue imports to CDN (using pre-compiled regex)
            transformed = transformed
                .replace(VUE_IMPORT_REGEX, """from "https://esm.sh/vue@3.4.0"""")

            // Transform .vue imports - they need to be served as JS
            transformed = transformed.replace(VUE_FILE_IMPORT_REGEX) { match ->
                val path = match.groupValues[1]
                """from "$path.vue""""
            }
        }

        // Transform CSS imports to dynamic injection
        transformed = transformCssImports(transformed, file)

        return transformed
    }

    /**
     * Transform Svelte-related files (both .svelte components and .js entry files)
     */
    private fun transformSvelteFiles(file: File, content: String): String {
        return when (file.extension) {
            "svelte" -> transformSvelte(file, content)
            "js", "mjs" -> transformSvelteJs(file, content)
            else -> content
        }
    }

    /**
     * Transform Svelte .js files (main.js, etc.)
     * Converts to work with the compiled Svelte components
     */
    private fun transformSvelteJs(file: File, content: String): String {
        var transformed = content

        // Check if this is a main entry file
        val isMainFile = content.contains("new App") || content.contains("target:")

        if (isMainFile) {
            // Transform .svelte imports to dynamic imports (using pre-compiled regex)
            transformed = transformed.replace(SVELTE_COMPONENT_IMPORT_REGEX) { match ->
                val componentName = match.groupValues[1]
                val path = match.groupValues[2]
                "const ${componentName}Module = await import('$path');\nconst $componentName = ${componentName}Module.default;"
            }

            // Transform the component instantiation to use our compiled class
            // new App({ target: ... }) stays the same since our compiled components use class syntax

            // Wrap in async IIFE if we have async imports
            if (transformed.contains("await import")) {
                transformed = """
                    |(async function() {
                    |  $transformed
                    |})();
                """.trimMargin()
            }
        } else {
            // For other JS files, just transform .svelte imports (using pre-compiled regex)
            transformed = transformed.replace(SVELTE_FILE_IMPORT_REGEX) { match ->
                val path = match.groupValues[1]
                """from "$path.svelte""""
            }
        }

        // Transform CSS imports to dynamic injection
        transformed = transformCssImports(transformed, file)

        return transformed
    }

    /**
     * Transform React JSX files for browser compatibility
     * Uses esm.sh CDN for React runtime
     * Uses pre-compiled regex patterns for performance
     */
    private fun transformReact(file: File, content: String): String {
        if (!file.extension.matches(JS_TSX_FILE_REGEX)) return content

        // For HTML files, inject React from CDN
        if (file.extension == "html") {
            return content
        }

        // Transform JSX imports to use CDN (using pre-compiled regex)
        var transformed = content

        // Replace bare imports with esm.sh CDN URLs (supports both default and named imports)
        // Handle: import React from 'react'
        // Handle: import { useState, useEffect } from 'react'
        // Handle: import React, { useState } from 'react'
        transformed = transformed
            .replace(REACT_IMPORT_REGEX, """from "https://esm.sh/react@18.2.0"""")
            .replace(REACT_DOM_IMPORT_REGEX, """from "https://esm.sh/react-dom@18.2.0"""")
            .replace(REACT_DOM_CLIENT_REGEX, """from "https://esm.sh/react-dom@18.2.0/client"""")
            .replace(REACT_ROUTER_REGEX, """from "https://esm.sh/react-router-dom@6"""")

        // Transform relative .jsx/.tsx imports to .js for browser compatibility
        // import App from './App.jsx' -> import App from './App.js'
        transformed = transformed.replace(JSX_IMPORT_REGEX) { match ->
            val path = match.groupValues[1]
            """from "$path.js""""
        }

        // Transform CSS imports to dynamic style injection
        // import './App.css' -> (fetch and inject as <link> tag)
        transformed = transformCssImports(transformed, file)

        // Transform JSX syntax using a simple regex-based approach
        // Note: This is simplified - production would use Babel/SWC
        if (file.extension == "jsx" || file.extension == "tsx") {
            transformed = transformJSXSyntax(transformed)
        }

        return transformed
    }

    /**
     * Transform CSS imports to dynamic style injection code
     * This converts `import './style.css'` to code that fetches and injects the CSS
     * Uses pre-compiled CSS_IMPORT_REGEX for performance
     */
    private fun transformCssImports(content: String, sourceFile: File): String {
        var result = content

        val cssImports = CSS_IMPORT_REGEX.findAll(content).toList()
        if (cssImports.isEmpty()) return content

        val cssInjectionCode = StringBuilder()

        for (match in cssImports) {
            val cssPath = match.groupValues[1]
            // Remove the import statement
            result = result.replace(match.value, "")

            // Calculate the CSS file's path relative to project root
            val cssFile = File(sourceFile.parentFile, cssPath.removePrefix("./"))
            val relativeCssPath = try {
                cssFile.relativeTo(projectRoot).path.replace("\\", "/")
            } catch (e: Exception) {
                cssPath.removePrefix("./")
            }

            // Add code to inject CSS dynamically via <link> tag
            cssInjectionCode.append("""
                |(function() {
                |  if (!document.querySelector('link[href="/$relativeCssPath"]')) {
                |    const link = document.createElement('link');
                |    link.rel = 'stylesheet';
                |    link.href = '/$relativeCssPath';
                |    document.head.appendChild(link);
                |  }
                |})();
            """.trimMargin())
            cssInjectionCode.append("\n")
        }

        // Prepend CSS injection code at the top of the file
        if (cssInjectionCode.isNotEmpty()) {
            result = cssInjectionCode.toString() + result
        }

        return result
    }

    /**
     * Simple JSX to JS transformation
     * Due to limitations of regex-based transformation, we use a different approach:
     * We'll use a Babel-like transform via the browser itself using esm.sh's ?jsx option
     *
     * For the preview to work correctly, we need to:
     * 1. Add proper React imports
     * 2. Let the esm.sh CDN handle JSX transformation via ?jsx query param
     * 3. Keep the JSX syntax intact but ensure all imports resolve correctly
     */
    private fun transformJSXSyntax(jsx: String): String {
        var result = jsx

        // Ensure we have React available for JSX (using pre-compiled regex)
        // esm.sh provides JSX runtime, but we need explicit React imports
        val hasReactImport = result.contains(REACT_IMPORT_DETECT_REGEX) ||
                result.contains(REACT_NAMED_IMPORT_DETECT_REGEX)

        if (!hasReactImport) {
            // Add React import at the top for JSX runtime
            result = """import React from "https://esm.sh/react@18.2.0";
$result"""
        }

        // For browser preview, we'll use a simple approach:
        // Transform the JSX file into HTML that loads it as a module with Babel standalone
        // This is embedded in the HTML transformation for the main entry point

        return result
    }

    /**
     * Generate an HTML wrapper that can execute JSX files in the browser
     * This uses Babel standalone to transform JSX at runtime
     */
    private fun generateJsxHtmlWrapper(entryFile: String): String {
        return """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |    <meta charset="UTF-8">
            |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
            |    <title>Preview</title>
            |    <script src="https://esm.sh/react@18.2.0"></script>
            |    <script src="https://esm.sh/react-dom@18.2.0"></script>
            |    <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
            |    <style>
            |        * { margin: 0; padding: 0; box-sizing: border-box; }
            |        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }
            |    </style>
            |</head>
            |<body>
            |    <div id="root"></div>
            |    <script type="text/babel" data-type="module" src="$entryFile"></script>
            |</body>
            |</html>
        """.trimMargin()
    }

    /**
     * Transform Vue SFC files for browser preview
     * Handles both Options API and <script setup> syntax
     * Uses pre-compiled regex patterns for performance
     */
    private fun transformVue(file: File, content: String): String {
        if (file.extension != "vue") return content

        // Extract template, script, and style sections (using pre-compiled regex)
        val template = TEMPLATE_REGEX.find(content)?.groupValues?.get(1)?.trim() ?: ""
        val scriptSetup = SCRIPT_SETUP_REGEX.find(content)?.groupValues?.get(1)?.trim()
        val script = SCRIPT_REGEX.find(content)?.groupValues?.get(1)?.trim() ?: ""
        val style = STYLE_REGEX.find(content)?.groupValues?.get(1)?.trim() ?: ""

        // Escape backticks in template for JS template literal
        val escapedTemplate = template.replace("`", "\\`").replace("\${", "\\\${")
        val escapedStyle = style.replace("`", "\\`").replace("\${", "\\\${")

        // Check if using <script setup>
        val isScriptSetup = scriptSetup != null

        return if (isScriptSetup) {
            // For <script setup>, we need to wrap the code in a setup function
            // Extract reactive declarations and transform them (using pre-compiled regex)
            val setupCode = scriptSetup!!
                .replace(VUE_IMPORT_REGEX, """from "https://esm.sh/vue@3.4.0"""")
                .replace(VUE_IMPORT_ALL_REGEX, "")

            // Extract variable names declared with const/let for return statement (using pre-compiled regex)
            val constDecls = CONST_DECL_REGEX.findAll(setupCode).map { it.groupValues[1] }.toList()
            val letDecls = LET_DECL_REGEX.findAll(setupCode).map { it.groupValues[1] }.toList()
            val funcDecls = FUNC_OR_CONST_DECL_REGEX.findAll(setupCode)
                .map { it.groupValues[1] }.filter { !it.matches(VUE_BUILTINS_REGEX) }.toList()
            val allDecls = (constDecls + letDecls + funcDecls).distinct()
            val returnStatement = if (allDecls.isNotEmpty()) "return { ${allDecls.joinToString(", ")} };" else ""

            """
                |// Vue SFC compiled for browser preview (script setup)
                |const { ref, reactive, computed, watch, onMounted, onUnmounted } = Vue;
                |
                |${if (escapedStyle.isNotEmpty()) """
                |(function() {
                |  const styleId = 'vue-style-${file.nameWithoutExtension}';
                |  if (!document.getElementById(styleId)) {
                |    const style = document.createElement('style');
                |    style.id = styleId;
                |    style.textContent = `$escapedStyle`;
                |    document.head.appendChild(style);
                |  }
                |})();
                |""".trimMargin() else ""}
                |
                |export default {
                |  setup() {
                |    $setupCode
                |    $returnStatement
                |  },
                |  template: `$escapedTemplate`
                |};
            """.trimMargin()
        } else {
            // Options API - transform the script directly (using pre-compiled regex)
            val transformedScript = script
                .replace(VUE_IMPORT_REGEX, """from "https://esm.sh/vue@3.4.0"""")
                .replace(VUE_EXPORT_DEFAULT_REGEX, "const componentOptions = {")

            """
                |// Vue SFC compiled for browser preview (Options API)
                |const { ref, reactive, computed, watch, onMounted, onUnmounted } = Vue;
                |
                |${if (escapedStyle.isNotEmpty()) """
                |(function() {
                |  const styleId = 'vue-style-${file.nameWithoutExtension}';
                |  if (!document.getElementById(styleId)) {
                |    const style = document.createElement('style');
                |    style.id = styleId;
                |    style.textContent = `$escapedStyle`;
                |    document.head.appendChild(style);
                |  }
                |})();
                |""".trimMargin() else ""}
                |
                |$transformedScript
                |
                |export default {
                |  ...componentOptions,
                |  template: `$escapedTemplate`
                |};
            """.trimMargin()
        }
    }

    /**
     * Transform Svelte files for browser preview
     * Creates a reactive vanilla JS component that mimics Svelte behavior
     * Uses pre-compiled regex patterns for performance
     */
    private fun transformSvelte(file: File, content: String): String {
        if (file.extension != "svelte") return content

        // Extract script and template (using pre-compiled regex - reuse SCRIPT_REGEX and STYLE_REGEX)
        val script = SCRIPT_REGEX.find(content)?.groupValues?.get(1)?.trim() ?: ""
        val style = STYLE_REGEX.find(content)?.groupValues?.get(1)?.trim() ?: ""
        val template = content
            .replace(SCRIPT_REGEX, "")
            .replace(STYLE_REGEX, "")
            .trim()

        // Escape backticks in template and style
        val escapedTemplate = template.replace("`", "\\`").replace("\${", "\\\${")
        val escapedStyle = style.replace("`", "\\`").replace("\${", "\\\${")

        // Parse script for reactive variable declarations (let x = ...) using pre-compiled regex
        val reactiveVars = SVELTE_VAR_DECL_REGEX.findAll(script).map { it.groupValues[1] }.toList()

        // Transform Svelte template syntax to JS (using pre-compiled regex)
        // {variable} -> ${state.variable}
        // on:click={handler} -> onclick="handler()"
        // {#if condition}...{/if} -> conditional rendering
        var jsTemplate = escapedTemplate
            .replace(SVELTE_VAR_INTERPOLATION_REGEX) { "\${state.${it.groupValues[1]}}" }
            .replace(SVELTE_CLICK_HANDLER_REGEX) { "onclick=\"\$1()\"" }

        // Generate a reactive component
        return """
            |// Svelte component compiled for browser preview
            |
            |${if (escapedStyle.isNotEmpty()) """
            |(function() {
            |  const styleId = 'svelte-style-${file.nameWithoutExtension}';
            |  if (!document.getElementById(styleId)) {
            |    const style = document.createElement('style');
            |    style.id = styleId;
            |    style.textContent = `$escapedStyle`;
            |    document.head.appendChild(style);
            |  }
            |})();
            |""".trimMargin() else ""}
            |
            |export default class Component {
            |  constructor(options) {
            |    this.target = options.target;
            |    this.state = {};
            |    this._handlers = {};
            |
            |    // Initialize reactive state
            |    ${reactiveVars.joinToString("\n    ") { "this.state.$it = ${getInitialValue(script, it)};" }}
            |
            |    // Bind methods
            |    ${extractFunctions(script).joinToString("\n    ") { "this.$it = this.$it.bind(this);" }}
            |
            |    this.render();
            |  }
            |
            |  // Reactive setter
            |  set(key, value) {
            |    this.state[key] = value;
            |    this.render();
            |  }
            |
            |  ${extractFunctions(script).joinToString("\n  ") { fn ->
                val fnBody = extractFunctionBody(script, fn)
                """$fn() {
            |    ${fnBody.replace(SVELTE_INCREMENT_REGEX) { "this.set('${it.groupValues[1]}', this.state.${it.groupValues[1]} + 1)" }
                      .replace(SVELTE_ASSIGNMENT_REGEX) { "this.set('${it.groupValues[1]}', ${it.groupValues[2]})" }}
            |  }"""
            }}
            |
            |  render() {
            |    const state = this.state;
            |    const self = this;
            |    this.target.innerHTML = `$jsTemplate`;
            |
            |    // Rebind event handlers
            |    this.target.querySelectorAll('[onclick]').forEach(el => {
            |      const handler = el.getAttribute('onclick').replace('()', '');
            |      el.onclick = () => self[handler]();
            |      el.removeAttribute('onclick');
            |    });
            |  }
            |
            |  destroy() {
            |    this.target.innerHTML = '';
            |  }
            |}
        """.trimMargin()
    }

    /**
     * Extract initial value for a variable from script
     */
    private fun getInitialValue(script: String, varName: String): String {
        val regex = Regex("""let\s+$varName\s*=\s*([^;\n]+)""")
        return regex.find(script)?.groupValues?.get(1)?.trim() ?: "null"
    }

    /**
     * Extract function names from script
     * Uses pre-compiled SVELTE_FUNC_REGEX for performance
     */
    private fun extractFunctions(script: String): List<String> {
        return SVELTE_FUNC_REGEX.findAll(script).map { it.groupValues[1] }.toList()
    }

    /**
     * Extract function body from script
     */
    private fun extractFunctionBody(script: String, fnName: String): String {
        val regex = Regex("""function\s+$fnName\s*\([^)]*\)\s*\{([^}]*)\}""")
        return regex.find(script)?.groupValues?.get(1)?.trim() ?: ""
    }

    /**
     * Transform Next.js files
     * Uses pre-compiled regex patterns for performance
     */
    private fun transformNextJs(file: File, content: String): String {
        // Next.js pages are React components, transform like React
        if (file.extension.matches(JS_TSX_FILE_REGEX)) {
            var transformed = transformReact(file, content)

            // Handle Next.js specific imports (using pre-compiled regex)
            transformed = transformed
                .replace(NEXT_HEAD_IMPORT_REGEX, """from "https://esm.sh/next@14/head"""")
                .replace(NEXT_LINK_IMPORT_REGEX, """from "https://esm.sh/next@14/link"""")
                .replace(NEXT_IMAGE_IMPORT_REGEX, """from "https://esm.sh/next@14/image"""")
                .replace(NEXT_ROUTER_IMPORT_REGEX, """from "https://esm.sh/next@14/router"""")

            return transformed
        }
        return content
    }

    /**
     * Inject hot reload script into HTML
     */
    private fun injectHotReload(html: String): String {
        val hotReloadScript = """
            |<script>
            |(function() {
            |  let lastTimestamp = 0;
            |  const checkInterval = 1000; // Check every second
            |
            |  async function checkForChanges() {
            |    try {
            |      const response = await fetch('/__dev_changes__');
            |      const data = await response.json();
            |
            |      if (lastTimestamp > 0 && data.timestamp > lastTimestamp) {
            |        console.log('[DevServer] Changes detected, reloading...');
            |        location.reload();
            |      }
            |      lastTimestamp = data.timestamp;
            |    } catch (e) {
            |      // Server might be restarting
            |    }
            |  }
            |
            |  // Start checking for changes
            |  setInterval(checkForChanges, checkInterval);
            |  checkForChanges();
            |
            |  console.log('[DevServer] Hot reload enabled');
            |})();
            |</script>
        """.trimMargin()

        // Insert before closing body tag, or at end (using pre-compiled regex)
        return if (html.contains("</body>", ignoreCase = true)) {
            html.replace(BODY_CLOSE_REGEX, "$hotReloadScript\n</body>")
        } else if (html.contains("</html>", ignoreCase = true)) {
            html.replace(HTML_CLOSE_REGEX, "$hotReloadScript\n</html>")
        } else {
            "$html\n$hotReloadScript"
        }
    }

    private fun sendResponse(
        output: BufferedOutputStream,
        statusCode: Int,
        contentType: String,
        content: ByteArray
    ) {
        val statusText = when (statusCode) {
            200 -> "OK"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            else -> "Unknown"
        }

        val headers = buildString {
            appendLine("HTTP/1.1 $statusCode $statusText")
            appendLine("Content-Type: $contentType")
            appendLine("Content-Length: ${content.size}")
            appendLine("Access-Control-Allow-Origin: *")
            appendLine("Access-Control-Allow-Methods: GET, POST, OPTIONS")
            appendLine("Access-Control-Allow-Headers: *")
            appendLine("Cache-Control: no-cache, no-store, must-revalidate")
            appendLine("Connection: close")
            appendLine()
        }

        output.write(headers.toByteArray())
        output.write(content)
        output.flush()
    }

    private fun sendError(output: BufferedOutputStream, statusCode: Int, message: String) {
        val html = """
            |<!DOCTYPE html>
            |<html>
            |<head><title>Error $statusCode</title></head>
            |<body style="font-family: sans-serif; padding: 2rem; background: #1a1a2e; color: #fff;">
            |<h1>Error $statusCode</h1>
            |<p>$message</p>
            |</body>
            |</html>
        """.trimMargin()
        sendResponse(output, statusCode, "text/html", html.toByteArray())
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "jsx" -> "application/javascript; charset=utf-8"
            "ts", "tsx" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "xml" -> "application/xml; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "eot" -> "application/vnd.ms-fontobject"
            "otf" -> "font/otf"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain; charset=utf-8"
            "md" -> "text/markdown; charset=utf-8"
            "vue" -> "application/javascript; charset=utf-8"
            "svelte" -> "application/javascript; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private fun needsTransformation(extension: String): Boolean {
        // Include js/mjs for CSS import transformation in Vue/Svelte main.js files
        return extension in listOf("js", "mjs", "jsx", "tsx", "vue", "svelte")
    }

    private fun isServableFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext in listOf(
            "html", "htm", "css", "js", "mjs", "jsx", "ts", "tsx",
            "json", "xml", "svg", "png", "jpg", "jpeg", "gif", "webp", "ico",
            "woff", "woff2", "ttf", "eot", "otf", "mp4", "webm", "mp3", "wav",
            "pdf", "txt", "md", "vue", "svelte"
        )
    }

    private fun indexProjectFiles() {
        projectRoot.walkTopDown()
            .filter { it.isFile && isServableFile(it) }
            .forEach { file ->
                val relativePath = file.relativeTo(projectRoot).path
                fileModificationTimes[relativePath] = file.lastModified()
            }
    }

    /**
     * Release resources
     */
    fun destroy() {
        stop()
        serverScope.cancel()
    }
}

/**
 * Supported framework types for preview
 */
enum class FrameworkType(val displayName: String) {
    VANILLA("Vanilla HTML/JS"),
    REACT("React"),
    VUE("Vue.js"),
    SVELTE("Svelte"),
    NEXTJS("Next.js"),
    TAILWIND("Tailwind CSS");

    companion object {
        /**
         * Detect framework type from project files
         */
        fun detect(projectRoot: File): FrameworkType {
            val packageJson = File(projectRoot, "package.json")

            if (packageJson.exists()) {
                try {
                    val content = packageJson.readText().lowercase()
                    return when {
                        content.contains("\"next\"") -> NEXTJS
                        content.contains("\"svelte\"") -> SVELTE
                        content.contains("\"vue\"") -> VUE
                        content.contains("\"react\"") -> REACT
                        else -> VANILLA
                    }
                } catch (e: Exception) {
                    // Fall through to file-based detection
                }
            }

            // File-based detection
            val hasJsx = projectRoot.walkTopDown().any { it.extension == "jsx" }
            val hasVue = projectRoot.walkTopDown().any { it.extension == "vue" }
            val hasSvelte = projectRoot.walkTopDown().any { it.extension == "svelte" }
            val hasTailwind = File(projectRoot, "tailwind.config.js").exists() ||
                    File(projectRoot, "tailwind.config.ts").exists()

            return when {
                hasSvelte -> SVELTE
                hasVue -> VUE
                hasJsx -> REACT
                hasTailwind -> TAILWIND
                else -> VANILLA
            }
        }
    }
}
