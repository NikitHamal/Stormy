package com.oro.app.device

import com.google.common.truth.Truth.assertThat
import com.oro.app.device.FakePhone.Act
import com.oro.app.device.ScenarioBench.play
import com.oro.app.device.ScreenElement.Companion.ROLE_LIST
import com.oro.app.device.ScreenElement.Companion.ROLE_TEXT
import com.oro.app.device.ScreenFixtures.el
import com.oro.app.device.ScreenFixtures.screen
import com.oro.app.device.ScriptedModel.Companion.calls
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Real tasks, scripted end to end through the device agent's run loop (see [FakePhone]). */
class ScenarioBenchTest {

    private val home = screen(
        el(0, "icon", "YouTube", top = 1800, left = 100, right = 300),
        el(1, "icon", "WhatsApp", top = 1800, left = 400, right = 600),
        pkg = "com.android.launcher3", label = "Home",
    )

    @Test
    fun `YouTube - search link, tap the result by its words, an ad skipped on the way`() = runTest {
        val results = screen(
            el(0, ROLE_LIST, "", top = 250, height = 1900).copy(canScrollForward = true),
            el(1, "input", "Search YouTube", value = "lofi", top = 100),
            el(2, "button", "lofi hip hop radio - beats to relax/study to · Lofi Girl · 41K watching", top = 300, height = 400),
            el(3, "button", "Lofi beats playlist · 2 hours", top = 750, height = 400),
            pkg = "com.google.android.youtube", label = "YouTube",
        )
        val player = screen(
            el(0, "view", "Video player", top = 200, height = 700),
            el(1, "button", "Pause video", top = 500),
            el(2, ROLE_TEXT, "lofi hip hop radio - beats to relax/study to", top = 950),
            pkg = "com.google.android.youtube", label = "YouTube",
        )
        val phone = ScenarioBench.phone(this, home)
        phone.rules = { a ->
            when {
                a is Act.Url && "youtube.com/results" in a.url -> results
                a is Act.Tap && a.label.startsWith("lofi hip hop radio") -> player.also {
                    // The service skips the pre-roll by itself while the agent waits.
                    DeviceAgent.onAdSkipped("Skipped a YouTube ad")
                }
                else -> null
            }
        }
        val model = ScriptedModel(
            calls("open_url" to """{"url":"https://www.youtube.com/results?search_query=lofi"}"""),
            calls("tap_text" to """{"text":"lofi hip hop radio"}"""),
            calls("finish_task" to """{"summary":"Playing lofi hip hop radio on YouTube.","success":true}"""),
        )

        val end = play(phone, model, "Play lofi on YouTube and skip the ads")

        assertThat(phone.log).containsExactly(
            Act.Url("https://www.youtube.com/results?search_query=lofi"),
            Act.Tap("lofi hip hop radio - beats to relax/study to · Lofi Girl · 41K watching", 2),
        ).inOrder()
        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.DONE)
        assertThat(end.summary).isEqualTo("Playing lofi hip hop radio on YouTube.")
        // The results list says there is more below it, so "the end" is never guessed.
        assertThat(model.seen[1]).contains("list more below")
        assertThat(model.seen[2]).contains("Screen changed.")
        assertThat(model.seen[2]).contains("Oro skipped an ad automatically")
        assertThat(end.steps.map { it.tool }).contains("skip_ad")
    }

    @Test
    fun `checkout - a row that shifts under a two-call turn never slips Place order past the guard`() = runTest {
        val checkout = screen(
            el(0, ROLE_TEXT, "Checkout"),
            el(1, "button", "Apply coupon"),
            el(2, ROLE_TEXT, "Total ₹500"),
            el(3, "button", "Place order"),
            pkg = "com.shop", label = "Shop",
        )
        val couponApplied = screen(
            el(0, ROLE_TEXT, "Checkout"),
            el(1, ROLE_TEXT, "Coupon SAVE10 applied"),
            el(2, "button", "Apply coupon"),
            el(3, ROLE_TEXT, "Total ₹450"),
            el(4, "button", "Place order"),
            pkg = "com.shop", label = "Shop",
        )
        val phone = ScenarioBench.phone(this, checkout)
        phone.rules = { a -> if (a is Act.Tap && a.label == "Apply coupon") couponApplied else null }
        val model = ScriptedModel(
            // [3] was "Place order" on the screen the model saw; after the coupon it is [4].
            calls("tap" to """{"index":1}""", "tap" to """{"index":3}"""),
            calls("finish_task" to """{"summary":"Coupon applied; the order needs your OK.","success":false}"""),
        )

        val asked = ScenarioBench.answerApprovals(this, allow = false)
        val end = play(phone, model, "Apply the coupon and place the order")

        assertThat(asked).hasSize(1)
        assertThat(asked.single()).contains("Place order")
        assertThat(phone.log).containsExactly(Act.Tap("Apply coupon", 1))
        assertThat(model.seen[1]).contains("Blocked: the user denied")
        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.FAILED)
    }

    @Test
    fun `stuck - a tap that changes nothing escalates instead of repeating forever`() = runTest {
        val feed = screen(el(0, "button", "Load more"), el(1, ROLE_TEXT, "Post 1"), pkg = "com.feed", label = "Feed")
        val phone = ScenarioBench.phone(this, feed)
        val tap = calls("tap_text" to """{"text":"Load more"}""")
        val model = ScriptedModel(tap, tap, calls("finish_task" to """{"summary":"Nothing more loads.","success":false}"""))

        play(phone, model, "Load all posts")

        assertThat(model.seen[1]).contains("⚠ The screen did not change")
        assertThat(model.seen[2]).contains("That is 2 in a row: stop repeating it")
    }

    @Test
    fun `scroll_to stops where the list says it ends, without a wasted scroll`() = runTest {
        fun page(n: Int, more: Boolean) = screen(
            el(0, ROLE_LIST, "", top = 200, height = 2000).copy(canScrollForward = more, canScrollBack = n > 1),
            el(1, ROLE_TEXT, "Contact ${n}a", top = 300),
            el(2, ROLE_TEXT, "Contact ${n}b", top = 500),
            pkg = "com.contacts", label = "Contacts",
        )
        val pages = listOf(page(1, true), page(2, true), page(3, false))
        val phone = ScenarioBench.phone(this, pages[0])
        var at = 0
        phone.rules = { a -> if (a is Act.Scroll && at < 2) pages[++at] else null }
        val model = ScriptedModel(
            calls("scroll_to" to """{"text":"Zed"}"""),
            calls("finish_task" to """{"summary":"No contact named Zed.","success":true}"""),
        )

        play(phone, model, "Is Zed in my contacts?")

        assertThat(phone.scrolls).isEqualTo(2)
        assertThat(model.seen[1]).contains("Reached the end after 2 scrolls")
    }

    @Test
    fun `scrolling a list that is at its end answers at once, no swipe`() = runTest {
        val end = screen(
            el(0, ROLE_LIST, "", top = 200, height = 2000).copy(canScrollBack = true),
            el(1, ROLE_TEXT, "Last contact", top = 300),
            pkg = "com.contacts", label = "Contacts",
        )
        val phone = ScenarioBench.phone(this, end)
        val model = ScriptedModel(
            calls("scroll" to """{"direction":"down"}"""),
            calls("finish_task" to """{"summary":"That's everyone.","success":true}"""),
        )

        play(phone, model, "Read me all my contacts")

        assertThat(phone.scrolls).isEqualTo(0)
        assertThat(model.seen[1]).contains("Nothing more down: that list is at its end")
    }

    @Test
    fun `scrolling sideways moves the carousel, never the feed it sits on`() = runTest {
        val shop = screen(
            el(0, ROLE_LIST, "", top = 200, height = 2100).copy(canScrollForward = true),
            el(1, ROLE_LIST, "", top = 400, height = 300).copy(horizontal = true, canScrollBack = true, canScrollForward = true),
            el(2, ROLE_TEXT, "Deals of the day", top = 300),
            pkg = "com.shop", label = "Shop",
        )
        val phone = ScenarioBench.phone(this, shop)
        val model = ScriptedModel(
            // The feed is at its top, but "left" is about the carousel: it must not be refused.
            calls("scroll" to """{"direction":"left"}"""),
            calls("scroll" to """{"direction":"down"}"""),
            calls("finish_task" to """{"summary":"Browsed the deals.","success":true}"""),
        )

        play(phone, model, "Show me today's deals")

        assertThat(phone.log).containsExactly(Act.Scroll("left", 1), Act.Scroll("down", 0)).inOrder()
        assertThat(model.seen[1]).doesNotContain("Nothing more left")
    }

    @Test
    fun `sideways with no carousel is tried, not refused by the vertical list`() = runTest {
        val feed = screen(
            el(0, ROLE_LIST, "", top = 200, height = 2100).copy(canScrollForward = true),
            el(1, ROLE_TEXT, "Story 1", top = 300),
            pkg = "com.news", label = "News",
        )
        val phone = ScenarioBench.phone(this, feed)
        val model = ScriptedModel(
            calls("scroll" to """{"direction":"right"}"""),
            calls("finish_task" to """{"summary":"Swiped.","success":true}"""),
        )

        play(phone, model, "Swipe to the next story")

        assertThat(phone.log).containsExactly(Act.Scroll("right", null))
    }

    @Test
    fun `a tap by coordinates on the open keyboard is refused like an element tap`() = runTest {
        val chat = screen(
            el(0, "input", "Message", value = "Running late", top = 1300, height = 120),
            el(1, ROLE_TEXT, "Earlier message", top = 1900),
            pkg = "com.chat", label = "Chat",
        ).copy(keyboardVisible = true, keyboardTop = 1500)
        val phone = ScenarioBench.phone(this, chat)
        val model = ScriptedModel(
            calls("tap" to """{"x":980,"y":2250}"""),
            calls("swipe" to """{"x1":540,"y1":2000,"x2":540,"y2":1000}"""),
            calls("finish_task" to """{"summary":"Left the draft.","success":false}"""),
        )

        play(phone, model, "Send the draft")

        assertThat(phone.log).isEmpty()
        assertThat(model.seen[1]).contains("is under the keyboard")
        assertThat(model.seen[2]).contains("is under the keyboard")
    }

    @Test
    fun `reading the same screen while waiting is not going in circles`() = runTest {
        val video = screen(el(0, "view", "Video player"), el(1, ROLE_TEXT, "Buffering"), pkg = "com.video", label = "Video")
        val phone = ScenarioBench.phone(this, video)
        val read = calls("read_screen" to "{}")
        val model = ScriptedModel(read, read, read, read, calls("finish_task" to """{"summary":"Playing.","success":true}"""))

        play(phone, model, "Wait for the video")

        assertThat(model.seen.joinToString("\n")).doesNotContain("going in circles")
    }

    @Test
    fun `WhatsApp - one quick action opens the chat, Send still waits for the user's OK`() = runTest {
        val chat = screen(
            el(0, ROLE_TEXT, "Mom", top = 100),
            el(1, "input", "Message", value = "Running 10 min late", top = 2100),
            el(2, "icon", "Send", top = 2100, left = 950, right = 1060),
            pkg = "com.whatsapp", label = "WhatsApp",
        )
        val sent = screen(
            el(0, ROLE_TEXT, "Mom", top = 100),
            el(1, ROLE_TEXT, "Running 10 min late · 21:41 · Delivered", top = 1800),
            el(2, "input", "Message", top = 2100),
            pkg = "com.whatsapp", label = "WhatsApp",
        )
        val phone = ScenarioBench.phone(this, home)
        phone.rules = { a ->
            when {
                a is Act.Quick && a.args.action == "whatsapp" -> chat
                a is Act.Tap && a.label == "Send" -> sent
                else -> null
            }
        }
        val model = ScriptedModel(
            calls("quick_action" to """{"action":"whatsapp","to":"+977 980-000-0000","text":"Running 10 min late"}"""),
            calls("tap_text" to """{"text":"Send"}"""),
            calls("finish_task" to """{"summary":"Told Mom you're running 10 minutes late.","success":true}"""),
        )

        val asked = ScenarioBench.answerApprovals(this, allow = true)
        val end = play(phone, model, "WhatsApp Mom that I'm running 10 min late")

        val quick = phone.log.filterIsInstance<Act.Quick>().single().args
        assertThat(quick.action).isEqualTo("whatsapp")
        assertThat(quick.to).isEqualTo("+977 980-000-0000")
        assertThat(quick.text).isEqualTo("Running 10 min late")
        assertThat(asked.single()).contains("Send")
        assertThat(phone.log.last()).isEqualTo(Act.Tap("Send", 2))
        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.DONE)
    }

    @Test
    fun `a target that changed while the model was thinking is skipped, never guessed`() = runTest {
        val inbox = screen(el(0, "button", "Archive"), el(1, "button", "Delete"), pkg = "com.mail", label = "Mail")
        val refreshed = screen(el(0, "button", "Archive"), el(1, "button", "Reply"), pkg = "com.mail", label = "Mail")
        val phone = ScenarioBench.phone(this, inbox)
        val model = ScriptedModel(
            { request ->
                // A new mail arrives and the toolbar changes while the model decides.
                phone.change(refreshed)
                calls("tap" to """{"index":1}""")(request)
            },
            calls("finish_task" to """{"summary":"The mail changed; nothing deleted.","success":false}"""),
        )

        play(phone, model, "Delete this mail")

        assertThat(phone.log).isEmpty()
        assertThat(model.seen[1]).contains("Skipped: that element changed or left the screen")
    }

    @Test
    fun `a sign-in wall and a captcha are named on screen`() = runTest {
        val login = screen(
            el(0, ROLE_TEXT, "Sign in to continue"),
            el(1, "input", "Email"),
            el(2, "input", "Password", password = true),
            el(3, "button", "Sign in"),
            el(4, "checkbox", "I'm not a robot", checkable = true),
            pkg = "com.site", label = "Site",
        )
        val phone = ScenarioBench.phone(this, login)
        val model = ScriptedModel(calls("finish_task" to """{"summary":"Needs your login.","success":false}"""))

        play(phone, model, "Check my order status")

        assertThat(model.seen[0]).contains("Hint: This screen asks to sign in")
        assertThat(model.seen[0]).contains("Hint: A human check (captcha)")
    }
}

