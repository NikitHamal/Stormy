package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import com.oro.app.browser.BrowserBench.play
import com.oro.app.browser.FakeBrowser.Act
import com.oro.app.browser.PageFixtures.el
import com.oro.app.browser.PageFixtures.page
import com.oro.app.browser.ScriptedModel.Companion.calls
import com.oro.app.browser.ScriptedModel.Companion.text
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The browser agent's real run loop on scripted pages: forms in one call,
 * human checks handed to the user, secrets that never reach the model,
 * sensitive actions gated, stuck signals, endings. Same bench shape the
 * device agent was proved on.
 */
class BrowserScenarioTest {

    // ------------------------------------------------------------ forms

    @Test
    fun `fill_form fills a whole sign-up form in one call and submits it`() = runTest {
        val form = page(
            el(1, "textbox", "Full name", top = 100, selector = "input#fullname"),
            el(2, "textbox", "Email", top = 200, selector = "input#email"),
            el(3, "textbox", "Confirm email", top = 300, selector = "input#confirm-email"),
            el(4, "checkbox", "I agree to the Terms", top = 400, editable = false),
            el(5, "combobox", "Country", top = 500, editable = false, options = 12, selected = ""),
            el(6, "button", "Continue with Google", top = 600, editable = false),
            el(7, "button", "Create account", top = 700, editable = false),
            url = "https://shop.example/signup", title = "Sign up",
        )
        val welcome = page(el(1, "textbox", "", value = ""), url = "https://shop.example/welcome", title = "Welcome, Priya", text = "Welcome, Priya")
        val browser = BrowserBench.browser(this, form)
        browser.rules = { a -> if (a is Act.Click && a.label == "Create account") welcome else null }
        BrowserBench.answerApprovals(this, allow = true)
        val fields = """[{"field":"Full name","value":"Priya Rao"},{"field":"Email","value":"priya@example.com"},""" +
            """{"field":"Confirm email","value":"priya@example.com"},{"field":"I agree to the Terms","value":"yes"},{"field":"Country","value":"India"}]"""
        val model = ScriptedModel(
            calls("fill_form" to """{"fields":$fields,"submit":true}"""),
            calls("finish_task" to """{"summary":"Account created for priya@example.com.","success":true}"""),
        )

        val state = play(browser, model, "Sign me up on shop.example as Priya Rao, priya@example.com")

        assertThat(browser.log).containsAtLeast(
            Act.Type("Full name", "Priya Rao"),
            Act.Type("Email", "priya@example.com"),
            Act.Type("Confirm email", "priya@example.com"),
            Act.Check("I agree to the Terms", true),
            Act.Select("Country", "India"),
            Act.Click("Create account", 7),
        ).inOrder()
        assertThat(browser.log).doesNotContain(Act.Click("Continue with Google", 6))
        assertThat(model.seen[1]).contains("Full name: filled")
        assertThat(model.seen[1]).contains("Email: filled")
        assertThat(model.seen[1]).contains("Country: chose")
        assertThat(model.seen[1]).contains("Submitted:")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
        assertThat(state.summary).contains("Account created")
    }

    @Test
    fun `a field whose name fits two boxes is reported, never guessed`() = runTest {
        val form = page(
            el(1, "textbox", "Name", top = 100),
            el(2, "textbox", "Name", top = 220),
            el(3, "textbox", "Email", top = 340),
            el(4, "button", "Submit", top = 460, editable = false),
        )
        val browser = BrowserBench.browser(this, form)
        val model = ScriptedModel(
            calls("fill_form" to """{"fields":[{"field":"Name","value":"Priya"},{"field":"Email","value":"p@x.io"}],"submit":true}"""),
            calls("finish_task" to """{"summary":"Blocked on the ambiguous field.","success":false}"""),
        )

        play(browser, model, "Fill in the form")

        assertThat(browser.log).containsExactly(Act.Type("Email", "p@x.io"))
        assertThat(model.seen[1]).contains("Name: fits 2 fields ([1], [2]); give its index")
        assertThat(model.seen[1]).contains("Not submitted: some fields were not filled.")
    }

    // ---------------------------------------------------- secrets & login

