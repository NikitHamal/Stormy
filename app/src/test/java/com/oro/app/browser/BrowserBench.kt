package com.oro.app.browser

import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.data.ai.FunctionCall
import com.oro.app.data.ai.StreamEvent
import com.oro.app.data.ai.ToolCallResponse
import com.oro.app.data.ai.agent.AgentModelClient
import com.oro.app.data.ai.agent.AgentRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

/** Small builders for pages in tests: no Android types involved. */
internal object PageFixtures {

    fun el(
        index: Int,
        role: String,
        label: String,
        top: Int = index * 80,
        left: Int = 0,
        width: Int = 1080,
        height: Int = 90,
        editable: Boolean = role == "textbox",
        password: Boolean = false,
        checked: Boolean? = if (role == "checkbox" || role == "radio" || role == "switch") false else null,
        disabled: Boolean = false,
        value: String? = null,
        oid: String = "o$index",
        selector: String = "",
        options: Int = 0,
        selected: String? = null,
        expanded: Boolean? = null,
        canScrollForward: Boolean = false,
        canScrollBack: Boolean = false,
        horizontal: Boolean = false,
        inputType: String? = if (editable && password) "password" else null,
        multiline: Boolean = false,
    ) = PageElement(
        index = index, role = role, label = label,
        bounds = Bounds(left, top, left + width, top + height),
        oid = oid, selector = selector, frame = 0,
        editable = editable, password = password, disabled = disabled,
        checked = checked, value = value, expanded = expanded,
        options = options, selected = selected,
        canScrollBack = canScrollBack, canScrollForward = canScrollForward, horizontal = horizontal,
        inputType = inputType, multiline = multiline,
    )

    fun page(
        vararg elements: PageElement,
        url: String = "https://example.com/",
        title: String = "Example",
        text: String = "",
        scrollY: Int = 0,
        pageHeight: Int = 2_000,
        viewportW: Int = 412,
        viewportH: Int = 915,
        humanCheck: HumanCheckKind = HumanCheckKind.NONE,
        checkPassed: Boolean = false,
        frames: List<FrameInfo> = emptyList(),
        loading: Boolean = false,
    ) = PageState(
        url = url, title = title, elements = elements.toList(), text = text,
        signature = PageModel.signatureOf(url, elements.toList(), text, scrollY, viewportH, pageHeight, humanCheck, checkPassed),
        scrollY = scrollY, pageHeight = pageHeight,
        viewportWidth = viewportW, viewportHeight = viewportH,
        loading = loading, humanCheck = humanCheck, humanCheckPassed = checkPassed, frames = frames,
    )
}

/**
 * The scenario bench: a browser that follows a script and a model that
 * follows a script, so the browser agent's real run loop -- observation,
 * target resolution, the guard, human-check hand-offs, secrets, stuck
 * signals, endings -- runs in a JVM test, start to finish, with no WebView.
 *
 * A scenario says which page the browser starts on, how each action changes
 * the page ([FakeBrowser.rules]), and what the model calls each turn. The
 * test then checks what reached the browser ([FakeBrowser.log]), what the
 * model was shown ([ScriptedModel.seen]) and how the run ended.
 */
