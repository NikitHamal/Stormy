package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class TargetsTest {

    private fun args(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) ->
        k to when (v) {
            is Int -> JsonPrimitive(v)
            is Double -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }
    })

    private val seen = screen(el(0, "button", "Delete", top = 500), el(1, "button", "Keep", top = 700), pkg = "notes")

    @Test
    fun `a stale index is skipped even when x and y came with it`() {
        val now = screen(el(0, "button", "Undo", top = 500), el(1, "button", "Done", top = 700), pkg = "notes")
        val t = Targets.tap(args("index" to 0, "x" to 540, "y" to 545), seen, now)
        assertThat(t).isEqualTo(Target.Stale())
    }

    @Test
    fun `another app in front makes the call stale`() {
        val now = screen(el(0, "button", "Delete", top = 500), pkg = "com.android.settings")
        assertThat(Targets.tap(args("index" to 0), seen, now)).isEqualTo(Target.Stale(appChanged = true))
        assertThat(Targets.text(args("text" to "Delete"), seen, now)).isEqualTo(Target.Stale(appChanged = true))
    }

    @Test
    fun `an index that never existed says which numbers are valid`() {
        val t = Targets.tap(args("index" to 9), seen, seen)
        assertThat((t as Target.Missing).message).contains("0-1")
    }

    @Test
    fun `a point on a small control taps the control itself, and says where the point was`() {
        val t = Targets.tap(args("x" to 300, "y" to 540), seen, seen) as Target.OnElement
        assertThat(t.element.label).isEqualTo("Delete")
        assertThat(t.snappedFrom).isEqualTo(300 to 540)
    }

    @Test
    fun `a point on something big keeps the point and resolves to what a finger there would press`() {
        val map = screen(el(0, "button", "Map", top = 200, height = 1400), pkg = "maps")
        val t = Targets.tap(args("x" to 300, "y" to 640), map, map)
        assertThat((t as Target.AtPoint).element?.label).isEqualTo("Map")
    }

    @Test
    fun `tap_text gives choices instead of guessing`() {
        val rows = screen(el(0, "button", "Delete"), el(1, "button", "Delete"), pkg = "notes")
        val t = Targets.text(args("text" to "Delete"), null, rows)
        assertThat((t as Target.Choices).candidates.map { it.index }).containsExactly(0, 1).inOrder()
        assertThat(Targets.text(args("text" to "Rename"), null, rows)).isEqualTo(Target.NotFound("Rename"))
    }

    @Test
    fun `type_text without an index means the focused field`() {
        val form = screen(el(0, "input", "Name"), el(1, "input", "Email", focused = true), pkg = "form")
        assertThat((Targets.field(args("text" to "a@b.c"), null, form) as Target.Focused).field?.label).isEqualTo("Email")
    }

    @Test
    fun `an approval holds only if the screen still shows the same thing`() {
        val before = Targets.tap(args("index" to 0), seen, seen)
        val unchanged = Targets.tap(args("index" to 0), seen, screen(el(0, "button", "Delete", top = 520), el(1, "button", "Keep", top = 720), pkg = "notes"))
        assertThat(Targets.same(before, unchanged)).isTrue()
        val changed = Targets.tap(args("index" to 0), seen, screen(el(0, "button", "Delete forever", top = 500), pkg = "notes"))
        assertThat(Targets.same(before, changed)).isFalse()
    }
}