    @Test
    fun `a plaintext password from the task never reaches the page -- the run refuses and asks the user`() = runTest {
        val login = page(
            el(1, "textbox", "Username", top = 100),
            el(2, "textbox", "Password", top = 200, password = true, editable = true),
            el(3, "button", "Sign in", top = 300, editable = false),
            url = "https://notes.example/login",
        )
        val browser = BrowserBench.browser(this, login)
        BrowserBench.answerSecrets(this, secret = "s3cret-from-user")
        val model = ScriptedModel(
            // The task text carries a password; the model tries to type it.
            calls("type_text" to """{"index":2,"text":"hunter2"}"""),
            // After the refusal it asks the user for the secret instead.
            calls("ask_user_secret" to """{"purpose":"your notes.example password","index":2}"""),
            calls("finish_task" to """{"summary":"Signed in.","success":true}"""),
        )

        val state = play(browser, model, "Log in to notes.example as priya with password hunter2")

        // The plaintext password was never typed...
        assertThat(browser.log).doesNotContain(Act.Type("Password", "hunter2"))
        // ...and what the engine typed came from the user, not the model.
        assertThat(browser.log).contains(Act.Secret("Password", "s3cret-from-user"))
        // The model was told to route secrets properly.
        assertThat(model.seen[1]).contains("Refused to type a plaintext password")
        assertThat(model.seen[1]).contains("ask_user_secret")
        // The secret itself never entered the model's context.
        assertThat(model.seen.joinToString("\n")).doesNotContain("s3cret-from-user")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
    }

    @Test
    fun `the user can type the secret straight into the page and the model only learns that it happened`() = runTest {
        val login = page(
            el(1, "textbox", "Password", top = 100, password = true, editable = true),
            url = "https://bank.example",
        )
        val browser = BrowserBench.browser(this, login)
        val asked = BrowserBench.answerSecrets(this, secret = null) // typed in the page
        val model = ScriptedModel(
            calls("ask_user_secret" to """{"purpose":"your banking password"}"""),
            calls("finish_task" to """{"summary":"Password entered by the user.","success":true}"""),
        )

        play(browser, model, "Enter my password on bank.example")

        assertThat(asked).containsExactly("your banking password")
        assertThat(browser.log).isEmpty() // nothing typed through the engine
        assertThat(model.seen[1]).contains("typed the secret straight into the page")
        assertThat(model.seen.joinToString("\n")).doesNotContain("password is")
    }

