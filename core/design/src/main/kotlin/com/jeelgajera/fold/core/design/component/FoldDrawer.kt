package com.jeelgajera.fold.core.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.theme.FoldMotion
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.foldTween
import kotlin.math.roundToInt

/** One destination in the overflow drawer. */
data class FoldDrawerItem(val label: String, val meta: String, val key: String)

/**
 * The overflow drawer: a 284dp glass panel that slides in from the trailing edge
 * over a dimmed, blurred scrim.
 *
 * It holds the destinations that do not deserve a dock tab -- settings, glyph,
 * widgets, the onboarding replay -- plus the theme control, which lives here
 * because it is a property of the whole app rather than of any one screen.
 */
@Composable
fun FoldDrawer(
    open: Boolean,
    items: List<FoldDrawerItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit,
    footer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    scrimContentDescription: String,
) {
    val colors = FoldTheme.colors
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = foldTween(FoldMotion.SLIDE),
        label = "FoldDrawerSlide",
    )

    if (progress <= 0f) return

    Box(modifier.fillMaxSize()) {
        // Scrim. Dismisses on tap; carries its own description because it is the
        // only way back out for a switch-access user.
        Box(
            Modifier
                .fillMaxSize()
                .alpha(progress)
                .background(Color(0xFF060606).copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = scrimContentDescription,
                    onClick = onDismiss,
                ),
        )

        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(FoldSizing.drawerWidth)
                .fillMaxHeight()
                .slideInFromEnd(progress)
                .foldGlass(
                    fill = Color(0xFF0E0D0D).copy(alpha = 0.82f),
                    opaqueFallback = Color(0xFF0E0D0D),
                    borderColor = colors.onBackground.copy(alpha = 0.14f),
                ),
        ) {
            Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 16.dp)) {
                header()
            }
            FoldHairline(color = colors.onBackground.copy(alpha = 0.14f))

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEach { item ->
                    val active = item.key == selectedKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (active) colors.onBackground.copy(alpha = 0.06f) else Color.Transparent,
                            )
                            .selectable(
                                selected = active,
                                role = Role.Tab,
                                onClick = { onSelect(item.key) },
                            )
                            .bottomRule(colors.onBackground.copy(alpha = 0.10f))
                            .defaultMinSize(minHeight = 56.dp)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // A 3dp accent tick marks the current destination, so the
                        // selection is a shape as well as a tint.
                        Box(
                            Modifier
                                .size(width = 3.dp, height = 18.dp)
                                .background(if (active) colors.accent else Color.Transparent),
                        )
                        Text(
                            item.label,
                            style = FoldTheme.typography.body,
                            color = colors.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            item.meta,
                            style = FoldTheme.typography.meta,
                            color = colors.onBackground.copy(alpha = 0.55f),
                            maxLines = 1,
                        )
                    }
                }
            }

            FoldHairline(color = colors.onBackground.copy(alpha = 0.14f))
            Box(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp)) {
                footer()
            }
        }
    }
}

/**
 * Slides a full-height panel in from the trailing edge.
 *
 * Written as a layout modifier rather than `offset` so the panel is never
 * measured wider than the screen while it is off-stage.
 */
private fun Modifier.slideInFromEnd(progress: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        val dx = (placeable.width * (1f - progress)).roundToInt()
        placeable.place(IntOffset(dx, 0))
    }
}

/** The segmented LIGHT / DARK / SYSTEM control at the foot of the drawer. */
@Composable
fun FoldSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .androidxBorder(colors.onBackground.copy(alpha = 0.2f)),
    ) {
        options.forEachIndexed { index, option ->
            val active = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (active) colors.accent else Color.Transparent)
                    .selectable(
                        selected = active,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .then(
                        if (index < options.lastIndex) {
                            Modifier.endRule(colors.onBackground.copy(alpha = 0.14f))
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = FoldTheme.typography.labelS,
                    color = if (active) colors.accentInk else colors.onBackground.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Modifier.androidxBorder(color: Color) =
    androidx.compose.foundation.border(1.dp, color, RectangleShape)
