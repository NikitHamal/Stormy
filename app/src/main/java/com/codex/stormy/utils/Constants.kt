package com.codex.stormy.utils

/**
 * Application-wide constants to centralize magic numbers and strings.
 * This improves maintainability and makes configuration changes easier.
 *
 * All limits are carefully tuned to balance functionality vs. resource usage
 * on low-end Android devices while preventing edge case failures.
 */
object Constants {

    /**
     * Editor and UI Constants
     */
    object Editor {
        const val DEFAULT_FONT_SIZE = 14f
        const val MIN_FONT_SIZE = 8f  // Lowered for accessibility
        const val MAX_FONT_SIZE = 48f // Raised for accessibility
        const val DEFAULT_TAB_SIZE = 4
        const val DEFAULT_LINE_NUMBERS = true
        const val DEFAULT_WORD_WRAP = true
        const val MAX_LINE_LENGTH = 10000 // Prevent UI freeze on very long lines
        const val MAX_UNDO_HISTORY = 100 // Limit undo stack memory
    }

    /**
     * Drawer Widths
     */
    object DrawerWidth {
        const val FILE_TREE = 280
        const val GIT_PANEL = 320
        const val ASSET_MANAGER = 300
        const val INSPECTOR = 360
        const val MIN_DRAWER = 200
        const val MAX_DRAWER = 500
    }

    /**
     * Animation Durations (ms)
     */
    object Animation {
        const val FAST = 150
        const val NORMAL = 300
        const val SLOW = 500
        const val DRAWER_OPEN = 300
        const val TAB_SWITCH = 200
        const val FADE = 150
        const val PROGRESS_UPDATE = 100 // Minimum time between progress updates
    }

    /**
     * AI and Chat Constants - Carefully tuned for reliability
     */
    object Ai {
        // Agent iteration limits
        const val MAX_AGENT_ITERATIONS = 50 // Raised from 25 for complex tasks
        const val MIN_AGENT_ITERATIONS = 1
        const val DEFAULT_AGENT_ITERATIONS = 25
        const val ITERATION_TIMEOUT_PER_STEP_MS = 60000L // 1 min per iteration

        // Temperature and generation settings
        const val DEFAULT_TEMPERATURE = 0.7f
        const val MIN_TEMPERATURE = 0.0f
        const val MAX_TEMPERATURE = 2.0f
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_MAX_TOKENS = 4096
        const val MAX_OUTPUT_TOKENS = 16384 // Raised for longer responses

        // Context window management
        const val DEFAULT_CONTEXT_TOKENS = 8000
        const val MIN_CONTEXT_TOKENS = 1000
        const val MAX_CONTEXT_TOKENS = 128000 // For Llama 3.3 128k context
        const val CONTEXT_BUFFER_TOKENS = 1000 // Safety buffer
        const val CHARS_PER_TOKEN_ESTIMATE = 4.0f // For token estimation

        // Message limits
        const val MESSAGE_TRUNCATE_LENGTH = 500
        const val MAX_MESSAGE_LENGTH = 100000 // 100KB max per message
        const val MAX_SYSTEM_PROMPT_LENGTH = 50000
        const val MAX_TOOL_RESULT_LENGTH = 50000

        // Tool call limits
        const val MAX_TOOL_CALLS_PER_RESPONSE = 20
        const val MAX_PARALLEL_TOOL_CALLS = 5
        const val TOOL_EXECUTION_TIMEOUT_MS = 30000L
        const val MAX_TOOL_RETRIES = 3

        // Streaming settings
        const val STREAM_DEBOUNCE_MS = 100L
        const val STREAM_BUFFER_SIZE = 8192
        const val MAX_STREAM_DURATION_MS = 300000L // 5 minute max
        const val STREAM_CHUNK_TIMEOUT_MS = 60000L // 1 min between chunks

        // Rate limiting
        const val MIN_REQUEST_INTERVAL_MS = 500L // Prevent request flooding
        const val REQUESTS_PER_MINUTE_LIMIT = 60
        const val REQUESTS_PER_HOUR_LIMIT = 500

        // Error recovery
        const val MAX_CONSECUTIVE_ERRORS = 5
        const val ERROR_BACKOFF_INITIAL_MS = 1000L
        const val ERROR_BACKOFF_MAX_MS = 60000L
        const val ERROR_BACKOFF_MULTIPLIER = 2.0f
    }

