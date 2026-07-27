package dev.financemate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * The app theme.
 *
 * ## Why there is no dynamic colour, and no light mode
 *
 * Material's dynamic colour derives a palette from the user's wallpaper. That is
 * a good default for most apps and the wrong one here, because this design
 * system gives colour a job: **orange means the app found you money.** A
 * finding card, the active tab, and the primary action are all orange, and a
 * user learns to read that in a session. Hand the palette over to the wallpaper
 * and orange might be teal, the accent might collide with the income green, and
 * the one signal the interface relies on stops being reliable.
 *
 * The system is dark-first for the same reason: orange at 5% coverage reads as
 * emphasis against warm black, and as a warning against white.
 *
 * A light theme is a real piece of design work rather than an inversion, so it
 * is deliberately absent until it is designed rather than approximated here.
 */
@Composable
fun FinanceMateTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSemanticColours provides DefaultSemanticColours) {
        MaterialTheme(
            colorScheme = FinanceMateColourScheme,
            typography = FinanceMateTypography,
            shapes = FinanceMateShapes,
            content = content,
        )
    }
}

/**
 * Semantic colours for the current theme.
 *
 * `FinanceMate.colours.foundMoney` rather than `MaterialTheme.colorScheme.primary`
 * at the call site, so the reason a thing is orange is legible from the code.
 */
object FinanceMate {
    val colours: SemanticColours
        @Composable
        @ReadOnlyComposable
        get() = LocalSemanticColours.current
}

private val FinanceMateColourScheme = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Palette.OnPrimary,
    primaryContainer = Palette.Tint,
    onPrimaryContainer = Palette.Soft,

    // Secondary is deliberately neutral. Material components reach for it
    // automatically, and a second accent hue would dilute the one that matters.
    secondary = Palette.SecondaryText,
    onSecondary = Palette.Canvas,
    secondaryContainer = Palette.Elevated,
    onSecondaryContainer = Palette.PrimaryText,

    tertiary = Palette.Income,
    onTertiary = Palette.Canvas,

    background = Palette.Canvas,
    onBackground = Palette.PrimaryText,

    surface = Palette.Canvas,
    onSurface = Palette.PrimaryText,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.SecondaryText,
    surfaceContainerLowest = Palette.Canvas,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Raised,
    surfaceContainerHigh = Palette.Elevated,
    surfaceContainerHighest = Palette.Elevated,

    outline = Palette.Outline,
    outlineVariant = Palette.Elevated,

    error = Palette.Destructive,
    onError = Palette.Canvas,
    errorContainer = Palette.Raised,
    onErrorContainer = Palette.Destructive,

    scrim = Palette.Canvas,
)

/**
 * Corners are gentler on content than on controls: cards are calm rectangles at
 * 12dp, while buttons are fully rounded so the one primary action on a screen
 * reads as a distinct object rather than another panel.
 */
private val FinanceMateShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