internal class FakeBrowser(
    private val clock: () -> Long,
    start: PageState,
) : Browser {

    /** Something the agent did to the browser. */
    sealed interface Act {
        data class Click(val label: String, val index: Int) : Act
        data class Type(val field: String?, val text: String) : Act
        /** A secret typed by the engine (vault or user): the plaintext, to prove it never reaches the model. */
        data class Secret(val field: String?, val text: String) : Act
        data class Select(val field: String, val option: String) : Act
        data class Check(val field: String, val value: Boolean) : Act
        data class Key(val key: String) : Act
        data class Scroll(val direction: String, val index: Int? = null) : Act
        data class Nav(val url: String) : Act
        data object Submit : Act
        data class Upload(val field: String, val path: String) : Act
        data class Js(val code: String) : Act
        data class Tab(val action: String, val id: Int? = null) : Act
    }

    var page: PageState = start
        private set

    /** What each action turns the page into; null leaves it as it is. */
    var rules: (Act) -> PageState? = { null }

    /** What read_page returns; defaults to the page's text. */
    var readResult: (String, String?) -> String = { _, _ -> page.text }

    /** What evaluate returns. */
    var evalResult: (String) -> String = { "(eval ok)" }

    /** Downloads the run sees. */
    var downloadsList: List<DownloadInfo> = emptyList()
    var downloadTexts: Map<String, String> = emptyMap()

    var tabsList: List<TabInfo> = listOf(TabInfo(1, start.url, start.title, true))

    /** Every action that reached the browser, in order. */
    val log = mutableListOf<Act>()

    private var latest: PageState? = null
    private var lastChange = 0L

    /** The page changes on its own (a lazy list loaded, a modal popped). */
    fun change(next: PageState) {
        page = next
        lastChange = clock() + 1
    }

    private fun act(a: Act) {
        log += a
        rules(a)?.let { change(it) }
    }

    override fun uptime(): Long = clock()
    override val lastChangeAt: Long get() = lastChange
    override fun isLatest(state: PageState): Boolean = state === latest
    override suspend fun snapshot(): PageState = page.copy(capturedAt = clock()).also { latest = it }
    override suspend fun settle(quietMs: Long, maxMs: Long, minMs: Long) { delay(minMs) }

    override suspend fun navigate(url: String): String {
        act(Act.Nav(url))
        return "Opened $url."
    }

    override suspend fun back(): String { act(Act.Nav("back")); return "Went back to ${page.url}." }
    override suspend fun forward(): String { act(Act.Nav("forward")); return "Went forward to ${page.url}." }
    override suspend fun reload(): String { act(Act.Nav(page.url)); return "Reloaded ${page.url}." }

    override suspend fun click(e: PageElement): String {
        act(Act.Click(e.label, e.index))
        return "Clicked [${e.index}] ${e.role} “${e.label}”."
    }

    override suspend fun bySelector(selector: String): PageElement? =
        page.elements.firstOrNull { it.selector == selector }

    override suspend fun type(e: PageElement?, text: String, append: Boolean): String {
        act(Act.Type(e?.label, text))
        return "Typed into ${e?.let { "[${it.index}] ${it.role} “${it.label}”" } ?: "the focused field"}: now “${text.take(30)}”."
    }

    override suspend fun typeSecret(e: PageElement, secret: String): String {
        act(Act.Secret(e.label, secret))
        return "The secret was entered into [${e.index}] ${e.role} “${e.label}” (its value is never shown)."
    }

    override suspend fun submit(): String { act(Act.Submit); return "Pressed Enter." }

    override suspend fun pressKey(key: String): String { act(Act.Key(key)); return "Pressed $key." }

    override suspend fun select(e: PageElement, option: String): String {
        act(Act.Select(e.label, option))
        return "Chose “$option” in [${e.index}] “${e.label}”."
    }

    override suspend fun setChecked(e: PageElement, checked: Boolean): String {
        if (e.checked == checked) return if (checked) "[${e.index}] was already checked." else "[${e.index}] was already off."
        act(Act.Check(e.label, checked))
        return if (checked) "Checked [${e.index}] “${e.label}”." else "Unchecked [${e.index}] “${e.label}”."
    }

    override suspend fun scroll(direction: String, e: PageElement?, amount: Float): String {
        act(Act.Scroll(direction, e?.index))
        return "Scrolled $direction to y=${page.scrollY} of ${page.pageHeight}."
    }

    override suspend fun scrollToText(text: String): String {
        act(Act.Scroll("to:$text"))
        val found = page.elements.any { it.label.contains(text, ignoreCase = true) } || page.text.contains(text, true)
        return if (found) "Scrolled to “$text”." else "“$text” is not on this page."
    }

    override suspend fun hover(e: PageElement): String {
        act(Act.Click("hover:${e.label}", e.index))
        return "Hovered over [${e.index}] “${e.label}”."
    }

    override suspend fun evaluate(js: String): String { act(Act.Js(js)); return evalResult(js) }

    override suspend fun readPage(mode: String, selector: String?, maxChars: Int): String =
        readResult(mode, selector).take(maxChars)

    override suspend fun screenshot(fullPage: Boolean): String? = null

    override suspend fun waitForText(text: String, gone: Boolean, timeoutMs: Long): String {
        val present = page.elements.any { it.label.contains(text, true) } || page.text.contains(text, true)
        return if (present != gone) "“$text” ${if (gone) "is gone" else "appeared"}."
        else "Waited ${timeoutMs / 1000}s; “$text” ${if (gone) "is still there" else "did not appear"}."
    }

    override suspend fun waitForSelector(selector: String, gone: Boolean, timeoutMs: Long): String {
        val present = page.elements.any { it.selector == selector }
        return if (present != gone) "$selector ${if (gone) "is gone" else "appeared"}."
        else "Waited ${timeoutMs / 1000}s; $selector ${if (gone) "is still there" else "did not appear"}."
    }

    override suspend fun waitForUrl(fragment: String, timeoutMs: Long): String =
        if (page.url.contains(fragment, true)) "The URL now contains “$fragment”."
        else "Waited ${timeoutMs / 1000}s; the URL still does not contain “$fragment”."

    override suspend fun textVisible(text: String): Boolean =
        page.elements.any { it.label.contains(text, ignoreCase = true) } || page.text.contains(text, true)

    override fun url(): String = page.url
    override fun title(): String = page.title
    override fun tabs(): List<TabInfo> = tabsList

    override suspend fun switchTab(id: Int): String { act(Act.Tab("switch", id)); return "Switched to tab $id." }
    override suspend fun closeTab(id: Int): String { act(Act.Tab("close", id)); return "Closed tab $id." }
    override suspend fun newTab(url: String?): String { act(Act.Tab("new")); return url?.let { navigate(it) } ?: "Opened a blank tab." }

    override suspend fun uploadFile(e: PageElement, absolutePath: String): String {
        act(Act.Upload(e.label, absolutePath))
        return "Gave ${absolutePath.substringAfterLast('/')} to [${e.index}] “${e.label}”."
    }

    override fun downloads(): List<DownloadInfo> = downloadsList

    override fun readDownload(name: String, maxChars: Int): String =
        downloadTexts[name]?.take(maxChars) ?: "No download named “$name”."

    override suspend fun focusForUser(e: PageElement?) = Unit
    override fun answerHttpAuth(proceed: Boolean, user: String?, password: String?) = Unit
    override fun status(): String = "Browser: 1 tab\nURL: ${page.url}\nTitle: ${page.title}"
}

