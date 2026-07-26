package dev.financemate.feature.insight.subscription

import dev.financemate.core.model.AccountId
import dev.financemate.core.model.MerchantKey
import dev.financemate.core.model.Transaction
import dev.financemate.core.model.TransactionId
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.feature.insight.recurring.RecurringSeriesDetector
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class SubscriptionAnalyserTest {

    private val detector = RecurringSeriesDetector()
    private val analyser = SubscriptionAnalyser()
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

    /** A monthly subscription running up to (but not including) [months] from [start]. */
    private fun monthly(merchant: String, minor: Long, start: LocalDate, months: Int) =
        (0 until months).map { txn(merchant, minor, start.plusMonths(it.toLong())) }

    private fun analyse(transactions: List<Transaction>) =
        analyser.analyse(detector.detect(transactions), today)

    // --- Duplicate subscriptions — the headline feature ---------------------------------

    @Test
    fun `finds two music services running at once`() {
        val report = analyse(
            monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 5), 6) +
                monthly("APPLE MUSIC", -1099, LocalDate.of(2026, 1, 20), 6),
        )

        val duplicate = report.duplicates.single()
        duplicate.serviceClass shouldBe ServiceClass.MUSIC_STREAMING
        duplicate.subscriptions.size shouldBe 2
    }

    @Test
    fun `duplicate saving assumes keeping the cheapest`() {
        val report = analyse(
            monthly("DROPBOX", -1199, LocalDate.of(2026, 1, 5), 6) +
                monthly("GOOGLE STORAGE", -199, LocalDate.of(2026, 1, 12), 6),
        )

        val duplicate = report.duplicates.single()
        duplicate.serviceClass shouldBe ServiceClass.CLOUD_STORAGE
        // Most expensive listed first, and the saving is the cost of dropping it.
        duplicate.subscriptions.first().merchantKey.value shouldBe "DROPBOX"
        duplicate.potentialAnnualSaving.minorUnits shouldBe 14_598L
    }

    @Test
    fun `several video services are not treated as a problem`() {
        // Plenty of people subscribe to several on purpose. Flagging it turns a
        // savings feature into nagging, and users switch off nagging.
        val report = analyse(
            monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 5), 6) +
                monthly("HULU", -1799, LocalDate.of(2026, 1, 12), 6) +
                monthly("DISNEY PLUS", -1399, LocalDate.of(2026, 1, 20), 6),
        )

        report.duplicates.shouldBeEmpty()
        // Still counted in the total, so the user sees what they are spending.
        report.activeSubscriptions.size shouldBe 3
    }

    @Test
    fun `a cancelled duplicate is not reported`() {
        // Spotify ran Jan-Mar and stopped; Apple Music is current. That is a
        // switch, not an overlap, and reporting it would be wrong.
        val report = analyse(
            monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 5), 3) +
                monthly("APPLE MUSIC", -1099, LocalDate.of(2026, 4, 5), 3),
        )

        report.duplicates.shouldBeEmpty()
    }

    @Test
    fun `unknown merchants are never reported as duplicates`() {
        // A wrong duplicate claim is worse than a missed one: the user acts on
        // it, cancels something, and stops trusting the app.
        val report = analyse(
            monthly("SOME LOCAL SERVICE", -999, LocalDate.of(2026, 1, 5), 6) +
                monthly("ANOTHER LOCAL SERVICE", -999, LocalDate.of(2026, 1, 15), 6),
        )
        report.duplicates.shouldBeEmpty()
    }

    // --- Price rises -------------------------------------------------------------------

    @Test
    fun `detects a price rise that stuck`() {
        val start = LocalDate.of(2026, 1, 15)
        val charges = (0 until 4).map { txn("NETFLIX", -1599, start.plusMonths(it.toLong())) } +
            (4 until 7).map { txn("NETFLIX", -1799, start.plusMonths(it.toLong())) }

        val rise = analyse(charges).priceIncreases.single()
        rise.previousAmount shouldBe Money(1599, usd)
        rise.currentAmount shouldBe Money(1799, usd)
        rise.isIncrease shouldBe true
        // $2/month, annualised over 12.175 monthly periods.
        rise.annualImpact.minorUnits shouldBe 2435L
    }

    @Test
    fun `a one-off charge is not a price rise`() {
        // An annual add-on or a partial month must not read as a permanent
        // increase, or the user gets an alert every time anything varies.
        val start = LocalDate.of(2026, 1, 15)
        val charges = listOf(-999L, -999L, -4999L, -999L, -999L, -999L)
            .mapIndexed { index, minor -> txn("SOME SERVICE", minor, start.plusMonths(index.toLong())) }

        analyse(charges).priceIncreases.shouldBeEmpty()
    }

    @Test
    fun `tiny changes are ignored`() {
        // A few cents of tax or FX drift is not a vendor decision, and alerting
        // on it trains the user to ignore the alerts that matter.
        val start = LocalDate.of(2026, 1, 15)
        val charges = listOf(-1000L, -1000L, -1001L, -1002L, -1001L, -1000L)
            .mapIndexed { index, minor -> txn("SOME SERVICE", minor, start.plusMonths(index.toLong())) }

        analyse(charges).priceIncreases.shouldBeEmpty()
    }

    @Test
    fun `a price cut is not reported as an increase`() {
        val start = LocalDate.of(2026, 1, 15)
        val charges = (0 until 4).map { txn("SOME SERVICE", -1999, start.plusMonths(it.toLong())) } +
            (4 until 7).map { txn("SOME SERVICE", -999, start.plusMonths(it.toLong())) }

        analyse(charges).priceIncreases.shouldBeEmpty()
    }

    // --- Cost totals -------------------------------------------------------------------

    @Test
    fun `annual and monthly totals cover only active subscriptions`() {
        val report = analyse(
            monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 6) +
                // Ended in March, so should not count towards what the user pays now.
                monthly("OLD GYM", -5000, LocalDate.of(2025, 10, 1), 3),
        )

        report.activeSubscriptions.size shouldBe 1
        report.cancelledSubscriptions.size shouldBe 1
        report.totalAnnualCost.minorUnits shouldBe 19_468L
    }

    @Test
    fun `four-weekly billing is annualised over thirteen charges`() {
        // The financial reason the cadence distinction matters: calling this
        // monthly would understate the yearly cost by a full payment.
        val fourWeekly = (0 until 8).map {
            txn("MOBILE PLAN", -3500, LocalDate.of(2026, 1, 5).plusDays(it * 28L))
        }
        val report = analyse(fourWeekly)
        val subscription = report.activeSubscriptions.single()

        // 35.00 * 13.04 periods, versus 426.13 if treated as monthly.
        subscription.annualCost.minorUnits shouldBe 45_656L
    }

    @Test
    fun `identified savings aggregate across duplicate findings`() {
        val report = analyse(
            monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 5), 6) +
                monthly("APPLE MUSIC", -1099, LocalDate.of(2026, 1, 20), 6) +
                monthly("DROPBOX", -1199, LocalDate.of(2026, 1, 8), 6) +
                monthly("ICLOUD", -299, LocalDate.of(2026, 1, 14), 6),
        )

        report.duplicates.size shouldBe 2
        // Drop one music service (13.38/mo annualised) and Dropbox.
        report.identifiedAnnualSavings.minorUnits shouldBe (13_380L + 14_598L)
    }

    @Test
    fun `duplicate findings are ordered by what they would save`() {
        val report = analyse(
            monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 5), 6) +
                monthly("APPLE MUSIC", -1099, LocalDate.of(2026, 1, 20), 6) +
                monthly("DROPBOX", -1199, LocalDate.of(2026, 1, 8), 6) +
                monthly("ICLOUD", -299, LocalDate.of(2026, 1, 14), 6),
        )

        report.duplicates.first().serviceClass shouldBe ServiceClass.CLOUD_STORAGE
    }

    // --- Classification safety -----------------------------------------------------------

    @Test
    fun `classification is exact, never a substring match`() {
        // "AMERICAN EXPRESS" contains "EXPRESS"; a fuzzy match would pair it
        // with ExpressVPN and produce a confidently wrong recommendation.
        ServiceCatalogue.classify(MerchantKey("AMERICAN EXPRESS")) shouldBe null
        ServiceCatalogue.classify(MerchantKey("EXPRESSVPN")) shouldBe ServiceClass.VPN
        ServiceCatalogue.classify(MerchantKey("MAX")) shouldBe ServiceClass.VIDEO_STREAMING
        ServiceCatalogue.classify(MerchantKey("MAXWELL PLUMBING")) shouldBe null
    }

    @Test
    fun `subscriptions carry the detail the UI needs`() {
        val report = analyse(monthly("NETFLIX", -1599, LocalDate.of(2026, 2, 15), 5))
        val netflix = report.activeSubscriptions.single()

        netflix.serviceClass shouldBe ServiceClass.VIDEO_STREAMING
        netflix.currentAmount shouldBe Money(1599, usd)
        netflix.lastCharged shouldBe LocalDate.of(2026, 6, 15)
        netflix.nextExpected shouldBe LocalDate.of(2026, 7, 15)
        netflix.chargeDates.size shouldBe 5
        netflix.amountHistory.first().minorUnits shouldBe 1599L
    }

    @Test
    fun `low-confidence series are not presented as subscriptions`() {
        // Irregular spending at one merchant should never reach the
        // subscriptions list; a false subscription is what makes users stop
        // believing the savings numbers.
        val dates = listOf(3, 9, 10, 21, 22, 23, 28).map { LocalDate.of(2026, 1, it) }
        val report = analyse(dates.map { txn("GROCERY STORE", -4200, it) })
        report.subscriptions.shouldBeEmpty()
    }

    @Test
    fun `an empty ledger produces an empty report`() {
        val report = analyse(emptyList())
        report.subscriptions.shouldBeEmpty()
        report.duplicates.shouldBeEmpty()
        report.totalAnnualCost.isZero shouldBe true
        report.identifiedAnnualSavings.isZero shouldBe true
    }

    @Test
    fun `price change records when it happened`() {
        val start = LocalDate.of(2026, 1, 15)
        val charges = (0 until 4).map { txn("NETFLIX", -1599, start.plusMonths(it.toLong())) } +
            (4 until 7).map { txn("NETFLIX", -1799, start.plusMonths(it.toLong())) }

        val rise = analyse(charges).priceIncreases.single()
        rise.changedOn.shouldNotBeNull()
        rise.changedOn shouldBe LocalDate.of(2026, 5, 15)
    }
}
