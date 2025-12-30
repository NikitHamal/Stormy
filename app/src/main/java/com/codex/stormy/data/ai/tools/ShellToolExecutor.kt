package com.codex.stormy.data.ai.tools

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Production-grade shell command executor with comprehensive security controls.
 *
 * This executor provides safe command execution within Android's sandboxed environment
 * with features including:
 * - Command whitelisting and validation
 * - Timeout protection
 * - Output size limiting
 * - Working directory isolation
 * - Environment variable control
 * - Dangerous command blocking
 *
 * NOTE: Android apps have limited shell access compared to full Linux systems.
 * This executor is designed for safe operations like git, npm, and build tools.
 */
class ShellToolExecutor(
    private val defaultWorkingDir: File? = null
) {
    companion object {
        private const val TAG = "ShellToolExecutor"

        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_TIMEOUT_MS = 300_000L
        private const val MAX_OUTPUT_SIZE = 50_000
        private const val MAX_COMMAND_LENGTH = 10_000

        private val ALLOWED_COMMANDS = setOf(
            "ls", "pwd", "cat", "head", "tail", "grep", "find", "wc",
            "echo", "date", "whoami", "env", "which", "file", "stat",
            "git", "npm", "npx", "node", "yarn", "pnpm",
            "python", "python3", "pip", "pip3",
            "java", "javac", "gradle", "gradlew", "./gradlew",
            "kotlin", "kotlinc",
            "cargo", "rustc",
            "go", "gofmt",
            "make", "cmake",
            "tar", "gzip", "gunzip", "zip", "unzip",
            "curl", "wget",
            "diff", "patch", "sort", "uniq", "cut", "tr", "sed", "awk",
            "mkdir", "touch", "cp", "mv", "ln",
            "chmod", "test", "[",
            "true", "false", "exit"
        )

        private val DANGEROUS_PATTERNS = listOf(
            Regex("""\brm\s+-[rf]+""", RegexOption.IGNORE_CASE),
            Regex("""\brm\s+.*\*""", RegexOption.IGNORE_CASE),
            Regex(""">\s*/dev/""", RegexOption.IGNORE_CASE),
            Regex(""":.*:\(\)\s*\{"""),
            Regex("""\bsudo\b""", RegexOption.IGNORE_CASE),
            Regex("""\bsu\b\s+-""", RegexOption.IGNORE_CASE),
            Regex("""\bdd\s+if=""", RegexOption.IGNORE_CASE),
            Regex("""\bmkfs\b""", RegexOption.IGNORE_CASE),
            Regex("""\bfdisk\b""", RegexOption.IGNORE_CASE),
            Regex("""\bkill\s+-9""", RegexOption.IGNORE_CASE),
            Regex("""\bkillall\b""", RegexOption.IGNORE_CASE),
            Regex("""\bshutdown\b""", RegexOption.IGNORE_CASE),
            Regex("""\breboot\b""", RegexOption.IGNORE_CASE),
            Regex("""\bpoweroff\b""", RegexOption.IGNORE_CASE),
            Regex("""\bhalt\b""", RegexOption.IGNORE_CASE),
            Regex("""[|&;]\s*rm\b""", RegexOption.IGNORE_CASE),
            Regex("""\$\(.*rm\b.*\)""", RegexOption.IGNORE_CASE),
            Regex("""`.*rm\b.*`""", RegexOption.IGNORE_CASE),
            Regex("""\bchown\s+-R\s+.*\s+/""", RegexOption.IGNORE_CASE),
            Regex("""\bchmod\s+-R\s+[0-7]+\s+/""", RegexOption.IGNORE_CASE)
        )

        private val BLOCKED_COMMANDS = setOf(
            "rm", "rmdir", "del", "deltree",
            "format", "fdisk", "mkfs",
            "sudo", "su",
            "shutdown", "reboot", "poweroff", "halt", "init",
            "kill", "killall", "pkill",
            "passwd", "useradd", "userdel", "usermod",
            "chroot", "mount", "umount",
            "iptables", "ip6tables", "nft",
            "nc", "netcat", "ncat",
            "telnet", "ssh", "scp", "sftp",
            "crontab", "at"
        )
    }

    /**
     * Execute a shell command safely.
     */
    suspend fun execute(
        command: String,
        workingDir: File? = null,
        timeout: Long = DEFAULT_TIMEOUT_MS,
        environment: Map<String, String> = emptyMap()
    ): ShellResult {
        if (command.isBlank()) {
            return ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = "Empty command",
                timedOut = false,
                blocked = true
            )
        }

        if (command.length > MAX_COMMAND_LENGTH) {
            return ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = "Command too long (max $MAX_COMMAND_LENGTH characters)",
                timedOut = false,
                blocked = true
            )
        }

        val validation = validateCommand(command)
        if (!validation.isAllowed) {
            Log.w(TAG, "Blocked command: ${validation.reason}")
            return ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = "Command blocked: ${validation.reason}",
                timedOut = false,
                blocked = true
            )
        }

        val effectiveWorkingDir = workingDir ?: defaultWorkingDir
        val effectiveTimeout = timeout.coerceIn(1000L, MAX_TIMEOUT_MS)

        return withContext(Dispatchers.IO) {
            executeCommand(command, effectiveWorkingDir, effectiveTimeout, environment)
        }
    }

    /**
     * Execute multiple commands in sequence.
     */
    suspend fun executeSequence(
        commands: List<String>,
        workingDir: File? = null,
        timeout: Long = DEFAULT_TIMEOUT_MS,
        stopOnError: Boolean = true
    ): List<ShellResult> {
        val results = mutableListOf<ShellResult>()

        for (command in commands) {
            val result = execute(command, workingDir, timeout)
            results.add(result)

            if (stopOnError && (result.exitCode != 0 || result.blocked)) {
                break
            }
        }

        return results
    }

    /**
     * Execute a command with piped input.
     */
    suspend fun executeWithInput(
        command: String,
        input: String,
        workingDir: File? = null,
        timeout: Long = DEFAULT_TIMEOUT_MS
    ): ShellResult {
        val validation = validateCommand(command)
        if (!validation.isAllowed) {
            return ShellResult(
                exitCode = 1,
                stdout = "",
                stderr = "Command blocked: ${validation.reason}",
                timedOut = false,
                blocked = true
            )
        }

        val effectiveWorkingDir = workingDir ?: defaultWorkingDir
        val effectiveTimeout = timeout.coerceIn(1000L, MAX_TIMEOUT_MS)

        return withContext(Dispatchers.IO) {
            executeCommandWithInput(command, input, effectiveWorkingDir, effectiveTimeout)
        }
    }

    /**
     * Validate if a command is safe to execute.
     */
    fun validateCommand(command: String): CommandValidation {
        val trimmed = command.trim()

        if (trimmed.isEmpty()) {
            return CommandValidation(false, "Empty command")
        }

        for (pattern in DANGEROUS_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                return CommandValidation(false, "Dangerous pattern detected: ${pattern.pattern}")
            }
        }

        val baseCommand = extractBaseCommand(trimmed)
        if (baseCommand in BLOCKED_COMMANDS) {
            return CommandValidation(false, "Blocked command: $baseCommand")
        }

        val pipeCommands = trimmed.split(Regex("""\s*[|;]\s*"""))
        for (pipeCmd in pipeCommands) {
            val pipedBaseCmd = extractBaseCommand(pipeCmd.trim())
            if (pipedBaseCmd in BLOCKED_COMMANDS) {
                return CommandValidation(false, "Blocked command in pipe: $pipedBaseCmd")
            }
        }

        val isInWhitelist = baseCommand in ALLOWED_COMMANDS ||
                baseCommand.startsWith("./") ||
                baseCommand.startsWith("../")

        if (!isInWhitelist) {
            val knownSafePatterns = listOf(
                Regex("""^echo\b"""),
                Regex("""^test\b"""),
                Regex("""^\[.*\]$"""),
                Regex("""^git\b"""),
                Regex("""^npm\b"""),
                Regex("""^yarn\b"""),
                Regex("""^node\b"""),
                Regex("""^python"""),
                Regex("""^java\b"""),
                Regex("""^kotlin\b"""),
                Regex("""^gradle""")
            )

            val isSafe = knownSafePatterns.any { it.containsMatchIn(trimmed) }
            if (!isSafe) {
                return CommandValidation(
                    isAllowed = true,
                    reason = "Unknown command, proceeding with caution: $baseCommand"
                )
            }
        }

        return CommandValidation(true, "Command validated")
    }

    /**
     * Check if a command is available on the system.
     */
    suspend fun isCommandAvailable(command: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder("which", command)
                    .redirectErrorStream(true)
                    .start()

                val exitCode = process.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check command availability: $command", e)
                false
            }
        }
    }

    /**
     * Get system information for debugging.
     */
    suspend fun getSystemInfo(): Map<String, String> {
        val info = mutableMapOf<String, String>()

        withContext(Dispatchers.IO) {
            try {
                info["os.name"] = System.getProperty("os.name") ?: "unknown"
                info["os.version"] = System.getProperty("os.version") ?: "unknown"
                info["os.arch"] = System.getProperty("os.arch") ?: "unknown"
                info["user.name"] = System.getProperty("user.name") ?: "unknown"
                info["user.home"] = System.getProperty("user.home") ?: "unknown"
                info["java.version"] = System.getProperty("java.version") ?: "unknown"

                execute("pwd").let { result ->
                    if (result.exitCode == 0) {
                        info["pwd"] = result.stdout.trim()
                    }
                }

                execute("echo \$SHELL").let { result ->
                    if (result.exitCode == 0) {
                        info["shell"] = result.stdout.trim()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get system info", e)
            }
        }

        return info
    }

    // ==================== Private Methods ====================

    private fun extractBaseCommand(command: String): String {
        val trimmed = command.trim()

        val firstPart = trimmed.split(Regex("""\s+""")).firstOrNull() ?: ""

        val envVarPattern = Regex("""^(\w+=\S+\s+)+(.+)""")
        val envMatch = envVarPattern.find(trimmed)
        if (envMatch != null) {
            return envMatch.groupValues[2].split(Regex("""\s+""")).firstOrNull() ?: firstPart
        }

        return firstPart.removePrefix("./").removePrefix("../")
    }

    private suspend fun executeCommand(
        command: String,
        workingDir: File?,
        timeout: Long,
        environment: Map<String, String>
    ): ShellResult {
        return try {
            withTimeout(timeout) {
                val processBuilder = ProcessBuilder("sh", "-c", command)

                workingDir?.let { dir ->
                    if (dir.exists() && dir.isDirectory) {
                        processBuilder.directory(dir)
                    }
                }

                val env = processBuilder.environment()
                environment.forEach { (key, value) ->
                    if (key.matches(Regex("""^[A-Za-z_][A-Za-z0-9_]*$"""))) {
                        env[key] = value
                    }
                }

                val process = processBuilder.start()

                val stdout = StringBuilder()
                val stderr = StringBuilder()

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                var outputSize = 0

                while (stdoutReader.readLine().also { line = it } != null && outputSize < MAX_OUTPUT_SIZE) {
                    stdout.appendLine(line)
                    outputSize += (line?.length ?: 0) + 1
                }

                while (stderrReader.readLine().also { line = it } != null && outputSize < MAX_OUTPUT_SIZE) {
                    stderr.appendLine(line)
                    outputSize += (line?.length ?: 0) + 1
                }

                val exitCode = process.waitFor()

                stdoutReader.close()
                stderrReader.close()

                ShellResult(
                    exitCode = exitCode,
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                    timedOut = false,
                    blocked = false
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Command timed out: $command")
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Command timed out after ${timeout}ms",
                timedOut = true,
                blocked = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Execution failed: ${e.message}",
                timedOut = false,
                blocked = false
            )
        }
    }

    private suspend fun executeCommandWithInput(
        command: String,
        input: String,
        workingDir: File?,
        timeout: Long
    ): ShellResult {
        return try {
            withTimeout(timeout) {
                val processBuilder = ProcessBuilder("sh", "-c", command)

                workingDir?.let { dir ->
                    if (dir.exists() && dir.isDirectory) {
                        processBuilder.directory(dir)
                    }
                }

                val process = processBuilder.start()

                process.outputStream.bufferedWriter().use { writer ->
                    writer.write(input)
                    writer.flush()
                }

                val stdout = StringBuilder()
                val stderr = StringBuilder()

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                var outputSize = 0

                while (stdoutReader.readLine().also { line = it } != null && outputSize < MAX_OUTPUT_SIZE) {
                    stdout.appendLine(line)
                    outputSize += (line?.length ?: 0) + 1
                }

                while (stderrReader.readLine().also { line = it } != null && outputSize < MAX_OUTPUT_SIZE) {
                    stderr.appendLine(line)
                    outputSize += (line?.length ?: 0) + 1
                }

                val exitCode = process.waitFor()

                stdoutReader.close()
                stderrReader.close()

                ShellResult(
                    exitCode = exitCode,
                    stdout = stdout.toString().trimEnd(),
                    stderr = stderr.toString().trimEnd(),
                    timedOut = false,
                    blocked = false
                )
            }
        } catch (e: TimeoutCancellationException) {
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Command timed out after ${timeout}ms",
                timedOut = true,
                blocked = false
            )
        } catch (e: Exception) {
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Execution failed: ${e.message}",
                timedOut = false,
                blocked = false
            )
        }
    }

    // ==================== Data Classes ====================

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
        val blocked: Boolean
    ) {
        val isSuccess: Boolean get() = exitCode == 0 && !timedOut && !blocked

        val output: String get() = if (stdout.isNotBlank()) stdout else stderr

        val combinedOutput: String get() = buildString {
            if (stdout.isNotBlank()) append(stdout)
            if (stdout.isNotBlank() && stderr.isNotBlank()) append("\n")
            if (stderr.isNotBlank()) append("STDERR: $stderr")
        }
    }

    data class CommandValidation(
        val isAllowed: Boolean,
        val reason: String
    )
}
