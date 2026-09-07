package com.jeelgajera.fold.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * FOLD's colour roles, transcribed from `tokens.json`.
 *
 * Material 3 is present in the dependency graph as a skeleton (ripples, window
 * insets, text-field plumbing) but none of its colour defaults reach the screen.
 * Everything visible resolves through this class.
 *
 * The red is not decoration. It marks exactly four things: an active transfer,
 * something that needs attention, a destructive action, and the unlocked vault.
 * Per the token file's `$redRule`, red is never the only carrier of a meaning --
 * every red state is paired with a label, a numeral, an inversion or a shape
 * change so the state survives a colour-blind or greyscale reading.
 */
@Immutable
data class FoldColors(
    val background: Color,
    val surface: Color,
    val surfaceGlass: Color,
    /**
     * Opaque ground for surfaces that float above content -- the dock and the
     * drawer -- used when translucency is unavailable or unwanted.
     *
     * This exists as a role because those two surfaces previously carried the
     * dark value inline, which meant they stayed black in light mode however the
     * rest of the app was themed.
     */
    val surfaceRaised: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    val onBackgroundDisabled: Color,
    val divider: Color,
    val dividerStrong: Color,
    val accent: Color,
    val accentPressed: Color,
    /** Ink drawn *on top of* an accent fill. Near-black, 5.9:1 against the red. */
    val accentInk: Color,
    val accentTint: Color,
    /** The only accent value safe for small body copy on the dark ground (8.7:1). */
    val accentBodyText: Color,
    val dotIdle: Color,
    val isDark: Boolean,
)

val FoldDarkColors = FoldColors(
    background = Color(0xFF0C0B0B),
    surface = Color(0xFFF3F2F2).copy(alpha = 0.06f),
    surfaceGlass = Color(0xFF100F0F).copy(alpha = 0.62f),
    surfaceRaised = Color(0xFF100F0F),
    onBackground = Color(0xFFF3F2F2),
    onBackgroundMuted = Color(0xFFF3F2F2).copy(alpha = 0.62f),
    onBackgroundDisabled = Color(0xFFF3F2F2).copy(alpha = 0.46f),
    divider = Color(0xFFF3F2F2).copy(alpha = 0.12f),
    dividerStrong = Color(0xFFF3F2F2).copy(alpha = 0.16f),
    accent = Color(0xFFEC3013),
    accentPressed = Color(0xFFFF563C),
    accentInk = Color(0xFF0C0B0B),
    accentTint = Color(0xFFEC3013).copy(alpha = 0.08f),
    accentBodyText = Color(0xFFFF9783),
    dotIdle = Color(0xFFF3F2F2).copy(alpha = 0.28f),
    isDark = true,
)

/**
 * The light ground. It is also the vault's ground: inverting the whole surface
 * is the second, non-colour carrier of "you are inside the vault".
 *
 * `tokens.json` specifies fewer light roles than dark ones. The values derived
 * here -- surfaceGlass, accentPressed, accentTint, dotIdle, onBackgroundDisabled
 * -- follow the same alpha ratios as their dark counterparts against the light
 * ink (#201E1D) rather than the light paper.
 */
val FoldLightColors = FoldColors(
    background = Color(0xFFF3F2F2),
    surface = Color(0xFFEAE9E9),
    // A shade *darker* than the paper, mirroring how the dark glass sits a shade
    // lighter than its ground. Deriving it as the background colour, which is
    // what it was, gave the floating dock zero contrast against the page it
    // floats over.
    surfaceGlass = Color(0xFFE6E5E5).copy(alpha = 0.86f),
    // Lifted just off the paper, so a floating surface reads as above the page
    // rather than as a hole in it.
    surfaceRaised = Color(0xFFEDECEC),
    onBackground = Color(0xFF201E1D),
    onBackgroundMuted = Color(0xFF201E1D).copy(alpha = 0.62f),
    onBackgroundDisabled = Color(0xFF201E1D).copy(alpha = 0.46f),
    divider = Color(0xFF201E1D).copy(alpha = 0.25f),
    dividerStrong = Color(0xFF201E1D).copy(alpha = 0.40f),
    accent = Color(0xFFEC3013),
    accentPressed = Color(0xFFFF563C),
    accentInk = Color(0xFF0C0B0B),
    accentTint = Color(0xFFEC3013).copy(alpha = 0.08f),
    // #AE1800 is the token file's `accentBodyText`, the only accent that clears
    // 4.5:1 for small text on the light paper.
    accentBodyText = Color(0xFFAE1800),
    dotIdle = Color(0xFF201E1D).copy(alpha = 0.28f),
    isDark = false,
)

val LocalFoldColors = staticCompositionLocalOf { FoldDarkColors }
