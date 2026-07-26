package dev.financemate.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An exact monetary amount, stored as a whole number of minor units.
 *
 * Money is never represented as a floating-point number anywhere in FinanceMate.
 * `0.1 + 0.2 != 0.3` in binary floating point, and a budgeting app that drifts by
 * fractions of a cent across thousands of transactions produces totals its user
 * cannot reconcile against their bank. Integer minor units make every operation
 * here exact by construction.
 *
 * Negative amounts represent money leaving the account (debits); positive amounts
 * represent money arriving (credits).
 */
public data class Money(
    public val minorUnits: Long,
    public val currency: CurrencyCode,
) : Comparable<Money> {

    public val isZero: Boolean get() = minorUnits == 0L
    public val isPositive: Boolean get() = minorUnits > 0L
    public val isNegative: Boolean get() = minorUnits < 0L

    public operator fun plus(other: Money): Money =
        Money(Math.addExact(minorUnits, other.requireSameCurrency(this).minorUnits), currency)

    public operator fun minus(other: Money): Money =
        Money(Math.subtractExact(minorUnits, other.requireSameCurrency(this).minorUnits), currency)

    public operator fun times(factor: Long): Money =
        Money(Math.multiplyExact(minorUnits, factor), currency)

    public operator fun times(factor: Int): Money = times(factor.toLong())

    public operator fun unaryMinus(): Money = Money(Math.negateExact(minorUnits), currency)

    public fun abs(): Money = if (isNegative) -this else this

    override fun compareTo(other: Money): Int =
        minorUnits.compareTo(other.requireSameCurrency(this).minorUnits)

    /**
     * Scales by an arbitrary rational factor, rounding half-up to the nearest
     * minor unit. Use for percentages, FX conversion, and pro-rating.
     *
     * Rounding here is lossy by definition — that is exactly why [allocate] exists
     * for the cases where the parts must sum back to the whole.
     */
    public fun scaleBy(factor: BigDecimal, rounding: RoundingMode = RoundingMode.HALF_UP): Money =
        Money(
            BigDecimal(minorUnits).multiply(factor).setScale(0, rounding).longValueExact(),
            currency,
        )

    public fun percent(percent: BigDecimal): Money =
        scaleBy(percent.divide(BigDecimal(100)))

    /**
     * Splits this amount across [weights], guaranteeing the parts sum back to
     * exactly this amount.
     *
     * The naive approach — divide, round each part, hope — leaks or invents money.
     * Splitting $10.00 three ways gives $3.33 x 3 = $9.99, and the missing cent has
     * to go somewhere. This uses the largest-remainder method: floor every share,
     * then hand the leftover minor units out one at a time to the parts with the
     * biggest truncated remainder. The result is deterministic and always exact.
     *
     * @throws IllegalArgumentException if [weights] is empty, contains a negative
     *   weight, or sums to zero.
     */
    public fun allocate(weights: List<Long>): List<Money> {
        require(weights.isNotEmpty()) { "Cannot allocate across zero parts" }
        require(weights.all { it >= 0 }) { "Allocation weights must be non-negative, got $weights" }
        val totalWeight = weights.sumOf { it }
        require(totalWeight > 0) { "Allocation weights must not all be zero" }

        // Work in absolute value so flooring behaves symmetrically for debits and
        // credits; the sign is reapplied at the end.
        val sign = if (isNegative) -1L else 1L
        val total = Math.abs(minorUnits)

        val shares = LongArray(weights.size)
        var distributed = 0L
        for (i in weights.indices) {
            // total * weight / totalWeight, computed in BigDecimal so a large
            // amount multiplied by a large weight cannot overflow on the way.
            val share = BigDecimal(total)
                .multiply(BigDecimal(weights[i]))
                .divide(BigDecimal(totalWeight), 0, RoundingMode.FLOOR)
                .longValueExact()
            shares[i] = share
            distributed += share
        }

        var remainder = total - distributed

        // Hand out leftover units to the largest truncated remainders first, so the
        // allocation is stable and does not favour whoever happens to be first.
        val order = weights.indices.sortedWith(
            compareByDescending<Int> { i ->
                BigDecimal(total)
                    .multiply(BigDecimal(weights[i]))
                    .remainder(BigDecimal(totalWeight))
            }.thenBy { it },
        )
        var cursor = 0
        while (remainder > 0) {
            val target = order[cursor % order.size]
            // Never award a leftover unit to a zero-weight part; it asked for nothing.
            if (weights[target] > 0) {
                shares[target]++
                remainder--
            }
            cursor++
        }

        return shares.map { Money(it * sign, currency) }
    }

    /** Splits evenly into [parts], distributing any remainder one unit at a time. */
    public fun split(parts: Int): List<Money> {
        require(parts > 0) { "Cannot split into $parts parts" }
        return allocate(List(parts) { 1L })
    }

    /** The amount as a decimal in major units. For display and export only. */
    public fun toBigDecimal(): BigDecimal =
        BigDecimal(minorUnits).movePointLeft(currency.minorUnitScale)

    /** Plain, locale-independent representation: `-1234.56 USD`. */
    override fun toString(): String = "${toBigDecimal().toPlainString()} $currency"

    private fun requireSameCurrency(other: Money): Money {
        require(currency == other.currency) {
            "Cannot combine $currency with ${other.currency}. " +
                "Convert explicitly via an FX rate rather than mixing currencies."
        }
        return this
    }

    public companion object {
        public fun zero(currency: CurrencyCode): Money = Money(0, currency)

        public fun ofMinor(minorUnits: Long, currency: CurrencyCode): Money =
            Money(minorUnits, currency)

        /** Builds from a major-unit decimal, e.g. `12.34` USD -> 1234 minor units. */
        public fun ofMajor(major: BigDecimal, currency: CurrencyCode): Money {
            val scaled = major.setScale(currency.minorUnitScale, RoundingMode.UNNECESSARY)
            return Money(scaled.movePointRight(currency.minorUnitScale).longValueExact(), currency)
        }

        public fun ofMajor(major: String, currency: CurrencyCode): Money =
            ofMajor(BigDecimal(major), currency)
    }
}

/** Sums a collection of [Money], returning zero in [currency] when empty. */
public fun Iterable<Money>.sum(currency: CurrencyCode): Money =
    fold(Money.zero(currency)) { acc, money -> acc + money }
