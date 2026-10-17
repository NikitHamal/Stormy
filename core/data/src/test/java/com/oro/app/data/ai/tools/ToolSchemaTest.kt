package com.oro.app.data.ai.tools

import com.oro.app.data.ai.Tool
import com.oro.app.data.ai.agents.DelegationTools
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * Validates every tool schema the app can send.
 *
 * These exist because of a real 400 that made agent mode unusable on strict
 * providers:
 *
 *     Tool 38 function has invalid 'parameters' schema:
 *     None is not of type 'array' ... on schema['properties']
 *     ['file_pattern']['enum']
 *
 * A nullable `enum` field was being serialised as `"enum": null`, which JSON
 * Schema does not allow. The important part is the blast radius: providers
 * validate the *whole* tools array, so one malformed tool rejected every
 * message rather than just one tool call. A single bad schema anywhere is a
 * total outage for agent mode, which is why this is checked exhaustively
 * rather than spot-checked.
 */
class ToolSchemaTest {

    /**
     * Exactly what the app sends. OroTools.getAllTools() already appends
     * AdvancedTools, so adding it again here would double-count -- which this
     * test's own duplicate check caught on the first run.
     */
    private fun allTools(): List<Tool> = OroTools.getAllTools() + DelegationTools.all

    @Test
    fun `no tool schema contains a null anywhere`() {
        val offenders = mutableListOf<String>()
        allTools().forEach { tool ->
            collectNullPaths(tool.function.parameters, "").forEach { path ->
                offenders += "${tool.function.name}$path"
            }
        }
        // Null is never valid in a JSON Schema position this app emits, and it
        // is the exact shape that broke agent mode.
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `every enum is a non-empty array of strings`() {
        val offenders = mutableListOf<String>()
        allTools().forEach { tool ->
            forEachProperty(tool.function.parameters) { name, property ->
                val enum = property["enum"] ?: return@forEachProperty
                when {
                    enum !is JsonArray -> offenders += "${tool.function.name}.$name: enum is not an array"
                    enum.isEmpty() -> offenders += "${tool.function.name}.$name: enum is empty"
                    enum.any { it !is JsonPrimitive || !it.isString } ->
                        offenders += "${tool.function.name}.$name: enum has non-string entries"
                }
            }
        }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `every tool declares an object schema with properties`() {
        allTools().forEach { tool ->
            val params = tool.function.parameters
            assertThat(params).isInstanceOf(JsonObject::class.java)
            val obj = params as JsonObject
            assertThat(obj["type"]?.let { (it as JsonPrimitive).content })
                .isEqualTo("object")
            assertThat(obj["properties"]).isInstanceOf(JsonObject::class.java)
        }
    }

    @Test
    fun `every required parameter actually exists in properties`() {
        val offenders = mutableListOf<String>()
        allTools().forEach { tool ->
            val obj = tool.function.parameters as? JsonObject ?: return@forEach
            val properties = (obj["properties"] as? JsonObject)?.keys.orEmpty()
            val required = (obj["required"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.content }
            required.filterNot { it in properties }.forEach { missing ->
                // A required name with no matching property is a schema the
                // model cannot satisfy: it is told to send a field that is not
                // described anywhere.
                offenders += "${tool.function.name}: required '$missing' is not a property"
            }
        }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `every property declares a type and a description`() {
        val offenders = mutableListOf<String>()
        allTools().forEach { tool ->
            forEachProperty(tool.function.parameters) { name, property ->
                if (property["type"] == null) {
                    offenders += "${tool.function.name}.$name: no type"
                }
                val description = (property["description"] as? JsonPrimitive)?.content
                if (description.isNullOrBlank()) {
                    // An undescribed parameter is one the model guesses at.
                    offenders += "${tool.function.name}.$name: no description"
                }
            }
        }
        assertThat(offenders).isEmpty()
    }

    @Test
    fun `tool names are unique`() {
        val names = allTools().map { it.function.name }
        // Duplicates make the model's choice ambiguous and some providers
        // reject the array outright.
        assertThat(names).containsNoDuplicates()
    }

    @Test
    fun `tool names use the snake_case the descriptions reference`() {
        val offenders = allTools()
            .map { it.function.name }
            .filterNot { it.matches(Regex("^[a-z][a-z0-9_]*$")) }
        assertThat(offenders).isEmpty()
    }

    // --- helpers ------------------------------------------------------------

    private fun collectNullPaths(element: JsonElement, path: String): List<String> = when (element) {
        is JsonNull -> listOf(path)
        is JsonObject -> element.entries.flatMap { (key, value) ->
            collectNullPaths(value, "$path.$key")
        }
        is JsonArray -> element.flatMapIndexed { index, value ->
            collectNullPaths(value, "$path[$index]")
        }
        else -> emptyList()
    }

    private fun forEachProperty(
        parameters: JsonElement,
        block: (name: String, property: JsonObject) -> Unit
    ) {
        val properties = (parameters as? JsonObject)?.get("properties") as? JsonObject ?: return
        properties.forEach { (name, value) ->
            (value as? JsonObject)?.let { block(name, it) }
        }
    }

    @Test
    fun `the exact tool that triggered the 400 serialises without a null enum`() {
        val searchFiles = allTools().first { it.function.name == "search_files" }
        val json = searchFiles.function.parameters.toString()
        // Previously: {"type":"string","description":"...","enum":null}
        assertThat(json).doesNotContain("null")
        assertThat(json).contains("file_pattern")
    }

    @Test
    fun `a tool with a real enum still carries it`() {
        // The fix omits nulls; it must not omit populated enums as well.
        val withEnum = allTools().first { it.function.name == "generate_tests" }
        val properties = (withEnum.function.parameters as JsonObject)["properties"] as JsonObject
        val framework = properties["framework"] as JsonObject
        assertThat(framework["enum"]).isInstanceOf(JsonArray::class.java)
        assertThat((framework["enum"] as JsonArray)).isNotEmpty()
    }
}
