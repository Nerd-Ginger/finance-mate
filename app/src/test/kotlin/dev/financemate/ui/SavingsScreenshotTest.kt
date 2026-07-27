package dev.financemate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.financemate.core.model.MerchantKey
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.feature.insight.recurring.Cadence
import dev.financemate.feature.insight.subscription.DuplicateSubscriptionFinding
import dev.financemate.feature.insight.subscription.PriceChangeFinding
import dev.financemate.feature.insight.subscription.ServiceClass
import dev.financemate.feature.insight.subscription.Subscription
import dev.financemate.feature.insight.subscription.SubscriptionReport
import dev.financemate.ui.savings.SavingsScreen
import dev.financemate.ui.savings.SavingsUiState
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Renders the savings screen in each of its states.
 *
 * The fixture below is deliberately close to what the sample statement produces,
 * so the committed images show the screen as a real user would first meet it
 * rather than as an idealised mock.
 */
@RunWith(AndroidJUnit4::class)
class SavingsScreenshotTest : ScreenshotTest() {

    private val usd = CurrencyCode.USD
    private fun usd(minor: Long) = Money(minor, usd)

    @Test
    fun loaded() {
        capture("savings-loaded") {
            SavingsScreen(state = loadedState(), onTagMerchant = {})
        }
    }

    @Test
    fun empty() {
        capture("savings-empty") {
            SavingsScreen(
                state = SavingsUiState.Loaded(
                    report = SubscriptionReport(
                        subscriptions = emptyList(),
                        duplicates = emptyList(),
                        priceIncreases = emptyList(),
                        totalAnnualCost = Money.zero(usd),
                        totalMonthlyCost = Money.zero(usd),
                    ),
                    untaggedMerchantCount = 0,
                ),
                onTagMerchant = {},
            )
        }
    }

    @Test
    fun loading() {
        capture("savings-loading") {
            SavingsScreen(state = SavingsUiState.Loading, onTagMerchant = {})
        }
    }

    @Test
    fun failed() {
        capture("savings-failed") {
            SavingsScreen(
                state = SavingsUiState.Failed("Could not analyze your transactions (IOException)."),
                onTagMerchant = {},
            )
        }
    }

    // --- Fixture ---------------------------------------------------------------

    private fun subscription(
        name: String,
        minorPerCharge: Long,
        serviceClass: ServiceClass?,
        confidence: Double = 0.95,
        active: Boolean = true,
    ): Subscription {
        val annual = (minorPerCharge * 12.175).toLong()
        return Subscription(
            merchantKey = MerchantKey(name),
            serviceClass = serviceClass,
            cadence = Cadence.MONTHLY,
            currentAmount = usd(minorPerCharge),
            annualCost = usd(annual),
            monthlyEquivalent = usd(annual / 12),
            firstSeen = LocalDate.of(2026, 1, 5),
            lastCharged = LocalDate.of(2026, 7, 5),
            nextExpected = LocalDate.of(2026, 8, 5),
            isActive = active,
            confidence = confidence,
            amountHistory = listOf(usd(minorPerCharge)),
            chargeDates = listOf(LocalDate.of(2026, 7, 5)),
        )
    }

    private fun loadedState(): SavingsUiState.Loaded {
        val spotify = subscription("SPOTIFY", 1199, ServiceClass.MUSIC_STREAMING)
        val appleMusic = subscription("APPLE MUSIC", 1099, ServiceClass.MUSIC_STREAMING)
        val netflix = subscription("NETFLIX", 1799, ServiceClass.VIDEO_STREAMING)
        val gym = subscription("PLANET FITNESS", 2499, ServiceClass.FITNESS)
        // Deliberately unclassified and low-confidence, so the committed image
        // shows the uncertain treatment rather than only the confident one.
        val parking = subscription("NORTHGATE PARKING", 950, null, confidence = 0.61)

        return SavingsUiState.Loaded(
            report = SubscriptionReport(
                subscriptions = listOf(spotify, appleMusic, netflix, gym, parking),
                duplicates = listOf(
                    DuplicateSubscriptionFinding(
                        serviceClass = ServiceClass.MUSIC_STREAMING,
                        subscriptions = listOf(spotify, appleMusic),
                        potentialAnnualSaving = usd(14_598),
                    ),
                ),
                priceIncreases = listOf(
                    PriceChangeFinding(
                        merchantKey = MerchantKey("NETFLIX"),
                        previousAmount = usd(1599),
                        currentAmount = usd(1799),
                        changedOn = LocalDate.of(2026, 5, 15),
                        annualImpact = usd(2435),
                        percentChange = BigDecimal("12.5"),
                    ),
                ),
                totalAnnualCost = usd(231_800),
                totalMonthlyCost = usd(19_313),
            ),
            untaggedMerchantCount = 1,
        )
    }
}
