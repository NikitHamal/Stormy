package com.oro.app.screenshots

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.oro.app.OroApplication
import com.oro.app.data.ai.AiModel
import com.oro.app.data.ai.AiProvider
import com.oro.app.browser.BrowserAgent
import com.oro.app.browser.BrowserAgent.StepKind
import com.oro.app.browser.BrowserAgent.StepStatus

/**
 * Shared set-up for the JVM screenshots (Robolectric native graphics +
 * Roborazzi). Run with `./gradlew :app:testDebugUnitTest -Poro.screenshots=true`;
 * images land in app/build/outputs/roborazzi. They are previews of real
 * composables with made-up runs, not golden images: nothing compares them.
 */
internal const val SHOTS_DIR = "build/outputs/roborazzi"

/** A 411x891dp phone at 2x, so a full-screen image is 822x1782 px. */
internal const val PHONE = "w411dp-h891dp-normal-long-notround-any-xhdpi-keyshidden-nonav"

internal const val SHOT_TAG = "shot"

/** Long enough for enter transitions, LaunchedEffects and a list's scroll to settle. */
private const val SETTLE_MS = 1_400L

/** The app Robolectric starts instead of OroApplication, whose onCreate opens the database and WebViews. */
class ShotsApplication : Application()

/**
 * Renders [content] in a [width] x [height] frame, lets it settle on the paused
 * test clock, optionally [interact]s (then settles again), and writes
 * `$SHOTS_DIR/$name.png`.
 *
 * The clock is paused because Orobi's loader and shimmer tick on every frame
 * forever; with auto-advance on, Compose would never be idle. Advancing by
 * hand still runs them, so the image shows a real mid-animation frame.
 */
internal fun ComposeContentTestRule.shoot(
    name: String,
    width: Dp = 411.dp,
    height: Dp = 891.dp,
    interact: (ComposeContentTestRule.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    mainClock.autoAdvance = false
    setContent {
        Box(Modifier.size(width, height).testTag(SHOT_TAG)) { content() }
    }
    mainClock.advanceTimeBy(SETTLE_MS)
    if (interact != null) {
        interact()
        mainClock.advanceTimeBy(SETTLE_MS)
    }
    onNodeWithTag(SHOT_TAG).captureRoboImage("$SHOTS_DIR/$name.png")
}

/**
 * The composer's mic reads a preference through OroApplication.getInstance().
 * Installs an OroApplication that only wraps the test app's context, so that
 * lookup works without running OroApplication.onCreate.
 */
internal object OroAppStub {
    fun install() {
        val field = OroApplication::class.java.getDeclaredField("instance").apply { isAccessible = true }
        if (field.get(null) != null) return
        val stub = OroApplication()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
            .invoke(stub, ApplicationProvider.getApplicationContext<Context>())
        field.set(null, stub)
    }
}

internal val ShotModel = AiModel(
    id = "mercury:mercury-3",
    name = "Mercury 3",
    provider = AiProvider.MERCURY,
    supportsToolCalls = false,
)

/** Made-up browser runs, in the shapes BrowserAgent really produces. */
internal object Runs {
    private val now = System.currentTimeMillis()
    private val started = now - 125_000

    const val TASK = "Find today's top story on Hacker News and summarise it"

    private fun step(
        id: Long,
        kind: StepKind,
        tool: String?,
        title: String,
        status: StepStatus = StepStatus.DONE,
        detail: String = "",
        changed: Boolean? = true,
    ) = BrowserAgent.Step(
        id = id,
        kind = kind,
        tool = tool,
        title = title,
        detail = detail,
        status = status,
        url = "https://news.ycombinator.com/",
        pageChanged = if (kind == StepKind.ACTION) changed else null,
        startedAt = started + id * 9_000,
        endedAt = if (status == StepStatus.RUNNING || status == StepStatus.WAITING) null else started + id * 9_000 + 900,
    )

    private val done = listOf(
        step(1, StepKind.THOUGHT, null, "One search beats five clicks; I'll look it up first."),
        step(2, StepKind.SYSTEM, "web_search", "Search the web for \"Hacker News\""),
        step(3, StepKind.ACTION, "open_url", "Open news.ycombinator.com"),
        step(4, StepKind.THOUGHT, null, "The top story is on the list; I'll read the page text."),
        step(5, StepKind.SYSTEM, "read_page", "Read the page"),
        step(6, StepKind.ACTION, "click_text", "Click \"Show HN: Oro 2.0\"", changed = true),
        step(7, StepKind.SYSTEM, "wait_for", "Wait for \"comments\""),
        step(8, StepKind.ACTION, "scroll", "Scroll down", changed = false),
    )

    private fun base(steps: List<BrowserAgent.Step>, actions: Int) = BrowserAgent.State(
        running = true,
        task = TASK,
        modelName = ShotModel.name,
        status = "Deciding",
        steps = steps,
        currentUrl = "https://news.ycombinator.com/item?id=42",
        currentTitle = "Show HN: Oro 2.0",
        actions = actions,
        startedAt = started,
    )

    /** Mid-run: a long step in flight, so the pill has to ellipsize. */
    fun working() = base(
        done + step(9, StepKind.ACTION, "read_page", "Read the discussion and the linked article", status = StepStatus.RUNNING),
        actions = 6,
    )

    fun paused() = base(done, actions = 6).copy(paused = true, status = "Paused")

    fun approval() = base(
        done + step(9, StepKind.APPROVAL, null, "Needs your OK: click \"Subscribe (\$8/month)\" on news.ycombinator.com", status = StepStatus.WAITING),
        actions = 6,
    ).copy(
        approval = "click \"Subscribe ($8/month)\" on news.ycombinator.com",
        status = "Waiting for your approval",
    )

    fun question() = base(
        done + step(9, StepKind.QUESTION, "ask_user", "Which story should I summarise?", status = StepStatus.WAITING),
        actions = 6,
    ).copy(
        question = BrowserAgent.Question("Which story should I summarise?", listOf("The top one", "The top Show HN", "You decide")),
        status = "Waiting for your answer",
    )

    fun finished() = base(done.map { if (it.status == StepStatus.FAILED) it.copy(status = StepStatus.DONE, detail = "") else it }, actions = 6).copy(
        running = false,
        success = true,
        endReason = BrowserAgent.EndReason.DONE,
        status = "Done",
        summary = "Top story: **Show HN: Oro 2.0** -- a native Android AI IDE. 412 points, 187 comments; " +
            "the discussion is mostly about on-device agents.",
        notes = listOf("Story: Show HN: Oro 2.0", "Points: 412, comments: 187"),
        endedAt = now - 2_000,
    )

    fun failed() = base(done, actions = 6).copy(
        running = false,
        success = false,
        endReason = BrowserAgent.EndReason.FAILED,
        status = "Stopped",
        error = "The site put up a human check and nobody was there to pass it.",
        endedAt = now - 2_000,
    )

    /** The user pressed Stop: a notice, not a failure. */
    fun stopped() = base(done, actions = 6).copy(
        running = false,
        success = false,
        endReason = BrowserAgent.EndReason.STOPPED_BY_USER,
        status = "Stopped",
        error = "Stopped by you.",
        endedAt = now - 2_000,
    )

    /** Never started: the screen shows the welcome. */
    fun idle() = BrowserAgent.State()
}

