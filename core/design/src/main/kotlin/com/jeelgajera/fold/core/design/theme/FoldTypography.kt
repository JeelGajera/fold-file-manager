package com.jeelgajera.fold.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jeelgajera.fold.core.design.R

/**
 * Two families, one job each.
 *
 * Archivo carries every piece of prose, title and control label. Doto — a
 * dot-matrix face — carries numerals, byte sizes, rates, paths, PINs and status
 * labels, and nothing else. The split is what makes a byte count read as machine
 * output rather than as writing.
 *
 * Both are open-licence Google Fonts, bundled as variable fonts rather than
 * fetched through the downloadable-fonts provider. Bundling costs about 1.1 MB
 * and buys three things: the typography renders on devices without Play
 * services, it renders on first launch with no network round trip, and the app
 * makes no network request of any kind (see docs/PRIVACY.md).
 *
 * Nothing's own faces, NType82 and NDot55, are deliberately not used,
 * referenced or bundled.
 *
 * Both files carry a weight axis. `Font(resId, weight)` applies that axis
 * through its default `variationSettings`, so one file covers every weight the
 * design uses.
 */
val ArchivoFamily = FontFamily(
    Font(R.font.archivo_variable, FontWeight.W400),
    Font(R.font.archivo_variable, FontWeight.W600),
    Font(R.font.archivo_variable, FontWeight.W800),
)

val DotoFamily = FontFamily(
    Font(R.font.doto_variable, FontWeight.W400),
    Font(R.font.doto_variable, FontWeight.W600),
    Font(R.font.doto_variable, FontWeight.W800),
)

/**
 * The named styles from `tokens.json`. Names match the token file exactly so a
 * design change can be traced from JSON to screen without a lookup table.
 */
@Immutable
data class FoldTypography(
    /** 44sp Doto. The storage headline number, and nothing else. */
    val displayXL: TextStyle,
    /** 30sp Doto. The server PIN. */
    val displayL: TextStyle,
    /** 26sp Doto. The server URL. */
    val displayM: TextStyle,
    val titleL: TextStyle,
    val titleM: TextStyle,
    val titleS: TextStyle,
    val body: TextStyle,
    val bodyS: TextStyle,
    /** 11sp Archivo 800, uppercase, wide. Section headings and CTA labels. */
    val label: TextStyle,
    val labelS: TextStyle,
    /** 11sp Doto. Sizes, dates, paths, counts -- never prose. */
    val meta: TextStyle,
    val metaS: TextStyle,
)

val FoldTypographyDefaults = FoldTypography(
    displayXL = TextStyle(
        fontFamily = DotoFamily, fontWeight = FontWeight.W800,
        fontSize = 44.sp, lineHeight = 40.sp, letterSpacing = 0.02.em,
    ),
    displayL = TextStyle(
        fontFamily = DotoFamily, fontWeight = FontWeight.W800,
        fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = 0.16.em,
    ),
    displayM = TextStyle(
        fontFamily = DotoFamily, fontWeight = FontWeight.W800,
        fontSize = 26.sp, lineHeight = 28.sp, letterSpacing = 0.01.em,
    ),
    titleL = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W800,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.03).em,
    ),
    titleM = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W800,
        fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.02).em,
    ),
    titleS = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W800,
        fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = (-0.02).em,
    ),
    body = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W400,
        fontSize = 14.sp, lineHeight = 22.sp,
    ),
    bodyS = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W400,
        fontSize = 13.sp, lineHeight = 20.sp,
    ),
    label = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W800,
        fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.14.em,
    ),
    labelS = TextStyle(
        fontFamily = ArchivoFamily, fontWeight = FontWeight.W800,
        fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.12.em,
    ),
    meta = TextStyle(
        fontFamily = DotoFamily, fontWeight = FontWeight.W600,
        fontSize = 11.sp, lineHeight = 16.sp,
    ),
    metaS = TextStyle(
        fontFamily = DotoFamily, fontWeight = FontWeight.W600,
        fontSize = 10.sp, lineHeight = 14.sp,
    ),
)

/**
 * `meta` and `metaS` are declared `wrap: nowrap` in the token file: a byte count
 * that wraps to a second line reads as two numbers. Call sites that render meta
 * text in a constrained row use this.
 */
const val MetaMaxLines: Int = 1
val MetaOverflow: TextOverflow = TextOverflow.Ellipsis

val LocalFoldTypography = staticCompositionLocalOf { FoldTypographyDefaults }
