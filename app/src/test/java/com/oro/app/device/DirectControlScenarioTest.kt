package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.FakePhone.Act
import com.oro.app.device.ScenarioBench.play
import com.oro.app.device.ScreenElement.Companion.ROLE_SLIDER
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import com.oro.app.device.ScriptedModel.Companion.calls
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The direct-control layer through the real run loop: media sessions, sliders, seek bars and tap feedback. */
class DirectControlScenarioTest {

    private val yt = "com.google.android.youtube"

    /** A YouTube watch page with the player's controls hidden. */
    private val playing = screen(
        el(0, "view", "", top = 200, height = 600, viewId = "$yt:id/watch_player"),
        el(1, ROLE_TEXT, "lofi hip hop radio", top = 850),
        pkg = yt, label = "YouTube",
    )

    /** The same page after one tap on the player: its controls, clock and seek bar. */
    private val controls = screen(
        el(0, "view", "", top = 200, height = 600, viewId = "$yt:id/watch_player"),
        el(1, "button", "Pause video", top = 450, left = 440, right = 640, height = 120),
        el(2, ROLE_TEXT, "1:23 / 4:56", top = 700, left = 40, right = 400, height = 50),
        el(3, ROLE_SLIDER, "", top = 760, height = 40, clickable = false, viewId = "$yt:id/time_bar"),
        el(4, ROLE_TEXT, "lofi hip hop radio", top = 850),
        pkg = yt, label = "YouTube",
    )

    @Test
    fun `media seek goes through the session when it can, touching nothing on screen`() = runTest {
        val phone = ScenarioBench.phone(this, playing)
        phone.seekResult = { _, _ -> MediaControl.SeekResult.Done("Jumped to 2:00 of 4:56 in YouTube.") }
        val model = ScriptedModel(
            calls("media" to """{"action":"seek","position":"2:00"}"""),
            calls("finish_task" to """{"summary":"At 2:00.","success":true}"""),
        )

        val end = play(phone, model, "Skip to 2:00 in this video")

        assertThat(phone.log).containsExactly(Act.Seek("2:00", null))
        assertThat(model.seen[1]).contains("Jumped to 2:00 of 4:56 in YouTube.")
        assertThat(model.seen[1]).doesNotContain("did not change")
        assertThat(end.steps.first { it.tool == "media" }.title).isEqualTo("Jump to 2:00")
    }

    @Test
    fun `without media control one call shows the hidden controls and moves the bar`() = runTest {
        val phone = ScenarioBench.phone(this, playing)
        phone.rules = { a -> if (a == Act.ShowControls) controls else null }
        val model = ScriptedModel(
            calls("media" to """{"action":"seek","position":"+30"}"""),
            calls("finish_task" to """{"summary":"Skipped ahead 30 s.","success":true}"""),
        )

        play(phone, model, "Skip ahead 30 seconds")

        // 30 s on from 1:23 on the player's clock; its length goes along for the bar's arithmetic.
        assertThat(phone.log).containsExactly(Act.ShowControls, Act.SetValue("", 3, "1:53", 296_000L)).inOrder()
        // The model learns the exact way exists, for the user to turn on.
        assertThat(model.seen[1]).contains(MediaControl.ACCESS_HINT)
    }

    @Test
    fun `set_value on a seek bar that hid since the model saw it brings the bar back first`() = runTest {
        val phone = ScenarioBench.phone(this, controls)
        phone.rules = { a -> if (a == Act.ShowControls) controls else null }
        val model = ScriptedModel(
            // The controls fade while the model thinks.
            { request -> phone.change(playing); calls("set_value" to """{"index":3,"value":"2:00"}""")(request) },
            calls("finish_task" to """{"summary":"At 2:00.","success":true}"""),
        )

        play(phone, model, "Go to 2:00 in the video")

        assertThat(phone.log).containsExactly(Act.ShowControls, Act.SetValue("", 3, "2:00", 296_000L)).inOrder()
    }

    @Test
    fun `a seek bar prefers the session, which needs no bar at all`() = runTest {
        val phone = ScenarioBench.phone(this, controls)
        phone.seekResult = { position, pkg -> MediaControl.SeekResult.Done("Jumped to $position in $pkg.") }
        val model = ScriptedModel(
            calls("set_value" to """{"index":3,"value":"50%"}"""),
            calls("finish_task" to """{"summary":"Halfway.","success":true}"""),
        )

        play(phone, model, "Go to the middle of the video")

        // Only the app in front's own session is asked.
        assertThat(phone.log).containsExactly(Act.Seek("50%", yt))
    }

