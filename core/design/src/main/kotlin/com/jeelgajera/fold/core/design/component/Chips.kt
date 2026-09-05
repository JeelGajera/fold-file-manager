package com.jeelgajera.fold.core.design.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldMotion
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.foldTween

/**
 * A search scope: ALL STORAGE / THIS FOLDER / VAULT. Exactly one is active, so
 * these are radio buttons wearing a chip.
 */
@Composable
fun FoldScopeChip(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors
    Box(
        modifier
            .height(FoldSizing.scopeChipHeight)
            .background(
                if (selected) colors.onBackground.copy(alpha = 0.12f) else Color.Transparent,
                RectangleShape,
            )
            .border(
                FoldRules.hairline,
                if (selected) {
                    colors.onBackground.copy(alpha = 0.32f)
                } else {
                    colors.onBackground.copy(alpha = 0.14f)
                },
                RectangleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = FoldSpacingChip),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = FoldTheme.typography.labelS,
            color = if (selected) colors.onBackground else colors.onBackground.copy(alpha = 0.5f),
            maxLines = 1,
        )
    }
}

/**
 * A search filter: several can be on at once, so these are checkboxes.
 *
 * When active the chip flips to a red fill *and* its leading dot flips to the
 * dark ink -- the fill alone is never the only signal.
 */
@Composable
fun FoldFilterChip(
    label: String,
    count: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors
    val background by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.onBackground.copy(alpha = 0.05f),
        animationSpec = foldTween(FoldMotion.FAST + 20),
        label = "FoldFilterChipBackground",
    )
    val content = if (checked) colors.accentInk else colors.onBackgroundMuted

    Row(
        modifier
            .height(FoldSizing.chipHeight)
            .background(background, RectangleShape)
            .border(
                FoldRules.hairline,
                if (checked) colors.accent else colors.onBackground.copy(alpha = 0.16f),
                RectangleShape,
            )
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
            .padding(horizontal = FoldSpacingChip),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(if (checked) colors.accentInk else colors.onBackground.copy(alpha = 0.35f)),
        )
        Text(label, style = FoldTheme.typography.labelS, color = content, maxLines = 1)
        Text(
            count,
            style = FoldTheme.typography.meta,
            color = content.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

/** The sort control: one chip that cycles DATE -> SIZE -> NAME. */
@Composable
fun FoldSortChip(
    label: String,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .height(FoldSizing.scopeChipHeight)
            .background(colors.onBackground.copy(alpha = 0.06f), RectangleShape)
            .border(FoldRules.hairline, colors.onBackground.copy(alpha = 0.16f), RectangleShape)
            .selectable(selected = false, role = Role.Button, onClick = onCycle)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(5.dp).background(colors.accent))
        Text(label, style = FoldTheme.typography.labelS, color = colors.onBackground, maxLines = 1)
    }
}

private val FoldSpacingChip = 12.dp
