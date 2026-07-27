package dev.financemate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation bar in both of its states.
 *
 * This exists because the bar is where the colour budget is easiest to overspend:
 * it is the one component visible on every screen, it has exactly two elements,
 * and both of them have a plausible claim to being orange. The first version gave
 * it to both, which is how the images below earn their place.
 */
@RunWith(AndroidJUnit4::class)
class BottomBarScreenshotTest : ScreenshotTest() {

    @Test
    fun savingsActive() {
        captureComponent("navbar-savings") {
            BottomBar(importActive = false, onSavings = {}, onImport = {})
        }
    }

    @Test
    fun importActive() {
        captureComponent("navbar-import") {
            BottomBar(importActive = true, onSavings = {}, onImport = {})
        }
    }
}