/** Shared plumbing for [ScenarioBenchTest]. */
class ProseTurnBenchTest {

    private val signIn = screen(
        el(0, ROLE_TEXT, "Instagram"),
        el(1, "input", "Phone number, username or email"),
        el(2, "input", "Password", password = true),
        el(3, "button", "Log in"),
        pkg = "com.instagram.android", label = "Instagram",
    )

    @Test
    fun `finish_task written as text ends the run as done, not unconfirmed`() = runTest {
        val phone = ScenarioBench.phone(this, signIn)
        val model = ScriptedModel(ScriptedModel.text("Nothing to do here.\nfinish_task(summary=\"Checked: Instagram needs a sign-in.\", success=false)"))

        val end = ScenarioBench.play(phone, model, "Tell me who messaged me on Instagram")

        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.FAILED)
        assertThat(end.summary).isEqualTo("Checked: Instagram needs a sign-in.")
        assertThat(model.seen).hasSize(1)
    }

    @Test
    fun `a question asked in prose reaches the user as a question card, and the run goes on`() = runTest {
        val phone = ScenarioBench.phone(this, signIn)
        val asked = ScenarioBench.answerQuestions(this, "Don't sign in, just tell me you couldn't check.")
        val model = ScriptedModel(
            ScriptedModel.text("I see the Instagram login screen. Could you give me your username and password?"),
            calls("finish_task" to """{"summary":"Instagram needs a sign-in, so I couldn't read your DMs.","success":false}"""),
        )

        val end = ScenarioBench.play(phone, model, "Tell me who messaged me on Instagram")

        assertThat(asked).containsExactly("Could you give me your username and password?")
        assertThat(model.seen[1]).contains("The user answered: Don't sign in")
        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.FAILED)
        assertThat(end.steps.map { it.kind }).contains(DeviceAgent.StepKind.QUESTION)
        assertThat(phone.log).isEmpty()
    }

    @Test
    fun `a plain done statement gets one concrete reminder to call finish_task`() = runTest {
        val phone = ScenarioBench.phone(this, signIn)
        val model = ScriptedModel(
            ScriptedModel.text("Done! Anything else?"),
            calls("finish_task" to """{"summary":"Opened Instagram.","success":true}"""),
        )

        val end = ScenarioBench.play(phone, model, "Open Instagram")

        assertThat(model.seen[1]).contains("If the task is complete, call finish_task")
        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.DONE)
    }

    @Test
    fun `a second done statement gets a plainer ask, a third ends the run unconfirmed`() = runTest {
        val phone = ScenarioBench.phone(this, signIn)
        val model = ScriptedModel(
            ScriptedModel.text("Do Not Disturb is now on."),
            ScriptedModel.text("Do Not Disturb is now enabled."),
            ScriptedModel.text("It is on."),
        )

        val end = ScenarioBench.play(phone, model, "Turn on Do Not Disturb")

        assertThat(model.seen[2]).contains("Call the finish_task tool now")
        assertThat(model.seen).hasSize(3)
        assertThat(end.endReason).isEqualTo(DeviceAgent.EndReason.UNCONFIRMED)
    }

    @Test
    fun `a missed scroll in Settings points at the one-step way to a page`() = runTest {
        val font = screen(
            el(0, "icon", "Navigate up"),
            el(1, ROLE_TEXT, "Font size and text"),
            el(2, "button", "Reset settings"),
            pkg = "com.android.settings", label = "Settings",
        )
        val phone = ScenarioBench.phone(this, font)
        val model = ScriptedModel(
            calls("scroll_to" to """{"text":"Wi-Fi"}"""),
            calls("finish_task" to """{"summary":"Not here.","success":false}"""),
        )

        ScenarioBench.play(phone, model, "Open the Wi-Fi settings")

        assertThat(model.seen[1]).contains("quick_action(settings, page=...)")
    }
}

