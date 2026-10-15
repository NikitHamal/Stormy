package com.codex.stormy.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Production-grade JSON parsing utilities for tool arguments.
 *
 * AI models sometimes generate malformed JSON with issues like:
 * - Unescaped newlines/tabs inside string values
 * - Trailing commas before closing braces
 * - Missing quotes around keys
 * - Single quotes instead of double quotes
 *
 * This parser uses a state machine approach to properly handle these cases
 * without corrupting valid JSON content.
 */
object JsonParserUtils {

    private const val TAG = "JsonParserUtils"

    /**
     * Lenient JSON parser for fallback parsing
     */
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Strict JSON parser for initial attempts
     */
    private val strictJson = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    /**
     * Parse JSON string to JsonObject with multiple fallback strategies.
     * Returns null only if all strategies fail.
     *
     * @param jsonString The raw JSON string from AI
     * @return Parsed JsonObject or null if parsing fails
     */
    fun parseToJsonObject(jsonString: String): JsonObject? {
        if (jsonString.isBlank()) {
            return JsonObject(emptyMap())
        }

        val trimmed = jsonString.trim()

        // Strategy 1: Try strict parsing first (fastest for valid JSON)
        try {
            return strictJson.parseToJsonElement(trimmed).jsonObject
        } catch (e: Exception) {
            Log.d(TAG, "Strict parsing failed, trying fixes")
        }

        // Strategy 2: Try lenient parsing
        try {
            return lenientJson.parseToJsonElement(trimmed).jsonObject
        } catch (e: Exception) {
            Log.d(TAG, "Lenient parsing failed, applying fixes")
        }

        // Strategy 3: Apply state-machine based fixes
        try {
            val fixed = fixMalformedJsonSafe(trimmed)
            return lenientJson.parseToJsonElement(fixed).jsonObject
        } catch (e: Exception) {
            Log.d(TAG, "Fixed parsing failed: ${e.message}")
        }

        // Strategy 4: Try aggressive recovery
        try {
            val recovered = aggressiveJsonRecovery(trimmed)
            return lenientJson.parseToJsonElement(recovered).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "All parsing strategies failed", e)
        }

        return null
    }

    /**
     * State-machine based JSON fixer that properly handles string contexts.
     *
     * This correctly escapes control characters ONLY inside strings,
     * preserving the structure of the JSON outside strings.
     */
    private fun fixMalformedJsonSafe(input: String): String {
        val result = StringBuilder()
        var i = 0
        var inString = false
        var escaped = false

        while (i < input.length) {
            val char = input[i]

            if (escaped) {
                // We're processing an escaped character
                result.append(char)
                escaped = false
                i++
                continue
            }

            if (char == '\\' && inString) {
                // Start of escape sequence inside string
                result.append(char)
                escaped = true
                i++
                continue
            }

            if (char == '"') {
                // Toggle string state
                inString = !inString
                result.append(char)
                i++
                continue
            }

            if (inString) {
                // Inside a string - escape control characters
                when (char) {
                    '\n' -> result.append("\\n")
                    '\r' -> result.append("\\r")
                    '\t' -> result.append("\\t")
                    '\b' -> result.append("\\b")
                    '\u000C' -> result.append("\\f")
                    else -> {
                        // Check for other control characters
                        if (char.code < 32) {
                            result.append("\\u${char.code.toString(16).padStart(4, '0')}")
                        } else {
                            result.append(char)
                        }
                    }
                }
            } else {
                // Outside a string - preserve whitespace and structure
                result.append(char)
            }

            i++
        }

        // Fix common structural issues
        var fixed = result.toString()

        // Remove trailing commas before closing braces/brackets
        fixed = fixed.replace(Regex(",\\s*}"), "}")
        fixed = fixed.replace(Regex(",\\s*]"), "]")

        // Ensure it starts with { if it doesn't already
        if (!fixed.trimStart().startsWith("{") && !fixed.trimStart().startsWith("[")) {
            fixed = "{$fixed}"
        }

        return fixed
    }

    /**
     * Aggressive JSON recovery for severely malformed input.
     * Attempts to extract key-value pairs even from broken JSON.
     */
    private fun aggressiveJsonRecovery(input: String): String {
        val result = StringBuilder("{")

        // Try to extract key-value pairs using regex
        val keyValuePattern = Regex(
            """["']?(\w+)["']?\s*:\s*("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\d+(?:\.\d+)?|true|false|null|\[[^\]]*\]|\{[^}]*\})"""
        )

        val pairs = mutableListOf<String>()

        keyValuePattern.findAll(input).forEach { match ->
            val key = match.groupValues[1]
            var value = match.groupValues[2]

            // Normalize single quotes to double quotes in values
            if (value.startsWith("'") && value.endsWith("'")) {
                value = "\"${value.substring(1, value.length - 1)}\""
            }

            // Escape any unescaped control characters in the value
            if (value.startsWith("\"")) {
                val content = value.substring(1, value.length - 1)
                val escapedContent = escapeJsonString(content)
                value = "\"$escapedContent\""
            }

            pairs.add("\"$key\":$value")
        }

        result.append(pairs.joinToString(","))
        result.append("}")

        return result.toString()
    }

    /**
     * Properly escape a string for JSON encoding.
     */
    private fun escapeJsonString(input: String): String {
        return buildString {
            for (char in input) {
                when (char) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    else -> {
                        if (char.code < 32) {
                            append("\\u${char.code.toString(16).padStart(4, '0')}")
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
    }

    /**
     * Validate that a path doesn't attempt directory traversal
     * and stays within the project root.
     *
     * @param path The sanitized path to validate
     * @param projectRoot The project root directory (for canonical path comparison)
     * @return true if path is safe, false if it's a potential security issue
     */
    fun validatePathSecurity(path: String, projectRoot: java.io.File): Boolean {
        if (path.isEmpty()) return true

        try {
            val targetFile = java.io.File(projectRoot, path)
            val canonicalTarget = targetFile.canonicalPath
            val canonicalRoot = projectRoot.canonicalPath

            // Ensure the target is within the project root
            if (!canonicalTarget.startsWith(canonicalRoot)) {
                Log.w(TAG, "Path traversal attempt detected: $path")
                return false
            }

            // Additional check: ensure no ".." in the canonical path after root
            val relativePart = canonicalTarget.removePrefix(canonicalRoot)
            if (relativePart.contains("..")) {
                Log.w(TAG, "Path contains ..: $path")
                return false
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Path validation error: ${e.message}")
            return false
        }
    }
}
