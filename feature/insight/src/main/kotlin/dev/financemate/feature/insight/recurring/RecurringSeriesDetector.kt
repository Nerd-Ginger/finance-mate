package dev.financemate.feature.insight.recurring

import dev.financemate.core.model.MerchantKey
import dev.financemate.core.model.Transaction
import dev.financemate.core.money.Money
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Finds repeating charges in a transaction history.
 *
 * ## The approach
 *
 * Group by merchant, measure the gaps between charges, and see whether those
 * gaps cluster around a known period. That is the whole idea; the difficulty is
 * entirely in the edge cases, and the edge cases are what make the difference
 * between a feature that works and one the user stops trusting:
 *
 * - **A missed month is not the end of a subscription.** A card declines, the
 *   charge retries next cycle, and a naive detector sees two short series
 *   instead of one. A gap close to a whole multiple of the period is treated as
 *   a skipped occurrence.
 * - **Monthly means "same date", not "30 days".** Gaps swing 28–31 naturally,
 *   and February wrecks any fixed-day assumption.
 * - **Price rises must not break the series.** Netflix at $15.99 and Netflix at
 *   $17.99 are the same subscription — detecting the rise is the *point*, so
 *   amount is not part of the matching.
 * - **Variable amounts are still recurring.** A utility bill differs every month
 *   but is unmistakably a monthly commitment, so amount stability feeds
 *   confidence rather than gating detection.
 *
 * ## Bias
 *
 * Where it is uncertain, this reports lower confidence rather than staying
 * silent — a subscription surfaced with 0.6 confidence is something the user can
 * confirm in a second, whereas one that is never surfaced keeps costing them
 * money. The UI decides what to show at what threshold.
 */
