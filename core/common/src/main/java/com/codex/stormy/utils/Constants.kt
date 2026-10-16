package com.codex.stormy.utils

/**
 * Application-wide constants to centralize magic numbers and strings.
 * This improves maintainability and makes configuration changes easier.
 */
object Constants {

    /**
     * Editor and UI Constants
     */
    object Editor {
        const val DEFAULT_FONT_SIZE = 14f
        const val MIN_FONT_SIZE = 10f
        const val MAX_FONT_SIZE = 32f
        const val DEFAULT_TAB_SIZE = 4
        const val DEFAULT_LINE_NUMBERS = true
        const val DEFAULT_WORD_WRAP = true
    }

    /**
     * Drawer Widths
     */
    object DrawerWidth {
        const val FILE_TREE = 280
        const val GIT_PANEL = 320
        const val ASSET_MANAGER = 300
        const val INSPECTOR = 360
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
    }

    /**
     * AI and Chat Constants
     */
    object Ai {
        const val MAX_AGENT_ITERATIONS = 25
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_CONTEXT_TOKENS = 8000
        const val MESSAGE_TRUNCATE_LENGTH = 500
    }

    /**
     * File Size Limits
     */
    object FileSize {
        const val MAX_PREVIEW_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB
        const val MAX_EDIT_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        const val MAX_ASSET_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB
    }

    /**
     * Preview Constants
     */
    object Preview {
        const val DEFAULT_DESKTOP_SCALE = 50
        const val MIN_SCALE = 25
        const val MAX_SCALE = 200
        const val TOUCH_SLOP = 4
    }

    /**
     * Git Constants
     */
    object Git {
        const val DEFAULT_REMOTE_NAME = "origin"
        const val DEFAULT_BRANCH_NAME = "main"
        const val MAX_COMMIT_MESSAGE_LENGTH = 500
        const val DIFF_CONTEXT_LINES = 3
    }

    /**
     * Database Constants
     */
    object Database {
        const val DATABASE_NAME = "codex_database"
        const val DATABASE_VERSION = 1
        const val MESSAGE_PAGE_SIZE = 50
        const val PROJECT_PAGE_SIZE = 20
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
        const val GIT_OPERATION_TIMEOUT = 60000L
        const val AI_STREAM_TIMEOUT = 120000L
        const val DEBOUNCE_DELAY = 300L
        const val AUTO_SAVE_DELAY = 5000L
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
        "__pycache__", ".env", ".env.local"
    )

    /**
     * Check if a file/folder should be ignored
     */
    fun shouldIgnore(name: String): Boolean {
        return name in IGNORED_PATTERNS || name.startsWith(".")
    }
}
