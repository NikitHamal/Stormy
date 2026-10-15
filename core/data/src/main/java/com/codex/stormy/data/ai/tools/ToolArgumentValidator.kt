package com.codex.stormy.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Production-grade tool argument validation.
 *
 * Provides comprehensive validation for tool arguments including:
 * - Required argument checking
 * - Type validation
 * - Path security validation
 * - Content length limits
 * - Format validation (URLs, patterns, etc.)
 * - Business logic validation
 */
object ToolArgumentValidator {

    private const val TAG = "ToolArgumentValidator"

    // Maximum sizes for various inputs
    private const val MAX_PATH_LENGTH = 500
    private const val MAX_CONTENT_LENGTH = 1_000_000
    private const val MAX_PATTERN_LENGTH = 1000
    private const val MAX_URL_LENGTH = 2000
    private const val MAX_COMMAND_LENGTH = 10_000
    private const val MAX_ARRAY_SIZE = 100

    /**
     * Validation result containing success status and any error messages.
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    ) {
        companion object {
            fun success() = ValidationResult(true)
            fun failure(error: String) = ValidationResult(false, listOf(error))
            fun failure(errors: List<String>) = ValidationResult(false, errors)
        }
    }

    /**
     * Validate tool arguments based on tool name.
     */
    fun validate(toolName: String, args: JsonObject, projectRoot: File? = null): ValidationResult {
        return when (toolName) {
            // File operations
            "read_file" -> validateReadFile(args, projectRoot)
            "write_file" -> validateWriteFile(args, projectRoot)
            "list_files" -> validateListFiles(args, projectRoot)
            "delete_file" -> validateDeleteFile(args, projectRoot)
            "create_folder" -> validateCreateFolder(args, projectRoot)
            "rename_file" -> validateRenameFile(args, projectRoot)
            "copy_file" -> validateCopyFile(args, projectRoot)
            "move_file" -> validateMoveFile(args, projectRoot)
            "patch_file" -> validatePatchFile(args, projectRoot)
            "insert_at_line" -> validateInsertAtLine(args, projectRoot)
            "append_to_file" -> validateAppendToFile(args, projectRoot)
            "prepend_to_file" -> validatePrependToFile(args, projectRoot)
            "regex_replace" -> validateRegexReplace(args, projectRoot)
            "get_file_info" -> validateGetFileInfo(args, projectRoot)

            // Search operations
            "search_files" -> validateSearchFiles(args)
            "search_replace" -> validateSearchReplace(args)

            // Memory operations
            "save_memory" -> validateSaveMemory(args)
            "recall_memory" -> validateRecallMemory(args)
            "update_memory" -> validateUpdateMemory(args)
            "delete_memory" -> validateDeleteMemory(args)

            // Git operations
            "git_commit" -> validateGitCommit(args)
            "git_branch" -> validateGitBranch(args)
            "git_checkout" -> validateGitCheckout(args)
            "git_stage" -> validateGitStage(args)
            "git_log" -> validateGitLog(args)

            // Advanced tools
            "find_references" -> validateFindReferences(args)
            "find_definition" -> validateFindDefinition(args)
            "batch_rename" -> validateBatchRename(args)
            "batch_modify" -> validateBatchModify(args)
            "create_component" -> validateCreateComponent(args, projectRoot)
            "scaffold_project" -> validateScaffoldProject(args)

            // Diff tools
            "diff_files" -> validateDiffFiles(args, projectRoot)
            "diff_content" -> validateDiffContent(args)
            "semantic_diff" -> validateSemanticDiff(args, projectRoot)

            // Shell tools
            "shell_exec" -> validateShellExec(args)
            "validate_command" -> validateValidateCommand(args)

            // Web tools
            "web_fetch" -> validateWebFetch(args)

            // Code generation
            "generate_boilerplate" -> validateGenerateBoilerplate(args)
            "refactor_code" -> validateRefactorCode(args, projectRoot)

            // Testing tools
            "generate_tests" -> validateGenerateTests(args, projectRoot)
            "analyze_test_coverage" -> validateAnalyzeTestCoverage(args, projectRoot)

            // Security tools
            "security_scan" -> validateSecurityScan(args, projectRoot)
            "find_secrets" -> validateFindSecrets(args, projectRoot)

            // Default: no specific validation
            else -> ValidationResult.success()
        }
    }

