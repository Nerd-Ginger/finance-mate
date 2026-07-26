package dev.financemate.feature.insight.recurring

import dev.financemate.core.model.AccountId
import dev.financemate.core.model.MerchantKey
import dev.financemate.core.model.Transaction
import dev.financemate.core.model.TransactionId
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class RecurringSeriesDetectorTest {

    private val detector = RecurringSeriesDetector()
    private val usd = CurrencyCode.USD
    private val account = AccountId("acct")
    private var counter = 0

    private fun txn(
        merchant: String,
        minor: Long,
        date: LocalDate,
        isTransfer: Boolean = false,
    ) = Transaction(
        id = TransactionId("t${counter++}"),
        accountId = account,
        postedDate = date,
        amount = Money(minor, usd),
        rawDescription = merchant,
        merchantKey = MerchantKey(merchant),
        dedupHash = "h${counter}",
        isTransfer = isTransfer,
    )

    /** Charges on the same day-of-month across [months] months. */
    private fun monthly(
        merchant: String,
        minor: Long,
        start: LocalDate,
        months: Int,
    ): List<Transaction> = (0 until months).map { txn(merchant, minor, start.plusMonths(it.toLong())) }

    // --- Core detection --------------------------------------------------------------------

    @Test
    fun `detects a monthly subscription`() {
        val series = detector.detect(monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 6))

        series.size shouldBe 1
        series.single().cadence shouldBe Cadence.MONTHLY
        series.single().typicalAmount shouldBe Money(-1599, usd)
        series.single().confidence shouldBeGreaterThan 0.8
    }

    @Test
    fun `monthly detection survives month-length variation`() {
        // 15 Jan to 15 Feb is 31 days; 15 Feb to 15 Mar is 28. A detector that
        // insisted on 30 would split this into unrelated charges.
        val series = detector.detect(monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 15), 12))
        series.single().cadence shouldBe Cadence.MONTHLY
        series.single().occurrences.size shouldBe 12
    }

    @Test
    fun `detects weekly and fortnightly cadences`() {
        val weekly = (0 until 8).map {
            txn("GYM", -1500, LocalDate.of(2026, 1, 5).plusWeeks(it.toLong()))
        }
        detector.detect(weekly).single().cadence shouldBe Cadence.WEEKLY

        val fortnightly = (0 until 8).map {
            txn("CLEANER", -8000, LocalDate.of(2026, 1, 5).plusWeeks(it.toLong() * 2))
        }
        detector.detect(fortnightly).single().cadence shouldBe Cadence.FORTNIGHTLY
    }

    @Test
    fun `distinguishes four-weekly from monthly`() {
        // Four-weekly billing produces 13 charges a year, not 12. Treating it as
        // monthly under-states the annual cost by a full month.
        val fourWeekly = (0 until 8).map {
            txn("MOBILE", -3500, LocalDate.of(2026, 1, 5).plusDays(it * 28L))
        }
        val series = detector.detect(fourWeekly).single()
        series.cadence shouldBe Cadence.FOUR_WEEKLY
        series.cadence.occurrencesPerYear shouldBeGreaterThan 13.0
    }

    @Test
    fun `detects quarterly and annual cadences`() {
        val quarterly = (0 until 5).map {
            txn("INSURANCE", -24000, LocalDate.of(2024, 1, 10).plusMonths(it * 3L))
        }
        detector.detect(quarterly).single().cadence shouldBe Cadence.QUARTERLY

        val annual = (0 until 4).map {
            txn("DOMAIN RENEWAL", -1200, LocalDate.of(2022, 6, 1).plusYears(it.toLong()))
        }
        detector.detect(annual).single().cadence shouldBe Cadence.ANNUAL
    }

    // --- The edge cases that separate working from not ---------------------------------------

    @Test
    fun `a missed payment does not break the series`() {
        // A declined card is common. Splitting here would show the user two dead
        // subscriptions instead of one live one.
        val start = LocalDate.of(2026, 1, 15)
        val withGap = listOf(0L, 1L, 2L, 4L, 5L, 6L) // month 3 skipped
            .map { txn("NETFLIX", -1599, start.plusMonths(it)) }

        val series = detector.detect(withGap).single()
        series.cadence shouldBe Cadence.MONTHLY
        series.occurrences.size shouldBe 6
        series.missedOccurrences shouldBe 1
    }

    @Test
    fun `a price rise does not break the series`() {
        // Detecting the rise is the whole point, so amount must not be part of
        // matching.
        val start = LocalDate.of(2026, 1, 15)
        val charges = (0 until 4).map { txn("NETFLIX", -1599, start.plusMonths(it.toLong())) } +
            (4 until 8).map { txn("NETFLIX", -1799, start.plusMonths(it.toLong())) }

        val series = detector.detect(charges).single()
        series.occurrences.size shouldBe 8
        series.cadence shouldBe Cadence.MONTHLY
    }

    @Test
    fun `variable amounts still count as recurring`() {
        // A utility bill differs every month but is unmistakably a monthly
        // commitment. Confidence drops; detection does not fail.
        val start = LocalDate.of(2026, 1, 20)
        val amounts = listOf(-8500L, -12300L, -9700L, -14200L, -7800L, -11100L)
        val charges = amounts.mapIndexed { index, minor ->
            txn("CITY ELECTRIC", minor, start.plusMonths(index.toLong()))
        }

        val variable = detector.detect(charges).single()
        variable.cadence shouldBe Cadence.MONTHLY

        // Detected, but held with less certainty than an identical-amount
        // subscription over the same dates. Comparing the two is the meaningful
        // assertion; an absolute threshold would just pin today's weightings.
        val fixed = detector.detect(monthly("FIXED CO", -10_000, start, 6)).single()
        variable.confidence shouldBeLessThan fixed.confidence
        variable.confidence shouldBeGreaterThan 0.4
    }

    @Test
    fun `typical amount is the median so one outlier cannot skew it`() {
        val start = LocalDate.of(2026, 1, 15)
        val charges = listOf(-1000L, -1000L, -1000L, -1000L, -50_000L)
            .mapIndexed { index, minor -> txn("SERVICE", minor, start.plusMonths(index.toLong())) }

        // A mean would report about $108. The median reports the $10 the user
        // actually pays each month.
        detector.detect(charges).single().typicalAmount shouldBe Money(-1000, usd)
    }

    @Test
    fun `same-day repeats are not a daily subscription`() {
        // Five coffees on one day, then a few more days. Without collapsing by
        // day this looks like an extremely frequent recurring charge.
        val day = LocalDate.of(2026, 3, 14)
        val charges = List(5) { txn("COFFEE SHOP", -375, day) } +
            List(3) { txn("COFFEE SHOP", -375, day.plusDays(it + 1L)) }

        detector.detect(charges).shouldBeEmpty()
    }

    // --- Things that must NOT be detected -----------------------------------------------------

    @Test
    fun `two transactions are not a pattern`() {
        val charges = listOf(
            txn("RANDOM SHOP", -2500, LocalDate.of(2026, 1, 10)),
            txn("RANDOM SHOP", -2500, LocalDate.of(2026, 2, 10)),
        )
        detector.detect(charges).shouldBeEmpty()
    }

    @Test
    fun `irregular spending is not recurring`() {
        // Groceries: same merchant, no rhythm. Reporting this as a subscription
        // would destroy trust in the feature.
        val dates = listOf(3, 9, 10, 21, 22, 23, 28).map { LocalDate.of(2026, 1, it) }
        val charges = dates.map { txn("GROCERY STORE", -4200, it) }

        detector.detect(charges).shouldBeEmpty()
    }

    @Test
    fun `income is not treated as a recurring charge`() {
        // Salary is regular, but it is not a subscription to cancel.
        val charges = (0 until 6).map {
            txn("PAYROLL", 250_000, LocalDate.of(2026, 1, 15).plusMonths(it.toLong()))
        }
        detector.detect(charges).shouldBeEmpty()
    }

    @Test
    fun `transfers between own accounts are excluded`() {
        // A monthly credit-card payment is regular, but counting it as a
        // subscription would double-count every purchase on that card.
        val charges = (0 until 6).map {
            txn("CREDIT CARD PAYMENT", -50_000, LocalDate.of(2026, 1, 15).plusMonths(it.toLong()), isTransfer = true)
        }
        detector.detect(charges).shouldBeEmpty()
    }

    // --- Derived figures ------------------------------------------------------------------------

    @Test
    fun `annualised cost reflects cadence`() {
        val monthly = detector.detect(monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 6)).single()
        // 15.99 * 12.175 periods per year, to the cent.
        monthly.annualisedCost.minorUnits shouldBe 19_468L

        val annual = detector.detect(
            (0 until 4).map { txn("DOMAIN", -1200, LocalDate.of(2022, 6, 1).plusYears(it.toLong())) },
        ).single()
        annual.annualisedCost.minorUnits shouldBe 1201L
    }

    @Test
    fun `next expected date projects from the last charge`() {
        val series = detector.detect(monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 4)).single()
        series.lastSeen shouldBe LocalDate.of(2026, 4, 15)
        series.nextExpected shouldBe LocalDate.of(2026, 5, 15)
    }

    @Test
    fun `a long-overdue series is treated as cancelled`() {
        val series = detector.detect(monthly("OLD GYM", -3000, LocalDate.of(2025, 1, 15), 5)).single()

        // Last charge May 2025.
        series.isActive(LocalDate.of(2025, 6, 20)) shouldBe true
        series.isActive(LocalDate.of(2025, 7, 20)) shouldBe false
    }

    @Test
    fun `a slightly late payment is still active`() {
        // Being strict here would flag every late monthly bill as cancelled.
        val series = detector.detect(monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 4)).single()
        series.isActive(LocalDate.of(2026, 5, 20)) shouldBe true
    }

    @Test
    fun `results are ordered by annual cost`() {
        val charges = monthly("CHEAP", -299, LocalDate.of(2026, 1, 5), 6) +
            monthly("EXPENSIVE", -4999, LocalDate.of(2026, 1, 10), 6) +
            monthly("MIDDLING", -1599, LocalDate.of(2026, 1, 20), 6)

        detector.detect(charges).map { it.merchantKey.value } shouldBe
            listOf("EXPENSIVE", "MIDDLING", "CHEAP")
    }

    @Test
    fun `multiple subscriptions are detected independently`() {
        val charges = monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 6) +
            monthly("SPOTIFY", -1099, LocalDate.of(2026, 1, 3), 6) +
            (0 until 8).map { txn("GYM", -1500, LocalDate.of(2026, 1, 6).plusWeeks(it.toLong())) }

        val series = detector.detect(charges)
        series.size shouldBe 3
        series.map { it.cadence }.toSet() shouldBe setOf(Cadence.MONTHLY, Cadence.WEEKLY)
    }

    // --- Cadence windows --------------------------------------------------------------------------

    @Test
    fun `gap lookup is deterministic where windows overlap`() {
        // Four-weekly and monthly windows necessarily overlap: a monthly charge
        // billed on the 15th has a 28-day gap every February. Closest-match
        // makes the lookup independent of declaration order.
        Cadence.forGap(28) shouldBe Cadence.FOUR_WEEKLY
        Cadence.forGap(29) shouldBe Cadence.MONTHLY
        Cadence.forGap(30) shouldBe Cadence.MONTHLY
        Cadence.forGap(31) shouldBe Cadence.MONTHLY

        // Only the four-weekly/monthly pair is allowed to be ambiguous.
        (1L..400L).forEach { gap ->
            val matching = Cadence.entries.filter { it.matches(gap) }
            if (matching.size > 1) {
                matching.toSet() shouldBe setOf(Cadence.FOUR_WEEKLY, Cadence.MONTHLY)
            }
        }
    }

    @Test
    fun `a February gap does not turn a monthly subscription four-weekly`() {
        // Jan 15 to Feb 15 is 31 days; Feb 15 to Mar 15 is 28 — the same gap a
        // four-weekly charge produces. Day-of-month stability is what separates
        // them, and getting it wrong overstates the annual cost by a payment.
        val series = detector.detect(monthly("NETFLIX", -1599, LocalDate.of(2026, 1, 15), 6)).single()
        series.cadence shouldBe Cadence.MONTHLY
    }

    @Test
    fun `a month-end subscription stays monthly through February`() {
        // Billed on the 31st, which posts on the 28th in February.
        val dates = listOf(
            LocalDate.of(2026, 1, 31),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 4, 30),
            LocalDate.of(2026, 5, 31),
        )
        val series = detector.detect(dates.map { txn("HOSTING", -2500, it) }).single()
        series.cadence shouldBe Cadence.MONTHLY
    }

    @Test
    fun `four-weekly billing is not mistaken for monthly`() {
        // Walks backwards through the calendar, so the day-of-month drifts.
        val fourWeekly = (0 until 8).map {
            txn("MOBILE PLAN", -3500, LocalDate.of(2026, 1, 5).plusDays(it * 28L))
        }
        detector.detect(fourWeekly).single().cadence shouldBe Cadence.FOUR_WEEKLY
    }
}
