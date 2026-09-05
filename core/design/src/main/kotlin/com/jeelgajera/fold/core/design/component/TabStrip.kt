package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * An equal-width strip of segments divided by hairlines: SEND / RECEIVE / QUICK,
 * STRIP / MATRIX / NO GLYPH.
 *
 * There is no sliding underline. The active segment is a filled cell -- the same
 * grammar as the category grid on the home screen, so the two read as one system.
 */
@Composable
fun FoldTabStrip(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .bottomRule(colors.dividerStrong, com.jeelgajera.fold.core.design.theme.FoldRules.sectionDivider),
        horizontalArrangement = Arrangement.Start,
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .defaultMinSize(minHeight = height)
                    .background(
                        if (active) colors.onBackground.copy(alpha = 0.08f) else Color.Transparent,
                    )
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                    .endRule(colors.divider),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = FoldTheme.typography.label,
                    color = if (active) colors.onBackground else colors.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
        }
    }
}
