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
 * Guards MIGRATION_5_6, which splits each project's chat into sessions.
 *
 * Same approach as [ProjectSchemaMigrationTest]: Room validates the migrated
 * tables against the entities at first open and crashes on a mismatch, so the
 * migration's CREATE statements are checked against the exported schema --
 * the one artefact that records what Room generates for the entities.
 */
class SessionSchemaMigrationTest {

    private val schemaDir = File("schemas/com.oro.app.data.local.database.OroDatabase")
    private val source = File("src/main/java/com/oro/app/data/local/database/OroDatabase.kt").readText()

    private fun table(version: Int, name: String): JsonObject? {
        val root = Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject
        return root["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .firstOrNull { it["tableName"]!!.jsonPrimitive.content == name }
    }

    private fun createSql(version: Int, name: String): String =
        table(version, name)!!["createSql"]!!.jsonPrimitive.content

    private fun indexSql(version: Int, tableName: String): List<String> =
        table(version, tableName)!!["indices"]!!.jsonArray.map {
            it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", tableName)
        }

    @Test
    fun `sessions are new in version 6`() {
        assertThat(table(5, "chat_sessions")).isNull()
        assertThat(table(6, "chat_sessions")).isNotNull()
    }

    @Test
    fun `the migration creates chat_sessions exactly as Room does`() {
        val expected = createSql(6, "chat_sessions").replace("\${TABLE_NAME}", "chat_sessions")
        assertThat(source).contains(expected)
        indexSql(6, "chat_sessions").forEach { assertThat(source).contains(it) }
    }

    @Test
    fun `the rebuilt chat_messages matches Room's table and indices`() {
        // Created under a temporary name and renamed, so compare with the name swapped.
        val expected = createSql(6, "chat_messages").replace("\${TABLE_NAME}", "chat_messages_new")
        assertThat(source).contains(expected)
        assertThat(source).contains("ALTER TABLE chat_messages_new RENAME TO chat_messages")
        val indices = indexSql(6, "chat_messages")
        assertThat(indices).contains(
            "CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)"
        )
        indices.forEach { assertThat(source).contains(it) }
    }

    @Test
    fun `sessionId is a required column with a cascading foreign key`() {
        val messages = table(6, "chat_messages")!!
        val sessionId = messages["fields"]!!.jsonArray.map { it.jsonObject }
            .first { it["columnName"]!!.jsonPrimitive.content == "sessionId" }
        assertThat(sessionId["notNull"]!!.jsonPrimitive.content).isEqualTo("true")
        val fk = messages["foreignKeys"]!!.jsonArray.map { it.jsonObject }
            .first { it["table"]!!.jsonPrimitive.content == "chat_sessions" }
        assertThat(fk["onDelete"]!!.jsonPrimitive.content).isEqualTo("CASCADE")
    }

    @Test
    fun `every column of the old table is carried over`() {
        // A column dropped from the INSERT ... SELECT would be silently lost
        // for every user on upgrade.
        val old = table(5, "chat_messages")!!["fields"]!!.jsonArray
            .map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        val copy = Regex("INSERT INTO chat_messages_new \\(([^)]*)\\)").find(source)!!.groupValues[1]
        old.forEach { assertThat(copy).contains(it) }
    }

    @Test
    fun `sessions are ordered pinned first, then by activity`() {
        val dao = File("src/main/java/com/oro/app/data/local/dao/ChatSessionDao.kt").readText()
        assertThat(dao).contains("ORDER BY s.isPinned DESC, s.updatedAt DESC")
    }
}
