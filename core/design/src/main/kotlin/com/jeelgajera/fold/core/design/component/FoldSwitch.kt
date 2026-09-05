package com.jeelgajera.fold.core.design.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldMotion
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.foldTween

/**
 * A square switch. No pill, no elevation -- a knob that slides inside a rule.
 *
 * The switch is not focusable on its own: it is always drawn inside a row that
 * is itself the toggle, so the row carries the click, the label and the
 * `Role.Switch` semantics, and the switch is decorative.
 */
@Composable
fun FoldSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
    val colors = FoldTheme.colors
    val trackWidth: Dp = if (small) FoldSizing.switchTrackWidthSmall else FoldSizing.switchTrackWidth
    val trackHeight: Dp = if (small) FoldSizing.switchTrackHeightSmall else FoldSizing.switchTrackHeight
    val knob: Dp = if (small) 18.dp else 20.dp
    val inset: Dp = if (small) 2.dp else 3.dp
    val travel = trackWidth - knob - inset * 2

    val offset by animateDpAsState(
        targetValue = if (checked) travel else 0.dp,
        animationSpec = foldTween(FoldMotion.BASE + 20),
        label = "FoldSwitchKnob",
    )

    Box(
        modifier
            .size(trackWidth, trackHeight)
            .background(
                if (checked) colors.accent else colors.onBackground.copy(alpha = 0.10f),
                RectangleShape,
            )
            .border(
                FoldRules.hairline,
                if (checked) colors.accent else colors.onBackground.copy(alpha = 0.22f),
                RectangleShape,
            )
            .clearAndSetSemantics { },
    ) {
        Box(
            Modifier
                .padding(inset)
                .offset(x = offset)
                .size(knob)
                .background(
                    if (checked) colors.accentInk else colors.onBackground.copy(alpha = 0.6f),
                    RectangleShape,
                ),
        )
    }
}
