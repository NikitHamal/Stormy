package com.oro.app.data.local

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Guards the `projects.isPinned` migration.
 *
 * Room validates a migrated database against the entity at startup and throws
 * if they disagree, which means a column added to the entity without a
 * matching migration is not a compile error and not a test failure -- it is a
 * crash on launch for every user who already has the app installed. The
 * exported schemas are the only artefact recording both sides, so they are
 * what is checked here.
 *
 * Pure JVM on purpose. Executing the migration for real needs an Android
 * SQLite, and the value is in catching the mismatch at all rather than in
 * exercising `ALTER TABLE`.
 */
class ProjectSchemaMigrationTest {

    private val schemaDir = File("schemas/com.oro.app.data.local.database.OroDatabase")

    private fun projectsTable(version: Int): JsonObject {
        val file = File(schemaDir, "$version.json")
        assertThat(file.exists()).isTrue()
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        return root["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["tableName"]!!.jsonPrimitive.content == "projects" }
    }

    private fun field(version: Int, name: String): JsonObject? =
        projectsTable(version)["fields"]!!.jsonArray
            .map { it.jsonObject }
            .firstOrNull { it["columnName"]!!.jsonPrimitive.content == name }

    @Test
    fun `version 4 adds isPinned`() {
        val pinned = field(4, "isPinned")
        assertThat(pinned).isNotNull()
        assertThat(pinned!!["affinity"]!!.jsonPrimitive.content).isEqualTo("INTEGER")
        assertThat(pinned["notNull"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `isPinned carries a SQL default so both creation paths agree`() {
        // The migration's ALTER TABLE must add `DEFAULT 0`, because existing
        // rows need a value. If the entity does not declare the same default,
        // a fresh install's table and an upgraded install's table differ by a
        // DEFAULT clause.
        val pinned = field(4, "isPinned")!!
        assertThat(pinned["defaultValue"]?.jsonPrimitive?.content).isEqualTo("0")
    }

    @Test
    fun `version 3 did not have it, so the migration is required`() {
        // Asserts the premise of MIGRATION_3_4. If the column is ever
        // backported into 3, this says the migration has become a no-op that
        // will fail with "duplicate column name".
        assertThat(field(3, "isPinned")).isNull()
    }

    @Test
    fun `the migration SQL matches the column the schema expects`() {
        val source = File(
            "src/main/java/com/oro/app/data/local/database/OroDatabase.kt"
        ).readText()
        assertThat(source).contains(
            "ALTER TABLE projects ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
        )
        assertThat(source).contains("Migration(3, 4)")
        assertThat(source).contains("Migration(4, 5)")
        assertThat(source).contains("Migration(5, 6)")
        assertThat(source).contains("version = 6")
    }

    @Test
    fun `ai_models reportedContextLength is added as a nullable integer`() {
        fun aiModelsField(version: Int, name: String): JsonObject? {
            val root = Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject
            return root["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject }
                .first { it["tableName"]!!.jsonPrimitive.content == "ai_models" }["fields"]!!.jsonArray
                .map { it.jsonObject }.firstOrNull { it["columnName"]!!.jsonPrimitive.content == name }
        }
        assertThat(aiModelsField(4, "reportedContextLength")).isNull()
        val field = aiModelsField(5, "reportedContextLength")!!
        assertThat(field["affinity"]!!.jsonPrimitive.content).isEqualTo("INTEGER")
        assertThat(field["notNull"]?.jsonPrimitive?.content ?: "false").isEqualTo("false")
        val source = File("src/main/java/com/oro/app/data/local/database/OroDatabase.kt").readText()
        assertThat(source).contains("ALTER TABLE ai_models ADD COLUMN reportedContextLength INTEGER\"")
    }

    @Test
    fun `pinned projects are ordered first by the query, not by the UI`() {
        val dao = File(
            "src/main/java/com/oro/app/data/local/dao/ProjectDao.kt"
        ).readText()
        // Every read path has to agree, or the list reorders itself depending
        // on whether the user is searching.
        val ordered = Regex("""ORDER BY\s+isPinned DESC""").findAll(dao).count()
        assertThat(ordered).isEqualTo(3)
    }
}
