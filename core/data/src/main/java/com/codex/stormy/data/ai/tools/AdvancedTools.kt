package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.ai.FunctionDefinition
import com.codex.stormy.data.ai.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Advanced tools for enhanced agent capabilities.
 *
 * These tools provide more sophisticated operations for:
 * - Code analysis and refactoring
 * - Multi-file operations
 * - Project management
 * - Dependency analysis
 * - Code quality checks
 */
object AdvancedTools {

    private val json = Json { encodeDefaults = true }

    @Serializable
    private data class ParameterProperty(
        val type: String,
        val description: String,
        val enum: List<String>? = null
    )

    @Serializable
    private data class ParametersSchema(
        val type: String = "object",
        val properties: Map<String, ParameterProperty>,
        val required: List<String>
    )

    private fun createParametersJson(
        properties: Map<String, Triple<String, String, List<String>?>>,
        required: List<String>
    ): JsonElement {
        val schema = ParametersSchema(
            type = "object",
            properties = properties.mapValues { (_, value) ->
                ParameterProperty(type = value.first, description = value.second, enum = value.third)
            },
            required = required
        )
        return json.parseToJsonElement(json.encodeToString(schema))
    }

    // Simple parameter helper without enum
    private fun param(type: String, desc: String) = Triple(type, desc, null as List<String>?)
    private fun enumParam(type: String, desc: String, values: List<String>) = Triple(type, desc, values)

    // ==================== Code Analysis Tools ====================