class ComparisonCueBenchTest {

    private fun results() = screen(
        el(0, ROLE_LIST, "", top = 200, height = 2000).copy(canScrollForward = true),
        el(1, "button", "Samsung 25W · ₹1,299", top = 300),
        el(2, "button", "Mi 33W · ₹649", top = 500),
        pkg = "com.shopkart", label = "ShopKart",
    )

    @Test
    fun `a task that compares items is told the list goes on below`() = runTest {
        val phone = ScenarioBench.phone(this, results())
        val model = ScriptedModel(calls("finish_task" to """{"summary":"x","success":false}"""))
        ScenarioBench.play(phone, model, "Add the cheapest charger to the cart")
        assertThat(model.seen[0]).contains("your task compares items")
    }

    @Test
    fun `other tasks are not`() = runTest {
        val phone = ScenarioBench.phone(this, results())
        val model = ScriptedModel(calls("finish_task" to """{"summary":"x","success":false}"""))
        ScenarioBench.play(phone, model, "Open the Mi charger")
        assertThat(model.seen[0]).doesNotContain("your task compares items")
    }

    @Test
    fun `a pick from the first screenful is held once, then the rest of the list is read for it, then it goes through`() = runTest {
        val phone = ScenarioBench.phone(this, results())
        val model = ScriptedModel(
            calls("tap" to """{"index":2}"""),
            calls("tap" to """{"index":2}"""),
            calls("tap" to """{"index":2}"""),
            calls("finish_task" to """{"summary":"Added Mi.","success":true}"""),
        )
        ScenarioBench.play(phone, model, "Add the cheapest charger to the cart")
        assertThat(model.seen[1]).contains("Not tapped yet")
        assertThat(model.seen[2]).contains("here is all of it")
        assertThat(model.seen[2]).contains("Samsung 25W · ₹1,299")
        assertThat(phone.log.filterIsInstance<Act.Scroll>()).isNotEmpty()
        assertThat(phone.log.filterIsInstance<Act.Tap>()).containsExactly(Act.Tap("Mi 33W · ₹649", 2))
    }

