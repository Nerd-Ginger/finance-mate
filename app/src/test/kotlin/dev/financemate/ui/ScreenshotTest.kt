package dev.financemate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.financemate.ui.theme.FinanceMateTheme
import org.junit.Rule
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base for screenshot tests.
 *
 * ## Why these exist
 *
 * The design system's whole value is precision — a specific orange, spent on
 * roughly 5% of a screen, with confidence encoded as saturation. That is exactly
 * the kind of thing that degrades silently: a component picks up
 * `MaterialTheme.colorScheme.primary` by accident, orange creeps to 15%, and
 * nobody notices until the signal has stopped meaning anything.
 *
 * Rendering every screen to a committed PNG makes that visible in review, and
 * makes it reviewable without a device or a build.
 *
 * ## Why the JVM rather than the device
 *
 * These run in seconds with no install cycle, cover states that are tedious to
 * reach by hand (loading, error, empty), and keep working when no phone is
 * plugged in. Device testing still matters for anything touching the Keystore,
 * SQLCipher, or the file picker — none of which these replace.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PIXEL_5_QUALIFIERS)
abstract class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * Renders [content] inside the app theme and writes it to
     * `app/src/test/screenshots/<name>.png`.
     *
     * The theme wrapper is not optional: rendering a component outside it would
     * exercise Material defaults rather than the design system, which is the
     * opposite of what these tests are for.
     */
    protected fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            FinanceMateTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                ) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}

/**
 * Pixel 5 proportions — matching the test device, and deliberately smaller than
 * the Pixel 8 Pro the designs were drawn at. If a screen only works with the
 * extra vertical room, that should show up here rather than on someone's phone.
 */
const val PIXEL_5_QUALIFIERS: String = "w411dp-h891dp-normal-long-notround-any-420dpi-keyshidden-nonav"