    @Test
    fun `a slider is listed with its value and set by it`() = runTest {
        val sound = screen(
            el(0, ROLE_TEXT, "Sound", top = 100),
            el(1, ROLE_SLIDER, "Media volume", top = 400, clickable = false, viewId = "android:id/seekbar")
                .copy(range = ValueRange(0f, 15f, 5f)),
            pkg = "com.android.settings", label = "Settings",
        )
        val phone = ScenarioBench.phone(this, sound)
        phone.seekResult = { _, _ -> error("a volume slider never seeks the media") }
        val model = ScriptedModel(
            calls("set_value" to """{"index":1,"value":"loud"}"""),
            calls("set_value" to """{"index":1,"value":"40%"}"""),
            calls("finish_task" to """{"summary":"Media volume at 40%.","success":true}"""),
        )

        val end = play(phone, model, "Set media volume to 40%")

        assertThat(model.seen[0]).contains("[1] slider \"Media volume\" 5/15")
        assertThat(model.seen[1]).contains("set_value needs value")
        assertThat(phone.log).containsExactly(Act.SetValue("Media volume", 1, "40%"))
        assertThat(end.steps.last { it.tool == "set_value" }.title).isEqualTo("Set “Media volume” to “40%”")
    }

    @Test
    fun `a slide-to-pay slider still asks first`() = runTest {
        val pay = screen(
            el(0, ROLE_TEXT, "Total ₹450", top = 300),
            el(1, ROLE_SLIDER, "Slide to pay", top = 2000, clickable = false).copy(range = ValueRange(0f, 100f, 0f)),
            pkg = "com.shop", label = "Shop",
        )
        val phone = ScenarioBench.phone(this, pay)
        val model = ScriptedModel(
            calls("set_value" to """{"index":1,"value":"100%"}"""),
            calls("finish_task" to """{"summary":"Payment needs your OK.","success":false}"""),
        )
        val asked = ScenarioBench.answerApprovals(this, allow = false)

        play(phone, model, "Pay for the order")

        assertThat(asked.single()).contains("Slide to pay")
        assertThat(phone.log).isEmpty()
    }

    @Test
    fun `a ten-second jump with no player or bar falls back to the player's own gesture`() = runTest {
        val music = screen(el(0, ROLE_TEXT, "Now playing", top = 300), pkg = "com.music", label = "Music")
        val phone = ScenarioBench.phone(this, music)
        val model = ScriptedModel(
            calls("media" to """{"action":"seek","position":"+10"}"""),
            calls("finish_task" to """{"summary":"Forward 10 s.","success":true}"""),
        )

        play(phone, model, "Skip ahead ten seconds")

        assertThat(phone.log).containsExactly(Act.Media("forward"))
    }

    @Test
    fun `a tap by coordinates says what it hit, or that nothing took it`() = runTest {
        val notes = screen(
            el(0, ROLE_TEXT, "Delete draft?", top = 300),
            el(1, "button", "Draft", top = 1100, height = 900),
            pkg = "com.notes", label = "Notes",
        )
        val phone = ScenarioBench.phone(this, notes)
        val model = ScriptedModel(
            calls("tap" to """{"x":300,"y":1160}"""),
            calls("tap" to """{"x":900,"y":600}"""),
            calls("finish_task" to """{"summary":"Kept the draft.","success":true}"""),
        )

        play(phone, model, "Keep the draft")

        assertThat(model.seen[1]).contains("Tapped (300,1160) → hit button \"Draft\".")
        assertThat(model.seen[2]).contains("Tapped (900,600) → nothing registered the tap.")
    }

    @Test
    fun `a point on or beside a small button taps the button itself`() = runTest {
        val notes = screen(
            el(0, ROLE_TEXT, "Delete draft?", top = 900),
            el(1, "button", "Keep", top = 1100, left = 100, right = 500, height = 120),
            pkg = "com.notes", label = "Notes",
        )
        val phone = ScenarioBench.phone(this, notes)
        val model = ScriptedModel(
            calls("tap" to """{"x":300,"y":1235}"""),
            calls("finish_task" to """{"summary":"Kept the draft.","success":true}"""),
        )

        play(phone, model, "Keep the draft")

        assertThat(phone.log).containsExactly(FakePhone.Act.Tap("Keep", 1))
        assertThat(model.seen[1]).contains("Tapped [1] button \"Keep\". (your point (300,1235) was on or beside it)")
    }
}
