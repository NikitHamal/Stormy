package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Replies with no tool call, as the live evals saw them (the device agent's lessons, kept). */
class BrowserProseTest {

    @Test
    fun `finish_task written as text is a finish, in every spelling models used`() {
        assertThat(BrowserProse.read("finish_task(summary=\"The form was submitted.\", success=true)"))
            .isEqualTo(BrowserProse.Finish("The form was submitted.", true))
        assertThat(BrowserProse.read("The page loaded.\nfinish_task(\"Top story found.\", success=true)"))
            .isEqualTo(BrowserProse.Finish("Top story found.", true))
        assertThat(BrowserProse.read("finish_task({\"summary\": \"The site needs a sign-in.\", \"success\": false})"))
            .isEqualTo(BrowserProse.Finish("The site needs a sign-in.", false))
        assertThat(BrowserProse.read("`finish_task(summary: 'Price is 42 USD', success: true)`"))
            .isEqualTo(BrowserProse.Finish("Price is 42 USD", true))
    }

    @Test
    fun `a question for the user written as text becomes a question`() {
        val reply = "I see the login screen. I need your credentials to continue. " +
            "Could you provide your username, or would you prefer to skip login?"
        assertThat(BrowserProse.read(reply)).isEqualTo(
            BrowserProse.Ask("Could you provide your username, or would you prefer to skip login?")
        )
        assertThat(BrowserProse.read("ask_user(question=\"Which account should I use?\")"))
            .isEqualTo(BrowserProse.Ask("Which account should I use?"))
        assertThat(BrowserProse.read("Please provide the one-time code from your authenticator."))
            .isEqualTo(BrowserProse.Ask("Please provide the one-time code from your authenticator."))
    }

    @Test
    fun `statements and offers of more help stay statements`() {
        assertThat(BrowserProse.read("The form was submitted.")).isEqualTo(BrowserProse.Statement)
        assertThat(BrowserProse.read("Done! Is there anything else you'd like me to do?")).isEqualTo(BrowserProse.Statement)
        assertThat(BrowserProse.read("Why would the page redirect here?")).isEqualTo(BrowserProse.Statement)
        assertThat(BrowserProse.read("")).isEqualTo(BrowserProse.Statement)
    }

    @Test
    fun `tasks that compare items are recognised`() {
        assertThat(BrowserPrompt.comparesItems("Find the cheapest 2 TB SSD")).isTrue()
        assertThat(BrowserPrompt.comparesItems("Which of these has the best rating?")).isTrue()
        assertThat(BrowserPrompt.comparesItems("Read me all the comments")).isTrue()
        assertThat(BrowserPrompt.comparesItems("Open the login page")).isFalse()
    }

    @Test
    fun `the same question twice is recognised`() {
        assertThat(BrowserPrompt.sameQuestion("Which account should I use?", "Which account should I use to sign in?")).isTrue()
        assertThat(BrowserPrompt.sameQuestion("Which account?", "Continue with the task?")).isFalse()
    }
}