    @Test
    fun `a pick after reading on, or a tap on Sort, is not held`() = runTest {
        val sorted = screen(
            el(0, ROLE_LIST, "", top = 200, height = 2000).copy(canScrollForward = true),
            el(1, "button", "Sort", top = 250),
            el(2, "button", "Mi 33W · ₹649", top = 500),
            pkg = "com.shopkart", label = "ShopKart",
        )
        val phone = ScenarioBench.phone(this, sorted)
        val model = ScriptedModel(
            calls("tap" to """{"index":1}"""),
            calls("read_page" to """{"pages":2}"""),
            calls("tap" to """{"index":2}"""),
            calls("finish_task" to """{"summary":"Added Mi.","success":true}"""),
        )
        ScenarioBench.play(phone, model, "Add the cheapest charger to the cart")
        assertThat(model.seen.joinToString()).doesNotContain("Not tapped yet")
        assertThat(phone.log.filterIsInstance<Act.Tap>().map { it.index }).containsExactly(1, 2).inOrder()
    }
}

class RepeatBenchTest {

    private fun results() = screen(
        el(0, ROLE_LIST, "", top = 200, height = 2000).copy(canScrollForward = true),
        el(1, "input", "Search YouTube", top = 100),
        el(2, "button", "Blinding Lights (Lyrics) · 7clouds", top = 300),
        el(3, "button", "Blinding Lights | Piano Cover", top = 500),
        pkg = "com.google.android.youtube", label = "YouTube",
    )

