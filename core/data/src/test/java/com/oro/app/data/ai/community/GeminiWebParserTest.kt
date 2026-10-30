package com.oro.app.data.ai.community

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import org.junit.Test

class GeminiWebParserTest {

    @Test
    fun `reads the longest rc_ snapshot and ignores other rows`() {
        val inner = buildJsonArray {
            add(JsonNull)
            add(buildJsonArray {
                add(JsonPrimitive("c_conversation"))
                add(JsonPrimitive("r_response"))
            })
            add(JsonNull)
            add(JsonNull)
            add(buildJsonArray {
                add(buildJsonArray {
                    add(JsonPrimitive("rc_abc"))
                    add(buildJsonArray {
                        add(JsonPrimitive("Hel"))
                        add(JsonPrimitive("Hello"))
                        add(JsonPrimitive("Hello world"))
                    })
                })
                add(buildJsonArray {
                    add(JsonPrimitive("rc_def"))
                    add(buildJsonArray { add(JsonPrimitive("A second part")) })
                })
            })
        }
        val line = buildJsonArray {
            add(buildJsonArray {
                add(JsonPrimitive("wrb.fr"))
                add(JsonNull)
                add(JsonPrimitive(inner.toString()))
            })
        }.toString()
        assertThat(GeminiWebParser.parse(line)).containsExactly("Hello world", "A second part").inOrder()
    }

    @Test
    fun `junk lines never throw`() {
        assertThat(GeminiWebParser.parse("not json")).isEmpty()
        assertThat(GeminiWebParser.parse("[]")).isEmpty()
        assertThat(GeminiWebParser.parse("""[["wrb.fr",null,"{}"]]""")).isEmpty()
        assertThat(GeminiWebParser.parse("""[["wrb.fr",null,"[[null,null,null,null,[[\"rc_\",[7]]]]]"]]""")).isEmpty()
    }
}
