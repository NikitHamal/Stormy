package com.codex.stormy.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.codex.stormy.ui.brand.OroBrand
import com.codex.stormy.ui.brand.OrobiColors
import com.codex.stormy.ui.brand.OrobiMotion
import com.codex.stormy.ui.brand.OrobiPose
import com.codex.stormy.ui.brand.drawOrobi
import com.codex.stormy.ui.brand.drawSparkle
import com.codex.stormy.ui.brand.inViewBox
import com.codex.stormy.ui.brand.rememberAnimationClock
import com.codex.stormy.ui.brand.rememberReducedMotion
import com.codex.stormy.ui.theme.PoppinsFontFamily
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * "Prompt to page": a prompt is typed, Orobi swallows it, thinks (turning into
 * its own loader), code tokens fly into a phone, the page assembles block by
 * block, and Orobi hops when it goes live.
 *
 * The whole scene is one 11-second timeline evaluated per frame, in a fixed
 * 400x300 design space. Each element is a pure function of time, so layers
 * that must stay in sync -- the prompt shrinking *into* the mascot, tokens
 * landing exactly as their block appears -- cannot drift apart.
 */
@Composable
fun PromptToPageHero(
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    // Restart from the top every time the page comes back into view: the story
    // only makes sense from the beginning.
    val clock = rememberAnimationClock(running = playing && !reduced, restartKey = playing)

    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminanceApprox() < 0.5f
    val palette = HeroPalette(
        on = scheme.onSurface,
        surface = scheme.surfaceContainerLowest.takeIf { !dark } ?: scheme.surfaceContainerLow,
        background = scheme.background,
        surfaceHigh = scheme.surfaceContainerHighest,
        accent = scheme.primary,
        green = if (dark) OroBrand.GreenDark else OroBrand.GreenLight,
    )

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val texts = remember(measurer, density, palette.on) {
        val sans = TextStyle(
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = with(density) { 13f.toSp() },
            color = palette.on,
        )
        val mono = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            fontSize = with(density) { 10.5f.toSp() },
            color = palette.on,
        )
        HeroTexts(
            prompt = measurer.measure("Add a pricing section", sans),
            working = measurer.measure("Writing pricing.html", sans),
            live = measurer.measure("Pricing page is live", sans),
            tokens = Tokens.map { measurer.measure(it.label, mono) },
        )
    }
    val scratch = remember { HeroScratch() }
    val orobiColors = OrobiColors(shadow = scheme.onSurface)

    Canvas(
        modifier
            .aspectRatio(400f / 300f)
            .semantics {
                contentDescription =
                    "A prompt becomes a finished mobile page while Orobi thinks"
            }
    ) {
        val t = if (reduced) 10f else clock.value.mod(Duration)
        inViewBox(Rect(0f, 0f, 400f, 300f), Rect(Offset.Zero, size)) {
            drawScene(t, palette, texts, scratch, orobiColors)
        }
    }
}

private const val Duration = 11f

private data class HeroPalette(
    val on: Color,
    val surface: Color,
    val background: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val green: Color,
) {
    val hairline = on.copy(alpha = 0.18f)
}

private class HeroTexts(
    val prompt: TextLayoutResult,
    val working: TextLayoutResult,
    val live: TextLayoutResult,
    val tokens: List<TextLayoutResult>,
)

private class HeroScratch {
    val path = Path()
    val sparkle = Path()
    val segment = Path()
    val phoneClip = Path().apply {
        addRoundRect(RoundRect(252f, 24f, 382f, 276f, CornerRadius(24f)))
    }
    val flow = Path().apply {
        moveTo(206f, 160f)
        cubicTo(230f, 158f, 238f, 142f, 252f, 134f)
    }
    val check = Path().apply {
        moveTo(31.5f, 33.2f)
        relativeLineTo(2.6f, 2.6f)
        relativeLineTo(5f, -5.4f)
    }
    val checkMeasure = PathMeasure().apply { setPath(check, false) }
    val dots: List<Offset> = buildList {
        var y = 1f
        while (y < 300f) {
            var x = 1f
            while (x < 400f) {
                add(Offset(x, y)); x += 18f
            }
            y += 18f
        }
    }
}

private data class Token(val label: String, val start: Float, val dx: Float, val dy: Float, val width: Float)

private val Tokens = listOf(
    Token("<nav>", 2.9f, 111f, -118f, 48f),
    Token("<h1>", 3.4f, 94f, -86f, 41.6f),
    Token("class", 3.8f, 93f, -62f, 48f),
    Token("grid", 4.1f, 111f, -10f, 41.6f),
    Token("{ }", 4.5f, 111f, 39f, 35.2f),
    Token("$12", 4.9f, 111f, 78f, 35.2f),
)