    @Test
    fun `searching again for the same results points below the fold`() = runTest {
        val phone = ScenarioBench.phone(this, results())
        val model = ScriptedModel(
            calls("type_text" to """{"index":1,"text":"blinding lights official","submit":true}"""),
            calls("type_text" to """{"index":1,"text":"the weeknd blinding lights","submit":true}"""),
            calls("finish_task" to """{"summary":"x","success":false}"""),
        )
        ScenarioBench.play(phone, model, "Play the official music video of Blinding Lights by The Weeknd")
        assertThat(model.seen[1]).doesNotContain("the same results as your last search")
        assertThat(model.seen[2]).contains("the same results as your last search")
        // Not a person to ask about: the results share a title.
        assertThat(model.seen.joinToString()).doesNotContain("ask_user; don't guess")
    }

    @Test
    fun `the same question a third time is answered from the answers so far`() = runTest {
        val phone = ScenarioBench.phone(this, results())
        val asked = ScenarioBench.answerQuestions(this, "I don't know it")
        val model = ScriptedModel(
            calls("ask_user" to """{"question":"What is Priya's phone number?"}"""),
            calls("ask_user" to """{"question":"Please tell me Priya's phone number with country code."}"""),
            calls("ask_user" to """{"question":"I need Priya's phone number to continue."}"""),
            calls("finish_task" to """{"summary":"No number.","success":false}"""),
        )
        ScenarioBench.play(phone, model, "WhatsApp Priya that I'm late")
        assertThat(asked).hasSize(2)
        assertThat(model.seen[3]).contains("Not asked again: you asked this 2 times")
        assertThat(model.seen[3]).contains("\"I don't know it\"")
    }
}

