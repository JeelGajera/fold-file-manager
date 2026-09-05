package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldDotMatrix
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * One band of a segmented meter.
 *
 * @param weight fraction of the meter this band occupies, 0..1.
 */
data class MeterBand(val weight: Float, val color: Color, val label: String, val readout: String)

/**
 * The storage meter: [FoldDotMatrix.METER_CELLS] hard-edged cells, filled left to
 * right in band order.
 *
 * It is a bar chart drawn as discrete cells, not a rounded progress bar. Cells
 * make the proportion countable, which is the point -- you can see that media is
 * eight cells and apps is six without reading either number.
 */
@Composable
fun DotMeter(
    bands: List<MeterBand>,
    modifier: Modifier = Modifier,
    cells: Int = FoldDotMatrix.METER_CELLS,
    height: Dp = 22.dp,
    emptyColor: Color = FoldTheme.colors.onBackground.copy(alpha = 0.10f),
    contentDescription: String? = null,
) {
    val colors = remainderColors(bands, cells, emptyColor)
    val meterLabel = contentDescription
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (meterLabel != null) {
                    Modifier.clearAndSetSemantics { this.contentDescription = meterLabel }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        colors.forEach { color ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

private fun remainderColors(bands: List<MeterBand>, cells: Int, emptyColor: Color): List<Color> {
    val out = ArrayList<Color>(cells)
    var filled = 0
    bands.forEach { band ->
        val count = (band.weight * cells).toInt().coerceAtLeast(0)
        repeat(count) { if (out.size < cells) out.add(band.color) }
        filled += count
    }
    while (out.size < cells) out.add(emptyColor)
    return out
}

/**
 * A transfer progress bar: [FoldDotMatrix.PROGRESS_CELLS] cells, filled in red.
 *
 * The percentage is always rendered next to it as a numeral -- red alone never
 * carries the state.
 */
@Composable
fun DotProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    cells: Int = FoldDotMatrix.PROGRESS_CELLS,
    height: Dp = 14.dp,
    fillColor: Color = FoldTheme.colors.accent,
    trackColor: Color = FoldTheme.colors.onBackground.copy(alpha = 0.14f),
) {
    val clamped = progress.coerceIn(0f, 1f)
    Row(
        modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(cells) { i ->
            val on = i.toFloat() / cells < clamped
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) fillColor else trackColor),
            )
        }
    }
}

/**
 * A square dot grid. Used at 5x5 for the vault lock mark, 3x3 for a discovered
 * device, and 25x25 for the Glyph Matrix preview.
 *
 * @param on returns the opacity of the cell at (x, y), 0..1.
 */
@Composable
fun DotGrid(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    color: Color = FoldTheme.colors.accent,
    gap: Dp = 2.dp,
    on: (x: Int, y: Int) -> Float,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        repeat(rows) { y ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                repeat(columns) { x ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .alpha(on(x, y).coerceIn(0f, 1f))
                            .background(color),
                    )
                }
            }
        }
    }
}

/**
 * The three-dot density mark on a category cell: how full this category is,
 * counted rather than shaded.
 */
@Composable
fun DotCount(
    filled: Int,
    total: Int = 3,
    modifier: Modifier = Modifier,
    onColor: Color = FoldTheme.colors.accent,
    offColor: Color = FoldTheme.colors.dotIdle,
    cell: Dp = FoldDotMatrix.cell,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(total) { i ->
            Box(Modifier.size(cell).background(if (i < filled) onColor else offColor))
        }
    }
}
