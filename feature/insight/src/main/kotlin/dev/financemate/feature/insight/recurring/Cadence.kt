package dev.financemate.feature.insight.recurring

/**
 * How often a recurring charge repeats.
 *
 * ## Why the windows overlap, and what resolves it
 *
 * A gap of 28 days is genuinely ambiguous. A subscription billed every four
 * weeks produces 28-day gaps by definition — and so does a *monthly*
 * subscription billed on the 15th, every February. There is no gap length that
 * separates them, so [FOUR_WEEKLY] and [MONTHLY] deliberately overlap.
 *
 * The distinction is made elsewhere, on the signal that actually differs:
 * monthly charges land on the same **day of the month** while four-weekly
 * charges drift backwards through the calendar. `RecurringSeriesDetector`
 * disambiguates on that, not on gap length.
 *
 * The difference matters financially: four-weekly billing produces 13 charges a
 * year rather than 12, so calling it monthly understates the annual cost by a
 * full month's payment.
 */
public enum class Cadence(
    public val approximateDays: Int,
    public val toleranceDays: Int,
    public val displayName: String,
) {
    // Tolerances reflect why each cadence drifts:
    //  - weekly/fortnightly shift when a due date falls at a weekend
    //  - monthly swings 28..31 because months are not equal
    //  - annual drifts across leap years and renewal-date changes
    WEEKLY(7, 2, "Weekly"),
    FORTNIGHTLY(14, 3, "Every 2 weeks"),
    FOUR_WEEKLY(28, 2, "Every 4 weeks"),
    MONTHLY(30, 4, "Monthly"),
    QUARTERLY(91, 10, "Quarterly"),
    SEMIANNUAL(182, 15, "Every 6 months"),
    ANNUAL(365, 21, "Annually"),
    ;

    /** How many times this recurs in a year. Used to annualise costs. */
    public val occurrencesPerYear: Double
        get() = DAYS_IN_YEAR / approximateDays.toDouble()

    public fun matches(gapDays: Long): Boolean =
        gapDays >= approximateDays - toleranceDays && gapDays <= approximateDays + toleranceDays

    /** True where this cadence cannot be told from another by gap length alone. */
    public val isCalendarAmbiguous: Boolean
        get() = this == FOUR_WEEKLY || this == MONTHLY

    public companion object {
        private const val DAYS_IN_YEAR = 365.25

        /**
         * The cadence whose nominal period is closest to [gapDays], among those
         * whose window contains it.
         *
         * Closest-match rather than first-match, so the result does not depend on
         * declaration order where windows overlap. A 29-day gap sits exactly
         * between four-weekly and monthly, so ties break toward [MONTHLY]:
         * monthly billing is far more common, and the detector re-checks the
         * choice against day-of-month stability regardless.
         */
        public fun forGap(gapDays: Long): Cadence? = entries
            .filter { it.matches(gapDays) }
            .minWithOrNull(
                compareBy(
                    { kotlin.math.abs(gapDays - it.approximateDays) },
                    { if (it == MONTHLY) 0 else 1 },
                ),
            )
    }
}
