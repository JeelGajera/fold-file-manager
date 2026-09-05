package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jeelgajera.fold.core.design.icon.FoldIcon
import com.jeelgajera.fold.core.design.icon.FoldIconPaths
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldSpacing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * The 56dp bar at the top of every screen: the wordmark, a Doto readout of where
 * you are, and two controls.
 *
 * The readout is not decoration -- it is the only persistent statement of which
 * screen you are on, so it replaces a per-screen title bar entirely.
 */
@Composable
fun FoldAppBar(
    meta: String,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    searchContentDescription: String,
    menuContentDescription: String,
) {
    val colors = FoldTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(FoldSizing.appBarHeight)
            .bottomRule(colors.dividerStrong, FoldRules.hairline)
            .padding(horizontal = FoldSpacing.screenGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(FoldSpacing.s2),
        ) {
            Text("FOLD", style = FoldTheme.typography.titleS, color = colors.onBackground)
            Text(
                text = meta,
                style = FoldTheme.typography.meta,
                color = colors.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FoldSpacing.s1)) {
            FoldIconButton(searchContentDescription, onSearch) {
                FoldIcon(FoldIconPaths.Search, tint = colors.onBackground, strokeWidth = 1.5f)
            }
            FoldIconButton(menuContentDescription, onMenu) {
                // Three stacked squares. The overflow control is a dot column
                // rather than a kebab so it matches the dot-matrix language.
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(3) {
                        Box(Modifier.size(4.dp).background(colors.onBackground))
                    }
                }
            }
        }
    }
}