internal object ScenarioBench {

    /** Answers every question card with [answer]; the questions asked go into the returned list. */
    fun answerQuestions(scope: TestScope, answer: String): List<String> {
        val asked = mutableListOf<String>()
        scope.backgroundScope.launch {
            DeviceAgent.state.collect { s ->
                val q = s.question?.text
                if (q != null) {
                    if (asked.lastOrNull() != q) asked += q
                    DeviceAgent.answer(answer)
                }
            }
        }
        return asked
    }

    fun phone(scope: TestScope, start: ScreenState) = FakePhone({ scope.testScheduler.currentTime }, start)

    /** Answers every approval prompt with [allow]; the prompts asked go into the returned list. */
    fun answerApprovals(scope: TestScope, allow: Boolean): List<String> {
        val asked = mutableListOf<String>()
        scope.backgroundScope.launch {
            DeviceAgent.state.collect { s ->
                val q = s.approval
                if (q != null) {
                    if (asked.lastOrNull() != q) asked += q
                    DeviceAgent.approve(allow)
                }
            }
        }
        return asked
    }

    suspend fun play(phone: FakePhone, model: ScriptedModel, task: String, settings: DeviceAgent.Settings = DeviceAgent.Settings()) =
        DeviceAgent.runScenario(phone, task, ScriptedModel.model, model, settings)
}
