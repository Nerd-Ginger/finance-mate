package dev.financemate.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The black-and-orange palette.
 *
 * Dark-first and warm-neutral: black carries the interface, and orange is spent
 * almost nowhere. The budget is roughly **5% of any screen** — orange is
 * reserved for money the app has found for you and for the one primary action.
 * Everything else is neutral. Spend it more freely and it stops meaning
 * anything, which would take the app's whole signalling system with it.
 */
internal object Palette {

    // --- Neutrals. Warm black, hue held constant across the ramp. -----------
    val Canvas = Color(0xFF0A0908) // app background
    val Surface = Color(0xFF121110) // bars, sheets
    val Raised = Color(0xFF1A1817) // cards, list groups
    val Elevated = Color(0xFF24211F) // hairlines, chip fill
    val Outline = Color(0xFF332F2C) // borders, dividers
    val Muted = Color(0xFF5C5651) // disabled, metadata
    val SecondaryText = Color(0xFFA39C95) // labels, captions
    val PrimaryText = Color(0xFFF2EDE8) // amounts, headings

    // --- Orange. ------------------------------------------------------------
    val Tint = Color(0xFF2A1608) // finding card fill
    val TintPressed = Color(0xFF4A2409) // state layer
    val Dim = Color(0xFFB85A14) // accent borders, tracks
    val Primary = Color(0xFFFF7A18) // FAB, active nav, key figure
    val Bright = Color(0xFFFF9A4D) // links, hover
    val Soft = Color(0xFFFFC894) // accent text on tint

    /**
     * Text and icons on [Primary] are near-black, never white.
     *
     * Orange is a light colour; white on it fails contrast at body sizes.
     */
    val OnPrimary = Canvas

    /**
     * Darker than [Canvas], and used for one thing: the egress log panel.
     *
     * The step down is the point. Network-touching surfaces are meant to look
     * like instrumentation rather than interface, so they sit *below* the app's
     * own background instead of raised above it like every other panel.
     */
    val EgressPanel = Color(0xFF050403)

    // --- The only two non-orange hues in the system. -------------------------
    val Income = Color(0xFF5FB980) // positive amounts only
    val Destructive = Color(0xFFE2543F) // actions that lose data
}

/**
 * Colours carrying meaning the Material scheme has no slot for.
 *
 * Kept separate from [androidx.compose.material3.ColorScheme] so the rules about
 * *what a colour is allowed to say* live in one readable place rather than being
 * smuggled into `primary`/`tertiary` where the next person has to guess.
 */
@Immutable
data class SemanticColours(
    /**
     * Money the app found: duplicates, price rises, avoidable fees, the what-if
     * total. Orange means the app noticed something.
     */
    val foundMoney: Color,
    val foundMoneyFill: Color,
    val foundMoneyBorder: Color,
    val foundMoneyText: Color,

    /**
     * Tappable text. Brighter than [foundMoney] because it sits on the canvas
     * with no fill behind it to carry the contrast.
     */
    val foundMoneyLink: Color,

    /**
     * Ordinary spending. Deliberately **neutral** — the negative sign carries
     * the meaning. Colouring every purchase would make each one feel like a
     * warning, which is both exhausting and untrue.
     */
    val moneyOut: Color,

    /** Positive amounts only. Muted so it never competes with orange. */
    val moneyIn: Color,

    /**
     * Findings the engine is not confident about.
     *
     * Confidence is encoded as **saturation, not as a badge**: certain findings
     * are filled, likely ones get a hairline, and a guess drops out of orange
     * entirely into dashed neutral. The user reads certainty from the drawing
     * before they read any label.
     */
    val uncertain: Color,
    val uncertainText: Color,

    /** Reserved for actions that lose data. Never for spending. */
    val destructive: Color,

    /**
     * Anything network-touching: the egress log, payload previews, AI toggles.
     * A hairline orange rule on pure black, always set in mono, so the parts of
     * the app that can talk to the outside world are recognisable on sight.
     */
    val egressRule: Color,
    val egressBackground: Color,
)

internal val DefaultSemanticColours = SemanticColours(
    foundMoney = Palette.Primary,
    foundMoneyFill = Palette.Tint,
    foundMoneyBorder = Palette.Dim,
    foundMoneyText = Palette.Soft,
    foundMoneyLink = Palette.Bright,
    moneyOut = Palette.PrimaryText,
    moneyIn = Palette.Income,
    uncertain = Palette.Outline,
    uncertainText = Palette.SecondaryText,
    destructive = Palette.Destructive,
    egressRule = Palette.Dim,
    egressBackground = Palette.EgressPanel,
)

val LocalSemanticColours = staticCompositionLocalOf { DefaultSemanticColours }
