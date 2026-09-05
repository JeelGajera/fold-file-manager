package com.jeelgajera.fold.core.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale from `tokens.json`. Every gap in FOLD is one of these. */
object FoldSpacing {
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 20.dp
    val s6: Dp = 24.dp
    val s8: Dp = 32.dp
    val s9: Dp = 36.dp

    /** Horizontal padding for every full-width screen. */
    val screenGutter: Dp = 16.dp

    /** Bottom padding a scrolling list needs so the floating dock never covers content. */
    val dockClearance: Dp = 104.dp
}

/**
 * Rule weights. FOLD separates content with lines, not with shadows or cards --
 * a hairline inside a group, a 2dp rule between groups.
 */
object FoldRules {
    val hairline: Dp = 1.dp
    val strong: Dp = 2.dp
    val sectionDivider: Dp = 2.dp
    val accentEdge: Dp = 2.dp
}

/** Fixed component sizes, transcribed from the token file's `sizing` block. */
object FoldSizing {
    /** Nothing interactive is ever smaller than this. */
    val touchTargetMin: Dp = 48.dp
    val appBarHeight: Dp = 56.dp
    val fileRowMinHeight: Dp = 64.dp
    val settingRowMinHeight: Dp = 60.dp
    val gridTileMinHeight: Dp = 118.dp
    val typeBadge: Dp = 38.dp
    val typeBadgeGrid: Dp = 34.dp
    val dockItem: Dp = 60.dp
    val dockPadding: Dp = 7.dp
    val dockBottomInset: Dp = 14.dp
    val drawerWidth: Dp = 284.dp
    val chipHeight: Dp = 40.dp
    val scopeChipHeight: Dp = 34.dp
    val switchTrackWidth: Dp = 52.dp
    val switchTrackHeight: Dp = 28.dp
    val switchTrackWidthSmall: Dp = 44.dp
    val switchTrackHeightSmall: Dp = 24.dp
}

/**
 * The dot matrix is structure, never texture. It draws meters, progress and
 * state -- and it is never used as a decorative background.
 */
object FoldDotMatrix {
    val cell: Dp = 5.dp
    val gap: Dp = 3.dp
    const val METER_CELLS = 40
    const val PROGRESS_CELLS = 28
    const val WIDGET_CELLS = 24
}

/**
 * Radius is 0 everywhere. The only exceptions in the entire app are the floating
 * tab dock and its selection indicator, which are pills.
 */
object FoldRadius {
    val none: Dp = 0.dp
    val pill: Dp = 999.dp
}
