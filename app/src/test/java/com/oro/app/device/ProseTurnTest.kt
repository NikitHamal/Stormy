package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Replies with no tool call, as the live eval saw them (docs/research/device-live-eval.md). */
class ProseTurnTest {

    @Test
    fun `finish_task written as text is a finish, in every spelling models used`() {
        assertThat(ProseTurn.read("finish_task(summary=\"Do Not Disturb has been turned on.\", success=true)"))
            .isEqualTo(ProseTurn.Finish("Do Not Disturb has been turned on.", true))
        assertThat(ProseTurn.read("The video is playing.\nfinish_task(\"Playing Blinding Lights.\", success=true)"))
            .isEqualTo(ProseTurn.Finish("Playing Blinding Lights.", true))
        assertThat(ProseTurn.read("finish_task({\"summary\": \"Instagram needs a sign-in.\", \"success\": false})"))
            .isEqualTo(ProseTurn.Finish("Instagram needs a sign-in.", false))
        assertThat(ProseTurn.read("`finish_task(summary: 'Cart has the Mi charger (₹649)', success: true)`"))
            .isEqualTo(ProseTurn.Finish("Cart has the Mi charger (₹649)", true))
    }

    @Test
    fun `a question for the user written as text becomes a question`() {
        val reply = "I see the Instagram login screen. I need your credentials to read DMs. " +
            "Could you provide your username and password, or would you prefer to skip login?"
        assertThat(ProseTurn.read(reply)).isEqualTo(
            ProseTurn.Ask("Could you provide your username and password, or would you prefer to skip login?")
        )
        assertThat(ProseTurn.read("ask_user(question=\"Which Priya, Sharma or Rao?\")"))
            .isEqualTo(ProseTurn.Ask("Which Priya, Sharma or Rao?"))
    }

    @Test
    fun `statements and offers of more help stay statements`() {
        assertThat(ProseTurn.read("Do Not Disturb has been turned on.")).isEqualTo(ProseTurn.Statement)
        assertThat(ProseTurn.read("Done! Is there anything else you'd like me to do?")).isEqualTo(ProseTurn.Statement)
        assertThat(ProseTurn.read("Why would the app crash here?")).isEqualTo(ProseTurn.Statement)
        assertThat(ProseTurn.read("")).isEqualTo(ProseTurn.Statement)
    }

    @Test
    fun `tasks that pick among items are recognised`() {
        assertThat(DevicePrompt.comparesItems("Add the cheapest USB-C charger to the cart")).isTrue()
        assertThat(DevicePrompt.comparesItems("Which of these has the best rating?")).isTrue()
        assertThat(DevicePrompt.comparesItems("Read me all my contacts")).isTrue()
        assertThat(DevicePrompt.comparesItems("Turn on Do Not Disturb")).isFalse()
        assertThat(DevicePrompt.comparesItems("Play Blinding Lights on YouTube")).isFalse()
    }

    @Test
    fun `a name in the task that starts several tappable labels is flagged`() {
        val chats = ScreenFixtures.screen(
            ScreenFixtures.el(0, "button", "Priya Sharma · See you soon · 8:50 PM"),
            ScreenFixtures.el(1, "button", "Mom · Hi"),
            ScreenFixtures.el(2, "button", "Priya Rao · See you soon · 8:52 PM"),
            ScreenFixtures.el(3, "text", "Priya Kapoor (not tappable)", clickable = false),
            pkg = "com.whatsapp", label = "WhatsApp",
        )
        assertThat(DevicePrompt.ambiguousNames("Send \"Running late\" to Priya on WhatsApp", chats))
            .containsExactly("Priya" to listOf("Priya Sharma", "Priya Rao"))
        // Named in full, or only inside the quoted message: nothing to ask.
        assertThat(DevicePrompt.ambiguousNames("Send \"hi\" to Priya Rao", chats)).isEmpty()
        assertThat(DevicePrompt.ambiguousNames("Send \"Priya says hi\" to Mom", chats)).isEmpty()
        // The first word of a sentence is capitalised anyway.
        assertThat(DevicePrompt.ambiguousNames("Message Priya on WhatsApp: running late", chats).map { it.first }).containsExactly("Priya")
    }

    @Test
    fun `a title in the task is not a person to ask about`() {
        val results = ScreenFixtures.screen(
            ScreenFixtures.el(0, "button", "Blinding Lights (Lyrics) · 7clouds · 45M views"),
            ScreenFixtures.el(1, "button", "Blinding Lights | Piano Cover · Peter Buka"),
            ScreenFixtures.el(2, "button", "Blinding Lights but it's lofi · Chill Beats"),
            pkg = "com.google.android.youtube", label = "YouTube",
        )
        assertThat(DevicePrompt.ambiguousNames("Play the official music video of Blinding Lights by The Weeknd on YouTube.", results)).isEmpty()
        // Results that share a word are choices, not namesakes.
        assertThat(DevicePrompt.ambiguousNames("Play Blinding on YouTube", results)).isEmpty()
    }

    @Test
    fun `options offered as a list keep their lead-in`() {
        val reply = "I don't see an HDFC Bank app on your phone.\n\nWould you like me to:\n1. Check your Messages for HDFC texts?\n2. Look for other banking notifications?"
        assertThat(ProseTurn.read(reply)).isEqualTo(
            ProseTurn.Ask("Would you like me to:\n1. Check your Messages for HDFC texts?\n2. Look for other banking notifications?")
        )
    }

    @Test
    fun `a request for what only the user has is a question without a question mark`() {
        val reply = "I see the Instagram login screen. To check your DMs, I need your login credentials. " +
            "Please provide your Instagram username/email and password so I can log in."
        assertThat(ProseTurn.read(reply)).isEqualTo(
            ProseTurn.Ask("To check your DMs, I need your login credentials. Please provide your Instagram username/email and password so I can log in.")
        )
        assertThat(ProseTurn.read("Done. Please let me know if you need anything else.")).isEqualTo(ProseTurn.Statement)
        assertThat(ProseTurn.read("Do Not Disturb is now on.")).isEqualTo(ProseTurn.Statement)
    }
}
