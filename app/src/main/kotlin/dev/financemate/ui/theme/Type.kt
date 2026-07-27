package dev.financemate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.financemate.R

/**
 * IBM Plex, bundled in the APK.
 *
 * **Deliberately not downloadable fonts.** Google's font provider fetches over
 * the network on first use, which would mean the app phoning out on a cold start
 * — directly contradicting the promise made on its own welcome screen that it
 * works with aeroplane mode on. Roughly 800 KB of TTF is a small price for a
 * claim the product is built around.
 *
 * IBM Plex is OFL-1.1 licensed, so bundling is permitted.
 */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/**
 * Mono is not decorative. It marks two specific things:
 *
 * - **Anything network-touching** — the egress log, payload previews — so those
 *   parts of the app are recognisable on sight.
 * - **Section labels and metadata**, where the wider tracking reads as
 *   instrumentation rather than prose.
 *
 * Amounts stay in Plex Sans with tabular figures; mono numerals would make
 * ordinary money look like machine output.
 */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

val FinanceMateTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.14.em,
    ),
)

/**
 * Amounts, everywhere.
 *
 * Tabular figures are not a nicety here. Money is almost always shown in a
 * vertical list, and proportional digits make columns of numbers fail to line
 * up — which is exactly when a reader is trying to compare them.
 */
val AmountStyle: TextStyle = TextStyle(
    fontFamily = PlexSans,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
    textAlign = TextAlign.End,
)

/** Section labels: mono, uppercase, wide-tracked. */
val SectionLabelStyle: TextStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.14.em,
)

/**
 * Mono metadata: file names, row dates, source locations.
 *
 * Same family as [SectionLabelStyle] but without the wide tracking, because this
 * is content to be read rather than a label to be scanned. It marks values that
 * came out of a file verbatim, so the user can tell them apart from text the app
 * wrote.
 */
val MonoMetaStyle: TextStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

/** Anything that touches the network. Always mono. */
val EgressStyle: TextStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 17.sp,
)