/** A model that answers each turn from a script and remembers what it was shown. */
internal class ScriptedModel(private vararg val turns: (AgentRequest) -> Flow<StreamEvent>) : AgentModelClient {

    /** Per turn: everything the model received since its own last message (task or tool results). */
    val seen = mutableListOf<String>()
    private var next = 0

    override suspend fun stream(request: AgentRequest): Flow<StreamEvent> {
        seen += request.messages.takeLastWhile { it.role != "assistant" }.joinToString("\n") { it.content.orEmpty() }
        val turn = turns.getOrNull(next++) ?: return flowOf(StreamEvent.Started, StreamEvent.ContentDelta("Done."), StreamEvent.Completed)
        return turn(request)
    }

    companion object {
        /** One turn that calls these tools, as `name to jsonArguments`. */
        fun calls(vararg calls: Pair<String, String>): (AgentRequest) -> Flow<StreamEvent> = {
            flowOf(
                StreamEvent.Started,
                StreamEvent.ToolCalls(calls.mapIndexed { i, (name, args) -> ToolCallResponse("call_$i", "function", FunctionCall(name, args)) }),
                StreamEvent.FinishReason("tool_calls"),
                StreamEvent.Completed,
            )
        }

        /** One turn that answers in plain text, with no tool call. */
        fun text(reply: String): (AgentRequest) -> Flow<StreamEvent> = {
            flowOf(StreamEvent.Started, StreamEvent.ContentDelta(reply), StreamEvent.FinishReason("stop"), StreamEvent.Completed)
        }

        val model = AiModel(id = "bench", name = "Bench", provider = AiProvider.CUSTOM, supportsToolCalls = true)
    }
}

internal object BrowserBench {

    fun browser(scope: TestScope, start: PageState) = FakeBrowser({ scope.testScheduler.currentTime }, start)

    /** Answers every question card with [answer]; the questions asked go into the returned list. */
    fun answerQuestions(scope: TestScope, answer: String): List<String> {
        val asked = mutableListOf<String>()
        scope.backgroundScope.launch {
            BrowserAgent.state.collect { s ->
                val q = s.question?.text
                if (q != null) {
                    if (asked.lastOrNull() != q) asked += q
                    BrowserAgent.answer(answer)
                }
            }
        }
        return asked
    }

    /** Answers every approval prompt with [allow]; the prompts asked go into the returned list. */
    fun answerApprovals(scope: TestScope, allow: Boolean): List<String> {
        val asked = mutableListOf<String>()
        scope.backgroundScope.launch {
            BrowserAgent.state.collect { s ->
                val q = s.approval
                if (q != null) {
                    if (asked.lastOrNull() != q) asked += q
                    BrowserAgent.approve(allow)
                }
            }
        }
        return asked
    }

    /**
     * Answers every secret request: with [secret] through the card, or as
     * "typed in the page" when [secret] is null. Requests seen go into the list.
     */
    fun answerSecrets(scope: TestScope, secret: String?): List<String> {
        val asked = mutableListOf<String>()
        scope.backgroundScope.launch {
            BrowserAgent.state.collect { s ->
                val q = s.secretRequest?.purpose
                if (q != null) {
                    if (asked.lastOrNull() != q) asked += q
                    if (secret != null) BrowserAgent.provideSecret(secret) else BrowserAgent.secretEnteredDirectly()
                }
            }
        }
        return asked
    }

    suspend fun play(
        browser: FakeBrowser,
        model: ScriptedModel,
        task: String,
        settings: BrowserAgent.Settings = BrowserAgent.Settings(),
        startUrl: String? = null,
    ): BrowserAgent.State =
        BrowserAgent.runScenario(browser, task, ScriptedModel.model, model, settings, startUrl = startUrl)
}