public class RecurringSeriesDetector(
    /**
     * Fewest charges before a pattern is claimed.
     *
     * Three is the minimum that establishes a rhythm: two points define a gap,
     * but any two transactions have *some* gap between them, so two would make
     * every pair of coffees look like a subscription.
     */
    private val minimumOccurrences: Int = 3,

    /** Series below this are not returned at all. */
    private val minimumConfidence: Double = 0.4,
) {

    public fun detect(transactions: List<Transaction>): List<RecurringSeries> =
        transactions
            // Recurring *charges*. Incoming money has its own rhythm (salary),
            // handled separately by income detection, and mixing the two would
            // let a refund cancel out a subscription.
            .filter { it.amount.isNegative && !it.isTransfer }
            .groupBy { it.merchantKey }
            .mapNotNull { (merchant, charges) -> detectForMerchant(merchant, charges) }
            .filter { it.confidence >= minimumConfidence }
            .sortedByDescending { it.annualisedCost.abs().minorUnits }

    private fun detectForMerchant(
        merchant: MerchantKey,
        charges: List<Transaction>,
    ): RecurringSeries? {
        if (charges.size < minimumOccurrences) return null

        val ordered = charges.sortedBy { it.postedDate }

        // Same-day duplicates are two purchases, not two cycles of one
        // subscription; collapsing them keeps a busy coffee shop from looking
        // like a daily recurring charge.
        val byDay = ordered.groupBy { it.postedDate }.values.map { it.first() }
        if (byDay.size < minimumOccurrences) return null

        val gaps = byDay.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.postedDate, b.postedDate) }
        val guessed = bestCadence(gaps) ?: return null
        val candidate = disambiguateCalendarCadence(guessed, byDay.map { it.postedDate })

        val (matched, missed) = classifyGaps(gaps, candidate)

        // Most gaps must fit the cadence. Without this floor, irregular spending
        // at one merchant — groceries, a favourite restaurant — produces a
        // scattering of gaps where a couple happen to land in the weekly window,
        // and gets reported as a subscription. Surfacing a false subscription is
        // the fastest way to lose the user's trust in the whole feature.
        val gapFit = matched.toDouble() / gaps.size
        if (gapFit < MINIMUM_GAP_FIT) return null

        val amounts = byDay.map { it.amount }
        val confidence = scoreConfidence(
            cadence = candidate,
            gaps = gaps,
            matchedGaps = matched,
            missedOccurrences = missed,
            amounts = amounts,
            occurrenceCount = byDay.size,
        )

        return RecurringSeries(
            merchantKey = merchant,
            cadence = candidate,
            occurrences = byDay,
            typicalAmount = medianAmount(amounts),
            confidence = confidence,
            missedOccurrences = missed,
        )
    }

    /**
     * Separates four-weekly from monthly, which gap length cannot do.
     *
     * A monthly charge lands on the same **day of the month** every time; a
     * four-weekly charge walks backwards through the calendar, losing two or
     * three days a month. So the test is whether the day-of-month holds steady.
     *
     * Month-end charges need care: a subscription billed on the 31st posts on
     * the 28th in February without being any less monthly. Any date in the last
     * few days of a month is therefore treated as equivalent to any other
     * month-end date.
     *
     * Getting this right is worth real money — four-weekly billing means 13
     * charges a year, so misreading it as monthly understates the annual cost by
     * a full payment.
     */
    private fun disambiguateCalendarCadence(
        guess: Cadence,
        dates: List<LocalDate>,
    ): Cadence {
        if (!guess.isCalendarAmbiguous) return guess

        val dayOfMonthStable = dates.all { date ->
            val reference = dates.first()
            sameCalendarPosition(reference, date)
        }
        return if (dayOfMonthStable) Cadence.MONTHLY else Cadence.FOUR_WEEKLY
    }

    private fun sameCalendarPosition(a: LocalDate, b: LocalDate): Boolean {
        if (kotlin.math.abs(a.dayOfMonth - b.dayOfMonth) <= DAY_OF_MONTH_TOLERANCE) return true
        // Both sitting at the end of their respective months counts as the same
        // position, so a 31st-of-the-month charge is not broken by February.
        val aFromEnd = a.lengthOfMonth() - a.dayOfMonth
        val bFromEnd = b.lengthOfMonth() - b.dayOfMonth
        return aFromEnd <= MONTH_END_WINDOW && bFromEnd <= MONTH_END_WINDOW
    }

    /**
     * Picks the cadence that explains the most gaps.
     *
     * Uses the median gap as the starting hypothesis — robust against one long
     * gap from a skipped payment, which a mean is not — then verifies it against
     * every gap rather than trusting the median alone.
     */
    private fun bestCadence(gaps: List<Long>): Cadence? {
        if (gaps.isEmpty()) return null

        val median = gaps.sorted()[gaps.size / 2]
        Cadence.forGap(median)?.let { return it }

        // The median may itself be a skipped-payment gap. Fall back to whichever
        // cadence explains the largest share of gaps, requiring a majority so a
        // coincidental match cannot win.
        return Cadence.entries
            .map { cadence -> cadence to gaps.count { explains(cadence, it) != null } }
            .filter { (_, matches) -> matches > gaps.size / 2 }
            .maxByOrNull { (_, matches) -> matches }
            ?.first
    }

    /**
     * How many periods a gap represents, or null if it fits none.
     *
     * Allows up to three skipped cycles: a card that fails for a quarter is
     * plausible, but a year-long silence is a cancellation followed by a
     * re-subscription, which is genuinely a different thing.
     */
    private fun explains(cadence: Cadence, gapDays: Long): Int? {
        for (periods in 1..MAX_SKIPPED_PERIODS) {
            val expected = cadence.approximateDays.toLong() * periods
            // Tolerance widens with the number of periods, because drift
            // accumulates — but sub-linearly, so it cannot swallow everything.
            val tolerance = cadence.toleranceDays.toLong() * periods
            if (abs(gapDays - expected) <= tolerance) return periods
        }
        return null
    }

    private fun classifyGaps(gaps: List<Long>, cadence: Cadence): Pair<Int, Int> {
        var matched = 0
        var missed = 0
        for (gap in gaps) {
            val periods = explains(cadence, gap)
            if (periods != null) {
                matched++
                missed += periods - 1
            }
        }
        return matched to missed
    }

    /**
     * Confidence blends four signals:
     *
     * - **How many gaps fit** the cadence. The dominant term: a pattern that
     *   explains every gap is a pattern.
     * - **How many charges** there are. Ten is more convincing than three.
     * - **How stable the amount is.** A fixed price is a strong subscription
     *   signal, but a variable one only weakens confidence rather than
     *   disqualifying — utility bills are real commitments.
     * - **How many cycles were skipped.** Some tolerance, but a series held
     *   together by assumed skips is a weaker claim.
     */
    private fun scoreConfidence(
        cadence: Cadence,
        gaps: List<Long>,
        matchedGaps: Int,
        missedOccurrences: Int,
        amounts: List<Money>,
        occurrenceCount: Int,
    ): Double {
        val gapFit = matchedGaps.toDouble() / gaps.size

        val volume = when {
            occurrenceCount >= 12 -> 1.0
            occurrenceCount >= 6 -> 0.9
            occurrenceCount >= 4 -> 0.8
            else -> 0.7
        }

        val amountStability = amountStability(amounts)

        val skipPenalty = 1.0 - (missedOccurrences.toDouble() / (gaps.size + 1)).coerceAtMost(0.5)

        // Annual series are inherently thinner evidence: three charges span three
        // years, and a lot can look periodic over that distance.
        val cadencePenalty = if (cadence == Cadence.ANNUAL && occurrenceCount < 3) 0.7 else 1.0

        return (gapFit * 0.5 + volume * 0.2 + amountStability * 0.2 + skipPenalty * 0.1)
            .times(cadencePenalty)
            .coerceIn(0.0, 1.0)
    }

    /**
     * 1.0 when every charge is identical, falling as they vary.
     *
     * Measured as relative spread around the median so it is scale-free: a $2
     * swing means something very different on a $10 subscription than on a $500
     * mortgage payment.
     */
    private fun amountStability(amounts: List<Money>): Double {
        if (amounts.size < 2) return 0.5
        val values = amounts.map { abs(it.minorUnits).toDouble() }
        val median = values.sorted()[values.size / 2]
        if (median == 0.0) return 0.5

        val meanDeviation = values.map { abs(it - median) }.average()
        val relativeSpread = meanDeviation / median
        return (1.0 - relativeSpread).coerceIn(0.0, 1.0)
    }

    private fun medianAmount(amounts: List<Money>): Money {
        val sorted = amounts.sortedBy { it.minorUnits }
        return sorted[sorted.size / 2]
    }

    private companion object {
        const val MAX_SKIPPED_PERIODS = 3

        /** Share of gaps that must fit the cadence before a series is claimed. */
        const val MINIMUM_GAP_FIT = 0.6

        /** Day-of-month drift still counted as "the same date each month". */
        const val DAY_OF_MONTH_TOLERANCE = 1

        /** Distance from month end within which two dates count as equivalent. */
        const val MONTH_END_WINDOW = 3
    }
}

/** Convenience: only the series still live as of [asOf]. */
public fun List<RecurringSeries>.active(asOf: LocalDate): List<RecurringSeries> =
    filter { it.isActive(asOf) }