    /**
     * File Size Limits - Balanced for mobile devices
     */
    object FileSize {
        const val MAX_PREVIEW_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB
        const val MAX_EDIT_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        const val MAX_ASSET_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
        const val MAX_AI_READ_SIZE_BYTES = 1 * 1024 * 1024L // 1 MB for AI context
        const val MAX_PROJECT_SIZE_BYTES = 500 * 1024 * 1024L // 500 MB total project
        const val LARGE_FILE_THRESHOLD_BYTES = 512 * 1024L // 512 KB
        const val MAX_FILES_PER_PROJECT = 10000
        const val MAX_FOLDER_DEPTH = 50 // Prevent stack overflow on deep nesting
        const val CHUNK_SIZE_BYTES = 64 * 1024L // 64 KB chunks for large files
        const val MIN_FREE_SPACE_BYTES = 100 * 1024 * 1024L // 100 MB minimum
    }

    /**
     * Preview Constants
     */
    object Preview {
        const val DEFAULT_DESKTOP_SCALE = 50
        const val MIN_SCALE = 10 // Lowered for better overview
        const val MAX_SCALE = 400 // Raised for detail work
        const val TOUCH_SLOP = 4
        const val DOUBLE_TAP_TIMEOUT_MS = 300L
        const val REFRESH_DEBOUNCE_MS = 500L
        const val MAX_PREVIEW_HISTORY = 50
    }

    /**
     * Git Constants
     */
    object Git {
        const val DEFAULT_REMOTE_NAME = "origin"
        const val DEFAULT_BRANCH_NAME = "main"
        const val MAX_COMMIT_MESSAGE_LENGTH = 500
        const val DIFF_CONTEXT_LINES = 3
        const val MAX_DIFF_SIZE_BYTES = 1 * 1024 * 1024L // 1 MB max diff
        const val MAX_COMMITS_TO_SHOW = 500
        const val MAX_FILES_PER_COMMIT = 100
        const val CLONE_TIMEOUT_MS = 300000L // 5 minutes
        const val PUSH_PULL_TIMEOUT_MS = 120000L // 2 minutes
        const val FETCH_TIMEOUT_MS = 60000L
    }

    /**
     * Database Constants
     */
    object Database {
        const val DATABASE_NAME = "codex_database"
        const val DATABASE_VERSION = 4 // Version 4: Added resilience tracking tables
        const val MESSAGE_PAGE_SIZE = 50
        const val PROJECT_PAGE_SIZE = 20
        const val MAX_MESSAGES_PER_PROJECT = 10000
        const val MAX_PROJECTS = 500
        const val VACUUM_THRESHOLD_MB = 50 // Auto-vacuum when DB exceeds this
        const val CHECKPOINT_INTERVAL_OPS = 1000
        const val WAL_CHECKPOINT_TRUNCATE = true
        const val ERROR_LOG_RETENTION_DAYS = 7
        const val API_STATS_RETENTION_DAYS = 30
    }

    /**
     * Project Template Types
     */
    object ProjectTemplates {
        const val BLANK = "blank"
        const val HTML_CSS = "html_css"
        const val HTML_CSS_JS = "html_css_js"
        const val BOOTSTRAP = "bootstrap"
        const val TAILWIND = "tailwind"
        const val REACT = "react"
        const val VUE = "vue"
    }

    /**
     * Timeouts and Delays (ms)
     */
    object Timeout {
        const val NETWORK_TIMEOUT = 30000L
        const val NETWORK_READ_TIMEOUT = 60000L
        const val NETWORK_WRITE_TIMEOUT = 30000L
        const val GIT_OPERATION_TIMEOUT = 60000L
        const val AI_STREAM_TIMEOUT = 180000L // Raised to 3 min
        const val AI_CONNECT_TIMEOUT = 15000L
        const val DEBOUNCE_DELAY = 300L
        const val AUTO_SAVE_DELAY = 5000L
        const val FILE_WATCH_DEBOUNCE = 500L
        const val SEARCH_DEBOUNCE = 200L
        const val KEYBOARD_DEBOUNCE = 100L
    }

    /**
     * Retry Configuration
     */
    object Retry {
        const val MAX_RETRIES = 3
        const val INITIAL_DELAY_MS = 1000L
        const val MAX_DELAY_MS = 30000L
        const val BACKOFF_MULTIPLIER = 2.0
        const val JITTER_FACTOR = 0.1 // 10% random jitter

        // Specific retry policies
        const val AI_REQUEST_RETRIES = 3
        const val NETWORK_RETRIES = 2
        const val FILE_OPERATION_RETRIES = 2
        const val GIT_OPERATION_RETRIES = 1
    }

    /**
     * Memory and Performance Limits
     */
    object Memory {
        const val MAX_CACHE_SIZE_MB = 50
        const val MAX_IMAGE_CACHE_SIZE_MB = 25
        const val MAX_SYNTAX_HIGHLIGHT_LINES = 5000 // Disable for very large files
        const val MAX_FILE_TREE_ITEMS = 5000
        const val RECYCLE_THRESHOLD_ITEMS = 100
        const val GC_TRIGGER_THRESHOLD_MB = 100
    }