    // ==================== File Operation Validators ====================

    private fun validateReadFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            errors.add("Missing required argument: path")
        } else {
            validatePath(path, projectRoot)?.let { errors.add(it) }
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateWriteFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            errors.add("Missing required argument: path")
        } else {
            validatePath(path, projectRoot)?.let { errors.add(it) }
        }

        val content = args.getStringArg("content")
        if (content == null) {
            errors.add("Missing required argument: content")
        } else if (content.length > MAX_CONTENT_LENGTH) {
            errors.add("Content exceeds maximum length of ${MAX_CONTENT_LENGTH / 1000}KB")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateListFiles(args: JsonObject, projectRoot: File?): ValidationResult {
        val path = args.getStringArg("path") ?: ""
        if (path.isNotEmpty()) {
            validatePath(path, projectRoot)?.let { return ValidationResult.failure(it) }
        }
        return ValidationResult.success()
    }

    private fun validateDeleteFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: path")
        }
        validatePath(path, projectRoot)?.let { return ValidationResult.failure(it) }

        val dangerousPaths = listOf(
            "", ".", "..", "/", "src", "app", "lib", "node_modules",
            ".git", ".github", "build", "dist", "out"
        )
        if (path in dangerousPaths) {
            return ValidationResult.failure("Cannot delete protected path: $path")
        }

        return ValidationResult.success()
    }

    private fun validateCreateFolder(args: JsonObject, projectRoot: File?): ValidationResult {
        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: path")
        }
        validatePath(path, projectRoot)?.let { return ValidationResult.failure(it) }
        return ValidationResult.success()
    }

    private fun validateRenameFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val oldPath = args.getStringArg("old_path")
        val newPath = args.getStringArg("new_path")

        if (oldPath.isNullOrBlank()) errors.add("Missing required argument: old_path")
        else validatePath(oldPath, projectRoot)?.let { errors.add(it) }

        if (newPath.isNullOrBlank()) errors.add("Missing required argument: new_path")
        else validatePath(newPath, projectRoot)?.let { errors.add(it) }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateCopyFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val sourcePath = args.getStringArg("source_path")
        val destPath = args.getStringArg("destination_path")

        if (sourcePath.isNullOrBlank()) errors.add("Missing required argument: source_path")
        else validatePath(sourcePath, projectRoot)?.let { errors.add(it) }

        if (destPath.isNullOrBlank()) errors.add("Missing required argument: destination_path")
        else validatePath(destPath, projectRoot)?.let { errors.add(it) }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateMoveFile(args: JsonObject, projectRoot: File?): ValidationResult {
        return validateCopyFile(args, projectRoot)
    }

    private fun validatePatchFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) errors.add("Missing required argument: path")
        else validatePath(path, projectRoot)?.let { errors.add(it) }

        if (args.getStringArg("old_content").isNullOrEmpty()) {
            errors.add("Missing required argument: old_content")
        }
        if (args.getStringArg("new_content") == null) {
            errors.add("Missing required argument: new_content")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateInsertAtLine(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) errors.add("Missing required argument: path")
        else validatePath(path, projectRoot)?.let { errors.add(it) }

        val lineNumber = args.getStringArg("line_number")?.toIntOrNull()
        if (lineNumber == null) {
            errors.add("Missing or invalid argument: line_number (must be an integer)")
        } else if (lineNumber < 0) {
            errors.add("line_number must be non-negative")
        }

        if (args.getStringArg("content") == null) {
            errors.add("Missing required argument: content")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateAppendToFile(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) errors.add("Missing required argument: path")
        else validatePath(path, projectRoot)?.let { errors.add(it) }

        if (args.getStringArg("content") == null) {
            errors.add("Missing required argument: content")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validatePrependToFile(args: JsonObject, projectRoot: File?): ValidationResult {
        return validateAppendToFile(args, projectRoot)
    }

    private fun validateRegexReplace(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) errors.add("Missing required argument: path")
        else validatePath(path, projectRoot)?.let { errors.add(it) }

        val pattern = args.getStringArg("pattern")
        if (pattern.isNullOrBlank()) {
            errors.add("Missing required argument: pattern")
        } else {
            try {
                Regex(pattern)
            } catch (e: Exception) {
                errors.add("Invalid regex pattern: ${e.message}")
            }
        }

        if (args.getStringArg("replacement") == null) {
            errors.add("Missing required argument: replacement")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateGetFileInfo(args: JsonObject, projectRoot: File?): ValidationResult {
        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: path")
        }
        validatePath(path, projectRoot)?.let { return ValidationResult.failure(it) }
        return ValidationResult.success()
    }

    // ==================== Search Validators ====================

    private fun validateSearchFiles(args: JsonObject): ValidationResult {
        val query = args.getStringArg("query")
        if (query.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: query")
        }
        if (query.length > MAX_PATTERN_LENGTH) {
            return ValidationResult.failure("Query too long (max $MAX_PATTERN_LENGTH characters)")
        }
        return ValidationResult.success()
    }

    private fun validateSearchReplace(args: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()

        val search = args.getStringArg("search")
        if (search.isNullOrBlank()) errors.add("Missing required argument: search")
        else if (search.length > MAX_PATTERN_LENGTH) {
            errors.add("Search pattern too long (max $MAX_PATTERN_LENGTH characters)")
        }

        if (args.getStringArg("replace") == null) {
            errors.add("Missing required argument: replace")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    // ==================== Memory Validators ====================

    private fun validateSaveMemory(args: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()

        val key = args.getStringArg("key")
        if (key.isNullOrBlank()) errors.add("Missing required argument: key")
        else if (!isValidMemoryKey(key)) errors.add("Invalid memory key format")

        if (args.getStringArg("value").isNullOrBlank()) {
            errors.add("Missing required argument: value")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateRecallMemory(args: JsonObject): ValidationResult {
        val key = args.getStringArg("key")
        if (key.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: key")
        }
        if (!isValidMemoryKey(key)) {
            return ValidationResult.failure("Invalid memory key format")
        }
        return ValidationResult.success()
    }

    private fun validateUpdateMemory(args: JsonObject): ValidationResult {
        return validateSaveMemory(args)
    }

    private fun validateDeleteMemory(args: JsonObject): ValidationResult {
        return validateRecallMemory(args)
    }

    // ==================== Git Validators ====================

    private fun validateGitCommit(args: JsonObject): ValidationResult {
        val message = args.getStringArg("message")
        if (message.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: message")
        }
        if (message.length > 5000) {
            return ValidationResult.failure("Commit message too long (max 5000 characters)")
        }
        return ValidationResult.success()
    }

    private fun validateGitBranch(args: JsonObject): ValidationResult {
        val action = args.getStringArg("action")
        if (action.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: action")
        }
        if (action !in listOf("list", "create", "delete")) {
            return ValidationResult.failure("Invalid action: must be 'list', 'create', or 'delete'")
        }
        if (action in listOf("create", "delete")) {
            val name = args.getStringArg("name")
            if (name.isNullOrBlank()) {
                return ValidationResult.failure("Branch name required for '$action' action")
            }
            if (!isValidBranchName(name)) {
                return ValidationResult.failure("Invalid branch name format")
            }
        }
        return ValidationResult.success()
    }

    private fun validateGitCheckout(args: JsonObject): ValidationResult {
        val branch = args.getStringArg("branch")
        if (branch.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: branch")
        }
        if (!isValidBranchName(branch)) {
            return ValidationResult.failure("Invalid branch name format")
        }
        return ValidationResult.success()
    }

    private fun validateGitStage(args: JsonObject): ValidationResult {
        val paths = args.getStringArg("paths")
        if (paths.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: paths")
        }
        return ValidationResult.success()
    }

    private fun validateGitLog(args: JsonObject): ValidationResult {
        val count = args.getStringArg("count")?.toIntOrNull()
        if (count != null && (count < 1 || count > 1000)) {
            return ValidationResult.failure("Count must be between 1 and 1000")
        }
        return ValidationResult.success()
    }

    // ==================== Advanced Tool Validators ====================

    private fun validateFindReferences(args: JsonObject): ValidationResult {
        val symbol = args.getStringArg("symbol")
        if (symbol.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: symbol")
        }
        if (symbol.length > 200) {
            return ValidationResult.failure("Symbol name too long")
        }
        return ValidationResult.success()
    }

    private fun validateFindDefinition(args: JsonObject): ValidationResult {
        return validateFindReferences(args)
    }

    private fun validateBatchRename(args: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()

        val oldName = args.getStringArg("old_name")
        val newName = args.getStringArg("new_name")

        if (oldName.isNullOrBlank()) errors.add("Missing required argument: old_name")
        if (newName.isNullOrBlank()) errors.add("Missing required argument: new_name")

        if (oldName == newName) {
            errors.add("old_name and new_name must be different")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateBatchModify(args: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()

        if (args.getStringArg("file_pattern").isNullOrBlank()) {
            errors.add("Missing required argument: file_pattern")
        }

        val operation = args.getStringArg("operation")
        if (operation.isNullOrBlank()) {
            errors.add("Missing required argument: operation")
        } else if (operation !in listOf("prepend", "append", "replace", "insert_after", "insert_before")) {
            errors.add("Invalid operation: $operation")
        }

        if (args.getStringArg("content") == null) {
            errors.add("Missing required argument: content")
        }

        if (operation in listOf("replace", "insert_after", "insert_before")) {
            if (args.getStringArg("target").isNullOrBlank()) {
                errors.add("Target required for '$operation' operation")
            }
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateCreateComponent(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val name = args.getStringArg("name")
        if (name.isNullOrBlank()) {
            errors.add("Missing required argument: name")
        } else if (!isValidComponentName(name)) {
            errors.add("Invalid component name: must be PascalCase")
        }

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            errors.add("Missing required argument: path")
        } else {
            validatePath(path, projectRoot)?.let { errors.add(it) }
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateScaffoldProject(args: JsonObject): ValidationResult {
        val scaffoldType = args.getStringArg("scaffold_type")
        if (scaffoldType.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: scaffold_type")
        }
        val validTypes = listOf(
            "folder_structure", "api_routes", "auth_setup",
            "database_config", "testing_setup", "ci_workflow"
        )
        if (scaffoldType !in validTypes) {
            return ValidationResult.failure("Invalid scaffold_type: $scaffoldType")
        }
        return ValidationResult.success()
    }

    // ==================== Diff Tool Validators ====================

    private fun validateDiffFiles(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path1 = args.getStringArg("path1")
        val path2 = args.getStringArg("path2")

        if (path1.isNullOrBlank()) errors.add("Missing required argument: path1")
        else validatePath(path1, projectRoot)?.let { errors.add(it) }

        if (path2.isNullOrBlank()) errors.add("Missing required argument: path2")
        else validatePath(path2, projectRoot)?.let { errors.add(it) }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateDiffContent(args: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()

        if (args.getStringArg("original") == null) {
            errors.add("Missing required argument: original")
        }
        if (args.getStringArg("modified") == null) {
            errors.add("Missing required argument: modified")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateSemanticDiff(args: JsonObject, projectRoot: File?): ValidationResult {
        return validateDiffFiles(args, projectRoot)
    }

    // ==================== Shell Tool Validators ====================

    private fun validateShellExec(args: JsonObject): ValidationResult {
        val command = args.getStringArg("command")
        if (command.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: command")
        }
        if (command.length > MAX_COMMAND_LENGTH) {
            return ValidationResult.failure("Command too long (max $MAX_COMMAND_LENGTH characters)")
        }
        return ValidationResult.success()
    }

    private fun validateValidateCommand(args: JsonObject): ValidationResult {
        return validateShellExec(args)
    }

    // ==================== Web Tool Validators ====================

    private fun validateWebFetch(args: JsonObject): ValidationResult {
        val url = args.getStringArg("url")
        if (url.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: url")
        }
        if (url.length > MAX_URL_LENGTH) {
            return ValidationResult.failure("URL too long (max $MAX_URL_LENGTH characters)")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ValidationResult.failure("URL must start with http:// or https://")
        }
        return ValidationResult.success()
    }

    // ==================== Code Generation Validators ====================

    private fun validateGenerateBoilerplate(args: JsonObject): ValidationResult {
        val errors = mutableListOf<String>()

        val type = args.getStringArg("type")
        if (type.isNullOrBlank()) {
            errors.add("Missing required argument: type")
        }

        val name = args.getStringArg("name")
        if (name.isNullOrBlank()) {
            errors.add("Missing required argument: name")
        } else if (!isValidIdentifier(name)) {
            errors.add("Invalid name: must be a valid identifier")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    private fun validateRefactorCode(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) errors.add("Missing required argument: path")
        else validatePath(path, projectRoot)?.let { errors.add(it) }

        val operation = args.getStringArg("operation")
        if (operation.isNullOrBlank()) {
            errors.add("Missing required argument: operation")
        }

        if (args.getStringArg("target").isNullOrBlank()) {
            errors.add("Missing required argument: target")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    // ==================== Testing Tool Validators ====================

    private fun validateGenerateTests(args: JsonObject, projectRoot: File?): ValidationResult {
        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: path")
        }
        validatePath(path, projectRoot)?.let { return ValidationResult.failure(it) }
        return ValidationResult.success()
    }

    private fun validateAnalyzeTestCoverage(args: JsonObject, projectRoot: File?): ValidationResult {
        val errors = mutableListOf<String>()

        val sourcePath = args.getStringArg("source_path")
        val testPath = args.getStringArg("test_path")

        if (sourcePath.isNullOrBlank()) errors.add("Missing required argument: source_path")
        else validatePath(sourcePath, projectRoot)?.let { errors.add(it) }

        if (testPath.isNullOrBlank()) errors.add("Missing required argument: test_path")
        else validatePath(testPath, projectRoot)?.let { errors.add(it) }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    // ==================== Security Tool Validators ====================

    private fun validateSecurityScan(args: JsonObject, projectRoot: File?): ValidationResult {
        val path = args.getStringArg("path")
        if (path.isNullOrBlank()) {
            return ValidationResult.failure("Missing required argument: path")
        }
        validatePath(path, projectRoot)?.let { return ValidationResult.failure(it) }
        return ValidationResult.success()
    }

    private fun validateFindSecrets(args: JsonObject, projectRoot: File?): ValidationResult {
        return validateSecurityScan(args, projectRoot)
    }

    // ==================== Helper Functions ====================

    private fun validatePath(path: String, projectRoot: File?): String? {
        if (path.length > MAX_PATH_LENGTH) {
            return "Path too long (max $MAX_PATH_LENGTH characters)"
        }

        if (path.contains("\u0000")) {
            return "Path contains null bytes"
        }

        val normalized = path.replace("\\", "/")
        if (normalized.split("/").count { it == ".." } > 3) {
            return "Excessive parent directory references"
        }

        if (projectRoot != null) {
            try {
                val targetFile = File(projectRoot, sanitizePath(path))
                val canonicalTarget = targetFile.canonicalPath
                val canonicalRoot = projectRoot.canonicalPath

                if (!canonicalTarget.startsWith(canonicalRoot)) {
                    return "Path escapes project root"
                }
            } catch (e: Exception) {
                Log.w(TAG, "Path validation error: ${e.message}")
                return "Invalid path format"
            }
        }

        return null
    }

    private fun isValidMemoryKey(key: String): Boolean {
        return key.length in 1..100 &&
                key.matches(Regex("""^[a-zA-Z][a-zA-Z0-9_-]*$"""))
    }

    private fun isValidBranchName(name: String): Boolean {
        if (name.isBlank() || name.length > 255) return false

        val invalid = listOf(
            name.startsWith("-"),
            name.startsWith("."),
            name.endsWith("."),
            name.endsWith(".lock"),
            name.contains(".."),
            name.contains("~"),
            name.contains("^"),
            name.contains(":"),
            name.contains("\\"),
            name.contains(" "),
            name.contains("?"),
            name.contains("*"),
            name.contains("[")
        )

        return !invalid.any { it }
    }

    private fun isValidComponentName(name: String): Boolean {
        return name.matches(Regex("""^[A-Z][a-zA-Z0-9]*$"""))
    }

    private fun isValidIdentifier(name: String): Boolean {
        return name.matches(Regex("""^[a-zA-Z_][a-zA-Z0-9_]*$"""))
    }

    private fun JsonObject.getStringArg(key: String): String? {
        return this[key]?.let {
            if (it is JsonPrimitive) it.contentOrNull else null
        }
    }
}
