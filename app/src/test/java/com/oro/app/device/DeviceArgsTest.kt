package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class DeviceArgsTest {

    @Test
    fun `integers arrive as numbers, floats and padded strings`() {
        val a = JsonObject(
            mapOf(
                "a" to JsonPrimitive(7), "b" to JsonPrimitive(7.0), "c" to JsonPrimitive(" 2"),
                "d" to JsonPrimitive("3.0"), "e" to JsonPrimitive(7.5), "f" to JsonPrimitive("seven"), "g" to JsonNull,
            )
        )
        assertThat(DeviceArgs.int(a, "a")).isEqualTo(7)
        assertThat(DeviceArgs.int(a, "b")).isEqualTo(7)
        assertThat(DeviceArgs.int(a, "c")).isEqualTo(2)
        assertThat(DeviceArgs.int(a, "d")).isEqualTo(3)
        assertThat(DeviceArgs.int(a, "e")).isNull()
        assertThat(DeviceArgs.int(a, "f")).isNull()
        assertThat(DeviceArgs.int(a, "g")).isNull()
        assertThat(DeviceArgs.has(a, "g")).isFalse()
        assertThat(DeviceArgs.has(a, "f")).isTrue()
    }

    @Test
    fun `booleans arrive as booleans or text`() {
        val a = JsonObject(mapOf("a" to JsonPrimitive(true), "b" to JsonPrimitive("true"), "c" to JsonPrimitive(" TRUE "), "d" to JsonPrimitive("no")))
        assertThat(DeviceArgs.bool(a, "a")).isTrue()
        assertThat(DeviceArgs.bool(a, "b")).isTrue()
        assertThat(DeviceArgs.bool(a, "c")).isTrue()
        assertThat(DeviceArgs.bool(a, "d")).isFalse()
        assertThat(DeviceArgs.bool(a, "missing")).isFalse()
    }
}
