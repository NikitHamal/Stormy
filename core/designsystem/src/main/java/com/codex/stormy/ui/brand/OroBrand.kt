package com.codex.stormy.ui.brand

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Oro brand constants.
 *
 * The app chrome is monochrome (see Color.kt); these are the only hues the brand
 * owns, and they belong to Orobi. They are fixed across light and dark on
 * purpose: a mascot that changes colour with the theme stops being a character
 * and becomes an icon.
 */
@Immutable
object OroBrand {
    const val AppName = "Oro"
    const val AssistantName = "Orobi"

    val Peach = Color(0xFFFDDFC6)
    val Magenta = Color(0xFFF800F8)
    val Ink = Color(0xFF1A1F4A)

    /** Warm end of the brand gradient. Used for the second sparkle colour. */
    val Apricot = Color(0xFFFFB38A)

    /** Success, per theme. The one status the brand lets carry a hue. */
    val GreenDark = Color(0xFF3DDC97)
    val GreenLight = Color(0xFF12A150)

    // Provider swatches for the model orbit.
    val Blue = Color(0xFF6C8CFF)
    val Yellow = Color(0xFFF2C94C)
    val Violet = Color(0xFFA78BFA)
    val Nvidia = Color(0xFF76B900)
    val Coral = Color(0xFFFF7A59)
}

/**
 * Orobi's geometry, in the units of the original artwork.
 *
 * The figure is an infinity loop with a head: the same stroke that draws the
 * loading indicator is the body. Everything here is kept in the source
 * coordinate space so motion values (hop heights, eye travel) can be read
 * straight off the design spec without conversion.
 */
object OrobiGeometry {
    /** The artwork's viewBox: x 200..1400, y 400..1220. */
    val ViewBox = Rect(200f, 400f, 1400f, 1220f)

    /** Where the body stands. Squash, stretch and tilt all pivot here. */
    val Foot = Offset(800f, 1180f)

    val LeftEye = Offset(717f, 634f)
    val RightEye = Offset(885f, 634f)
    const val EyeRadiusX = 10f
    const val EyeRadiusY = 13f

    val ShadowCenter = Offset(800f, 1185f)
    const val ShadowRadiusX = 400f
    const val ShadowRadiusY = 24f

    /** The infinity loader's viewBox. */
    val LoopViewBox = Rect(250f, 500f, 1360f, 1150f)

    // Both loop holes are wound opposite to the outline, so the silhouette
    // cuts out correctly under the non-zero rule as well as even-odd. That
    // matters for clipping: a clip path ignores fill type on some renderers
    // (VectorDrawable's <clip-path> among them), and a band clipped to a
    // hole-less body paints over the loops.
    //
    // The artwork's left hole runs the same way as the outline and is reversed
    // here; its right hole already runs the other way and is kept as drawn.
    // Reversing both re-fills the right loop.
    const val BODY =
        "M810,710C760,635 650,503 520,503C370,503 256,660 256,810C256,990 380,1148 510,1148" +
            "C640,1148 740,1050 810,933C880,1040 980,1148 1100,1148C1250,1148 1345,1020 1345,880" +
            "C1345,760 1290,650 1190,590C1205,570 1200,520 1160,495C1140,478 1115,472 1085,472" +
            "C1060,472 1040,480 1030,487L995,520C910,560 850,640 810,710Z" +
            "M743,825C690,905 600,1017 520,1017C440,1017 377,940 377,835C377,730 435,631 520,631" +
            "C600,631 690,745 743,825Z" +
            "M880,817C940,720 1000,650 1030,632C1065,645 1110,650 1160,630C1215,690 1241,770 1241,835" +
            "C1241,940 1180,1017 1115,1017C1030,1017 940,940 880,817Z"

    const val BAND_LEFT =
        "M553,400L553,503C515,555 487,600 477,637L470,660L420,760L390,930" +
            "C400,1010 440,1090 505,1148L505,1200L317,1200L317,925Q317,850 256,815" +
            "L256,803Q317,770 317,690L317,400Z"

    const val BAND_RIGHT =
        "M1020,400L1020,470C1070,520 1105,580 1127,642L1150,670L1190,950L1205,965" +
            "C1195,1050 1160,1110 1097,1148L1097,1200L1285,1200L1285,935Q1285,850 1340,820" +
            "L1340,800Q1285,770 1285,680L1285,400Z"

    /** The centre line of the body: what the loader runs along. */
    const val LOOP =
        "M810 822C880 700 980 568 1105 568C1230 568 1295 700 1295 830C1295 960 1220 1082 1105 1082" +
            "C980 1082 880 940 810 822C740 700 640 567 520 567C400 567 317 700 317 825" +
            "C317 950 400 1082 520 1082C640 1082 740 940 810 822Z"
}

/**
 * Parsed paths, built once per process.
 *
 * Paths are mutable objects, but nothing writes to these after construction;
 * they are only ever read by draw calls on the main thread.
 */
internal object OrobiPaths {
    val body: Path by lazy { parse(OrobiGeometry.BODY) }
    val bandLeft: Path by lazy { parse(OrobiGeometry.BAND_LEFT) }
    val bandRight: Path by lazy { parse(OrobiGeometry.BAND_RIGHT) }
    val loop: Path by lazy { parse(OrobiGeometry.LOOP) }
    val loopMeasure: PathMeasure by lazy { PathMeasure().apply { setPath(loop, false) } }

    // Even-odd as well, matching the artwork's fill-rule: belt and braces
    // against a future edit to the path that gets a winding wrong again.
    private fun parse(d: String): Path =
        PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.EvenOdd }
}

/**
 * A four-point sparkle, the brand's only decorative glyph.
 *
 * Built from four quadratic curves pulled in towards the centre; [pinch] is
 * how far the control points sit from it (0.15 matches the artwork).
 */
fun sparklePath(center: Offset, radius: Float, pinch: Float = 0.15f, into: Path = Path()): Path {
    val (cx, cy) = center
    val k = radius * pinch
    return into.apply {
        reset()
        moveTo(cx, cy - radius)
        quadraticTo(cx + k, cy - k, cx + radius, cy)
        quadraticTo(cx + k, cy + k, cx, cy + radius)
        quadraticTo(cx - k, cy + k, cx - radius, cy)
        quadraticTo(cx - k, cy - k, cx, cy - radius)
        close()
    }
}