/** Per-element progress helpers, mirroring the spec's --s / --d variables. */
private class Beat(private val t: Float) {
    fun p(start: Float, duration: Float = .6f) = ((t - start) / duration).coerceIn(0f, 1f)
    fun e(start: Float, duration: Float = .6f) = OrobiMotion.easeOut(p(start, duration))
    fun b(start: Float, duration: Float = .6f) = OrobiMotion.backOut(p(start, duration))
}

private fun DrawScope.drawScene(
    t: Float,
    c: HeroPalette,
    texts: HeroTexts,
    s: HeroScratch,
    orobiColors: OrobiColors,
) {
    val beat = Beat(t)
    val fadeOut = ((Duration - t) / .7f).coerceIn(0f, 1f)

    // --- The mascot's own timeline ---------------------------------------
    val m = OrobiMotion
    val lean = m.smoothstep(t, .5f, .6f) - m.smoothstep(t, 2.3f, .5f)
    val build = m.smoothstep(t, 3f, .6f) - m.smoothstep(t, 8f, .6f)
    val typed = m.ramp(t, .9f, 1.2f)
    val think = m.ramp(t, 2.6f, .3f) - m.ramp(t, 6.6f, .4f)
    val morph = m.smoothstep(t, 2.75f, .6f) - m.smoothstep(t, 6.3f, .6f)
    val swallow = m.bump(t, 2.3f, .55f)
    val anticipate = m.bump(t, 7f, .45f)
    val hop = m.bump(t, 7.29f, .67f)
    val land = m.bump(t, 7.8f, .48f)
    val happy = m.ramp(t, 7.35f, .13f) - m.ramp(t, 7.9f, .16f)
    val blink = maxOf(
        0f,
        1f - (t - 2.15f) * (t - 2.15f) * 260f,
        maxOf(1f - (t - 4.4f) * (t - 4.4f) * 260f, 1f - (t - 9.6f) * (t - 9.6f) * 260f),
    )
    val done = m.ramp(t, 6.8f, .4f)
    val squash = swallow * .06f + land * .12f + anticipate * .1f
    val breath = .012f * sin(t * 1.9f)
    // Eyes follow the story: to the prompt while it is typed, then to the phone.
    val lookX = lean * -12f + lean * typed * 20f + build * 26f
    val lookY = lean * -11f + build * -5f

    val pose = OrobiPose(
        lift = hop * 160f,
        tilt = lean * -1.6f + build * 1.6f,
        stretchX = 1f + squash + breath,
        stretchY = 1f - squash * 1.1f - breath,
        lookX = lookX,
        lookY = lookY,
        eyeOpen = max(.08f, 1f - .72f * happy - .9f * blink),
        bandShift = swallow * 50f,
        shadowAlpha = ((.16f - .09f * hop) * (1f - morph)).coerceAtLeast(0f),
        shadowScale = 1f - .35f * hop,
        morph = morph,
        loaderPhase = (t / OrobiMotion.LoaderPeriod).mod(1f),
    )

    // --- Field -----------------------------------------------------------
    drawRect(lerp(c.surface, c.on, .05f), size = Size(400f, 300f))
    drawPoints(s.dots, PointMode.Points, c.on.copy(alpha = .18f), strokeWidth = 2f, cap = StrokeCap.Round)

    // Thought line from Orobi to the phone, only while thinking.
    if (think > 0f) {
        drawPath(
            s.flow,
            c.accent,
            alpha = think * .5f,
            style = Stroke(
                width = 1.6f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 7f), 16f - (t * 20f).mod(16f)),
            ),
        )
    }

    drawOrobi(pose, orobiColors, s.path, bounds = Rect(16f, 108f, 206f, 238f))

    // --- Phone -----------------------------------------------------------
    val winE = beat.e(.5f, .9f)
    val winB = beat.b(.5f, .9f)
    val winA = winE * fadeOut
    if (winA > 0f) {
        withTransform({
            translate(top = (1f - winB) * 26f)
            val k = .94f + .06f * winB
            scale(k, k, pivot = Offset(317f, 155f))
        }) {
            drawRoundRect(c.on, Offset(250f, 32f), Size(134f, 256f), CornerRadius(26f), alpha = .07f * winA)
            drawRoundRect(c.background, Offset(250f, 22f), Size(134f, 256f), CornerRadius(26f), alpha = winA)
            drawRoundRect(
                c.on.copy(alpha = .85f), Offset(250f, 22f), Size(134f, 256f), CornerRadius(26f),
                style = Stroke(3f), alpha = winA,
            )
            clipPath(s.phoneClip) { drawPage(beat, c, winA) }
            drawRoundRect(c.on.copy(alpha = .85f), Offset(301f, 30f), Size(32f, 7f), CornerRadius(3.5f), alpha = winA)
            drawRoundRect(c.surfaceHigh, Offset(317f, 267f), Size(34f, 3f), CornerRadius(1.5f), alpha = winA)
        }
    }

    // --- Tokens, flying from Orobi into the page -------------------------
    Tokens.forEachIndexed { i, token ->
        val p = beat.p(token.start, .9f)
        val a = minOf(1f, p * 8f, (1f - p) * 5f) * fadeOut
        if (a <= 0f) return@forEachIndexed
        val arc = sin(p * PI.toFloat())
        val x = 204f + OrobiMotion.easeOut(p) * token.dx
        val y = 172f + p * token.dy - arc * 26f
        val k = .7f + .4f * arc
        scale(k, k, pivot = Offset(x, y)) {
            val half = token.width / 2f
            drawRoundRect(c.surface, Offset(x - half, y - 9f), Size(token.width, 18f), CornerRadius(9f), alpha = a)
            drawRoundRect(
                c.hairline, Offset(x - half, y - 9f), Size(token.width, 18f), CornerRadius(9f),
                style = Stroke(1.2f), alpha = a,
            )
            val label = texts.tokens[i]
            drawText(
                label,
                topLeft = Offset(x - label.size.width / 2f, y + 3.6f - label.firstBaseline),
                alpha = a,
            )
        }
    }

    // --- Celebration sparkles --------------------------------------------
    val sparkA = m.ramp(t, 7.45f, .55f)
    val sparkB = m.ramp(t, 7.5f, .55f)
    drawSparkle(Offset(30f, 122f), 11f, OroBrand.Magenta, s.sparkle, sin(sparkA * PI.toFloat()), sparkA * 90f)
    drawSparkle(Offset(196f, 114f), 9f, OroBrand.Apricot, s.sparkle, sin(sparkB * PI.toFloat()), sparkB * 90f)
    drawSparkle(Offset(186f, 208f), 7f, OroBrand.Magenta, s.sparkle, sin(sparkA * PI.toFloat()), sparkA * 90f)
    drawSparkle(Offset(36f, 216f), 6f, OroBrand.Apricot, s.sparkle, sin(sparkB * PI.toFloat()), sparkB * 90f)

    // --- Prompt pill: typed, then swallowed ------------------------------
    val gulp = beat.e(2.4f, .55f)
    val pillA = (1f - gulp * gulp * gulp) * beat.e(.3f, .7f)
    if (pillA > 0f) {
        withTransform({
            translate(gulp * -12f, gulp * 143f + (1f - beat.b(.3f, .7f)) * 14f)
            val k = 1f - .86f * gulp
            scale(k, k, pivot = Offset(123f, 33f))
        }) {
            drawPill(214f, c, pillA)
            drawSparkle(Offset(35f, 33f), 7f, c.accent, s.sparkle, alpha = pillA)
            drawText(texts.prompt, topLeft = Offset(52f, 37.4f - texts.prompt.firstBaseline), alpha = pillA)
            // Typing is a cover sliding off the text, with a caret riding its edge.
            val p = beat.p(.9f, 1.2f)
            if (p < 1f) {
                drawRect(c.surface, Offset(50f + 176f * p, 21f), Size(176f * (1f - p), 24f), alpha = pillA)
            }
            val caretA = minOf(1f, p * 40f, (1f - p) * 40f)
            if (caretA > 0f) {
                drawRoundRect(c.accent, Offset(50f + 176f * p, 22f), Size(2f, 22f), CornerRadius(1f), alpha = pillA * caretA)
            }
        }
    }

    // --- Status pill: working --------------------------------------------
    val workA = beat.e(2.7f) * fadeOut * (1f - done)
    if (workA > 0f) {
        translate(top = (1f - beat.b(2.7f)) * 10f) {
            drawPill(190f, c, workA)
            drawArc(
                c.accent,
                startAngle = (t / .9f).mod(1f) * 360f,
                sweepAngle = 190f,
                useCenter = false,
                topLeft = Offset(29f, 27f),
                size = Size(12f, 12f),
                style = Stroke(2.4f, cap = StrokeCap.Round),
                alpha = workA,
            )
            drawText(texts.working, topLeft = Offset(52f, 37.4f - texts.working.firstBaseline), alpha = workA)
        }
    }

    // --- Status pill: live ----------------------------------------------
    val liveA = beat.e(7.2f) * fadeOut
    if (liveA > 0f) {
        translate(top = (1f - beat.b(7.2f)) * 10f) {
            drawPill(202f, c, liveA)
            drawCircle(c.green, 8f, Offset(35f, 33f), alpha = liveA)
            val draw = m.ramp(t, 7.7f, .4f)
            if (draw > 0f) {
                s.segment.reset()
                s.checkMeasure.getSegment(0f, s.checkMeasure.length * draw, s.segment, true)
                drawPath(
                    s.segment, c.background, alpha = liveA,
                    style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            drawText(texts.live, topLeft = Offset(52f, 37.4f - texts.live.firstBaseline), alpha = liveA)
        }
    }
}

private fun DrawScope.drawPill(width: Float, c: HeroPalette, alpha: Float) {
    drawRoundRect(c.surface, Offset(16f, 16f), Size(width, 34f), CornerRadius(17f), alpha = alpha)
    drawRoundRect(c.hairline, Offset(16f, 16f), Size(width, 34f), CornerRadius(17f), style = Stroke(1.2f), alpha = alpha)
}

/** The page being built inside the phone. Blocks land in reading order. */
private fun DrawScope.drawPage(beat: Beat, c: HeroPalette, parentAlpha: Float) {
    // Nav bar.
    run {
        val a = beat.e(3.4f, .5f) * parentAlpha
        if (a <= 0f) return@run
        translate(top = (1f - beat.b(3.4f, .5f)) * 10f) {
            drawRoundRect(c.accent, Offset(262f, 48f), Size(28f, 8f), CornerRadius(4f), alpha = a)
            drawRoundRect(c.surfaceHigh, Offset(356f, 47f), Size(16f, 10f), CornerRadius(5f), alpha = a)
        }
    }

    // Hero block, with its lines writing themselves left to right.
    run {
        val a = beat.e(3.9f) * parentAlpha
        if (a <= 0f) return@run
        translate(top = (1f - beat.b(3.9f)) * 10f) {
            drawRoundRect(lerp(c.surface, c.accent, .09f), Offset(262f, 66f), Size(110f, 68f), CornerRadius(12f), alpha = a)
            reveal(beat.e(4.1f, .5f), Offset(272f, 77f), Size(84f, 8f), 4f, c.on, .85f * a)
            reveal(beat.e(4.25f, .5f), Offset(272f, 90f), Size(60f, 8f), 4f, c.on, .85f * a)
            reveal(beat.e(4.4f, .5f), Offset(272f, 106f), Size(72f, 4f), 2f, c.on, .3f * a)
            reveal(beat.e(4.55f, .4f), Offset(272f, 116f), Size(42f, 12f), 6f, c.accent, a)
        }
    }

    // Three pricing cards; the middle one is the recommended plan.
    drawCard(beat, 4.6f, 142f, 40f, hot = false, c, parentAlpha)
    drawCard(beat, 5f, 190f, 46f, hot = true, c, parentAlpha)
    drawCard(beat, 5.4f, 244f, 40f, hot = false, c, parentAlpha)
}

private fun DrawScope.reveal(progress: Float, at: Offset, size: Size, radius: Float, color: Color, alpha: Float) {
    if (progress <= 0f || alpha <= 0f) return
    drawRoundRect(color, at, Size(size.width * progress, size.height), CornerRadius(min(radius, size.width * progress / 2f)), alpha = alpha)
}

private fun DrawScope.drawCard(
    beat: Beat,
    start: Float,
    top: Float,
    height: Float,
    hot: Boolean,
    c: HeroPalette,
    parentAlpha: Float,
) {
    val a = beat.e(start, .55f) * parentAlpha
    if (a <= 0f) return
    val b = beat.b(start, .55f)
    withTransform({
        translate(top = (1f - b) * 16f)
        val k = .85f + .15f * b
        scale(k, k, pivot = Offset(317f, top + height))
    }) {
        val fill = if (hot) lerp(c.surface, c.accent, .08f) else c.surface
        drawRoundRect(fill, Offset(262f, top), Size(110f, height), CornerRadius(10f), alpha = a)
        drawRoundRect(
            if (hot) c.accent else c.hairline,
            Offset(262f, top), Size(110f, height), CornerRadius(10f),
            style = Stroke(if (hot) 1.5f else 1f), alpha = a,
        )
        val inset = if (hot) 14f else 11f
        drawRoundRect(c.on, Offset(272f, top + inset), Size(32f, 5f), CornerRadius(2.5f), alpha = .2f * a)
        drawRoundRect(c.on, Offset(272f, top + inset + 10f), Size(38f, 9f), CornerRadius(4.5f), alpha = .8f * a)
        drawRoundRect(
            if (hot) c.accent else c.surfaceHigh,
            Offset(330f, top + inset + 2f), Size(32f, 14f), CornerRadius(7f), alpha = a,
        )
        if (hot) {
            drawRoundRect(c.accent, Offset(318f, top - 5f), Size(32f, 10f), CornerRadius(5f), alpha = a)
        }
    }
}

/** Cheap relative luminance, good enough to tell a light scheme from a dark one. */
internal fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
