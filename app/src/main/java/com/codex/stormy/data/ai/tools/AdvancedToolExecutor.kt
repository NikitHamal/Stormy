package com.codex.stormy.data.ai.tools

import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.FileTreeNode
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * Executor for advanced tools.
 * Handles code analysis, batch operations, code quality, and project scaffolding.
 *
 * Also delegates to ExtendedToolExecutor for additional advanced tools:
 * - Diff tools (diff_files, diff_content, semantic_diff)
 * - Shell/Command tools (shell_exec, validate_command, check_command_available)
 * - Web tools (web_fetch)
 * - Code generation tools (generate_boilerplate, refactor_code)
 * - Testing tools (generate_tests, analyze_test_coverage)
 * - Security tools (security_scan, find_secrets)
 * - Performance tools (analyze_bundle, find_dead_code)
 */
class AdvancedToolExecutor(
    private val projectRepository: ProjectRepository
) {
    // Current project path for file operations
    private var currentProjectPath: File? = null

    // Extended tool executor for additional capabilities
    private val extendedToolExecutor = ExtendedToolExecutor(projectRepository)

    /**
     * Set the current project path
     */
    fun setProjectPath(path: File) {
        currentProjectPath = path
        extendedToolExecutor.setProjectPath(path)
    }

    /**
     * Execute an advanced tool
     */
    suspend fun execute(
        projectId: String,
        toolName: String,
        args: JsonObject
    ): ToolResult? {
        // First try extended tools
        extendedToolExecutor.execute(projectId, toolName, args)?.let { return it }

        // Then try original advanced tools
        return when (toolName) {
            // Code Analysis
            "find_references" -> executeFindReferences(projectId, args)
            "find_definition" -> executeFindDefinition(projectId, args)
            "analyze_imports" -> executeAnalyzeImports(projectId, args)
            "analyze_dependencies" -> executeAnalyzeDependencies(projectId)

            // Batch Operations
            "batch_rename" -> executeBatchRename(projectId, args)
            "batch_modify" -> executeBatchModify(projectId, args)

            // Code Quality
            "check_syntax" -> executeCheckSyntax(projectId, args)
            "format_code" -> executeFormatCode(projectId, args)
            "validate_json" -> executeValidateJson(projectId, args)
            "validate_html" -> executeValidateHtml(projectId, args)

            // Project Structure
            "create_component" -> executeCreateComponent(projectId, args)
            "scaffold_project" -> executeScaffoldProject(projectId, args)

            // Context Analysis
            "get_file_summary" -> executeGetFileSummary(projectId, args)
            "get_project_structure" -> executeGetProjectStructure(projectId, args)

            // Documentation
            "generate_docs" -> executeGenerateDocs(projectId, args)

            // Run/Execute
            "run_script" -> executeRunScript(projectId, args)

            else -> null // Not an advanced tool
        }
    }

    // ==================== Code Analysis Tools ====================

    private suspend fun executeFindReferences(projectId: String, args: JsonObject): ToolResult {
        val symbol = args.getStringArg("symbol")
            ?: return ToolResult(false, "", "Missing required argument: symbol")
        val filePattern = args.getStringArg("file_pattern")

        return try {
            val results = StringBuilder()
            val fileTree = projectRepository.getFileTree(projectId)

            searchForSymbol(projectId, fileTree, symbol, filePattern, results, includeDeclarations = true)

            if (results.isEmpty()) {
                ToolResult(true, "No references found for symbol: $symbol")
            } else {
                ToolResult(true, "References to '$symbol':\n$results")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to find references: ${e.message}")
        }
    }

    private suspend fun executeFindDefinition(projectId: String, args: JsonObject): ToolResult {
        val symbol = args.getStringArg("symbol")
            ?: return ToolResult(false, "", "Missing required argument: symbol")
        val filePattern = args.getStringArg("file_pattern")

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val definition = findSymbolDefinition(projectId, fileTree, symbol, filePattern)

            if (definition != null) {
                ToolResult(true, "Definition of '$symbol':\n$definition")
            } else {
                ToolResult(true, "No definition found for symbol: $symbol")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to find definition: ${e.message}")
        }
    }

    private suspend fun executeAnalyzeImports(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
        val showUnused = args.getBooleanArg("show_unused", false)

        return try {
            if (path != null) {
                // Analyze single file
                val content = projectRepository.readFile(projectId, path).getOrNull()
                    ?: return ToolResult(false, "", "File not found: $path")

                val imports = extractImports(content, path)
                val output = formatImportAnalysis(imports, content, showUnused)
                ToolResult(true, "Import analysis for $path:\n$output")
            } else {
                // Analyze whole project
                val fileTree = projectRepository.getFileTree(projectId)
                val allImports = mutableListOf<ImportInfo>()

                collectAllImports(projectId, fileTree, allImports)

                val grouped = allImports.groupBy { it.source }
                val output = buildString {
                    appendLine("Project Import Analysis (${allImports.size} imports):")
                    grouped.entries.sortedByDescending { it.value.size }.take(20).forEach { (source, imports) ->
                        appendLine("  $source: ${imports.size} usage(s)")
                    }
                }
                ToolResult(true, output)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to analyze imports: ${e.message}")
        }
    }

    private suspend fun executeAnalyzeDependencies(projectId: String): ToolResult {
        return try {
            val output = StringBuilder()

            // Check for package.json (Node.js)
            val packageJson = projectRepository.readFile(projectId, "package.json").getOrNull()
            if (packageJson != null) {
                output.appendLine("Node.js Dependencies (package.json):")
                val deps = extractPackageJsonDeps(packageJson)
                deps.forEach { (name, version) ->
                    output.appendLine("  $name: $version")
                }
            }

            // Check for build.gradle (Android/Kotlin)
            val buildGradle = projectRepository.readFile(projectId, "build.gradle").getOrNull()
                ?: projectRepository.readFile(projectId, "build.gradle.kts").getOrNull()
            if (buildGradle != null) {
                output.appendLine("\nGradle Dependencies:")
                val deps = extractGradleDeps(buildGradle)
                deps.forEach { dep ->
                    output.appendLine("  $dep")
                }
            }

            // Check for requirements.txt (Python)
            val requirements = projectRepository.readFile(projectId, "requirements.txt").getOrNull()
            if (requirements != null) {
                output.appendLine("\nPython Dependencies (requirements.txt):")
                requirements.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach {
                    output.appendLine("  $it")
                }
            }

            if (output.isEmpty()) {
                ToolResult(true, "No dependency files found (package.json, build.gradle, requirements.txt)")
            } else {
                ToolResult(true, output.toString())
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to analyze dependencies: ${e.message}")
        }
    }

    // ==================== Batch Operation Tools ====================

    private suspend fun executeBatchRename(projectId: String, args: JsonObject): ToolResult {
        val oldName = args.getStringArg("old_name")
            ?: return ToolResult(false, "", "Missing required argument: old_name")
        val newName = args.getStringArg("new_name")
            ?: return ToolResult(false, "", "Missing required argument: new_name")
        val filePattern = args.getStringArg("file_pattern")
        val dryRun = args.getBooleanArg("dry_run", false)

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val changes = mutableListOf<RenameChange>()

            collectRenameChanges(projectId, fileTree, oldName, newName, filePattern, changes)

            if (changes.isEmpty()) {
                return ToolResult(true, "No occurrences of '$oldName' found")
            }

            if (dryRun) {
                val output = buildString {
                    appendLine("Would rename '$oldName' to '$newName' in ${changes.size} location(s):")
                    changes.forEach { change ->
                        appendLine("  ${change.file}:${change.line} - ${change.preview}")
                    }
                }
                ToolResult(true, output)
            } else {
                // Apply the renames
                val modifiedFiles = mutableSetOf<String>()
                changes.groupBy { it.file }.forEach { (file, fileChanges) ->
                    val content = projectRepository.readFile(projectId, file).getOrNull() ?: return@forEach
                    val newContent = content.replace(oldName, newName)
                    projectRepository.writeFile(projectId, file, newContent)
                    modifiedFiles.add(file)
                }

                ToolResult(true, "Renamed '$oldName' to '$newName' in ${modifiedFiles.size} file(s)")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to batch rename: ${e.message}")
        }
    }

    private suspend fun executeBatchModify(projectId: String, args: JsonObject): ToolResult {
        val filePattern = args.getStringArg("file_pattern")
            ?: return ToolResult(false, "", "Missing required argument: file_pattern")
        val operation = args.getStringArg("operation")
            ?: return ToolResult(false, "", "Missing required argument: operation")
        val content = args.getStringArg("content")
            ?: return ToolResult(false, "", "Missing required argument: content")
        val target = args.getStringArg("target")
        val dryRun = args.getBooleanArg("dry_run", false)

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val matchingFiles = mutableListOf<String>()

            collectMatchingFiles(fileTree, filePattern, matchingFiles)

            if (matchingFiles.isEmpty()) {
                return ToolResult(true, "No files matching pattern: $filePattern")
            }

            if (dryRun) {
                val output = buildString {
                    appendLine("Would apply '$operation' to ${matchingFiles.size} file(s):")
                    matchingFiles.take(10).forEach { file ->
                        appendLine("  $file")
                    }
                    if (matchingFiles.size > 10) {
                        appendLine("  ... and ${matchingFiles.size - 10} more")
                    }
                }
                ToolResult(true, output)
            } else {
                var modifiedCount = 0
                matchingFiles.forEach { file ->
                    val fileContent = projectRepository.readFile(projectId, file).getOrNull() ?: return@forEach

                    val newContent = when (operation.lowercase()) {
                        "prepend" -> content + fileContent
                        "append" -> fileContent + content
                        "replace" -> {
                            if (target == null) return ToolResult(false, "", "Replace operation requires 'target' argument")
                            fileContent.replace(target, content)
                        }
                        "insert_after" -> {
                            if (target == null) return ToolResult(false, "", "Insert_after operation requires 'target' argument")
                            fileContent.replace(target, target + content)
                        }
                        "insert_before" -> {
                            if (target == null) return ToolResult(false, "", "Insert_before operation requires 'target' argument")
                            fileContent.replace(target, content + target)
                        }
                        else -> return ToolResult(false, "", "Unknown operation: $operation")
                    }

                    if (newContent != fileContent) {
                        projectRepository.writeFile(projectId, file, newContent)
                        modifiedCount++
                    }
                }

                ToolResult(true, "Applied '$operation' to $modifiedCount file(s)")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to batch modify: ${e.message}")
        }
    }

    // ==================== Code Quality Tools ====================

    private suspend fun executeCheckSyntax(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val extension = path.substringAfterLast('.', "")
            val errors = checkSyntaxForType(content, extension)

            if (errors.isEmpty()) {
                ToolResult(true, "No syntax errors found in $path")
            } else {
                val output = buildString {
                    appendLine("Syntax issues in $path:")
                    errors.forEach { error ->
                        appendLine("  Line ${error.line}: ${error.message}")
                    }
                }
                ToolResult(true, output)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to check syntax: ${e.message}")
        }
    }

    private suspend fun executeFormatCode(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val indentSize = args.getStringArg("indent_size")?.toIntOrNull() ?: 2
        val useTabs = args.getBooleanArg("use_tabs", false)

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val extension = path.substringAfterLast('.', "")
            val formatted = formatCodeContent(content, extension, indentSize, useTabs)

            if (formatted != content) {
                projectRepository.writeFile(projectId, path, formatted)
                ToolResult(true, "Formatted $path")
            } else {
                ToolResult(true, "No formatting changes needed for $path")
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to format code: ${e.message}")
        }
    }

    private suspend fun executeValidateJson(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val errors = validateJsonContent(content)

            if (errors.isEmpty()) {
                ToolResult(true, "JSON is valid: $path")
            } else {
                val output = buildString {
                    appendLine("JSON validation errors in $path:")
                    errors.forEach { error ->
                        appendLine("  $error")
                    }
                }
                ToolResult(true, output)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to validate JSON: ${e.message}")
        }
    }

    private suspend fun executeValidateHtml(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val checkAccessibility = args.getBooleanArg("check_accessibility", false)

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val errors = validateHtmlContent(content, checkAccessibility)

            if (errors.isEmpty()) {
                ToolResult(true, "HTML is valid: $path")
            } else {
                val output = buildString {
                    appendLine("HTML validation issues in $path:")
                    errors.forEach { error ->
                        appendLine("  Line ${error.line}: ${error.message}")
                    }
                }
                ToolResult(true, output)
            }
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to validate HTML: ${e.message}")
        }
    }

    // ==================== Project Structure Tools ====================

    private suspend fun executeCreateComponent(projectId: String, args: JsonObject): ToolResult {
        val name = args.getStringArg("name")
            ?: return ToolResult(false, "", "Missing required argument: name")
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val type = args.getStringArg("type") ?: "functional"
        val withStyles = args.getBooleanArg("with_styles", false)
        val withTests = args.getBooleanArg("with_tests", false)

        return try {
            // Detect framework from project files
            val framework = detectFramework(projectId)
            val createdFiles = mutableListOf<String>()

            when (framework) {
                "react", "next" -> {
                    val componentContent = generateReactComponent(name, type)
                    val componentPath = "$path/$name.jsx"
                    projectRepository.createFile(projectId, componentPath, componentContent)
                    createdFiles.add(componentPath)

                    if (withStyles) {
                        val stylesPath = "$path/$name.css"
                        projectRepository.createFile(projectId, stylesPath, "/* Styles for $name component */\n")
                        createdFiles.add(stylesPath)
                    }
                }
                "vue" -> {
                    val componentContent = generateVueComponent(name)
                    val componentPath = "$path/$name.vue"
                    projectRepository.createFile(projectId, componentPath, componentContent)
                    createdFiles.add(componentPath)
                }
                "svelte" -> {
                    val componentContent = generateSvelteComponent(name)
                    val componentPath = "$path/$name.svelte"
                    projectRepository.createFile(projectId, componentPath, componentContent)
                    createdFiles.add(componentPath)
                }
                else -> {
                    // Default to React-style
                    val componentContent = generateReactComponent(name, type)
                    val componentPath = "$path/$name.jsx"
                    projectRepository.createFile(projectId, componentPath, componentContent)
                    createdFiles.add(componentPath)
                }
            }

            if (withTests) {
                val testContent = generateTestFile(name, framework)
                val testPath = "$path/$name.test.js"
                projectRepository.createFile(projectId, testPath, testContent)
                createdFiles.add(testPath)
            }

            ToolResult(true, "Created component files:\n${createdFiles.joinToString("\n") { "  $it" }}")
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to create component: ${e.message}")
        }
    }

    private suspend fun executeScaffoldProject(projectId: String, args: JsonObject): ToolResult {
        val scaffoldType = args.getStringArg("scaffold_type")
            ?: return ToolResult(false, "", "Missing required argument: scaffold_type")

        return try {
            val createdItems = mutableListOf<String>()

            when (scaffoldType.lowercase()) {
                "folder_structure" -> {
                    val folders = listOf("src", "src/components", "src/utils", "src/styles", "public", "tests")
                    folders.forEach { folder ->
                        projectRepository.createFolder(projectId, folder)
                        createdItems.add(folder)
                    }
                }
                "api_routes" -> {
                    projectRepository.createFolder(projectId, "src/api")
                    projectRepository.createFile(projectId, "src/api/index.js", generateApiBoilerplate())
                    createdItems.add("src/api/index.js")
                }
                "testing_setup" -> {
                    projectRepository.createFolder(projectId, "tests")
                    projectRepository.createFile(projectId, "tests/setup.js", generateTestSetup())
                    createdItems.add("tests/setup.js")
                }
                "ci_workflow" -> {
                    projectRepository.createFolder(projectId, ".github/workflows")
                    projectRepository.createFile(projectId, ".github/workflows/ci.yml", generateCiWorkflow())
                    createdItems.add(".github/workflows/ci.yml")
                }
                else -> {
                    return ToolResult(false, "", "Unknown scaffold type: $scaffoldType")
                }
            }

            ToolResult(true, "Scaffolded $scaffoldType:\n${createdItems.joinToString("\n") { "  $it" }}")
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to scaffold project: ${e.message}")
        }
    }

    // ==================== Context Analysis Tools ====================

    private suspend fun executeGetFileSummary(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val extension = path.substringAfterLast('.', "")
            val summary = generateFileSummary(content, extension, path)

            ToolResult(true, summary)
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to get file summary: ${e.message}")
        }
    }

    private suspend fun executeGetProjectStructure(projectId: String, args: JsonObject): ToolResult {
        val depth = args.getStringArg("depth")?.toIntOrNull() ?: 3

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val output = buildString {
                appendLine("Project Structure:")

                // Detect framework
                val framework = detectFramework(projectId)
                appendLine("Detected Framework: $framework")
                appendLine()

                // Build tree
                appendLine("Files:")
                buildProjectTree(fileTree, "", depth, 0, this)

                // Count stats
                val stats = collectProjectStats(fileTree)
                appendLine()
                appendLine("Statistics:")
                appendLine("  Total files: ${stats.fileCount}")
                appendLine("  Total folders: ${stats.folderCount}")
                stats.extensionCounts.entries.sortedByDescending { it.value }.take(5).forEach { (ext, count) ->
                    appendLine("  .$ext files: $count")
                }
            }

            ToolResult(true, output)
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to get project structure: ${e.message}")
        }
    }

    // ==================== Documentation Tools ====================

    private suspend fun executeGenerateDocs(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val symbol = args.getStringArg("symbol")
        val style = args.getStringArg("style") ?: "jsdoc"

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val extension = path.substringAfterLast('.', "")
            val docs = generateDocumentation(content, extension, symbol, style)

            ToolResult(true, "Generated documentation:\n$docs")
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to generate docs: ${e.message}")
        }
    }

    // ==================== Run/Execute Tools ====================

    private suspend fun executeRunScript(projectId: String, args: JsonObject): ToolResult {
        val scriptName = args.getStringArg("script_name")
            ?: return ToolResult(false, "", "Missing required argument: script_name")

        return try {
            // Read package.json to find the script
            val packageJson = projectRepository.readFile(projectId, "package.json").getOrNull()
                ?: return ToolResult(false, "", "No package.json found")

            val scripts = extractPackageJsonScripts(packageJson)
            val script = scripts[scriptName]
                ?: return ToolResult(false, "", "Script '$scriptName' not found in package.json")

            // Return the script command (actual execution would require shell access)
            ToolResult(true, "Script '$scriptName': $script\n\nNote: Script execution requires terminal access. Run this command manually:\nnpm run $scriptName")
        } catch (e: Exception) {
            ToolResult(false, "", "Failed to run script: ${e.message}")
        }
    }

    // ==================== Helper Methods ====================

    private suspend fun searchForSymbol(
        projectId: String,
        nodes: List<FileTreeNode>,
        symbol: String,
        filePattern: String?,
        results: StringBuilder,
        includeDeclarations: Boolean
    ) {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (filePattern != null && !matchesPattern(node.name, filePattern)) continue

                    projectRepository.readFile(projectId, node.path).onSuccess { content ->
                        content.lines().forEachIndexed { index, line ->
                            if (line.contains(symbol)) {
                                results.appendLine("  ${node.path}:${index + 1}: ${line.trim()}")
                            }
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    searchForSymbol(projectId, node.children, symbol, filePattern, results, includeDeclarations)
                }
            }
        }
    }

    private suspend fun findSymbolDefinition(
        projectId: String,
        nodes: List<FileTreeNode>,
        symbol: String,
        filePattern: String?
    ): String? {
        val definitionPatterns = listOf(
            Regex("""(function|const|let|var|class)\s+$symbol\b"""),
            Regex("""$symbol\s*[:=]\s*(function|\()"""),
            Regex("""(def|class)\s+$symbol\b"""),
            Regex("""(fun|class|object|val|var)\s+$symbol\b""")
        )

        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (filePattern != null && !matchesPattern(node.name, filePattern)) continue

                    projectRepository.readFile(projectId, node.path).onSuccess { content ->
                        content.lines().forEachIndexed { index, line ->
                            for (pattern in definitionPatterns) {
                                if (pattern.containsMatchIn(line)) {
                                    return "${node.path}:${index + 1}\n  $line"
                                }
                            }
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    val result = findSymbolDefinition(projectId, node.children, symbol, filePattern)
                    if (result != null) return result
                }
            }
        }
        return null
    }

    private fun matchesPattern(filename: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(filename)
    }

    private data class ImportInfo(val source: String, val file: String, val line: Int)

    private fun extractImports(content: String, path: String): List<ImportInfo> {
        val imports = mutableListOf<ImportInfo>()
        val extension = path.substringAfterLast('.', "")

        val patterns = when (extension) {
            "js", "jsx", "ts", "tsx" -> listOf(
                Regex("""import\s+.*?\s+from\s+['"](.*?)['"]"""),
                Regex("""require\s*\(\s*['"](.*?)['"]\s*\)""")
            )
            "py" -> listOf(
                Regex("""from\s+(\S+)\s+import"""),
                Regex("""import\s+(\S+)""")
            )
            "kt", "java" -> listOf(
                Regex("""import\s+([\w.]+)""")
            )
            else -> emptyList()
        }

        content.lines().forEachIndexed { index, line ->
            for (pattern in patterns) {
                pattern.find(line)?.let { match ->
                    imports.add(ImportInfo(match.groupValues[1], path, index + 1))
                }
            }
        }

        return imports
    }

    private fun formatImportAnalysis(imports: List<ImportInfo>, content: String, showUnused: Boolean): String {
        return buildString {
            appendLine("Found ${imports.size} import(s):")
            imports.forEach { import ->
                appendLine("  Line ${import.line}: ${import.source}")
            }

            if (showUnused && imports.isNotEmpty()) {
                appendLine("\nPotentially unused imports:")
                imports.forEach { import ->
                    val moduleName = import.source.substringAfterLast('/').substringBefore('.')
                    val usageCount = content.split(moduleName).size - 1
                    if (usageCount <= 1) { // Only the import line
                        appendLine("  ${import.source} (only used once)")
                    }
                }
            }
        }
    }

    private suspend fun collectAllImports(
        projectId: String,
        nodes: List<FileTreeNode>,
        imports: MutableList<ImportInfo>
    ) {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    val ext = node.name.substringAfterLast('.', "")
                    if (ext in listOf("js", "jsx", "ts", "tsx", "py", "kt", "java")) {
                        projectRepository.readFile(projectId, node.path).onSuccess { content ->
                            imports.addAll(extractImports(content, node.path))
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    collectAllImports(projectId, node.children, imports)
                }
            }
        }
    }

    private fun extractPackageJsonDeps(content: String): Map<String, String> {
        val deps = mutableMapOf<String, String>()
        val patterns = listOf(
            Regex(""""dependencies"\s*:\s*\{([^}]+)\}"""),
            Regex(""""devDependencies"\s*:\s*\{([^}]+)\}""")
        )

        patterns.forEach { pattern ->
            pattern.find(content)?.let { match ->
                val depsBlock = match.groupValues[1]
                Regex(""""(\w[\w-]*)"\s*:\s*"([^"]+)"""").findAll(depsBlock).forEach { dep ->
                    deps[dep.groupValues[1]] = dep.groupValues[2]
                }
            }
        }

        return deps
    }

    private fun extractGradleDeps(content: String): List<String> {
        val deps = mutableListOf<String>()
        val patterns = listOf(
            Regex("""implementation\s*[("']([^)"']+)[)"']"""),
            Regex("""api\s*[("']([^)"']+)[)"']"""),
            Regex("""testImplementation\s*[("']([^)"']+)[)"']""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                deps.add(match.groupValues[1])
            }
        }

        return deps
    }

    private data class RenameChange(val file: String, val line: Int, val preview: String)

    private suspend fun collectRenameChanges(
        projectId: String,
        nodes: List<FileTreeNode>,
        oldName: String,
        newName: String,
        filePattern: String?,
        changes: MutableList<RenameChange>
    ) {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (filePattern != null && !matchesPattern(node.name, filePattern)) continue

                    projectRepository.readFile(projectId, node.path).onSuccess { content ->
                        content.lines().forEachIndexed { index, line ->
                            if (line.contains(oldName)) {
                                val preview = line.trim().take(60)
                                changes.add(RenameChange(node.path, index + 1, preview))
                            }
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    collectRenameChanges(projectId, node.children, oldName, newName, filePattern, changes)
                }
            }
        }
    }

    private fun collectMatchingFiles(
        nodes: List<FileTreeNode>,
        pattern: String,
        files: MutableList<String>
    ) {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (matchesPattern(node.path, pattern) || matchesPattern(node.name, pattern)) {
                        files.add(node.path)
                    }
                }
                is FileTreeNode.FolderNode -> {
                    collectMatchingFiles(node.children, pattern, files)
                }
            }
        }
    }

    private data class SyntaxError(val line: Int, val message: String)

    private fun checkSyntaxForType(content: String, extension: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()

        when (extension) {
            "json" -> {
                try {
                    kotlinx.serialization.json.Json.parseToJsonElement(content)
                } catch (e: Exception) {
                    errors.add(SyntaxError(1, e.message ?: "Invalid JSON"))
                }
            }
            "html", "htm" -> {
                errors.addAll(checkHtmlSyntax(content))
            }
            "js", "jsx", "ts", "tsx" -> {
                errors.addAll(checkJsSyntax(content))
            }
            "css" -> {
                errors.addAll(checkCssSyntax(content))
            }
        }

        return errors
    }

    private fun checkHtmlSyntax(content: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        val tagStack = mutableListOf<Pair<String, Int>>()
        val selfClosingTags = setOf("br", "hr", "img", "input", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr")

        val tagPattern = Regex("""<(/?)(\w+)[^>]*(/?)>""")

        content.lines().forEachIndexed { index, line ->
            tagPattern.findAll(line).forEach { match ->
                val isClosing = match.groupValues[1] == "/"
                val tagName = match.groupValues[2].lowercase()
                val isSelfClosing = match.groupValues[3] == "/" || tagName in selfClosingTags

                if (!isSelfClosing) {
                    if (isClosing) {
                        if (tagStack.isEmpty()) {
                            errors.add(SyntaxError(index + 1, "Unexpected closing tag: </$tagName>"))
                        } else if (tagStack.last().first != tagName) {
                            errors.add(SyntaxError(index + 1, "Mismatched tag: expected </${tagStack.last().first}>, found </$tagName>"))
                        } else {
                            tagStack.removeAt(tagStack.lastIndex)
                        }
                    } else {
                        tagStack.add(tagName to index + 1)
                    }
                }
            }
        }

        tagStack.forEach { (tag, line) ->
            errors.add(SyntaxError(line, "Unclosed tag: <$tag>"))
        }

        return errors
    }

    private fun checkJsSyntax(content: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        var braceCount = 0
        var parenCount = 0
        var bracketCount = 0

        content.lines().forEachIndexed { index, line ->
            // Skip strings and comments for counting
            val cleanLine = line.replace(Regex("""(['"`])(?:(?!\1)[^\\]|\\.)*\1"""), "")
                .replace(Regex("""//.*$"""), "")

            braceCount += cleanLine.count { it == '{' } - cleanLine.count { it == '}' }
            parenCount += cleanLine.count { it == '(' } - cleanLine.count { it == ')' }
            bracketCount += cleanLine.count { it == '[' } - cleanLine.count { it == ']' }

            if (braceCount < 0) {
                errors.add(SyntaxError(index + 1, "Unexpected '}'"))
                braceCount = 0
            }
            if (parenCount < 0) {
                errors.add(SyntaxError(index + 1, "Unexpected ')'"))
                parenCount = 0
            }
            if (bracketCount < 0) {
                errors.add(SyntaxError(index + 1, "Unexpected ']'"))
                bracketCount = 0
            }
        }

        if (braceCount > 0) errors.add(SyntaxError(content.lines().size, "Missing $braceCount closing brace(s)"))
        if (parenCount > 0) errors.add(SyntaxError(content.lines().size, "Missing $parenCount closing parenthesis(es)"))
        if (bracketCount > 0) errors.add(SyntaxError(content.lines().size, "Missing $bracketCount closing bracket(s)"))

        return errors
    }

    private fun checkCssSyntax(content: String): List<SyntaxError> {
        val errors = mutableListOf<SyntaxError>()
        var braceCount = 0

        content.lines().forEachIndexed { index, line ->
            braceCount += line.count { it == '{' } - line.count { it == '}' }

            if (braceCount < 0) {
                errors.add(SyntaxError(index + 1, "Unexpected '}'"))
                braceCount = 0
            }

            // Check for common issues
            if (line.contains(":") && !line.contains(";") && !line.contains("{") && line.trim().isNotEmpty()) {
                if (!line.trim().endsWith(",") && !line.trim().endsWith("{")) {
                    errors.add(SyntaxError(index + 1, "Missing semicolon"))
                }
            }
        }

        if (braceCount > 0) errors.add(SyntaxError(content.lines().size, "Missing $braceCount closing brace(s)"))

        return errors
    }

    private fun formatCodeContent(content: String, extension: String, indentSize: Int, useTabs: Boolean): String {
        val indent = if (useTabs) "\t" else " ".repeat(indentSize)

        return when (extension) {
            "json" -> {
                try {
                    val element = kotlinx.serialization.json.Json.parseToJsonElement(content)
                    kotlinx.serialization.json.Json { prettyPrint = true }.encodeToString(
                        kotlinx.serialization.json.JsonElement.serializer(),
                        element
                    )
                } catch (e: Exception) {
                    content // Return original if parsing fails
                }
            }
            else -> {
                // Basic indentation normalization
                var currentIndent = 0
                content.lines().joinToString("\n") { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@joinToString ""

                    // Decrease indent for closing braces
                    if (trimmed.startsWith("}") || trimmed.startsWith("]") || trimmed.startsWith(")")) {
                        currentIndent = maxOf(0, currentIndent - 1)
                    }

                    val result = indent.repeat(currentIndent) + trimmed

                    // Increase indent for opening braces
                    if (trimmed.endsWith("{") || trimmed.endsWith("[") || trimmed.endsWith("(")) {
                        currentIndent++
                    }

                    result
                }
            }
        }
    }

    private fun validateJsonContent(content: String): List<String> {
        val errors = mutableListOf<String>()

        try {
            kotlinx.serialization.json.Json.parseToJsonElement(content)
        } catch (e: Exception) {
            errors.add(e.message ?: "Invalid JSON")
        }

        return errors
    }

    private data class HtmlError(val line: Int, val message: String)

    private fun validateHtmlContent(content: String, checkAccessibility: Boolean): List<HtmlError> {
        val errors = mutableListOf<HtmlError>()

        // Add syntax errors
        checkHtmlSyntax(content).forEach { error ->
            errors.add(HtmlError(error.line, error.message))
        }

        if (checkAccessibility) {
            content.lines().forEachIndexed { index, line ->
                // Check for images without alt
                if (line.contains("<img") && !line.contains("alt=")) {
                    errors.add(HtmlError(index + 1, "Image missing alt attribute"))
                }

                // Check for links without accessible text
                if (Regex("""<a[^>]*>\s*</a>""").containsMatchIn(line)) {
                    errors.add(HtmlError(index + 1, "Empty link text"))
                }

                // Check for form inputs without labels
                if (line.contains("<input") && !line.contains("aria-label") && !line.contains("id=")) {
                    errors.add(HtmlError(index + 1, "Input may be missing associated label"))
                }
            }
        }

        return errors
    }

    private suspend fun detectFramework(projectId: String): String {
        // Check package.json
        projectRepository.readFile(projectId, "package.json").onSuccess { content ->
            return when {
                content.contains("\"next\"") -> "next"
                content.contains("\"react\"") -> "react"
                content.contains("\"vue\"") -> "vue"
                content.contains("\"svelte\"") -> "svelte"
                else -> "vanilla"
            }
        }

        // Check for specific files
        if (projectRepository.readFile(projectId, "next.config.js").isSuccess) return "next"
        if (projectRepository.readFile(projectId, "vue.config.js").isSuccess) return "vue"
        if (projectRepository.readFile(projectId, "svelte.config.js").isSuccess) return "svelte"

        return "vanilla"
    }

    private fun generateReactComponent(name: String, type: String): String {
        return when (type) {
            "class" -> """
                |import React, { Component } from 'react';
                |
                |class $name extends Component {
                |  constructor(props) {
                |    super(props);
                |    this.state = {};
                |  }
                |
                |  render() {
                |    return (
                |      <div className="$name">
                |        <h1>$name Component</h1>
                |      </div>
                |    );
                |  }
                |}
                |
                |export default $name;
            """.trimMargin()
            else -> """
                |import React from 'react';
                |
                |const $name = () => {
                |  return (
                |    <div className="$name">
                |      <h1>$name Component</h1>
                |    </div>
                |  );
                |};
                |
                |export default $name;
            """.trimMargin()
        }
    }

    private fun generateVueComponent(name: String): String {
        return """
            |<template>
            |  <div class="$name">
            |    <h1>$name Component</h1>
            |  </div>
            |</template>
            |
            |<script>
            |export default {
            |  name: '$name',
            |  data() {
            |    return {};
            |  },
            |};
            |</script>
            |
            |<style scoped>
            |.$name {
            |  /* Component styles */
            |}
            |</style>
        """.trimMargin()
    }

    private fun generateSvelteComponent(name: String): String {
        return """
            |<script>
            |  // Component logic
            |</script>
            |
            |<div class="$name">
            |  <h1>$name Component</h1>
            |</div>
            |
            |<style>
            |  .$name {
            |    /* Component styles */
            |  }
            |</style>
        """.trimMargin()
    }

    private fun generateTestFile(name: String, framework: String): String {
        return """
            |describe('$name', () => {
            |  it('should render correctly', () => {
            |    // Add test implementation
            |    expect(true).toBe(true);
            |  });
            |});
        """.trimMargin()
    }

    private fun generateApiBoilerplate(): String {
        return """
            |// API Routes
            |
            |export const fetchData = async (endpoint) => {
            |  const response = await fetch(endpoint);
            |  if (!response.ok) {
            |    throw new Error(`HTTP error! status: ${'$'}{response.status}`);
            |  }
            |  return response.json();
            |};
            |
            |export const postData = async (endpoint, data) => {
            |  const response = await fetch(endpoint, {
            |    method: 'POST',
            |    headers: {
            |      'Content-Type': 'application/json',
            |    },
            |    body: JSON.stringify(data),
            |  });
            |  if (!response.ok) {
            |    throw new Error(`HTTP error! status: ${'$'}{response.status}`);
            |  }
            |  return response.json();
            |};
        """.trimMargin()
    }

    private fun generateTestSetup(): String {
        return """
            |// Test Setup
            |
            |beforeEach(() => {
            |  // Setup before each test
            |});
            |
            |afterEach(() => {
            |  // Cleanup after each test
            |});
        """.trimMargin()
    }

    private fun generateCiWorkflow(): String {
        return """
            |name: CI
            |
            |on:
            |  push:
            |    branches: [main]
            |  pull_request:
            |    branches: [main]
            |
            |jobs:
            |  build:
            |    runs-on: ubuntu-latest
            |
            |    steps:
            |      - uses: actions/checkout@v3
            |
            |      - name: Setup Node.js
            |        uses: actions/setup-node@v3
            |        with:
            |          node-version: '18'
            |
            |      - name: Install dependencies
            |        run: npm ci
            |
            |      - name: Run tests
            |        run: npm test
            |
            |      - name: Build
            |        run: npm run build
        """.trimMargin()
    }

    private fun generateFileSummary(content: String, extension: String, path: String): String {
        return buildString {
            appendLine("File: $path")
            appendLine("Type: $extension")
            appendLine("Lines: ${content.lines().size}")
            appendLine("Size: ${content.length} characters")
            appendLine()

            // Extract key elements based on file type
            when (extension) {
                "js", "jsx", "ts", "tsx" -> {
                    appendLine("Exports:")
                    Regex("""export\s+(default\s+)?(function|const|class|let|var)\s+(\w+)""")
                        .findAll(content).forEach { match ->
                            val isDefault = match.groupValues[1].isNotEmpty()
                            val type = match.groupValues[2]
                            val name = match.groupValues[3]
                            appendLine("  ${if (isDefault) "(default) " else ""}$type $name")
                        }

                    appendLine()
                    appendLine("Functions:")
                    Regex("""(function|const|let|var)\s+(\w+)\s*[=:]\s*(async\s+)?(\([^)]*\)|[\w]+)\s*=>""")
                        .findAll(content).forEach { match ->
                            appendLine("  ${match.groupValues[2]}")
                        }
                    Regex("""function\s+(\w+)\s*\(""")
                        .findAll(content).forEach { match ->
                            appendLine("  ${match.groupValues[1]}")
                        }
                }
                "py" -> {
                    appendLine("Classes:")
                    Regex("""class\s+(\w+)""").findAll(content).forEach { match ->
                        appendLine("  ${match.groupValues[1]}")
                    }

                    appendLine()
                    appendLine("Functions:")
                    Regex("""def\s+(\w+)\s*\(""").findAll(content).forEach { match ->
                        appendLine("  ${match.groupValues[1]}")
                    }
                }
                "kt", "java" -> {
                    appendLine("Classes:")
                    Regex("""(class|object|interface)\s+(\w+)""").findAll(content).forEach { match ->
                        appendLine("  ${match.groupValues[1]} ${match.groupValues[2]}")
                    }

                    appendLine()
                    appendLine("Functions:")
                    Regex("""fun\s+(\w+)\s*\(""").findAll(content).forEach { match ->
                        appendLine("  ${match.groupValues[1]}")
                    }
                }
                "html" -> {
                    appendLine("Structure:")
                    val hasHead = content.contains("<head")
                    val hasBody = content.contains("<body")
                    val scripts = Regex("""<script[^>]*>""").findAll(content).count()
                    val styles = Regex("""<link[^>]*stylesheet[^>]*>|<style[^>]*>""").findAll(content).count()

                    appendLine("  Has <head>: $hasHead")
                    appendLine("  Has <body>: $hasBody")
                    appendLine("  Scripts: $scripts")
                    appendLine("  Stylesheets: $styles")
                }
                "css" -> {
                    appendLine("Selectors:")
                    Regex("""([.#]?[\w-]+)\s*\{""").findAll(content).take(20).forEach { match ->
                        appendLine("  ${match.groupValues[1]}")
                    }
                }
            }
        }
    }

    private fun buildProjectTree(
        nodes: List<FileTreeNode>,
        prefix: String,
        maxDepth: Int,
        currentDepth: Int,
        output: StringBuilder
    ) {
        if (currentDepth >= maxDepth) return

        nodes.forEachIndexed { index, node ->
            val isLast = index == nodes.lastIndex
            val connector = if (isLast) "└── " else "├── "
            val childPrefix = if (isLast) "    " else "│   "

            when (node) {
                is FileTreeNode.FileNode -> {
                    output.appendLine("$prefix$connector${node.name}")
                }
                is FileTreeNode.FolderNode -> {
                    output.appendLine("$prefix$connector${node.name}/")
                    buildProjectTree(node.children, prefix + childPrefix, maxDepth, currentDepth + 1, output)
                }
            }
        }
    }

    private data class ProjectStats(
        val fileCount: Int,
        val folderCount: Int,
        val extensionCounts: Map<String, Int>
    )

    private fun collectProjectStats(nodes: List<FileTreeNode>): ProjectStats {
        var fileCount = 0
        var folderCount = 0
        val extensions = mutableMapOf<String, Int>()

        fun traverse(nodeList: List<FileTreeNode>) {
            for (node in nodeList) {
                when (node) {
                    is FileTreeNode.FileNode -> {
                        fileCount++
                        val ext = node.name.substringAfterLast('.', "")
                        if (ext.isNotEmpty()) {
                            extensions[ext] = (extensions[ext] ?: 0) + 1
                        }
                    }
                    is FileTreeNode.FolderNode -> {
                        folderCount++
                        traverse(node.children)
                    }
                }
            }
        }

        traverse(nodes)
        return ProjectStats(fileCount, folderCount, extensions)
    }

    private fun generateDocumentation(
        content: String,
        extension: String,
        symbol: String?,
        style: String
    ): String {
        return buildString {
            when (style.lowercase()) {
                "jsdoc" -> {
                    appendLine("/**")
                    appendLine(" * @description TODO: Add description")
                    appendLine(" * @param {type} param1 - Description")
                    appendLine(" * @returns {type} Description")
                    appendLine(" */")
                }
                "tsdoc" -> {
                    appendLine("/**")
                    appendLine(" * Description")
                    appendLine(" * @param param1 - Description")
                    appendLine(" * @returns Description")
                    appendLine(" */")
                }
                "kdoc" -> {
                    appendLine("/**")
                    appendLine(" * Description")
                    appendLine(" * @param param1 Description")
                    appendLine(" * @return Description")
                    appendLine(" */")
                }
                "markdown" -> {
                    appendLine("# ${symbol ?: "Documentation"}")
                    appendLine()
                    appendLine("## Overview")
                    appendLine("TODO: Add overview")
                    appendLine()
                    appendLine("## Usage")
                    appendLine("```")
                    appendLine("// Add usage example")
                    appendLine("```")
                }
                else -> {
                    appendLine("// Documentation for ${symbol ?: "this file"}")
                }
            }
        }
    }

    private fun extractPackageJsonScripts(content: String): Map<String, String> {
        val scripts = mutableMapOf<String, String>()

        Regex(""""scripts"\s*:\s*\{([^}]+)\}""").find(content)?.let { match ->
            val scriptsBlock = match.groupValues[1]
            Regex(""""(\w[\w-]*)"\s*:\s*"([^"]+)"""").findAll(scriptsBlock).forEach { script ->
                scripts[script.groupValues[1]] = script.groupValues[2]
            }
        }

        return scripts
    }
}
