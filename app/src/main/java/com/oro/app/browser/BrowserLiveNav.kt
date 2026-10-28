package com.oro.app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot "show me the live browser" requests.
 *
 * The eye on a `use_browser` tool card (and anything else that wants to show
 * the run) bumps [requests]; the nav graph collects it and opens the browser
 * screen. A counter rather than a boolean so two quick taps navigate twice,
 * and so the collector never mistakes an old request for a new one.
 */
object BrowserLiveNav {

    private val _requests = MutableStateFlow(0L)
    val requests: StateFlow<Long> = _requests.asStateFlow()

    fun request() {
        _requests.value += 1
    }
}