    val FIND_REFERENCES = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "find_references",
            description = "Find all references to a symbol (function, variable, class, etc.) across the project. Useful for refactoring and understanding code usage.",
            parameters = createParametersJson(
                properties = mapOf(
                    "symbol" to param("string", "The symbol name to find references for (e.g., 'handleClick', 'UserModel', 'API_URL')"),
                    "file_pattern" to param("string", "Optional file pattern to limit search (e.g., '*.js', '*.tsx')")
                ),
                required = listOf("symbol")
            )
        )
    )

    val FIND_DEFINITION = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "find_definition",
            description = "Find the definition of a symbol (function, class, variable, etc.). Returns the file path and line number where it's defined.",
            parameters = createParametersJson(
                properties = mapOf(
                    "symbol" to param("string", "The symbol name to find the definition for"),
                    "file_pattern" to param("string", "Optional file pattern to limit search")
                ),
                required = listOf("symbol")
            )
        )
    )

    val ANALYZE_IMPORTS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "analyze_imports",
            description = "Analyze imports in a file or across the project. Shows what modules/packages are used and identifies unused imports.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "File path to analyze, or empty for whole project"),
                    "show_unused" to param("boolean", "If true, highlight potentially unused imports")
                ),
                required = emptyList()
            )
        )
    )

    val ANALYZE_DEPENDENCIES = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "analyze_dependencies",
            description = "Analyze project dependencies from package.json, build.gradle, or similar config files. Shows installed packages, their versions, and potential updates.",
            parameters = createParametersJson(
                properties = mapOf(
                    "include_dev" to param("boolean", "Include dev dependencies in analysis"),
                    "check_updates" to param("boolean", "Check for available updates (requires network)")
                ),
                required = emptyList()
            )
        )
    )

    // ==================== Batch Operation Tools ====================

    val BATCH_RENAME = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "batch_rename",
            description = "Rename a symbol across multiple files. Performs a safe rename operation with preview option.",
            parameters = createParametersJson(
                properties = mapOf(
                    "old_name" to param("string", "The current name of the symbol"),
                    "new_name" to param("string", "The new name for the symbol"),
                    "file_pattern" to param("string", "File pattern to limit the rename scope (e.g., 'src/**/*.js')"),
                    "dry_run" to param("boolean", "If true, only show what would be changed without making changes")
                ),
                required = listOf("old_name", "new_name")
            )
        )
    )

    val BATCH_MODIFY = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "batch_modify",
            description = "Apply a modification to multiple files matching a pattern. Useful for bulk updates like adding headers, changing imports, etc.",
            parameters = createParametersJson(
                properties = mapOf(
                    "file_pattern" to param("string", "Glob pattern for files to modify (e.g., 'src/**/*.css')"),
                    "operation" to enumParam("string", "Type of operation to perform", listOf("prepend", "append", "replace", "insert_after", "insert_before")),
                    "target" to param("string", "For replace/insert operations, the text to find"),
                    "content" to param("string", "The content to add/replace with"),
                    "dry_run" to param("boolean", "If true, only show what would be changed")
                ),
                required = listOf("file_pattern", "operation", "content")
            )
        )
    )

    // ==================== Code Quality Tools ====================

    val CHECK_SYNTAX = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "check_syntax",
            description = "Check a file for syntax errors. Supports HTML, CSS, JavaScript, JSON, and other common formats.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the file to check")
                ),
                required = listOf("path")
            )
        )
    )

    val FORMAT_CODE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "format_code",
            description = "Format code according to language conventions. Fixes indentation, spacing, and style issues.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the file to format"),
                    "indent_size" to param("integer", "Number of spaces for indentation (default: 2)"),
                    "use_tabs" to param("boolean", "Use tabs instead of spaces for indentation")
                ),
                required = listOf("path")
            )
        )
    )

    val VALIDATE_JSON = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "validate_json",
            description = "Validate a JSON file and report any errors with line numbers and suggestions.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the JSON file to validate")
                ),
                required = listOf("path")
            )
        )
    )

    val VALIDATE_HTML = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "validate_html",
            description = "Validate HTML structure and report issues like unclosed tags, invalid nesting, accessibility problems.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the HTML file to validate"),
                    "check_accessibility" to param("boolean", "Include accessibility checks (WCAG compliance)")
                ),
                required = listOf("path")
            )
        )
    )

    // ==================== Project Structure Tools ====================

    val CREATE_COMPONENT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "create_component",
            description = "Create a new component with boilerplate code. Automatically detects framework (React, Vue, Svelte) and generates appropriate files.",
            parameters = createParametersJson(
                properties = mapOf(
                    "name" to param("string", "Component name (e.g., 'UserCard', 'LoginForm')"),
                    "path" to param("string", "Directory to create the component in (e.g., 'src/components')"),
                    "type" to enumParam("string", "Component type", listOf("functional", "class", "page", "layout")),
                    "with_styles" to param("boolean", "Generate accompanying style file"),
                    "with_tests" to param("boolean", "Generate test file skeleton")
                ),
                required = listOf("name", "path")
            )
        )
    )

    val SCAFFOLD_PROJECT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "scaffold_project",
            description = "Add common project structure elements like folder organization, config files, or feature scaffolds.",
            parameters = createParametersJson(
                properties = mapOf(
                    "scaffold_type" to enumParam("string", "Type of scaffolding to add", listOf("folder_structure", "api_routes", "auth_setup", "database_config", "testing_setup", "ci_workflow")),
                    "options" to param("string", "Additional options as JSON string (varies by scaffold type)")
                ),
                required = listOf("scaffold_type")
            )
        )
    )

    // ==================== Context Analysis Tools ====================

    val GET_FILE_SUMMARY = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "get_file_summary",
            description = "Get a summary of a file's contents including exports, functions, classes, and key structures. Useful for understanding a file without reading all content.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the file to summarize")
                ),
                required = listOf("path")
            )
        )
    )

    val GET_PROJECT_STRUCTURE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "get_project_structure",
            description = "Get a detailed overview of project structure including frameworks used, folder organization, and key files.",
            parameters = createParametersJson(
                properties = mapOf(
                    "depth" to param("integer", "Maximum folder depth to analyze (default: 3)")
                ),
                required = emptyList()
            )
        )
    )

    // ==================== Documentation Tools ====================

    val GENERATE_DOCS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "generate_docs",
            description = "Generate documentation comments for a function, class, or file. Creates JSDoc, KDoc, or appropriate format.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the file"),
                    "symbol" to param("string", "Optional specific function/class to document. If empty, documents entire file."),
                    "style" to enumParam("string", "Documentation style", listOf("jsdoc", "tsdoc", "kdoc", "markdown"))
                ),
                required = listOf("path")
            )
        )
    )

    // ==================== Run/Execute Tools ====================

    val RUN_SCRIPT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "run_script",
            description = "Run a script defined in package.json. Useful for running builds, tests, or custom scripts.",
            parameters = createParametersJson(
                properties = mapOf(
                    "script_name" to param("string", "Name of the script from package.json scripts section"),
                    "args" to param("string", "Additional arguments to pass to the script")
                ),
                required = listOf("script_name")
            )
        )
    )

    // ==================== Diff Tools ====================

    val DIFF_FILES = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "diff_files",
            description = "Compare two files and show the differences. Supports unified diff, side-by-side, and stats-only output formats.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path1" to param("string", "Path to the first file"),
                    "path2" to param("string", "Path to the second file"),
                    "format" to enumParam("string", "Output format for the diff", listOf("unified", "side_by_side", "stats_only"))
                ),
                required = listOf("path1", "path2")
            )
        )
    )

    val DIFF_CONTENT = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "diff_content",
            description = "Compare two text contents directly (useful for comparing proposed changes). Returns a unified diff showing additions and deletions.",
            parameters = createParametersJson(
                properties = mapOf(
                    "original" to param("string", "The original content"),
                    "modified" to param("string", "The modified content"),
                    "context_lines" to param("integer", "Number of context lines to show around changes (default: 3)")
                ),
                required = listOf("original", "modified")
            )
        )
    )

    val SEMANTIC_DIFF = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "semantic_diff",
            description = "Analyze code changes at a semantic level - identifies which functions, classes, or blocks were added, removed, or modified.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path1" to param("string", "Path to the original file"),
                    "path2" to param("string", "Path to the modified file")
                ),
                required = listOf("path1", "path2")
            )
        )
    )

    // ==================== Shell/Command Tools ====================

    val SHELL_EXEC = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "shell_exec",
            description = "Execute a safe shell command. Supports common development commands like git, npm, node, python, etc. Dangerous commands are blocked for security.",
            parameters = createParametersJson(
                properties = mapOf(
                    "command" to param("string", "The shell command to execute"),
                    "timeout" to param("integer", "Timeout in milliseconds (default: 30000, max: 300000)"),
                    "working_dir" to param("string", "Optional working directory for the command")
                ),
                required = listOf("command")
            )
        )
    )

    val VALIDATE_COMMAND = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "validate_command",
            description = "Check if a shell command is safe to execute without actually running it. Returns whether the command would be allowed.",
            parameters = createParametersJson(
                properties = mapOf(
                    "command" to param("string", "The shell command to validate")
                ),
                required = listOf("command")
            )
        )
    )

    val CHECK_COMMAND_AVAILABLE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "check_command_available",
            description = "Check if a command/executable is available on the system (e.g., 'git', 'node', 'python').",
            parameters = createParametersJson(
                properties = mapOf(
                    "command" to param("string", "The command name to check")
                ),
                required = listOf("command")
            )
        )
    )

    // ==================== Web/Network Tools ====================

    val WEB_FETCH = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "web_fetch",
            description = "Fetch content from a URL (documentation, API responses, etc.). Supports GET requests with optional headers. Returns text content or error message.",
            parameters = createParametersJson(
                properties = mapOf(
                    "url" to param("string", "The URL to fetch"),
                    "method" to enumParam("string", "HTTP method", listOf("GET", "POST")),
                    "headers" to param("string", "Optional headers as JSON object string"),
                    "body" to param("string", "Optional request body for POST requests"),
                    "timeout" to param("integer", "Timeout in milliseconds (default: 30000)")
                ),
                required = listOf("url")
            )
        )
    )

    // ==================== Code Generation Tools ====================

    val GENERATE_BOILERPLATE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "generate_boilerplate",
            description = "Generate boilerplate code for common patterns (API endpoints, models, tests, hooks, etc.).",
            parameters = createParametersJson(
                properties = mapOf(
                    "type" to enumParam("string", "Type of boilerplate to generate", listOf(
                        "api_endpoint", "rest_controller", "model_class", "test_suite",
                        "react_hook", "react_context", "vue_composable",
                        "express_router", "fastapi_router",
                        "database_migration", "dockerfile", "github_action"
                    )),
                    "name" to param("string", "Name for the generated code (e.g., 'User', 'Auth', 'Products')"),
                    "options" to param("string", "Additional options as JSON string (varies by type)")
                ),
                required = listOf("type", "name")
            )
        )
    )

    val REFACTOR_CODE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "refactor_code",
            description = "Apply common refactoring patterns to code. Extracts methods, renames variables with scope awareness, or restructures code.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the file to refactor"),
                    "operation" to enumParam("string", "Refactoring operation", listOf(
                        "extract_function", "extract_variable", "inline_function",
                        "rename_variable", "convert_to_async", "convert_class_to_function"
                    )),
                    "target" to param("string", "The code or symbol to target for refactoring"),
                    "new_name" to param("string", "New name for rename/extract operations"),
                    "dry_run" to param("boolean", "If true, only show what would be changed")
                ),
                required = listOf("path", "operation", "target")
            )
        )
    )

    // ==================== Testing Tools ====================

    val GENERATE_TESTS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "generate_tests",
            description = "Generate unit tests for a function, class, or file. Creates comprehensive test cases based on code analysis.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to the file containing code to test"),
                    "symbol" to param("string", "Optional specific function/class to test. If empty, generates tests for entire file."),
                    "framework" to enumParam("string", "Testing framework to use", listOf("jest", "vitest", "mocha", "pytest", "junit", "kotest")),
                    "include_edge_cases" to param("boolean", "Include edge case tests (nulls, empty values, boundaries)")
                ),
                required = listOf("path")
            )
        )
    )

    val ANALYZE_TEST_COVERAGE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "analyze_test_coverage",
            description = "Analyze which functions/branches in a file have test coverage and suggest missing tests.",
            parameters = createParametersJson(
                properties = mapOf(
                    "source_path" to param("string", "Path to the source file"),
                    "test_path" to param("string", "Path to the test file")
                ),
                required = listOf("source_path", "test_path")
            )
        )
    )

    // ==================== Security Analysis Tools ====================

    val SECURITY_SCAN = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "security_scan",
            description = "Scan code for common security vulnerabilities (SQL injection, XSS, hardcoded secrets, etc.).",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to file or directory to scan"),
                    "severity" to enumParam("string", "Minimum severity to report", listOf("low", "medium", "high", "critical"))
                ),
                required = listOf("path")
            )
        )
    )

    val FIND_SECRETS = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "find_secrets",
            description = "Scan files for accidentally committed secrets (API keys, passwords, tokens, etc.).",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to file or directory to scan"),
                    "include_patterns" to param("string", "Additional patterns to search for (comma-separated regex)")
                ),
                required = listOf("path")
            )
        )
    )

    // ==================== Performance Analysis Tools ====================

    val ANALYZE_BUNDLE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "analyze_bundle",
            description = "Analyze JavaScript/TypeScript bundle size and identify large dependencies or code that could be split.",
            parameters = createParametersJson(
                properties = mapOf(
                    "entry_point" to param("string", "Entry point file for analysis"),
                    "show_tree" to param("boolean", "Show dependency tree")
                ),
                required = listOf("entry_point")
            )
        )
    )

    val FIND_DEAD_CODE = Tool(
        type = "function",
        function = FunctionDefinition(
            name = "find_dead_code",
            description = "Find unused exports, functions, variables, and imports in the codebase.",
            parameters = createParametersJson(
                properties = mapOf(
                    "path" to param("string", "Path to file or directory to analyze"),
                    "include_private" to param("boolean", "Include private/internal functions in analysis")
                ),
                required = listOf("path")
            )
        )
    )

    /**
     * Get all advanced tools
     */
    fun getAllTools(): List<Tool> = listOf(
        // Code Analysis
        FIND_REFERENCES,
        FIND_DEFINITION,
        ANALYZE_IMPORTS,
        ANALYZE_DEPENDENCIES,
        // Batch Operations
        BATCH_RENAME,
        BATCH_MODIFY,
        // Code Quality
        CHECK_SYNTAX,
        FORMAT_CODE,
        VALIDATE_JSON,
        VALIDATE_HTML,
        // Project Structure
        CREATE_COMPONENT,
        SCAFFOLD_PROJECT,
        // Context Analysis
        GET_FILE_SUMMARY,
        GET_PROJECT_STRUCTURE,
        // Documentation
        GENERATE_DOCS,
        // Run/Execute
        RUN_SCRIPT,
        // Diff Tools
        DIFF_FILES,
        DIFF_CONTENT,
        SEMANTIC_DIFF,
        // Shell/Command Tools
        SHELL_EXEC,
        VALIDATE_COMMAND,
        CHECK_COMMAND_AVAILABLE,
        // Web/Network Tools
        WEB_FETCH,
        // Code Generation
        GENERATE_BOILERPLATE,
        REFACTOR_CODE,
        // Testing Tools
        GENERATE_TESTS,
        ANALYZE_TEST_COVERAGE,
        // Security Analysis
        SECURITY_SCAN,
        FIND_SECRETS,
        // Performance Analysis
        ANALYZE_BUNDLE,
        FIND_DEAD_CODE
    )
}
