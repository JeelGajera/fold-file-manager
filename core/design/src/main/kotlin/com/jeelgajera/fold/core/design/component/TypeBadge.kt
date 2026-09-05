package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * The square that stands in for a file icon: an outlined box with the extension
 * set in Doto.
 *
 * This is the visual half of FOLD's central claim. A file whose type the app
 * cannot name gets a `?` badge and stays in the list -- it is never hidden, and
 * it is never silently dropped for being unrecognised.
 *
 * The badge is decorative: the row that owns it already announces the file name
 * and type, so the badge itself is removed from the accessibility tree.
 */
@Composable
fun TypeBadge(
    mark: String,
    modifier: Modifier = Modifier,
    size: Dp = FoldSizing.typeBadge,
    borderColor: Color = FoldTheme.colors.onBackground.copy(alpha = 0.35f),
    fillColor: Color = Color.Transparent,
    textColor: Color = FoldTheme.colors.onBackground,
    dashed: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .background(fillColor, RectangleShape)
            .then(
                if (dashed) {
                    Modifier.dashedBorder(borderColor, FoldRules.hairline)
                } else {
                    Modifier.border(FoldRules.hairline, borderColor, RectangleShape)
                }
            )
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mark,
            style = FoldTheme.typography.metaS.copy(fontSize = 10.sp),
            color = textColor,
            maxLines = 1,
        )
    }
}

/** A dashed outline. Marks a hidden (dot-prefixed) entry, which is a visibility state, not a type. */
private fun Modifier.dashedBorder(color: Color, width: Dp): Modifier =
    androidx.compose.ui.draw.drawBehind {
        val strokePx = width.toPx()
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            floatArrayOf(3f * density, 3f * density),
            0f,
        )
        drawRect(
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokePx,
                pathEffect = dash,
            ),
        )
    }
