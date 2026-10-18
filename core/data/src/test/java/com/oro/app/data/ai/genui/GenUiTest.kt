package com.oro.app.data.ai.genui

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class GenUiTest {
    private fun components(result: GenUi.Result): List<JsonObject> {
        val msgs = (result as GenUi.Result.Ok).messages.map { Json.parseToJsonElement(it).jsonObject }
        assertThat(msgs.first().keys).contains("createSurface")
        assertThat(msgs.all { it["version"]!!.jsonPrimitive.content == "v0.9" }).isTrue()
        return (msgs.first { "updateComponents" in it }["updateComponents"]!!.jsonObject["components"] as JsonArray).map { it.jsonObject }
    }

    @Test
    fun `short form with inline children is flattened under a root`() {
        val block = """{"components":[{"component":"Card","child":{"type":"Column","children":[
            {"component":"Text","text":"Pro","variant":"h3"},
            {"component":"Button","child":{"component":"Text","text":"Choose"},"action":{"event":{"name":"choose"}}},
          ]}}], "data":{"n":1}}"""
        val comps = components(GenUi.toA2uiMessages(block, "s1"))
        val byId = comps.associateBy { it["id"]!!.jsonPrimitive.content }
        assertThat(byId["root"]!!["component"]!!.jsonPrimitive.content).isEqualTo("Card")
        val column = byId[byId["root"]!!["child"]!!.jsonPrimitive.content]!!
        assertThat(column["component"]!!.jsonPrimitive.content).isEqualTo("Column")
        assertThat((column["children"] as JsonArray).map { it.jsonPrimitive.content }.all { it in byId }).isTrue()
        assertThat(comps.none { "type" in it }).isTrue()
    }

    @Test
    fun `several top-level components get a column root and full messages are pinned`() {
        val comps = components(GenUi.toA2uiMessages("""[{"id":"a","component":"Text","text":"x"},{"id":"b","component":"Divider"}]""", "s2"))
        val root = comps.first { it["id"]!!.jsonPrimitive.content == "root" }
        assertThat((root["children"] as JsonArray).map { it.jsonPrimitive.content }).containsExactly("a", "b")

        val full = """{"version":"v0.9","updateComponents":{"surfaceId":"other","components":[{"id":"root","component":"Text","text":"hi"}]}}"""
        val msgs = (GenUi.toA2uiMessages(full, "s3") as GenUi.Result.Ok).messages
        assertThat(msgs.first()).contains(GenUi.CATALOG_ID)
        assertThat(msgs.last()).contains("\"surfaceId\":\"s3\"")
        assertThat(GenUi.toA2uiMessages("not json", "s4")).isInstanceOf(GenUi.Result.Invalid::class.java)
        assertThat(GenUi.actionMessage("say", mapOf("text" to "Show me more"))).isEqualTo("Show me more")
    }
}
