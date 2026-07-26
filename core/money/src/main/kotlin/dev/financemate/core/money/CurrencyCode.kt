package dev.financemate.core.money

import java.util.Currency

/**
 * An ISO-4217 currency code.
 *
 * Wrapping the raw string stops a currency from being silently confused with any
 * other string in the codebase, and gives one place to look up the minor-unit
 * scale. Most currencies use 2 decimal places, but JPY uses 0 and several use 3,
 * so the scale must never be assumed.
 */
@JvmInline
public value class CurrencyCode(public val code: String) {

    init {
        require(code.length == 3 && code.all { it in 'A'..'Z' }) {
            "Currency code must be three uppercase letters, got '$code'"
        }
    }

    /** Number of decimal places this currency subdivides into. USD -> 2, JPY -> 0. */
    public val minorUnitScale: Int
        get() = SCALES.getOrPut(code) {
            // A currency the JVM doesn't recognise is a programming error, not a
            // runtime condition to paper over with a default.
            val currency = requireNotNull(runCatching { Currency.getInstance(code) }.getOrNull()) {
                "Unknown ISO-4217 currency code '$code'"
            }
            // Currency.getDefaultFractionDigits() returns -1 for pseudo-currencies
            // such as XXX ("no currency"). Treat those as zero-decimal.
            currency.defaultFractionDigits.coerceAtLeast(0)
        }

    /** Number of minor units in one major unit. USD -> 100, JPY -> 1. */
    public val minorUnitsPerMajor: Long
        get() = POWERS_OF_TEN[minorUnitScale]

    override fun toString(): String = code

    public companion object {
        public val USD: CurrencyCode = CurrencyCode("USD")
        public val EUR: CurrencyCode = CurrencyCode("EUR")
        public val GBP: CurrencyCode = CurrencyCode("GBP")
        public val JPY: CurrencyCode = CurrencyCode("JPY")

        private val SCALES = java.util.concurrent.ConcurrentHashMap<String, Int>()

        private val POWERS_OF_TEN = longArrayOf(1, 10, 100, 1_000, 10_000)
    }
}
