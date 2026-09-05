package com.jeelgajera.fold.core.design.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Maps FOLD's roles onto Material 3's slots.
 *
 * This exists so nothing can leak: if a Compose internal or a stray call site
 * reads `MaterialTheme.colorScheme`, it gets FOLD's ink and FOLD's red rather
 * than Material's defaults. It is not a design surface -- do not read from it.
 */
internal fun FoldColors.toMaterialSkeleton(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = accentInk,
        primaryContainer = accentTint,
        onPrimaryContainer = accentBodyText,
        secondary = onBackground,
        onSecondary = background,
        background = background,
        onBackground = onBackground,
        surface = background,
        onSurface = onBackground,
        surfaceVariant = surface,
        onSurfaceVariant = onBackgroundMuted,
        outline = dividerStrong,
        outlineVariant = divider,
        error = accent,
        onError = accentInk,
        scrim = androidx.compose.ui.graphics.Color(0xFF060606),
    )
}
