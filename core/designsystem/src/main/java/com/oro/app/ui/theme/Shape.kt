package com.oro.app.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale.
 *
 * Expressive widened the corner scale at the top end: in addition to the
 * classic extraSmall..extraLarge steps it adds `largeIncreased`,
 * `extraLargeIncreased` and `extraExtraLarge`. The "increased" steps exist so a
 * component can grow its radius on interaction without jumping a whole step on
 * the scale, which is what makes the shape transitions read as motion rather
 * than as a different component.
 */
val OroShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)

/**
 * Named corner radii for surfaces that are not Material components (code
 * gutters, inspector swatches, the preview chrome). Kept here so the whole app
 * reads from one scale instead of scattering magic dp values.
 */
object OroCorners {
    val none = RoundedCornerShape(0.dp)
    val tiny = RoundedCornerShape(4.dp)
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(12.dp)
    val large = RoundedCornerShape(16.dp)
    val xLarge = RoundedCornerShape(24.dp)
    val pill = RoundedCornerShape(percent = 50)

    /** Chat bubble with the tail corner tightened on the given side. */
    fun bubble(fromUser: Boolean): CornerBasedShape = if (fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }
}

/**
 * The subset of [MaterialShapes] this app actually uses, as Compose [Shape]s.
 *
 * [MaterialShapes] entries are [RoundedPolygon]s, not Shapes -- they have to go
 * through `toShape()` before a Modifier will take them, and that conversion
 * allocates. Caching per composition keeps avatars and status dots from
 * rebuilding their outline on every recomposition.
 */
object ExpressiveShapes {
    @Composable fun circle(): Shape = rememberPolygonShape(MaterialShapes.Circle)
    @Composable fun square(): Shape = rememberPolygonShape(MaterialShapes.Square)
    @Composable fun slanted(): Shape = rememberPolygonShape(MaterialShapes.Slanted)
    @Composable fun arch(): Shape = rememberPolygonShape(MaterialShapes.Arch)
    @Composable fun pill(): Shape = rememberPolygonShape(MaterialShapes.Pill)
    @Composable fun diamond(): Shape = rememberPolygonShape(MaterialShapes.Diamond)
    @Composable fun pentagon(): Shape = rememberPolygonShape(MaterialShapes.Pentagon)
    @Composable fun gem(): Shape = rememberPolygonShape(MaterialShapes.Gem)
    @Composable fun sunny(): Shape = rememberPolygonShape(MaterialShapes.Sunny)
    @Composable fun verySunny(): Shape = rememberPolygonShape(MaterialShapes.VerySunny)
    @Composable fun cookie4(): Shape = rememberPolygonShape(MaterialShapes.Cookie4Sided)
    @Composable fun cookie6(): Shape = rememberPolygonShape(MaterialShapes.Cookie6Sided)
    @Composable fun cookie9(): Shape = rememberPolygonShape(MaterialShapes.Cookie9Sided)
    @Composable fun clover4(): Shape = rememberPolygonShape(MaterialShapes.Clover4Leaf)
    @Composable fun flower(): Shape = rememberPolygonShape(MaterialShapes.Flower)
    @Composable fun burst(): Shape = rememberPolygonShape(MaterialShapes.Burst)
    @Composable fun softBurst(): Shape = rememberPolygonShape(MaterialShapes.SoftBurst)
    @Composable fun puffy(): Shape = rememberPolygonShape(MaterialShapes.Puffy)
    @Composable fun heart(): Shape = rememberPolygonShape(MaterialShapes.Heart)
    @Composable fun ghostish(): Shape = rememberPolygonShape(MaterialShapes.Ghostish)

    /**
     * A stable shape per index, for decorating a list of items whose identity
     * has no inherent shape (project cards, model entries). Deterministic so a
     * given item keeps its shape across recompositions and restarts.
     */
    @Composable
    fun forIndex(index: Int): Shape {
        val polygons = listOf(
            MaterialShapes.Circle,
            MaterialShapes.Cookie9Sided,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Sunny,
            MaterialShapes.Gem,
            MaterialShapes.Flower,
            MaterialShapes.Pentagon,
            MaterialShapes.SoftBurst
        )
        return rememberPolygonShape(polygons[(index % polygons.size + polygons.size) % polygons.size])
    }
}

/**
 * `toShape()` is itself a composable that caches the converted outline, so this
 * is just a readable alias -- wrapping it in another `remember` would cache a
 * value that is already cached.
 */
@Composable
private fun rememberPolygonShape(polygon: RoundedPolygon): Shape = polygon.toShape()
