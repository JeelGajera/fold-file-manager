package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * The primary action: a full-bleed red block with left-aligned uppercase label.
 *
 * Left-aligned rather than centred on purpose -- it reads as the next line of
 * the page rather than as a floating button, which is what keeps the layout
 * feeling like a document.
 */
@Composable
fun FoldAccentButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 56.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = FoldTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        !enabled -> colors.onBackground.copy(alpha = 0.12f)
        pressed -> colors.accentPressed
        else -> colors.accent
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(fill, RectangleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = FoldSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.invoke()
        Text(
            text = label,
            style = FoldTheme.typography.label.copy(fontSize = 14.sp),
            color = if (enabled) colors.accentInk else colors.onBackgroundDisabled,
            maxLines = 1,
        )
    }
}

/** The quieter partner to [FoldAccentButton]: outline only, same geometry. */
@Composable
fun FoldOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = FoldSizing.touchTargetMin,
    borderColor: Color = FoldTheme.colors.onBackground.copy(alpha = 0.20f),
    contentColor: Color = FoldTheme.colors.onBackground,
) {
    Row(
        modifier
            .fillMaxWidth()
            .border(BorderStroke(FoldRules.hairline, borderColor), RectangleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = FoldSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FoldTheme.typography.label.copy(fontSize = 12.sp),
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** A solid ink block. Used inside the vault, where the ground is inverted. */
@Composable
fun FoldInkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = FoldSizing.touchTargetMin,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.onBackground, RectangleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = minHeight)
            .padding(horizontal = FoldSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FoldTheme.typography.label.copy(fontSize = 12.sp),
            color = colors.background,
            maxLines = 1,
        )
    }
}

/**
 * A 48dp icon-only control.
 *
 * [contentDescription] is required, not optional: the token file lists every
 * icon-only control in the app and every one of them carries a description.
 */
@Composable
fun FoldIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = FoldSizing.touchTargetMin,
    content: @Composable () -> Unit,
) {
    val label = contentDescription
    Box(
        modifier
            .size(size)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
