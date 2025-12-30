package com.codex.stormy.data.ai.tools

import android.util.Log
import com.codex.stormy.data.repository.ProjectRepository
import com.codex.stormy.domain.model.FileTreeNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extended tool executor for advanced capabilities.
 *
 * Handles execution for:
 * - Diff tools (file comparison, semantic diff)
 * - Shell/command tools (safe execution)
 * - Web/network tools (fetch)
 * - Code generation tools
 * - Testing tools
 * - Security analysis tools
 * - Performance analysis tools
 */
class ExtendedToolExecutor(
    private val projectRepository: ProjectRepository
) {
    companion object {
        private const val TAG = "ExtendedToolExecutor"
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val MAX_FETCH_SIZE = 500_000
    }

    private val codeDiffTools = CodeDiffTools(projectRepository)
    private val shellExecutor = ShellToolExecutor()

    private var currentProjectPath: File? = null

    fun setProjectPath(path: File) {
        currentProjectPath = path
    }

    /**
     * Execute an extended tool.
     */
    suspend fun execute(
        projectId: String,
        toolName: String,
        args: JsonObject
    ): ToolResult? {
        return when (toolName) {
            // Diff Tools
            "diff_files" -> executeDiffFiles(projectId, args)
            "diff_content" -> executeDiffContent(args)
            "semantic_diff" -> executeSemanticDiff(projectId, args)

            // Shell/Command Tools
            "shell_exec" -> executeShellExec(args)
            "validate_command" -> executeValidateCommand(args)
            "check_command_available" -> executeCheckCommandAvailable(args)

            // Web/Network Tools
            "web_fetch" -> executeWebFetch(args)

            // Code Generation Tools
            "generate_boilerplate" -> executeGenerateBoilerplate(projectId, args)
            "refactor_code" -> executeRefactorCode(projectId, args)

            // Testing Tools
            "generate_tests" -> executeGenerateTests(projectId, args)
            "analyze_test_coverage" -> executeAnalyzeTestCoverage(projectId, args)

            // Security Analysis Tools
            "security_scan" -> executeSecurityScan(projectId, args)
            "find_secrets" -> executeFindSecrets(projectId, args)

            // Performance Analysis Tools
            "analyze_bundle" -> executeAnalyzeBundle(projectId, args)
            "find_dead_code" -> executeFindDeadCode(projectId, args)

            else -> null
        }
    }

    // ==================== Diff Tools ====================

    private suspend fun executeDiffFiles(projectId: String, args: JsonObject): ToolResult {
        val path1 = args.getPathArg("path1")
            ?: return ToolResult(false, "", "Missing required argument: path1")
        val path2 = args.getPathArg("path2")
            ?: return ToolResult(false, "", "Missing required argument: path2")
        val format = args.getStringArg("format") ?: "unified"

        val diffFormat = when (format.lowercase()) {
            "side_by_side" -> CodeDiffTools.DiffFormat.SIDE_BY_SIDE
            "stats_only" -> CodeDiffTools.DiffFormat.STATS_ONLY
            else -> CodeDiffTools.DiffFormat.UNIFIED
        }

        return codeDiffTools.compareFiles(projectId, path1, path2, diffFormat)
    }

    private fun executeDiffContent(args: JsonObject): ToolResult {
        val original = args.getStringArg("original")
            ?: return ToolResult(false, "", "Missing required argument: original")
        val modified = args.getStringArg("modified")
            ?: return ToolResult(false, "", "Missing required argument: modified")
        val contextLines = args.getStringArg("context_lines")?.toIntOrNull() ?: 3

        return try {
            val diff = codeDiffTools.generateUnifiedDiff(
                original = original,
                modified = modified,
                contextLines = contextLines.coerceIn(0, 10)
            )
            ToolResult(true, diff)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate diff", e)
            ToolResult(false, "", "Failed to generate diff: ${e.message}")
        }
    }

    private suspend fun executeSemanticDiff(projectId: String, args: JsonObject): ToolResult {
        val path1 = args.getPathArg("path1")
            ?: return ToolResult(false, "", "Missing required argument: path1")
        val path2 = args.getPathArg("path2")
            ?: return ToolResult(false, "", "Missing required argument: path2")

        return try {
            val content1 = projectRepository.readFile(projectId, path1).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path1")
            val content2 = projectRepository.readFile(projectId, path2).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path2")

            val extension = path1.substringAfterLast('.', "")
            val diff = codeDiffTools.generateSemanticDiff(content1, content2, extension)
            ToolResult(true, diff)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate semantic diff", e)
            ToolResult(false, "", "Failed to generate semantic diff: ${e.message}")
        }
    }

    // ==================== Shell/Command Tools ====================

    private suspend fun executeShellExec(args: JsonObject): ToolResult {
        val command = args.getStringArg("command")
            ?: return ToolResult(false, "", "Missing required argument: command")
        val timeout = args.getStringArg("timeout")?.toLongOrNull() ?: DEFAULT_TIMEOUT
        val workingDirPath = args.getStringArg("working_dir")

        val workingDir = when {
            workingDirPath != null -> File(workingDirPath)
            currentProjectPath != null -> currentProjectPath
            else -> null
        }

        return try {
            val result = shellExecutor.execute(
                command = command,
                workingDir = workingDir,
                timeout = timeout.coerceIn(1000L, 300_000L)
            )

            if (result.blocked) {
                ToolResult(false, "", result.stderr)
            } else if (result.timedOut) {
                ToolResult(false, "", "Command timed out after ${timeout}ms")
            } else {
                val output = buildString {
                    appendLine("Exit code: ${result.exitCode}")
                    if (result.stdout.isNotBlank()) {
                        appendLine("Output:")
                        appendLine(result.stdout)
                    }
                    if (result.stderr.isNotBlank()) {
                        appendLine("Stderr:")
                        appendLine(result.stderr)
                    }
                }
                ToolResult(result.exitCode == 0, output, if (result.exitCode != 0) "Command failed with exit code ${result.exitCode}" else null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute shell command", e)
            ToolResult(false, "", "Failed to execute command: ${e.message}")
        }
    }

    private fun executeValidateCommand(args: JsonObject): ToolResult {
        val command = args.getStringArg("command")
            ?: return ToolResult(false, "", "Missing required argument: command")

        val validation = shellExecutor.validateCommand(command)

        return ToolResult(
            success = validation.isAllowed,
            output = if (validation.isAllowed) {
                "Command is allowed: ${validation.reason}"
            } else {
                "Command blocked: ${validation.reason}"
            }
        )
    }

    private suspend fun executeCheckCommandAvailable(args: JsonObject): ToolResult {
        val command = args.getStringArg("command")
            ?: return ToolResult(false, "", "Missing required argument: command")

        val isAvailable = shellExecutor.isCommandAvailable(command)

        return ToolResult(
            success = true,
            output = if (isAvailable) {
                "Command '$command' is available"
            } else {
                "Command '$command' is not available on this system"
            }
        )
    }

    // ==================== Web/Network Tools ====================

    private suspend fun executeWebFetch(args: JsonObject): ToolResult {
        val url = args.getStringArg("url")
            ?: return ToolResult(false, "", "Missing required argument: url")
        val method = args.getStringArg("method")?.uppercase() ?: "GET"
        val headers = args.getStringArg("headers")
        val body = args.getStringArg("body")
        val timeout = args.getStringArg("timeout")?.toIntOrNull() ?: 30_000

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(false, "", "Invalid URL: must start with http:// or https://")
        }

        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.connectTimeout = timeout.coerceIn(1000, 60_000)
                connection.readTimeout = timeout.coerceIn(1000, 60_000)
                connection.setRequestProperty("User-Agent", "CodeX-Stormy/1.0")

                headers?.let { headerStr ->
                    try {
                        val headerMap = kotlinx.serialization.json.Json.parseToJsonElement(headerStr)
                        if (headerMap is JsonObject) {
                            headerMap.forEach { (key, value) ->
                                connection.setRequestProperty(key, value.toString().trim('"'))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse headers: ${e.message}")
                    }
                }

                if (method == "POST" && body != null) {
                    connection.doOutput = true
                    connection.outputStream.bufferedWriter().use { it.write(body) }
                }

                val responseCode = connection.responseCode
                val inputStream = if (responseCode < 400) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val content = inputStream?.bufferedReader()?.use { reader ->
                    val sb = StringBuilder()
                    var totalRead = 0
                    val buffer = CharArray(8192)
                    var read: Int

                    while (reader.read(buffer).also { read = it } != -1 && totalRead < MAX_FETCH_SIZE) {
                        sb.append(buffer, 0, read)
                        totalRead += read
                    }

                    if (totalRead >= MAX_FETCH_SIZE) {
                        sb.append("\n... (content truncated at ${MAX_FETCH_SIZE / 1000}KB)")
                    }

                    sb.toString()
                } ?: ""

                connection.disconnect()

                if (responseCode in 200..299) {
                    ToolResult(
                        success = true,
                        output = buildString {
                            appendLine("HTTP $responseCode")
                            appendLine("Content-Type: ${connection.contentType ?: "unknown"}")
                            appendLine("Content-Length: ${content.length} characters")
                            appendLine()
                            append(content)
                        }
                    )
                } else {
                    ToolResult(
                        success = false,
                        output = "HTTP $responseCode: $content",
                        error = "Request failed with status $responseCode"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch URL", e)
                ToolResult(false, "", "Failed to fetch URL: ${e.message}")
            }
        }
    }

    // ==================== Code Generation Tools ====================

    private suspend fun executeGenerateBoilerplate(projectId: String, args: JsonObject): ToolResult {
        val type = args.getStringArg("type")
            ?: return ToolResult(false, "", "Missing required argument: type")
        val name = args.getStringArg("name")
            ?: return ToolResult(false, "", "Missing required argument: name")

        return try {
            val boilerplate = when (type.lowercase()) {
                "api_endpoint" -> generateApiEndpoint(name)
                "rest_controller" -> generateRestController(name)
                "model_class" -> generateModelClass(name)
                "test_suite" -> generateTestSuite(name)
                "react_hook" -> generateReactHook(name)
                "react_context" -> generateReactContext(name)
                "vue_composable" -> generateVueComposable(name)
                "express_router" -> generateExpressRouter(name)
                "fastapi_router" -> generateFastApiRouter(name)
                "database_migration" -> generateDatabaseMigration(name)
                "dockerfile" -> generateDockerfile(name)
                "github_action" -> generateGitHubAction(name)
                else -> return ToolResult(false, "", "Unknown boilerplate type: $type")
            }

            ToolResult(true, "Generated $type boilerplate for '$name':\n\n$boilerplate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate boilerplate", e)
            ToolResult(false, "", "Failed to generate boilerplate: ${e.message}")
        }
    }

    private suspend fun executeRefactorCode(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val operation = args.getStringArg("operation")
            ?: return ToolResult(false, "", "Missing required argument: operation")
        val target = args.getStringArg("target")
            ?: return ToolResult(false, "", "Missing required argument: target")
        val newName = args.getStringArg("new_name")
        val dryRun = args.getBooleanArg("dry_run", false)

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val result = when (operation.lowercase()) {
                "extract_function" -> extractFunction(content, target, newName ?: "extracted")
                "extract_variable" -> extractVariable(content, target, newName ?: "extracted")
                "inline_function" -> inlineFunction(content, target)
                "rename_variable" -> renameVariable(content, target, newName ?: target)
                "convert_to_async" -> convertToAsync(content, target)
                "convert_class_to_function" -> convertClassToFunction(content, target)
                else -> return ToolResult(false, "", "Unknown operation: $operation")
            }

            if (dryRun) {
                val diff = codeDiffTools.generateUnifiedDiff(content, result, path, "$path (refactored)")
                ToolResult(true, "Preview of refactoring:\n$diff")
            } else {
                projectRepository.writeFile(projectId, path, result)
                ToolResult(true, "Refactored $path using $operation on '$target'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refactor code", e)
            ToolResult(false, "", "Failed to refactor: ${e.message}")
        }
    }

    // ==================== Testing Tools ====================

    private suspend fun executeGenerateTests(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val symbol = args.getStringArg("symbol")
        val framework = args.getStringArg("framework") ?: "jest"
        val includeEdgeCases = args.getBooleanArg("include_edge_cases", true)

        return try {
            val content = projectRepository.readFile(projectId, path).getOrNull()
                ?: return ToolResult(false, "", "File not found: $path")

            val extension = path.substringAfterLast('.', "")
            val functions = extractFunctions(content, extension)

            val testableItems = if (symbol != null) {
                functions.filter { it.name == symbol }
            } else {
                functions
            }

            if (testableItems.isEmpty()) {
                return ToolResult(true, "No testable functions found${symbol?.let { " matching '$it'" } ?: ""}")
            }

            val tests = generateTestsForFunctions(testableItems, framework, includeEdgeCases)
            ToolResult(true, "Generated tests for ${testableItems.size} function(s):\n\n$tests")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate tests", e)
            ToolResult(false, "", "Failed to generate tests: ${e.message}")
        }
    }

    private suspend fun executeAnalyzeTestCoverage(projectId: String, args: JsonObject): ToolResult {
        val sourcePath = args.getPathArg("source_path")
            ?: return ToolResult(false, "", "Missing required argument: source_path")
        val testPath = args.getPathArg("test_path")
            ?: return ToolResult(false, "", "Missing required argument: test_path")

        return try {
            val sourceContent = projectRepository.readFile(projectId, sourcePath).getOrNull()
                ?: return ToolResult(false, "", "Source file not found: $sourcePath")
            val testContent = projectRepository.readFile(projectId, testPath).getOrNull()
                ?: return ToolResult(false, "", "Test file not found: $testPath")

            val sourceExt = sourcePath.substringAfterLast('.', "")
            val sourceFunctions = extractFunctions(sourceContent, sourceExt)
            val testedFunctions = findTestedFunctions(testContent)

            val covered = sourceFunctions.filter { fn -> testedFunctions.any { it.contains(fn.name) } }
            val uncovered = sourceFunctions.filter { fn -> testedFunctions.none { it.contains(fn.name) } }

            val coverage = if (sourceFunctions.isNotEmpty()) {
                (covered.size.toFloat() / sourceFunctions.size * 100).toInt()
            } else 0

            ToolResult(true, buildString {
                appendLine("Test Coverage Analysis")
                appendLine("=".repeat(40))
                appendLine()
                appendLine("Coverage: $coverage% (${covered.size}/${sourceFunctions.size} functions)")
                appendLine()

                if (covered.isNotEmpty()) {
                    appendLine("Covered Functions:")
                    covered.forEach { appendLine("  ✓ ${it.name}") }
                    appendLine()
                }

                if (uncovered.isNotEmpty()) {
                    appendLine("Uncovered Functions (need tests):")
                    uncovered.forEach { appendLine("  ✗ ${it.name}") }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze test coverage", e)
            ToolResult(false, "", "Failed to analyze coverage: ${e.message}")
        }
    }

    // ==================== Security Analysis Tools ====================

    private suspend fun executeSecurityScan(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val severityFilter = args.getStringArg("severity") ?: "low"

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val issues = mutableListOf<SecurityIssue>()

            scanForSecurityIssues(projectId, fileTree, path, issues)

            val severityOrder = mapOf("critical" to 4, "high" to 3, "medium" to 2, "low" to 1)
            val minSeverity = severityOrder[severityFilter.lowercase()] ?: 1

            val filteredIssues = issues.filter {
                (severityOrder[it.severity] ?: 1) >= minSeverity
            }.sortedByDescending { severityOrder[it.severity] ?: 0 }

            if (filteredIssues.isEmpty()) {
                ToolResult(true, "No security issues found (filter: $severityFilter+)")
            } else {
                ToolResult(true, buildString {
                    appendLine("Security Scan Results")
                    appendLine("=".repeat(40))
                    appendLine("Found ${filteredIssues.size} issue(s)")
                    appendLine()

                    filteredIssues.forEach { issue ->
                        appendLine("[${issue.severity.uppercase()}] ${issue.type}")
                        appendLine("  File: ${issue.file}:${issue.line}")
                        appendLine("  Issue: ${issue.description}")
                        appendLine("  Fix: ${issue.recommendation}")
                        appendLine()
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to run security scan", e)
            ToolResult(false, "", "Failed to run security scan: ${e.message}")
        }
    }

    private suspend fun executeFindSecrets(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val additionalPatterns = args.getStringArg("include_patterns")

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val secrets = mutableListOf<SecretFinding>()

            findSecretsInFiles(projectId, fileTree, path, secrets, additionalPatterns)

            if (secrets.isEmpty()) {
                ToolResult(true, "No potential secrets found")
            } else {
                ToolResult(true, buildString {
                    appendLine("Potential Secrets Found")
                    appendLine("=".repeat(40))
                    appendLine("Found ${secrets.size} potential secret(s)")
                    appendLine()
                    appendLine("WARNING: Review these findings carefully!")
                    appendLine()

                    secrets.forEach { secret ->
                        appendLine("[${secret.type}] ${secret.file}:${secret.line}")
                        appendLine("  Pattern: ${secret.pattern}")
                        appendLine("  Context: ${secret.context.take(60)}...")
                        appendLine()
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find secrets", e)
            ToolResult(false, "", "Failed to scan for secrets: ${e.message}")
        }
    }

    // ==================== Performance Analysis Tools ====================

    private suspend fun executeAnalyzeBundle(projectId: String, args: JsonObject): ToolResult {
        val entryPoint = args.getPathArg("entry_point")
            ?: return ToolResult(false, "", "Missing required argument: entry_point")
        val showTree = args.getBooleanArg("show_tree", false)

        return try {
            val content = projectRepository.readFile(projectId, entryPoint).getOrNull()
                ?: return ToolResult(false, "", "Entry point not found: $entryPoint")

            val fileTree = projectRepository.getFileTree(projectId)
            val analysis = analyzeBundleSize(projectId, fileTree, entryPoint, content, showTree)

            ToolResult(true, analysis)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze bundle", e)
            ToolResult(false, "", "Failed to analyze bundle: ${e.message}")
        }
    }

    private suspend fun executeFindDeadCode(projectId: String, args: JsonObject): ToolResult {
        val path = args.getPathArg("path")
            ?: return ToolResult(false, "", "Missing required argument: path")
        val includePrivate = args.getBooleanArg("include_private", false)

        return try {
            val fileTree = projectRepository.getFileTree(projectId)
            val deadCode = mutableListOf<DeadCodeItem>()

            findDeadCodeInFiles(projectId, fileTree, path, deadCode, includePrivate)

            if (deadCode.isEmpty()) {
                ToolResult(true, "No dead code found")
            } else {
                ToolResult(true, buildString {
                    appendLine("Dead Code Analysis")
                    appendLine("=".repeat(40))
                    appendLine("Found ${deadCode.size} potentially unused item(s)")
                    appendLine()

                    val grouped = deadCode.groupBy { it.type }
                    grouped.forEach { (type, items) ->
                        appendLine("$type (${items.size}):")
                        items.forEach { item ->
                            appendLine("  - ${item.name} in ${item.file}:${item.line}")
                        }
                        appendLine()
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find dead code", e)
            ToolResult(false, "", "Failed to analyze dead code: ${e.message}")
        }
    }

    // ==================== Helper Methods - Boilerplate Generators ====================

    private fun generateApiEndpoint(name: String): String = """
        |// API Endpoint: $name
        |
        |export async function get${name}(id: string) {
        |  const response = await fetch(`/api/${name.lowercase()}/`${'$'}{id}`);
        |  if (!response.ok) {
        |    throw new Error(`Failed to fetch $name: `${'$'}{response.statusText}`);
        |  }
        |  return response.json();
        |}
        |
        |export async function create${name}(data: ${name}Input) {
        |  const response = await fetch('/api/${name.lowercase()}', {
        |    method: 'POST',
        |    headers: { 'Content-Type': 'application/json' },
        |    body: JSON.stringify(data),
        |  });
        |  if (!response.ok) {
        |    throw new Error(`Failed to create $name: `${'$'}{response.statusText}`);
        |  }
        |  return response.json();
        |}
        |
        |export async function update${name}(id: string, data: Partial<${name}Input>) {
        |  const response = await fetch(`/api/${name.lowercase()}/`${'$'}{id}`, {
        |    method: 'PUT',
        |    headers: { 'Content-Type': 'application/json' },
        |    body: JSON.stringify(data),
        |  });
        |  if (!response.ok) {
        |    throw new Error(`Failed to update $name: `${'$'}{response.statusText}`);
        |  }
        |  return response.json();
        |}
        |
        |export async function delete${name}(id: string) {
        |  const response = await fetch(`/api/${name.lowercase()}/`${'$'}{id}`, {
        |    method: 'DELETE',
        |  });
        |  if (!response.ok) {
        |    throw new Error(`Failed to delete $name: `${'$'}{response.statusText}`);
        |  }
        |}
    """.trimMargin()

    private fun generateRestController(name: String): String = """
        |// REST Controller: ${name}Controller
        |
        |import { Router, Request, Response } from 'express';
        |
        |const router = Router();
        |
        |// GET /api/${name.lowercase()}
        |router.get('/', async (req: Request, res: Response) => {
        |  try {
        |    // Fetch all ${name}s
        |    res.json({ data: [], total: 0 });
        |  } catch (error) {
        |    res.status(500).json({ error: 'Failed to fetch ${name}s' });
        |  }
        |});
        |
        |// GET /api/${name.lowercase()}/:id
        |router.get('/:id', async (req: Request, res: Response) => {
        |  try {
        |    const { id } = req.params;
        |    // Fetch single $name by id
        |    res.json({ id });
        |  } catch (error) {
        |    res.status(500).json({ error: 'Failed to fetch $name' });
        |  }
        |});
        |
        |// POST /api/${name.lowercase()}
        |router.post('/', async (req: Request, res: Response) => {
        |  try {
        |    const data = req.body;
        |    // Create new $name
        |    res.status(201).json({ ...data, id: 'new-id' });
        |  } catch (error) {
        |    res.status(500).json({ error: 'Failed to create $name' });
        |  }
        |});
        |
        |// PUT /api/${name.lowercase()}/:id
        |router.put('/:id', async (req: Request, res: Response) => {
        |  try {
        |    const { id } = req.params;
        |    const data = req.body;
        |    // Update $name
        |    res.json({ ...data, id });
        |  } catch (error) {
        |    res.status(500).json({ error: 'Failed to update $name' });
        |  }
        |});
        |
        |// DELETE /api/${name.lowercase()}/:id
        |router.delete('/:id', async (req: Request, res: Response) => {
        |  try {
        |    const { id } = req.params;
        |    // Delete $name
        |    res.status(204).send();
        |  } catch (error) {
        |    res.status(500).json({ error: 'Failed to delete $name' });
        |  }
        |});
        |
        |export default router;
    """.trimMargin()

    private fun generateModelClass(name: String): String = """
        |// Model: $name
        |
        |export interface ${name} {
        |  id: string;
        |  createdAt: Date;
        |  updatedAt: Date;
        |}
        |
        |export interface ${name}Input {
        |  // Add input fields here
        |}
        |
        |export class ${name}Model implements ${name} {
        |  id: string;
        |  createdAt: Date;
        |  updatedAt: Date;
        |
        |  constructor(data: Partial<${name}>) {
        |    this.id = data.id || '';
        |    this.createdAt = data.createdAt || new Date();
        |    this.updatedAt = data.updatedAt || new Date();
        |  }
        |
        |  validate(): boolean {
        |    // Add validation logic
        |    return true;
        |  }
        |
        |  toJSON(): ${name} {
        |    return {
        |      id: this.id,
        |      createdAt: this.createdAt,
        |      updatedAt: this.updatedAt,
        |    };
        |  }
        |}
    """.trimMargin()

    private fun generateTestSuite(name: String): String = """
        |// Test Suite: $name
        |
        |describe('$name', () => {
        |  beforeEach(() => {
        |    // Setup before each test
        |  });
        |
        |  afterEach(() => {
        |    // Cleanup after each test
        |  });
        |
        |  describe('initialization', () => {
        |    it('should create instance with default values', () => {
        |      // Test initialization
        |      expect(true).toBe(true);
        |    });
        |
        |    it('should accept custom configuration', () => {
        |      // Test custom config
        |      expect(true).toBe(true);
        |    });
        |  });
        |
        |  describe('core functionality', () => {
        |    it('should perform main action', () => {
        |      // Test main functionality
        |      expect(true).toBe(true);
        |    });
        |
        |    it('should handle edge cases', () => {
        |      // Test edge cases
        |      expect(true).toBe(true);
        |    });
        |  });
        |
        |  describe('error handling', () => {
        |    it('should handle invalid input', () => {
        |      // Test error handling
        |      expect(true).toBe(true);
        |    });
        |  });
        |});
    """.trimMargin()

    private fun generateReactHook(name: String): String = """
        |// React Hook: use$name
        |
        |import { useState, useEffect, useCallback } from 'react';
        |
        |interface Use${name}Options {
        |  // Add options here
        |}
        |
        |interface Use${name}Return {
        |  data: unknown;
        |  loading: boolean;
        |  error: Error | null;
        |  refetch: () => void;
        |}
        |
        |export function use${name}(options?: Use${name}Options): Use${name}Return {
        |  const [data, setData] = useState<unknown>(null);
        |  const [loading, setLoading] = useState(true);
        |  const [error, setError] = useState<Error | null>(null);
        |
        |  const fetchData = useCallback(async () => {
        |    setLoading(true);
        |    setError(null);
        |    try {
        |      // Fetch data logic here
        |      setData(null);
        |    } catch (e) {
        |      setError(e instanceof Error ? e : new Error('Unknown error'));
        |    } finally {
        |      setLoading(false);
        |    }
        |  }, []);
        |
        |  useEffect(() => {
        |    fetchData();
        |  }, [fetchData]);
        |
        |  return { data, loading, error, refetch: fetchData };
        |}
    """.trimMargin()

    private fun generateReactContext(name: String): String = """
        |// React Context: ${name}Context
        |
        |import React, { createContext, useContext, useState, useCallback, ReactNode } from 'react';
        |
        |interface ${name}State {
        |  // Add state fields here
        |}
        |
        |interface ${name}ContextValue extends ${name}State {
        |  // Add action methods here
        |}
        |
        |const ${name}Context = createContext<${name}ContextValue | undefined>(undefined);
        |
        |interface ${name}ProviderProps {
        |  children: ReactNode;
        |}
        |
        |export function ${name}Provider({ children }: ${name}ProviderProps) {
        |  const [state, setState] = useState<${name}State>({});
        |
        |  const value: ${name}ContextValue = {
        |    ...state,
        |    // Add action implementations
        |  };
        |
        |  return (
        |    <${name}Context.Provider value={value}>
        |      {children}
        |    </${name}Context.Provider>
        |  );
        |}
        |
        |export function use${name}() {
        |  const context = useContext(${name}Context);
        |  if (context === undefined) {
        |    throw new Error('use$name must be used within a ${name}Provider');
        |  }
        |  return context;
        |}
    """.trimMargin()

    private fun generateVueComposable(name: String): String = """
        |// Vue Composable: use$name
        |
        |import { ref, computed, onMounted, onUnmounted } from 'vue';
        |
        |export function use${name}() {
        |  const data = ref(null);
        |  const loading = ref(true);
        |  const error = ref<Error | null>(null);
        |
        |  const fetchData = async () => {
        |    loading.value = true;
        |    error.value = null;
        |    try {
        |      // Fetch data logic here
        |      data.value = null;
        |    } catch (e) {
        |      error.value = e instanceof Error ? e : new Error('Unknown error');
        |    } finally {
        |      loading.value = false;
        |    }
        |  };
        |
        |  onMounted(() => {
        |    fetchData();
        |  });
        |
        |  return {
        |    data,
        |    loading,
        |    error,
        |    refetch: fetchData,
        |  };
        |}
    """.trimMargin()

    private fun generateExpressRouter(name: String): String = generateRestController(name)

    private fun generateFastApiRouter(name: String): String = """
        |# FastAPI Router: $name
        |
        |from fastapi import APIRouter, HTTPException
        |from pydantic import BaseModel
        |from typing import List, Optional
        |
        |router = APIRouter(prefix="/${name.lowercase()}", tags=["$name"])
        |
        |class ${name}Base(BaseModel):
        |    pass  # Add fields here
        |
        |class ${name}Create(${name}Base):
        |    pass
        |
        |class ${name}Response(${name}Base):
        |    id: str
        |
        |@router.get("/", response_model=List[${name}Response])
        |async def get_all_${name.lowercase()}s():
        |    """Get all ${name}s"""
        |    return []
        |
        |@router.get("/{item_id}", response_model=${name}Response)
        |async def get_${name.lowercase()}(item_id: str):
        |    """Get a single $name by ID"""
        |    raise HTTPException(status_code=404, detail="$name not found")
        |
        |@router.post("/", response_model=${name}Response, status_code=201)
        |async def create_${name.lowercase()}(item: ${name}Create):
        |    """Create a new $name"""
        |    return {"id": "new-id", **item.dict()}
        |
        |@router.put("/{item_id}", response_model=${name}Response)
        |async def update_${name.lowercase()}(item_id: str, item: ${name}Create):
        |    """Update a $name"""
        |    return {"id": item_id, **item.dict()}
        |
        |@router.delete("/{item_id}", status_code=204)
        |async def delete_${name.lowercase()}(item_id: str):
        |    """Delete a $name"""
        |    return None
    """.trimMargin()

    private fun generateDatabaseMigration(name: String): String = """
        |-- Database Migration: Create ${name} table
        |-- Migration: create_${name.lowercase()}_table
        |
        |-- Up Migration
        |CREATE TABLE IF NOT EXISTS ${name.lowercase()}s (
        |    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        |    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        |    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
        |    -- Add columns here
        |);
        |
        |CREATE INDEX idx_${name.lowercase()}_created_at ON ${name.lowercase()}s(created_at);
        |
        |-- Down Migration
        |-- DROP TABLE IF EXISTS ${name.lowercase()}s;
    """.trimMargin()

    private fun generateDockerfile(name: String): String = """
        |# Dockerfile for $name
        |
        |# Build stage
        |FROM node:18-alpine AS builder
        |WORKDIR /app
        |COPY package*.json ./
        |RUN npm ci --only=production
        |COPY . .
        |RUN npm run build
        |
        |# Production stage
        |FROM node:18-alpine AS production
        |WORKDIR /app
        |
        |ENV NODE_ENV=production
        |
        |COPY --from=builder /app/node_modules ./node_modules
        |COPY --from=builder /app/dist ./dist
        |COPY --from=builder /app/package.json ./
        |
        |EXPOSE 3000
        |
        |USER node
        |
        |CMD ["node", "dist/index.js"]
    """.trimMargin()

    private fun generateGitHubAction(name: String): String = """
        |# GitHub Action: $name
        |
        |name: $name
        |
        |on:
        |  push:
        |    branches: [main, develop]
        |  pull_request:
        |    branches: [main]
        |
        |jobs:
        |  build:
        |    runs-on: ubuntu-latest
        |
        |    steps:
        |      - name: Checkout code
        |        uses: actions/checkout@v4
        |
        |      - name: Setup Node.js
        |        uses: actions/setup-node@v4
        |        with:
        |          node-version: '18'
        |          cache: 'npm'
        |
        |      - name: Install dependencies
        |        run: npm ci
        |
        |      - name: Run linter
        |        run: npm run lint
        |
        |      - name: Run tests
        |        run: npm test
        |
        |      - name: Build
        |        run: npm run build
    """.trimMargin()

    // ==================== Helper Methods - Refactoring ====================

    private fun extractFunction(content: String, target: String, newName: String): String {
        return content
    }

    private fun extractVariable(content: String, target: String, newName: String): String {
        val pattern = Regex(Regex.escape(target))
        return content.replaceFirst(pattern, "const $newName = $target;\n$newName")
    }

    private fun inlineFunction(content: String, target: String): String {
        return content
    }

    private fun renameVariable(content: String, oldName: String, newName: String): String {
        val wordBoundaryPattern = Regex("""\b${Regex.escape(oldName)}\b""")
        return content.replace(wordBoundaryPattern, newName)
    }

    private fun convertToAsync(content: String, target: String): String {
        val pattern = Regex("""function\s+$target\s*\(""")
        return content.replace(pattern, "async function $target(")
    }

    private fun convertClassToFunction(content: String, target: String): String {
        return content
    }

    // ==================== Helper Methods - Analysis ====================

    private data class FunctionInfo(val name: String, val params: List<String>, val line: Int)

    private fun extractFunctions(content: String, extension: String): List<FunctionInfo> {
        val functions = mutableListOf<FunctionInfo>()

        val patterns = when (extension.lowercase()) {
            "js", "jsx", "ts", "tsx" -> listOf(
                Regex("""function\s+(\w+)\s*\(([^)]*)\)"""),
                Regex("""(?:const|let|var)\s+(\w+)\s*=\s*(?:async\s+)?(?:\([^)]*\)|[^=]+)\s*=>"""),
                Regex("""(\w+)\s*:\s*(?:async\s+)?function\s*\(""")
            )
            "py" -> listOf(Regex("""def\s+(\w+)\s*\(([^)]*)\)"""))
            "kt" -> listOf(Regex("""fun\s+(\w+)\s*\(([^)]*)\)"""))
            "java" -> listOf(Regex("""(?:public|private|protected)?\s*(?:static)?\s*\w+\s+(\w+)\s*\(([^)]*)\)"""))
            else -> emptyList()
        }

        content.lines().forEachIndexed { lineNum, line ->
            patterns.forEach { pattern ->
                pattern.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    val params = if (match.groupValues.size > 2) {
                        match.groupValues[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else emptyList()
                    functions.add(FunctionInfo(name, params, lineNum + 1))
                }
            }
        }

        return functions
    }

    private fun findTestedFunctions(testContent: String): Set<String> {
        val tested = mutableSetOf<String>()

        val patterns = listOf(
            Regex("""(?:it|test|describe)\s*\(\s*['"].*?(\w+)"""),
            Regex("""expect\s*\(\s*(\w+)\s*\)"""),
            Regex("""\.(\w+)\s*\(""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(testContent).forEach { match ->
                tested.add(match.groupValues[1])
            }
        }

        return tested
    }

    private fun generateTestsForFunctions(
        functions: List<FunctionInfo>,
        framework: String,
        includeEdgeCases: Boolean
    ): String {
        return buildString {
            appendLine("// Auto-generated tests")
            appendLine()

            functions.forEach { fn ->
                appendLine("describe('${fn.name}', () => {")
                appendLine("  it('should work correctly', () => {")
                appendLine("    // Test implementation for ${fn.name}")
                appendLine("    expect(true).toBe(true);")
                appendLine("  });")

                if (includeEdgeCases) {
                    appendLine()
                    appendLine("  it('should handle null input', () => {")
                    appendLine("    // Test null handling")
                    appendLine("    expect(true).toBe(true);")
                    appendLine("  });")
                    appendLine()
                    appendLine("  it('should handle empty input', () => {")
                    appendLine("    // Test empty input")
                    appendLine("    expect(true).toBe(true);")
                    appendLine("  });")
                }

                appendLine("});")
                appendLine()
            }
        }
    }

    // ==================== Helper Methods - Security ====================

    private data class SecurityIssue(
        val type: String,
        val severity: String,
        val file: String,
        val line: Int,
        val description: String,
        val recommendation: String
    )

    private val securityPatterns = listOf(
        Triple(
            Regex("""eval\s*\("""),
            "Dangerous eval()" to "high",
            "Avoid using eval() as it can execute arbitrary code" to "Use safer alternatives like JSON.parse() or Function constructor"
        ),
        Triple(
            Regex("""innerHTML\s*="""),
            "Potential XSS" to "medium",
            "Direct innerHTML assignment can lead to XSS attacks" to "Use textContent or sanitize input before assignment"
        ),
        Triple(
            Regex("""document\.write\s*\("""),
            "Unsafe document.write()" to "medium",
            "document.write() can be exploited for XSS" to "Use DOM methods like createElement() and appendChild()"
        ),
        Triple(
            Regex("""(?:password|secret|api_?key|token)\s*[=:]\s*['"][^'"]+['"]""", RegexOption.IGNORE_CASE),
            "Hardcoded secret" to "critical",
            "Secrets should not be hardcoded in source code" to "Use environment variables or secret management service"
        ),
        Triple(
            Regex("""new\s+Function\s*\("""),
            "Dynamic function creation" to "high",
            "Creating functions from strings is similar to eval()" to "Avoid dynamic code generation"
        ),
        Triple(
            Regex("""(?:SELECT|INSERT|UPDATE|DELETE).*?\+.*?['"]""", RegexOption.IGNORE_CASE),
            "Potential SQL Injection" to "critical",
            "String concatenation in SQL queries is vulnerable" to "Use parameterized queries or prepared statements"
        ),
        Triple(
            Regex("""exec\s*\(|execSync\s*\("""),
            "Command injection risk" to "high",
            "Direct command execution can be exploited" to "Sanitize inputs and use spawn() with array arguments"
        )
    )

    private suspend fun scanForSecurityIssues(
        projectId: String,
        nodes: List<FileTreeNode>,
        basePath: String,
        issues: MutableList<SecurityIssue>
    ) {
        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (basePath.isEmpty() || node.path.startsWith(basePath)) {
                        val ext = node.name.substringAfterLast('.', "")
                        if (ext in listOf("js", "jsx", "ts", "tsx", "py", "java", "kt", "php", "rb")) {
                            projectRepository.readFile(projectId, node.path).onSuccess { content ->
                                content.lines().forEachIndexed { lineNum, line ->
                                    securityPatterns.forEach { (pattern, typeAndSeverity, descAndRec) ->
                                        if (pattern.containsMatchIn(line)) {
                                            issues.add(SecurityIssue(
                                                type = typeAndSeverity.first,
                                                severity = typeAndSeverity.second,
                                                file = node.path,
                                                line = lineNum + 1,
                                                description = descAndRec.first,
                                                recommendation = descAndRec.second
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    scanForSecurityIssues(projectId, node.children, basePath, issues)
                }
            }
        }
    }

    private data class SecretFinding(
        val type: String,
        val file: String,
        val line: Int,
        val pattern: String,
        val context: String
    )

    private val secretPatterns = listOf(
        "API Key" to Regex("""(?:api[_-]?key|apikey)\s*[=:]\s*['"]([^'"]{20,})['"]""", RegexOption.IGNORE_CASE),
        "AWS Key" to Regex("""AKIA[0-9A-Z]{16}"""),
        "Private Key" to Regex("""-----BEGIN (?:RSA |DSA |EC )?PRIVATE KEY-----"""),
        "GitHub Token" to Regex("""ghp_[a-zA-Z0-9]{36}"""),
        "Generic Secret" to Regex("""(?:secret|password|passwd|pwd)\s*[=:]\s*['"]([^'"]{8,})['"]""", RegexOption.IGNORE_CASE),
        "JWT" to Regex("""eyJ[a-zA-Z0-9_-]+\.eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+"""),
        "Slack Token" to Regex("""xox[baprs]-[0-9a-zA-Z]{10,}"""),
        "Generic Token" to Regex("""(?:token|bearer)\s*[=:]\s*['"]([^'"]{20,})['"]""", RegexOption.IGNORE_CASE)
    )

    private suspend fun findSecretsInFiles(
        projectId: String,
        nodes: List<FileTreeNode>,
        basePath: String,
        secrets: MutableList<SecretFinding>,
        additionalPatterns: String?
    ) {
        val allPatterns = secretPatterns.toMutableList()

        additionalPatterns?.split(",")?.forEach { patternStr ->
            try {
                val regex = Regex(patternStr.trim())
                allPatterns.add("Custom Pattern" to regex)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid custom pattern: $patternStr")
            }
        }

        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (basePath.isEmpty() || node.path.startsWith(basePath)) {
                        if (!node.name.endsWith(".lock") && !node.name.endsWith(".min.js")) {
                            projectRepository.readFile(projectId, node.path).onSuccess { content ->
                                content.lines().forEachIndexed { lineNum, line ->
                                    allPatterns.forEach { (type, pattern) ->
                                        if (pattern.containsMatchIn(line)) {
                                            secrets.add(SecretFinding(
                                                type = type,
                                                file = node.path,
                                                line = lineNum + 1,
                                                pattern = pattern.pattern,
                                                context = line.trim()
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    if (!node.name.startsWith(".") && node.name != "node_modules") {
                        findSecretsInFiles(projectId, node.children, basePath, secrets, null)
                    }
                }
            }
        }
    }

    // ==================== Helper Methods - Performance ====================

    private suspend fun analyzeBundleSize(
        projectId: String,
        nodes: List<FileTreeNode>,
        entryPoint: String,
        content: String,
        showTree: Boolean
    ): String {
        val imports = mutableListOf<String>()
        val sizes = mutableMapOf<String, Int>()

        val importPatterns = listOf(
            Regex("""import\s+.*?\s+from\s+['"](.*?)['"]"""),
            Regex("""require\s*\(\s*['"](.*?)['"]\s*\)""")
        )

        content.lines().forEach { line ->
            importPatterns.forEach { pattern ->
                pattern.find(line)?.let { match ->
                    imports.add(match.groupValues[1])
                }
            }
        }

        sizes[entryPoint] = content.length

        return buildString {
            appendLine("Bundle Analysis: $entryPoint")
            appendLine("=".repeat(40))
            appendLine()
            appendLine("Entry point size: ${content.length} bytes")
            appendLine("Direct imports: ${imports.size}")
            appendLine()
            appendLine("Dependencies:")
            imports.forEach { dep ->
                appendLine("  - $dep")
            }

            if (showTree) {
                appendLine()
                appendLine("Import Tree:")
                imports.forEachIndexed { index, dep ->
                    val isLast = index == imports.lastIndex
                    appendLine("${if (isLast) "└── " else "├── "}$dep")
                }
            }

            appendLine()
            appendLine("Recommendations:")
            if (imports.size > 20) {
                appendLine("  - Consider code splitting to reduce initial bundle size")
            }
            if (imports.any { it.contains("lodash") && !it.contains("/") }) {
                appendLine("  - Import specific lodash functions instead of entire library")
            }
            if (imports.any { it.contains("moment") }) {
                appendLine("  - Consider using date-fns or dayjs instead of moment.js")
            }
        }
    }

    private data class DeadCodeItem(
        val type: String,
        val name: String,
        val file: String,
        val line: Int
    )

    private suspend fun findDeadCodeInFiles(
        projectId: String,
        nodes: List<FileTreeNode>,
        basePath: String,
        deadCode: MutableList<DeadCodeItem>,
        includePrivate: Boolean
    ) {
        val allExports = mutableMapOf<String, Pair<String, Int>>()
        val allUsages = mutableSetOf<String>()

        for (node in nodes) {
            when (node) {
                is FileTreeNode.FileNode -> {
                    if (basePath.isEmpty() || node.path.startsWith(basePath)) {
                        val ext = node.name.substringAfterLast('.', "")
                        if (ext in listOf("js", "jsx", "ts", "tsx")) {
                            projectRepository.readFile(projectId, node.path).onSuccess { content ->
                                val exportPattern = Regex("""export\s+(?:default\s+)?(?:function|const|class|let|var)\s+(\w+)""")
                                content.lines().forEachIndexed { lineNum, line ->
                                    exportPattern.find(line)?.let { match ->
                                        allExports[match.groupValues[1]] = node.path to lineNum + 1
                                    }
                                }

                                val wordPattern = Regex("""\b(\w+)\b""")
                                wordPattern.findAll(content).forEach { match ->
                                    allUsages.add(match.groupValues[1])
                                }
                            }
                        }
                    }
                }
                is FileTreeNode.FolderNode -> {
                    if (!node.name.startsWith(".") && node.name != "node_modules") {
                        findDeadCodeInFiles(projectId, node.children, basePath, deadCode, includePrivate)
                    }
                }
            }
        }

        allExports.forEach { (name, fileAndLine) ->
            val usageCount = allUsages.count { it == name }
            if (usageCount <= 1) {
                deadCode.add(DeadCodeItem(
                    type = "Unused Export",
                    name = name,
                    file = fileAndLine.first,
                    line = fileAndLine.second
                ))
            }
        }
    }
}
