package com.oro.app.ui.screens.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.oro.app.ui.brand.OroBrand
import com.oro.app.ui.brand.OrobiColors
import com.oro.app.ui.brand.OrobiMotion
import com.oro.app.ui.brand.drawOrobi
import com.oro.app.ui.brand.inViewBox
import com.oro.app.ui.brand.rememberAnimationClock
import com.oro.app.ui.brand.rememberReducedMotion
import com.oro.app.ui.theme.PoppinsFontFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class OrbitNode(val label: String, val angle: Float, val color: Color)

// Same order the providers appear in Settings, starting at twelve o'clock.
private val Nodes = listOf(
    OrbitNode("P", -90f, OroBrand.GreenDark),   // Pollinations
    OrbitNode("G", -30f, OroBrand.Blue),        // Gemini
    OrbitNode("N", 30f, OroBrand.Nvidia),       // NVIDIA NIM
    OrbitNode("Gq", 90f, OroBrand.Coral),       // Groq
    OrbitNode("C", 150f, OroBrand.Yellow),      // Cerebras
    OrbitNode("OR", 210f, OroBrand.Violet),     // OpenRouter
)

/**
 * "Bring any model": providers orbit Orobi, each feeding it along a dotted
 * spoke, while the core sends out a slow ripple. Labels stay upright as the
 * ring turns -- rotating text is the fastest way to make an orbit look cheap.
 */
@Composable
fun ModelOrbit(
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val clock = rememberAnimationClock(running = playing && !reduced)
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labels = remember(measurer, density, scheme.onSurface) {
        val style = TextStyle(
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = with(density) { 11f.toSp() },
            color = scheme.onSurface,
        )
        Nodes.map { measurer.measure(it.label, style) }
    }
    val scratch = remember { Path() }
    val nodeFill = scheme.surfaceContainerLow
    val outline = scheme.outlineVariant
    val orobiColors = OrobiColors(shadow = Color.Transparent)

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Pollinations, Gemini, NVIDIA, Groq, Cerebras and OpenRouter orbiting Orobi"
            }
    ) {
        val t = if (reduced) 0f else clock.value
        inViewBox(Rect(0f, 0f, 220f, 220f), Rect(Offset.Zero, size)) {
            val center = Offset(110f, 110f)

            drawCircle(
                outline, 82f, center,
                style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 6f))),
            )

            // Two ripples half a cycle apart, so one is always on its way out.
            if (!reduced) {
                for (delay in floatArrayOf(0f, 1.2f)) {
                    val q = OrobiMotion.Decelerate.transform(((t - delay) / 2.4f).mod(1f))
                    drawCircle(
                        OroBrand.Magenta, 32f * (1f + 1.1f * q), center,
                        alpha = .6f * (1f - q), style = Stroke(2f),
                    )
                }
            }

            val spin = t / 24f * 360f
            val flow = PathEffect.dashPathEffect(floatArrayOf(2f, 8f), (t * 10f).mod(10f))
            val positions = Nodes.map { node ->
                val rad = (node.angle + spin) * PI.toFloat() / 180f
                Offset(110f + 82f * cos(rad), 110f + 82f * sin(rad))
            }
            positions.forEach { p ->
                drawLine(
                    OroBrand.Magenta, center, p, strokeWidth = 1.5f, alpha = .6f,
                    pathEffect = flow, cap = StrokeCap.Butt,
                )
            }
            Nodes.forEachIndexed { i, node ->
                val p = positions[i]
                drawCircle(nodeFill, 17f, p)
                drawCircle(outline, 17f, p, style = Stroke(1.5f))
                val label = labels[i]
                drawText(
                    label,
                    topLeft = Offset(p.x - label.size.width / 2f, p.y + 4f - label.firstBaseline),
                )
                drawCircle(node.color, 3.5f, Offset(p.x + 12f, p.y - 12f))
            }

            drawCircle(OroBrand.Ink, 32f, center)
            drawOrobi(
                OrobiMotion.idle(t, look = false),
                orobiColors,
                scratch,
                bounds = Rect(85f, 93f, 135f, 127f),
                showShadow = false,
            )
        }
    }
}
