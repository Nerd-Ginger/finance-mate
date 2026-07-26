package dev.financemate.feature.insight.subscription

import dev.financemate.core.model.AccountId
import dev.financemate.core.model.MerchantKey
import dev.financemate.core.model.Transaction
import dev.financemate.core.model.TransactionId
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.feature.insight.recurring.RecurringSeriesDetector
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class MerchantClassifierTest {

    private val usd = CurrencyCode.USD
    private val today = LocalDate.of(2026, 7, 1)
    private var counter = 0

    private fun txn(merchant: String, minor: Long, date: LocalDate) = Transaction(
        id = TransactionId("t${counter++}"),
        accountId = AccountId("acct"),
        postedDate = date,
        amount = Money(minor, usd),
        rawDescription = merchant,
        merchantKey = MerchantKey(merchant),
        dedupHash = "h$counter",
    )

    private fun monthly(merchant: String, minor: Long, start: LocalDate, months: Int) =
        (0 until months).map { txn(merchant, minor, start.plusMonths(it.toLong())) }

    // --- Override precedence ------------------------------------------------------------

    @Test
    fun `falls back to the built-in catalogue when there is no override`() {
        val classifier = OverridingMerchantClassifier(emptyMap())
        classifier.classify(MerchantKey("NETFLIX")) shouldBe ServiceClass.VIDEO_STREAMING
        classifier.classify(MerchantKey("SOME LOCAL GYM")) shouldBe null
    }

    @Test
    fun `a user classification covers a merchant the catalogue does not know`() {
        // The whole point: the catalogue can never cover regional or niche
        // services, and the user can see their own statement.
        val classifier = OverridingMerchantClassifier(
            mapOf(MerchantKey("BOULDER ROCK CLUB") to MerchantOverride.Classified(ServiceClass.FITNESS)),
        )
        classifier.classify(MerchantKey("BOULDER ROCK CLUB")) shouldBe ServiceClass.FITNESS
    }

    @Test
    fun `a user classification beats the built-in catalogue`() {
        val classifier = OverridingMerchantClassifier(
            mapOf(MerchantKey("NETFLIX") to MerchantOverride.Classified(ServiceClass.SOFTWARE)),
        )
        classifier.classify(MerchantKey("NETFLIX")) shouldBe ServiceClass.SOFTWARE
    }

    @Test
    fun `not-a-service suppresses the catalogue rather than falling through`() {
        // Without a distinct "not a service" state, dismissing a wrong suggestion
        // would be impossible — the classifier would fall back to the catalogue
        // and suggest it again after every import.
        val classifier = OverridingMerchantClassifier(
            mapOf(MerchantKey("STEAM") to MerchantOverride.NotAService),
        )
        classifier.classify(MerchantKey("STEAM")) shouldBe null
        classifier.isOverridden(MerchantKey("STEAM")) shouldBe true
    }

    @Test
    fun `isOverridden distinguishes unclassified from dismissed`() {
        val classifier = OverridingMerchantClassifier(
            mapOf(MerchantKey("STEAM") to MerchantOverride.NotAService),
        )
        classifier.isOverridden(MerchantKey("STEAM")) shouldBe true
        classifier.isOverridden(MerchantKey("NETFLIX")) shouldBe false
    }

    // --- Effect on duplicate detection ------------------------------------------------------

    @Test
    fun `user tagging enables duplicate detection for unknown merchants`() {
        val charges = monthly("BOULDER ROCK CLUB", -8500, LocalDate.of(2026, 1, 5), 6) +
            monthly("CITY SWIM CENTRE", -4500, LocalDate.of(2026, 1, 15), 6)

        val series = RecurringSeriesDetector().detect(charges)

        // Unknown to the catalogue: no duplicate reported.
        SubscriptionAnalyser().analyse(series, today).duplicates.shouldBeEmpty()

        // Once the user says both are gyms, the overlap becomes visible.
        val tagged = SubscriptionAnalyser(
            classifier = OverridingMerchantClassifier(
                mapOf(
                    MerchantKey("BOULDER ROCK CLUB") to MerchantOverride.Classified(ServiceClass.FITNESS),
                    MerchantKey("CITY SWIM CENTRE") to MerchantOverride.Classified(ServiceClass.FITNESS),
                ),
            ),
        ).analyse(series, today)

        val duplicate = tagged.duplicates.single()
        duplicate.serviceClass shouldBe ServiceClass.FITNESS
        duplicate.subscriptions.first().merchantKey.value shouldBe "BOULDER ROCK CLUB"
        // Keeping the cheaper one saves the cost of the dearer:
        // $85.00 x 12.175 monthly periods a year = $1,034.88.
        duplicate.potentialAnnualSaving.minorUnits shouldBe 103_488L
    }

    @Test
    fun `dismissing a merchant removes it from duplicate findings`() {
        // Two music services, but one is a gift subscription the user does not
        // consider theirs to cancel. Dismissing it must make the finding go away
        // and stay away.
        val charges = monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 5), 6) +
            monthly("APPLE MUSIC", -1099, LocalDate.of(2026, 1, 20), 6)

        val series = RecurringSeriesDetector().detect(charges)
        SubscriptionAnalyser().analyse(series, today).duplicates.size shouldBe 1

        val dismissed = SubscriptionAnalyser(
            classifier = OverridingMerchantClassifier(
                mapOf(MerchantKey("APPLE MUSIC") to MerchantOverride.NotAService),
            ),
        ).analyse(series, today)

        dismissed.duplicates.shouldBeEmpty()
        // The subscription itself is still tracked and still counted in the total.
        dismissed.activeSubscriptions.size shouldBe 2
    }
}
