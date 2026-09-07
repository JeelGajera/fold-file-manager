package com.jeelgajera.fold.core.design.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat

/** Which ground the app is painted on. Mirrors the drawer's LIGHT/DARK/SYSTEM control. */
enum class FoldThemeMode { LIGHT, DARK, SYSTEM }

/**
 * Whether the platform can actually blur behind the dock and drawer.
 *
 * `RenderEffect`-backed blur needs API 31+. Below that -- and whenever the user
 * has asked the system to reduce transparency -- the token file's `$fallback`
 * applies: the same 1dp border over an opaque fill.
 */
val LocalFoldBlurAvailable: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/**
 * The only theme wrapper in the app.
 *
 * Material 3 is applied underneath because a handful of Compose primitives
 * (ripple, text selection handles, the text-field cursor) read from it. Every
 * colour it exposes is overwritten with a FOLD role first, so a component that
 * accidentally reaches for `MaterialTheme.colorScheme` still lands on the right
 * ink rather than on Material purple.
 */
@Composable
fun FoldTheme(
    mode: FoldThemeMode = FoldThemeMode.SYSTEM,
    reducedMotion: Boolean = false,
    blurAvailable: Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        FoldThemeMode.LIGHT -> false
        FoldThemeMode.DARK -> true
        FoldThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) FoldDarkColors else FoldLightColors

    // The system bars follow FOLD's own theme, not the platform's.
    //
    // `enableEdgeToEdge()` keys the bar icons off the system dark-mode setting,
    // which is the wrong source once the app carries its own LIGHT/DARK control:
    // a phone in dark mode with FOLD set to light drew white status icons on the
    // light paper, leaving the clock and battery invisible.
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = view.context.findActivity()?.window
        DisposableEffect(window, dark) {
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            onDispose { }
        }
    }

    CompositionLocalProvider(
        LocalFoldColors provides colors,
        LocalFoldTypography provides FoldTypographyDefaults,
        LocalFoldReducedMotion provides reducedMotion,
        LocalFoldBlurAvailable provides blurAvailable,
        LocalContentColor provides colors.onBackground,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialSkeleton(),
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

/** Unwraps the context chain, which is a `ContextWrapper` under a Compose view. */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * A themed [Text] that defaults to FOLD's body style and ink instead of
 * Material's. Prefer this over `androidx.compose.material3.Text` everywhere.
 */
@Composable
fun FoldText(
    text: String,
    style: TextStyle = FoldTheme.typography.body,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: androidx.compose.ui.text.style.TextOverflow =
        androidx.compose.ui.text.style.TextOverflow.Clip,
) {
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Short accessors so call sites read `FoldTheme.colors.accent`. */
object FoldTheme {
    val colors: FoldColors
        @Composable @ReadOnlyComposable
        get() = LocalFoldColors.current

    val typography: FoldTypography
        @Composable @ReadOnlyComposable
        get() = LocalFoldTypography.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalFoldReducedMotion.current
}
