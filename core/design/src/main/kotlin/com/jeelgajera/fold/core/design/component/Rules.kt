package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldTheme

/** A 1dp rule. Separates rows inside one group. */
@Composable
fun FoldHairline(modifier: Modifier = Modifier, color: Color = FoldTheme.colors.divider) {
    Box(
        modifier
            .fillMaxWidth()
            .height(FoldRules.hairline)
            .background(color),
    )
}

/** A 2dp rule. Separates one group of rows from the next. */
@Composable
fun FoldSectionRule(modifier: Modifier = Modifier, color: Color = FoldTheme.colors.dividerStrong) {
    Box(
        modifier
            .fillMaxWidth()
            .height(FoldRules.sectionDivider)
            .background(color),
    )
}

/**
 * Draws a rule along the bottom edge without adding a layout node.
 *
 * Rows use this rather than a trailing [FoldHairline] so a list of 200 files
 * costs 200 nodes instead of 400.
 */
fun Modifier.bottomRule(color: Color, width: Dp = FoldRules.hairline): Modifier =
    drawBehind {
        val strokePx = width.toPx()
        drawRect(
            color = color,
            topLeft = Offset(0f, size.height - strokePx),
            size = androidx.compose.ui.geometry.Size(size.width, strokePx),
        )
    }

/** Draws a rule along the right edge. Used by the grid tiles and the category cells. */
fun Modifier.endRule(color: Color, width: Dp = FoldRules.hairline): Modifier =
    drawBehind {
        val strokePx = width.toPx()
        drawRect(
            color = color,
            topLeft = Offset(size.width - strokePx, 0f),
            size = androidx.compose.ui.geometry.Size(strokePx, size.height),
        )
    }

/** The accent edge on a callout: a 2dp red bar down the leading side. */
fun Modifier.accentEdge(color: Color, width: Dp = FoldRules.accentEdge): Modifier =
    drawBehind {
        drawRect(
            color = color,
            topLeft = Offset.Zero,
            size = androidx.compose.ui.geometry.Size(width.toPx(), size.height),
        )
    }
