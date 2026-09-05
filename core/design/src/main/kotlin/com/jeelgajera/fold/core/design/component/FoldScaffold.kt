package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.jeelgajera.fold.core.design.theme.FoldSizing
import com.jeelgajera.fold.core.design.theme.FoldTheme

/**
 * The frame every screen sits in: ground, app bar, scrolling content, floating
 * dock, and the drawer on top of all of it.
 *
 * Content is *not* inset for the dock here -- each screen's own scroll container
 * adds `FoldSpacing.dockClearance` as bottom padding, so the list scrolls under
 * the glass instead of stopping short of it.
 */
@Composable
fun FoldScaffold(
    appBar: @Composable () -> Unit,
    dock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    drawer: @Composable () -> Unit = {},
    background: androidx.compose.ui.graphics.Color = FoldTheme.colors.background,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            appBar()
            Box(Modifier.weight(1f)) { content() }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FoldSizing.dockBottomInset),
        ) {
            dock()
        }
        drawer()
    }
}
