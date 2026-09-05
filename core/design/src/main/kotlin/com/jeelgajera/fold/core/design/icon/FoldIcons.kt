package com.jeelgajera.fold.core.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * FOLD's icon set, kept as SVG path data on a 24x24 viewport exactly as it is
 * written in the design source.
 *
 * Storing the strings and parsing them once beats hand-transcribing them into
 * `ImageVector` builders: a path that is byte-identical to the design cannot
 * drift from it.
 *
 * Every icon here is line art with an open stroke. Weight, not fill, marks the
 * active state -- 2dp when selected, 1.6dp otherwise.
 */
object FoldIconPaths {
    val Home = listOf("M4 20V9.5L12 4l8 5.5V20", "M9.5 20v-6h5v6")
    val Browse = listOf("M3 6h6l2 2.5h10V19H3z", "M3 11h18")
    val Share = listOf("M12 3v11", "M8 6.5 12 3l4 3.5", "M4.5 15v4.5h15V15")
    val Vault = listOf("M4 10h16v10H4z", "M8 10V7.5a4 4 0 0 1 8 0V10")
    val VaultFilled = listOf("M13.4 15a1.4 1.4 0 1 1-2.8 0 1.4 1.4 0 1 1 2.8 0z")
    val Search = listOf("M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z", "M16.5 16.5 21 21")
    val Close = listOf("M5 5l14 14", "M19 5 5 19")
    val Fingerprint = listOf(
        "M12 4c-3 0-5 2-5 5v6",
        "M12 4c3 0 5 2 5 5v3",
        "M9.5 20c-1-1.5-1.5-3-1.5-5",
        "M14.5 20c1-2 1.5-4 1.5-6.5",
    )
}

/**
 * Draws one of [FoldIconPaths] at [size].
 *
 * The icon is decorative by default: FOLD's icon-only controls put the content
 * description on the button, not on the glyph, so a screen reader announces the
 * action once rather than twice.
 */
@Composable
fun FoldIcon(
    paths: List<String>,
    modifier: Modifier = Modifier,
    filledPaths: List<String> = emptyList(),
    size: Dp = 19.dp,
    strokeWidth: Float = 1.6f,
    tint: Color = FoldTheme.colors.onBackground,
) {
    val stroked = remember(paths) { paths.map { it.toComposePath() } }
    val filled = remember(filledPaths) { filledPaths.map { it.toComposePath() } }

    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / VIEWPORT
        scale(scale = factor, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            stroked.forEach { drawPath(it, tint, style = style) }
            filled.forEach { drawPath(it, tint) }
        }
    }
}

private const val VIEWPORT = 24f

private fun String.toComposePath(): Path = PathParser().parsePathString(this).toPath()