    /**
     * Security Constants
     */
    object Security {
        const val MAX_PATH_LENGTH = 4096
        const val FORBIDDEN_CHARS = "<>:\"|?*\u0000"
        const val MAX_FILENAME_LENGTH = 255
        val DANGEROUS_EXTENSIONS = setOf("exe", "bat", "cmd", "sh", "ps1", "vbs", "msi", "dll")
        val SENSITIVE_PATTERNS = setOf(".env", "credentials", "secrets", "private", "password", "apikey", "api_key")
    }

    /**
     * Asset Directories to Scan
     */
    val ASSET_DIRECTORIES = listOf(
        "assets", "images", "img", "fonts", "media", "static", "public"
    )

    /**
     * Files/Folders to Ignore
     */
    val IGNORED_PATTERNS = setOf(
        ".git", ".gitignore", ".DS_Store", "Thumbs.db",
        "node_modules", ".idea", ".gradle", "build", "dist",
        "__pycache__", ".env", ".env.local", ".svn", ".hg",
        "*.pyc", "*.pyo", "*.class", "*.o", "*.obj"
    )

    /**
     * Supported file extensions for syntax highlighting
     */
    val CODE_EXTENSIONS = setOf(
        "html", "htm", "css", "scss", "sass", "less",
        "js", "jsx", "ts", "tsx", "mjs", "cjs",
        "json", "xml", "yaml", "yml", "toml",
        "md", "markdown", "txt", "csv",
        "py", "rb", "php", "java", "kt", "swift", "go", "rs",
        "c", "cpp", "h", "hpp", "cs", "m", "mm",
        "sql", "sh", "bash", "zsh", "ps1",
        "dockerfile", "makefile", "gradle"
    )

    /**
     * Binary file extensions (skip for text operations)
     */
    val BINARY_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "svg",
        "mp3", "wav", "ogg", "mp4", "webm", "avi", "mov",
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "zip", "rar", "7z", "tar", "gz", "bz2",
        "exe", "dll", "so", "dylib", "apk", "ipa",
        "ttf", "otf", "woff", "woff2", "eot"
    )

    /**
     * Check if a file/folder should be ignored
     */
    fun shouldIgnore(name: String): Boolean {
        if (name.isBlank()) return true
        if (name in IGNORED_PATTERNS) return true
        if (name.startsWith(".") && name != ".codex") return true

        // Check wildcard patterns
        IGNORED_PATTERNS.filter { it.contains("*") }.forEach { pattern ->
            val regex = pattern.replace(".", "\\.").replace("*", ".*")
            if (Regex(regex, RegexOption.IGNORE_CASE).matches(name)) return true
        }
        return false
    }

    /**
     * Check if file is binary based on extension
     */
    fun isBinaryFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in BINARY_EXTENSIONS
    }

    /**
     * Check if file is a code file
     */
    fun isCodeFile(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return ext in CODE_EXTENSIONS
    }

    /**
     * Validate path for security
     */
    fun isValidPath(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.length > Security.MAX_PATH_LENGTH) return false
        if (path.contains("..")) return false // Path traversal
        Security.FORBIDDEN_CHARS.forEach { if (path.contains(it)) return false }
        return true
    }

    /**
     * Sanitize filename
     */
    fun sanitizeFilename(name: String): String {
        var sanitized = name.trim()
        Security.FORBIDDEN_CHARS.forEach { sanitized = sanitized.replace(it.toString(), "_") }
        if (sanitized.length > Security.MAX_FILENAME_LENGTH) {
            val ext = sanitized.substringAfterLast('.', "")
            val base = sanitized.substringBeforeLast('.')
            val maxBase = Security.MAX_FILENAME_LENGTH - ext.length - 1
            sanitized = base.take(maxBase) + "." + ext
        }
        return sanitized.ifBlank { "unnamed" }
    }

    /**
     * Calculate exponential backoff delay
     */
    fun calculateBackoffDelay(attempt: Int, config: Retry = Retry): Long {
        val exponentialDelay = (config.INITIAL_DELAY_MS * Math.pow(config.BACKOFF_MULTIPLIER, attempt.toDouble())).toLong()
        val cappedDelay = minOf(exponentialDelay, config.MAX_DELAY_MS)
        val jitter = (cappedDelay * config.JITTER_FACTOR * Math.random()).toLong()
        return cappedDelay + jitter
    }

    /**
     * Estimate tokens from text
     */
    fun estimateTokens(text: String): Int {
        return (text.length / Ai.CHARS_PER_TOKEN_ESTIMATE).toInt() + 1
    }

    /**
     * Truncate text to fit token limit
     */
    fun truncateToTokenLimit(text: String, maxTokens: Int): String {
        val maxChars = (maxTokens * Ai.CHARS_PER_TOKEN_ESTIMATE).toInt()
        return if (text.length > maxChars) {
            text.take(maxChars - 3) + "..."
        } else text
    }
}
