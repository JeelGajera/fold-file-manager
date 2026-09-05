package com.jeelgajera.fold.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import com.jeelgajera.fold.core.design.theme.FoldRules
import com.jeelgajera.fold.core.design.theme.FoldTheme
import com.jeelgajera.fold.core.design.theme.LocalFoldBlurAvailable

/**
 * The glass treatment used by the dock, the drawer and the search field.
 *
 * An honest note on what this does and does not do: Compose has no backdrop
 * blur. `Modifier.blur` blurs a composable's *own* content, not what sits behind
 * it, and blurring a live backdrop needs either a platform `RenderEffect` on the
 * window (API 31+, and only for dialog windows) or a snapshot-and-blur pass that
 * costs a frame. FOLD renders the translucent fill and the 1dp inner-lit border
 * from the token file and skips the blur pass entirely.
 *
 * Where the platform cannot blur, or the user has asked for reduced
 * transparency, the token file's `$fallback` applies: the same border over an
 * opaque fill, so the dock never turns into an unreadable smear over a bright
 * photo.
 */
@Composable
fun Modifier.foldGlass(
    fill: Color = FoldTheme.colors.surfaceGlass,
    opaqueFallback: Color = Color(0xFF100F0F),
    borderColor: Color = FoldTheme.colors.onBackground.copy(alpha = 0.14f),
    shape: Shape = RectangleShape,
): Modifier {
    val translucent = LocalFoldBlurAvailable.current
    return this
        .background(if (translucent) fill else opaqueFallback, shape)
        .border(FoldRules.hairline, borderColor, shape)
}

/** The dock's pill-shaped glass. */
@Composable
fun Modifier.foldDockGlass(): Modifier = foldGlass(
    fill = FoldTheme.colors.surfaceGlass,
    opaqueFallback = Color(0xFF100F0F),
    borderColor = FoldTheme.colors.onBackground.copy(alpha = 0.14f),
    shape = RoundedCornerShape(percent = 50),
)
