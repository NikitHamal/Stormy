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
import com.oro.app.device.DeviceAgent
import com.oro.app.device.DeviceAgent.StepKind
import com.oro.app.device.DeviceAgent.StepStatus
import com.oro.app.device.overlay.IslandActions

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

/** The island's controls, doing nothing. */
internal val NoIslandActions = IslandActions(
    onPauseToggle = {},
    onStop = {},
    onApprove = {},
    onAnswer = {},
    onOpenOro = {},
    onTyping = {},
)

internal val ShotModel = AiModel(
    id = "mercury:mercury-3",
    name = "Mercury 3",
    provider = AiProvider.MERCURY,
    supportsToolCalls = false,
)

/** Made-up device runs, in the shapes DeviceAgent really produces. */
internal object Runs {
    private val now = System.currentTimeMillis()
    private val started = now - 125_000

    const val TASK = "Play lofi hip hop radio on YouTube and skip the ads"

    private fun step(
        id: Long,
        kind: StepKind,
        tool: String?,
        title: String,
        status: StepStatus = StepStatus.DONE,
        detail: String = "",
        changed: Boolean? = true,
    ) = DeviceAgent.Step(
        id = id,
        kind = kind,
        tool = tool,
        title = title,
        detail = detail,
        status = status,
        app = "YouTube",
        screenChanged = if (kind == StepKind.ACTION) changed else null,
        startedAt = started + id * 9_000,
        endedAt = if (status == StepStatus.RUNNING || status == StepStatus.WAITING) null else started + id * 9_000 + 900,
    )

    private val done = listOf(
        step(1, StepKind.THOUGHT, null, "YouTube's search page is the fastest way in, so I'll open it with the query."),
        step(2, StepKind.ACTION, "open_url", "Open youtube.com/results?search_query=lofi+hip+hop+radio"),
        step(3, StepKind.SYSTEM, "wait_for", "Wait for “lofi hip hop radio”"),
        step(4, StepKind.THOUGHT, null, "The **live stream** is the first result. Opening it."),
        step(5, StepKind.ACTION, "tap_text", "Tap “lofi hip hop radio - beats to relax/study to”"),
        step(6, StepKind.SYSTEM, null, "Skipped a YouTube ad"),
        step(7, StepKind.ACTION, "tap", "Tap “Full screen”", changed = false),
        step(8, StepKind.ACTION, "media", "Play", status = StepStatus.FAILED, detail = "No app answered the media key."),
    )

    private fun base(steps: List<DeviceAgent.Step>, actions: Int) = DeviceAgent.State(
        serviceConnected = true,
        running = true,
        task = TASK,
        modelName = ShotModel.name,
        status = "Deciding",
        steps = steps,
        currentApp = "YouTube",
        actions = actions,
        startedAt = started,
    )

    /** Mid-run: a long step in flight, so the pill has to ellipsize. */
    fun working() = base(
        done + step(9, StepKind.ACTION, "tap_text", "Tap “Play (k)” on the lofi hip hop radio live player", status = StepStatus.RUNNING),
        actions = 6,
    )

    fun paused() = base(done, actions = 6).copy(paused = true, status = "Paused")

    fun approval() = base(
        done + step(9, StepKind.APPROVAL, null, "Needs your OK: send “Running 10 min late, start without me” in Messages", status = StepStatus.WAITING),
        actions = 6,
    ).copy(
        approval = "send “Running 10 min late, start without me” in Messages",
        status = "Waiting for your approval",
        currentApp = "Messages",
    )

    fun question() = base(
        done + step(9, StepKind.QUESTION, "ask_user", "Which account should I post from?", status = StepStatus.WAITING),
        actions = 6,
    ).copy(
        question = DeviceAgent.Question("Which account should I post from?", listOf("@nikit", "@oro.studio", "Ask me later")),
        status = "Waiting for your answer",
    )

    fun finished() = base(done.map { if (it.status == StepStatus.FAILED) it.copy(status = StepStatus.DONE, detail = "") else it }, actions = 6).copy(
        running = false,
        success = true,
        status = "Done",
        summary = "Playing **lofi hip hop radio - beats to relax/study to** on YouTube, full screen. " +
            "One ad was skipped on the way.",
        notes = listOf("Stream: lofi hip hop radio - beats to relax/study to", "Channel: Lofi Girl, live"),
        endedAt = now - 2_000,
    )

    fun failed() = base(done, actions = 6).copy(
        running = false,
        success = false,
        status = "Stopped",
        error = "The Play Store asked for a Google sign-in, so I stopped there. Sign in once, then run the task again.",
        endedAt = now - 2_000,
    )

    /** Never started: the screen shows the welcome. */
    fun idle() = DeviceAgent.State(serviceConnected = true)
}

/**
 * Stand-in for whatever app Orobi is driving, drawn in neutral greys with the
 * platform font, so the island is seen over something that is not Oro.
 */
@Composable
internal fun FakeVideoApp(modifier: Modifier = Modifier) {
    val ink = Color(0xFF1F1F1F)
    val soft = Color(0xFFE8E8E8)
    val faint = Color(0xFFF3F3F3)
    val text = TextStyle(fontFamily = FontFamily.SansSerif, color = ink)
    Column(modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text("9:41", style = text.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(width = 22.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(ink))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 30.dp, height = 21.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)))
            Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text("Videos", style = text.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(22.dp).clip(CircleShape).background(soft))
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF8D6E63)))
        }
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF263238)))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Text(
                "lofi hip hop radio - beats to relax/study to",
                style = text.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            )
            androidx.compose.material3.Text("41K watching now", style = text.copy(fontSize = 13.sp, color = Color(0xFF606060)))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { Box(Modifier.size(width = 78.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(faint)) }
            }
        }
        repeat(3) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(10.dp)).background(soft))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(4.dp)).background(soft))
                    Box(Modifier.width(120.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(faint))
                }
            }
        }
    }
}