    @Test
    fun `a denied secret is not retried`() = runTest {
        val login = page(el(1, "textbox", "Password", top = 100, password = true, editable = true))
        val browser = BrowserBench.browser(this, login)
        backgroundDeclineSecrets()
        val model = ScriptedModel(
            calls("ask_user_secret" to """{"purpose":"a password"}"""),
            calls("finish_task" to """{"summary":"Blocked: no password.","success":false}"""),
        )
        val state = play(browser, model, "Sign in somewhere")
        assertThat(model.seen[1]).contains("declined to provide the secret")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.FAILED)
    }

    private fun kotlinx.coroutines.test.TestScope.backgroundDeclineSecrets() {
        backgroundScope.launch {
            BrowserAgent.state.collect { s ->
                if (s.secretRequest != null) BrowserAgent.declineSecret()
            }
        }
    }

    // ------------------------------------------------------- human checks

    @Test
    fun `a captcha is handed to the user and the run resumes itself when it clears`() = runTest {
        val checked = page(
            el(1, "textbox", "Search", top = 100),
            el(2, "button", "Go", top = 200, editable = false),
            url = "https://search.example/",
            humanCheck = HumanCheckKind.RECAPTCHA,
        )
        val clear = page(
            el(1, "textbox", "Search", top = 100),
            el(2, "button", "Go", top = 200, editable = false),
            url = "https://search.example/",
        )
        val browser = BrowserBench.browser(this, checked)
        browser.rules = { a -> if (a is Act.Click && a.label == "Go") clear else null }
        // The "user" passes the check shortly after the hand-off.
        backgroundScope.launch {
            BrowserAgent.state.collect { s ->
                if (s.question?.text?.startsWith("Pass the") == true) {
                    kotlinx.coroutines.delay(50)
                    browser.change(clear)
                }
            }
        }
        val model = ScriptedModel(
            calls("click" to """{"index":2}"""),
            calls("finish_task" to """{"summary":"Search submitted.","success":true}"""),
        )

        val state = play(browser, model, "Run the search")

        // The click happened only after the check cleared.
        assertThat(browser.log).contains(Act.Click("Go", 2))
        assertThat(state.notes).contains("The user passed a human check.")
        assertThat(model.seen[1]).contains("human check")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
    }

    // ------------------------------------------------------ safety gates

    @Test
    fun `a pay button asks the user first, and a denial stops that path`() = runTest {
        val cart = page(
            el(1, "button", "Place order", top = 100, editable = false),
            url = "https://shop.example/checkout",
        )
        val browser = BrowserBench.browser(this, cart)
        val asked = BrowserBench.answerApprovals(this, allow = false)
        val model = ScriptedModel(
            calls("click" to """{"index":1}"""),
            calls("finish_task" to """{"summary":"User declined the purchase.","success":false}"""),
        )

        val state = play(browser, model, "Buy the thing in my cart")

        assertThat(asked).hasSize(1)
        assertThat(asked[0]).contains("Place order")
        assertThat(browser.log).isEmpty() // never clicked
        assertThat(model.seen[1]).contains("the user denied")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.FAILED)
    }

    @Test
    fun `an allowed pay button clicks, and the page is re-checked after the decision`() = runTest {
        val cart = page(
            el(1, "button", "Place order", top = 100, editable = false),
            url = "https://shop.example/checkout",
        )
        val thanks = page(el(1, "textbox", "", top = 100), url = "https://shop.example/thanks", title = "Thanks!", text = "Order confirmed")
        val browser = BrowserBench.browser(this, cart)
        browser.rules = { a -> if (a is Act.Click && a.label == "Place order") thanks else null }
        BrowserBench.answerApprovals(this, allow = true)
        val model = ScriptedModel(
            calls("click" to """{"index":1}"""),
            calls("finish_task" to """{"summary":"Order placed.","success":true}"""),
        )

        val state = play(browser, model, "Place my order")

        assertThat(browser.log).contains(Act.Click("Place order", 1))
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
        assertThat(model.seen[1]).contains("Clicked")
    }

    // -------------------------------------------------------- navigation

    @Test
    fun `open_url lands the run on the task's page and the observation follows`() = runTest {
        val blank = page(url = "about:blank", title = "")
        val hn = page(
            el(1, "link", "Show HN: Oro 2.0", top = 100),
            url = "https://news.ycombinator.com/", title = "Hacker News",
            text = "1. Show HN: Oro 2.0 (412 points)",
        )
        val browser = BrowserBench.browser(this, blank)
        browser.rules = { a -> if (a is Act.Nav && a.url.contains("ycombinator")) hn else null }
        val model = ScriptedModel(
            calls("open_url" to """{"url":"https://news.ycombinator.com/"}"""),
            calls("read_page" to """{"mode":"markdown"}"""),
            calls("finish_task" to """{"summary":"Top story: Show HN: Oro 2.0, 412 points.","success":true}"""),
        )

        val state = play(browser, model, "What is the top story on Hacker News?")

        assertThat(browser.log).contains(Act.Nav("https://news.ycombinator.com/"))
        assertThat(model.seen[1]).contains("Hacker News")
        assertThat(state.summary).contains("412 points")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
    }

    @Test
    fun `click_text resolves synonyms and clicks the right element`() = runTest {
        val home = page(
            el(1, "link", "Sign in", top = 100),
            el(2, "link", "Pricing", top = 200),
        )
        val login = page(el(1, "textbox", "Email", top = 100), url = "https://x.example/login")
        val browser = BrowserBench.browser(this, home)
        browser.rules = { a -> if (a is Act.Click && a.label == "Sign in") login else null }
        val model = ScriptedModel(
            calls("click_text" to """{"text":"Log in"}"""),
            calls("finish_task" to """{"summary":"On the login page.","success":true}"""),
        )

        play(browser, model, "Get to the login page")

        assertThat(browser.log).contains(Act.Click("Sign in", 1))
    }

    @Test
    fun `an ambiguous click_text lists the candidates instead of guessing`() = runTest {
        val list = page(
            el(1, "button", "Delete", top = 100, editable = false),
            el(2, "button", "Delete", top = 300, editable = false),
        )
        val browser = BrowserBench.browser(this, list)
        BrowserBench.answerApprovals(this, allow = true) // "Delete" is sensitive: the guard asks
        val model = ScriptedModel(
            calls("click_text" to """{"text":"Delete"}"""),
            calls("click" to """{"index":2}"""),
            calls("finish_task" to """{"summary":"Deleted the second.","success":true}"""),
        )

        play(browser, model, "Delete the second row")

        assertThat(model.seen[1]).contains("matches 2 elements")
        assertThat(browser.log).containsExactly(Act.Click("Delete", 2))
    }

    // ------------------------------------------------------- stuck signal

    @Test
    fun `a click that changes nothing escalates after repeats`() = runTest {
        val stuck = page(el(1, "button", "Load more", top = 100, editable = false))
        val browser = BrowserBench.browser(this, stuck) // rules never change the page
        val model = ScriptedModel(
            calls("click" to """{"index":1}"""),
            calls("click" to """{"index":1}"""),
            calls("click" to """{"index":1}"""),
            calls("finish_task" to """{"summary":"Button is dead.","success":false}"""),
        )

        play(browser, model, "Click the button")

        assertThat(model.seen[1]).contains("The page did not change")
        assertThat(model.seen[2]).contains("2 in a row")
        assertThat(model.seen[2]).contains("stop repeating it")
    }

    @Test
    fun `a broken turn skips the rest of its calls`() = runTest {
        val p = page(el(1, "button", "OK", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        val model = ScriptedModel(
            calls(
                "click" to """{"index":99}""",
                "click" to """{"index":1}""",
            ),
            calls("finish_task" to """{"summary":"Recovered.","success":true}"""),
        )

        play(browser, model, "Do two clicks")

        assertThat(browser.log).isEmpty()
        assertThat(model.seen[1]).contains("an earlier action in this turn did not work")
    }

    // ------------------------------------------------------------ endings

    @Test
    fun `a prose finish_task ends the run as done`() = runTest {
        val p = page(el(1, "button", "OK", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        val model = ScriptedModel(
            text("""All done. finish_task(summary="Found the answer: 42", success=true)"""),
        )

        val state = play(browser, model, "Find the answer")

        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
        assertThat(state.summary).contains("42")
    }

    @Test
    fun `a prose question becomes a real card and the answer goes back`() = runTest {
        val p = page(el(1, "button", "OK", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        val asked = BrowserBench.answerQuestions(this, "The first one")
        val model = ScriptedModel(
            text("Which account should I use for the download?"),
            calls("finish_task" to """{"summary":"Used the first account.","success":true}"""),
        )

        val state = play(browser, model, "Download my invoice")

        assertThat(asked).containsExactly("Which account should I use for the download?")
        assertThat(model.seen[1]).contains("The user answered: The first one")
        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.DONE)
    }

    @Test
    fun `the action limit ends the run`() = runTest {
        val p = page(el(1, "button", "More", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        val model = ScriptedModel(
            calls("click" to """{"index":1}"""),
            calls("click" to """{"index":1}"""),
            calls("click" to """{"index":1}"""),
            calls("click" to """{"index":1}"""),
        )

        val state = play(
            browser, model, "Click forever",
            settings = BrowserAgent.Settings(maxActions = 2),
        )

        assertThat(state.endReason).isEqualTo(BrowserAgent.EndReason.ACTION_LIMIT)
    }

    @Test
    fun `notes survive across pages and come back in the report`() = runTest {
        val p1 = page(el(1, "link", "Next", top = 100), url = "https://a.example/1")
        val p2 = page(el(1, "button", "Done", top = 100, editable = false), url = "https://a.example/2")
        val browser = BrowserBench.browser(this, p1)
        browser.rules = { a -> if (a is Act.Click && a.label == "Next") p2 else null }
        val model = ScriptedModel(
            calls("take_note" to """{"note":"price is 42"}"""),
            calls("click" to """{"index":1}"""),
            calls("finish_task" to """{"summary":"Done.","success":true}"""),
        )

        val state = play(browser, model, "Note the price and move on")

        assertThat(model.seen[2]).contains("Your notes: price is 42")
        assertThat(state.notes).containsExactly("price is 42")
    }

    // --------------------------------------------------------- follow-ups

    @Test
    fun `a follow-up from the coding agent lands before the next round`() = runTest {
        val p1 = page(el(1, "link", "Page two", top = 100), url = "https://a.example/1")
        val p2 = page(el(1, "button", "Done", top = 100, editable = false), url = "https://a.example/2")
        val browser = BrowserBench.browser(this, p1)
        browser.rules = { a -> if (a is Act.Click && a.label == "Page two") p2 else null }
        val model = ScriptedModel(
            calls("click" to """{"index":1}"""),
            calls("click" to """{"index":1}"""),
            calls("finish_task" to """{"summary":"Done with the correction.","success":true}"""),
        )
        // The coordinator steers while the run works: queued as soon as the
        // run is live, delivered with the first round's results.
        backgroundScope.launch {
            BrowserAgent.state.collect { s ->
                if (s.running) BrowserAgent.queueFollowUp("actually stop after the second page")
            }
        }

        play(browser, model, "Walk the pages")

        assertThat(model.seen[1]).contains("Follow-up from the coding agent")
        assertThat(model.seen[1]).contains("actually stop after the second page")
    }

    @Test
    fun `queueing a follow-up with nothing running is refused`() {
        assertThat(BrowserAgent.queueFollowUp("hello?")).isFalse()
    }

    // ----------------------------------------------------- scraping tools

    @Test
    fun `read_page brings the article text back in one call`() = runTest {
        val article = page(
            el(1, "link", "Home", top = 50),
            url = "https://blog.example/post", title = "A post",
        )
        val browser = BrowserBench.browser(this, article)
        browser.readResult = { _, _ -> "# A post\n\nThe whole article body, in markdown." }
        val model = ScriptedModel(
            calls("read_page" to """{"mode":"markdown"}"""),
            calls("finish_task" to """{"summary":"Summarised.","success":true}"""),
        )

        play(browser, model, "Summarise the post")

        assertThat(model.seen[1]).contains("The whole article body")
        assertThat(model.seen[1]).contains("Page content (markdown)")
    }

    @Test
    fun `extract pulls structured rows in one call`() = runTest {
        val p = page(el(1, "button", "Next", top = 100, editable = false), url = "https://shop.example/list")
        val browser = BrowserBench.browser(this, p)
        browser.extractResult = { _ ->
            "42 rows of 42 matched:\n" + (1..3).joinToString("\n") { "{\"name\":\"Item $it\",\"price\":\"INR ${it}00\"}" }
        }
        val model = ScriptedModel(
            calls("extract" to """{"selector":".product-card","fields":[{"name":"name","selector":"h2"},{"name":"price","selector":".price"}],"limit":50}"""),
            calls("finish_task" to """{"summary":"42 items; first three priced 100-300.","success":true}"""),
        )

        play(browser, model, "Scrape every product name and price")

        assertThat(browser.log.filterIsInstance<Act.Js>().map { it.code }).contains("extract:.product-card")
        assertThat(model.seen[1]).contains("42 rows of 42 matched")
        assertThat(model.seen[1]).contains("INR 300")
    }

    @Test
    fun `read_page html mode gives the raw markup for code scraping`() = runTest {
        val p = page(el(1, "button", "Next", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        browser.readResult = { mode, _ -> if (mode == "html") "<pre class=\"code\">const x = 42;</pre>" else "" }
        val model = ScriptedModel(
            calls("read_page" to """{"mode":"html","selector":"pre.code"}"""),
            calls("finish_task" to """{"summary":"Got the snippet.","success":true}"""),
        )

        play(browser, model, "Scrape the code sample")

        assertThat(model.seen[1]).contains("const x = 42")
    }

    @Test
    fun `evaluate runs guarded javascript and returns its result`() = runTest {
        val p = page(el(1, "button", "OK", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        browser.evalResult = { "[{\"row\":1},{\"row\":2}]" }
        val model = ScriptedModel(
            calls("evaluate" to """{"js":"JSON.stringify([...document.querySelectorAll('tr')].map(r=>({row:1})))"}"""),
            calls("finish_task" to """{"summary":"2 rows.","success":true}"""),
        )

        play(browser, model, "Count the rows")

        assertThat(browser.log.filterIsInstance<Act.Js>()).hasSize(1)
        assertThat(model.seen[1]).contains("row")
    }

    @Test
    fun `a javascript url is refused`() = runTest {
        val p = page(el(1, "button", "OK", top = 100, editable = false))
        val browser = BrowserBench.browser(this, p)
        val model = ScriptedModel(
            calls("evaluate" to """{"js":"javascript:alert(1)"}"""),
            calls("finish_task" to """{"summary":"Stopped.","success":false}"""),
        )

        play(browser, model, "Do something bad")

        assertThat(model.seen[1]).contains("Refused: drop the javascript: prefix")
        assertThat(browser.log.filterIsInstance<Act.Js>()).isEmpty()
    }

    // --------------------------------------------------------- compaction

    @Test
    fun `older observations fold to their outcome line`() {
        val page1 = page(el(1, "button", "One", top = 0), url = "https://a/1", text = "first page text")
        val page2 = page(el(1, "button", "Two", top = 0), url = "https://a/2", text = "second page text")
        val history = listOf(
            com.oro.app.data.ai.ChatRequestMessage(role = "user", content = "Task: do things\n\n=== PAGE ===\n" + page1.render()),
            com.oro.app.data.ai.ChatRequestMessage(role = "assistant", content = "clicking"),
            com.oro.app.data.ai.ChatRequestMessage(role = "tool", content = "Clicked [1] “One”.\n\n=== PAGE ===\n" + page2.render()),
        )
        val compacted = BrowserAgent.compact(history, 0)
        assertThat(compacted[0].content).contains("[older page omitted]")
        assertThat(compacted[0].content).doesNotContain("first page text")
        // The newest page stays whole.
        assertThat(compacted[2].content).contains("second page text")
    }
}
