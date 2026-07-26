package dev.financemate.feature.insight.recurring

import dev.financemate.core.model.MerchantKey
import dev.financemate.core.model.Transaction
import dev.financemate.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A repeating charge from one merchant.
 *
 * This is the object every savings feature is built on — subscriptions,
 * duplicates, price rises, the bill calendar, and the cashflow forecast all read
 * from it.
 */
public data class RecurringSeries(
    val merchantKey: MerchantKey,
    val cadence: Cadence,
    /** Every matched charge, oldest first. */
    val occurrences: List<Transaction>,
    /**
     * The amount to expect next time.
     *
     * The **median**, not the mean: a single unusual charge (an annual top-up, a
     * one-off overage) would drag a mean far enough to make a price-rise
     * detector fire on noise.
     */
    val typicalAmount: Money,
    /** 0.0..1.0. See `RecurringSeriesDetector` for what feeds this. */
    val confidence: Double,
    /** Gaps that look like a skipped payment rather than the end of the series. */
    val missedOccurrences: Int,
) {
    val firstSeen: LocalDate get() = occurrences.first().postedDate
    val lastSeen: LocalDate get() = occurrences.last().postedDate

    /** When the next charge is due, projected from the last one. */
    val nextExpected: LocalDate get() = lastSeen.plusDays(cadence.approximateDays.toLong())

    /**
     * Whether this still appears to be live.
     *
     * A charge is treated as cancelled once it is more than one and a half
     * periods overdue. Being strict here would flag every slightly-late monthly
     * bill as cancelled; being loose would keep dead subscriptions in the user's
     * forecast for months.
     */
    public fun isActive(asOf: LocalDate): Boolean {
        val overdueBy = ChronoUnit.DAYS.between(lastSeen, asOf)
        return overdueBy <= cadence.approximateDays * 1.5
    }

    /** Total cost over a year at the current amount. */
    public val annualisedCost: Money
        get() = typicalAmount.abs().scaleBy(
            BigDecimal(cadence.occurrencesPerYear).setScale(4, RoundingMode.HALF_UP),
        )

    /** Equivalent monthly cost, for comparing an annual plan against a monthly one. */
    public val monthlyEquivalent: Money
        get() = annualisedCost.scaleBy(BigDecimal.ONE.divide(BigDecimal(12), 6, RoundingMode.HALF_UP))

    /** The amounts charged, oldest first. Used by price-rise detection. */
    public val amountHistory: List<Money> get() = occurrences.map { it.amount }
}
