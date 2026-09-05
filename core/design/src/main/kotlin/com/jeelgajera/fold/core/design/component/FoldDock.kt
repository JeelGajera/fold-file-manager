package com.jeelgajera.fold.core.design.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jeelgajera.fold.core.design.theme.FoldMotion
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.foldTween

/** One tab in the floating dock. */
data class FoldDockTab(
    val label: String,
    val paths: List<String>,
    val filledPaths: List<String> = emptyList(),
)

/**
 * The floating tab dock.
 *
 * This and its indicator are the only pills in FOLD; everything else has a 0dp
 * radius. It shrinks slightly and fades when the content under it scrolls down,
 * and returns the moment the user scrolls back up.
 *
 * Under reduced motion it neither scales nor slides: the indicator jumps and the
 * dock holds still, matching the token file's reduced-motion contract.
 */
@Composable
fun FoldDock(
    tabs: List<FoldDockTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
) {
    val colors = FoldTheme.colors
    val reduced = FoldTheme.reducedMotion

    val scale by animateFloatAsState(
        targetValue = if (collapsed && !reduced) 0.9f else 1f,
        animationSpec = foldTween(FoldMotion.EMPHASIZED),
        label = "FoldDockScale",
    )
    val dockAlpha by animateFloatAsState(
        targetValue = if (collapsed) 0.86f else 1f,
        animationSpec = foldTween(FoldMotion.BASE, FoldMotion.Linear),
        label = "FoldDockAlpha",
    )
    val indicatorOffset by animateDpAsState(
        targetValue = FoldSizing.dockItem * selectedIndex.coerceAtLeast(0),
        animationSpec = foldTween(FoldMotion.INDICATOR),
        label = "FoldDockIndicator",
    )

    Box(
        modifier
            .scale(scale)
            .alpha(dockAlpha)
            .foldDockGlass()
            .padding(FoldSizing.dockPadding),
    ) {
        // The indicator sits behind the tabs and never takes a touch.
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .size(FoldSizing.dockItem)
                .background(
                    colors.onBackground.copy(alpha = 0.10f),
                    RoundedCornerShape(percent = 50),
                )
                .border(
                    FoldRules.hairline,
                    colors.onBackground.copy(alpha = 0.14f),
                    RoundedCornerShape(percent = 50),
                ),
        )
        Row {
            tabs.forEachIndexed { index, tab ->
                val active = index == selectedIndex
                val tint = if (active) colors.onBackground else colors.onBackground.copy(alpha = 0.46f)
                Column(
                    Modifier
                        .size(FoldSizing.dockItem)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    com.jeelgajera.fold.core.design.icon.FoldIcon(
                        paths = tab.paths,
                        filledPaths = if (active) tab.filledPaths else emptyList(),
                        tint = tint,
                        strokeWidth = if (active) 2f else 1.6f,
                        size = 20.dp,
                    )
                    Box(Modifier.size(width = 1.dp, height = 5.dp))
                    // The label fades out on inactive tabs but stays in the
                    // tree, so TalkBack still announces "Vault, tab 4 of 4".
                    Text(
                        text = tab.label,
                        style = FoldTheme.typography.labelS.copy(fontSize = 8.sp),
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.alpha(if (active) 0.9f else 0f),
                    )
                }
            }
        }
    }
}
