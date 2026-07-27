package dev.financemate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.financemate.ui.onboarding.EgressProofScreen
import dev.financemate.ui.onboarding.EgressProofUiState
import dev.financemate.ui.onboarding.WelcomeScreen
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenshotTest : ScreenshotTest() {

    @Test
    fun welcome() {
        capture("onboarding-welcome") {
            WelcomeScreen(onImport = {}, onSeeEgress = {})
        }
    }

    /** A device that has never enabled AI. The state almost every user is in. */
    @Test
    fun egressProofOnACleanDevice() {
        capture("onboarding-egress-proof") {
            EgressProofScreen(
                state = EgressProofUiState(requestCount = 0),
                onBack = {},
                onContinue = {},
            )
        }
    }

    /**
     * The ledger has not opened yet.
     *
     * Captured because the tempting shortcut - defaulting the count to zero -
     * would look identical to the honest version in every screenshot, and would
     * be a false claim on the one screen that exists to be verifiable.
     */
    @Test
    fun egressProofBeforeTheCountIsKnown() {
        capture("onboarding-egress-proof-checking") {
            EgressProofScreen(
                state = EgressProofUiState(requestCount = null),
                onBack = {},
                onContinue = {},
            )
        }
    }
}
