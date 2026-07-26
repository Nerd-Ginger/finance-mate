package dev.financemate.core.parsing

import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses the amount formats that turn up in bank exports.
 *
 * There are more of these than you would hope:
 *
 * ```
 * -1234.56      1,234.56      $1,234.56     (1,234.56)     1234.56-
 * 1.234,56      1 234,56      USD 1,234.56  -$1,234.56
 * ```
 *
 * Parentheses and a trailing minus are both accounting notation for negative,
 * and both appear in real exports. Getting either wrong flips the sign of a
 * transaction, which is the single most damaging parse error possible — the
 * amount is right, so nothing looks broken, but income and spending swap places.
 */
public object AmountParser {

    private val CURRENCY_SYMBOLS = Regex("""[$£€¥₹]""")
    private val CURRENCY_CODE_PREFIX = Regex("""^[A-Z]{3}\s+""")
    private val NON_NUMERIC = Regex("""[^0-9.,\-]""")

    /**
     * Parses [raw] into a decimal, or returns null when it is not a number.
     *
     * Returns null rather than throwing because a statement often contains
     * non-amount rows (headers, section titles, running balances written as
     * text) and the caller decides whether that is a problem.
     */
    public fun parseDecimal(raw: String): BigDecimal? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        var negative = false

        // Accounting-style parentheses: (1,234.56) means -1234.56
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1).trim()
        }

        text = text.replace(CURRENCY_CODE_PREFIX, "")
        text = CURRENCY_SYMBOLS.replace(text, "")
        // Spaces of any kind (including the non-breaking space used as a
        // thousands separator in some locales) are stripped by NON_NUMERIC below.
        text = text.trim()

        // Trailing minus: "1234.56-"
        if (text.endsWith("-")) {
            negative = true
            text = text.dropLast(1).trim()
        }
        if (text.startsWith("-")) {
            negative = true
            text = text.drop(1).trim()
        }
        if (text.startsWith("+")) {
            text = text.drop(1).trim()
        }

        text = NON_NUMERIC.replace(text, "")
        if (text.isEmpty()) return null

        val normalised = normaliseSeparators(text) ?: return null

        val value = runCatching { BigDecimal(normalised) }.getOrNull() ?: return null
        return if (negative) value.negate() else value
    }

    /**
     * Parses [raw] into [Money] in [currency].
     *
     * @param invertSign set for statements where a positive figure means money
     *   leaving the account — American Express and Discover both report charges
     *   as positive. Flipping here, at the boundary, is what lets every
     *   downstream calculation trust that negative means spent.
     */
    public fun parse(
        raw: String,
        currency: CurrencyCode,
        invertSign: Boolean = false,
    ): Money? {
        val decimal = parseDecimal(raw) ?: return null
        val adjusted = if (invertSign) decimal.negate() else decimal
        // Round to the currency's precision rather than rejecting. An amount with
        // more decimals than the currency supports is almost always an FX-derived
        // figure; losing sub-cent precision is far better than dropping the whole
        // transaction, which is what refusing to parse would do.
        val scaled = adjusted.setScale(currency.minorUnitScale, RoundingMode.HALF_UP)
        return runCatching { Money.ofMajor(scaled, currency) }.getOrNull()
    }

    /**
     * Decides which of `.` and `,` is the decimal separator.
     *
     * `1,234.56` and `1.234,56` are the same number written for different
     * locales, and `1,234` is ambiguous on its own. The rule used here: whichever
     * separator appears last is the decimal point, unless the trailing group is
     * three digits long and the separator appears more than once, which makes it
     * a thousands separator.
     */
    private fun normaliseSeparators(text: String): String? {
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')

        return when {
            lastComma < 0 && lastDot < 0 -> text

            // Only dots present.
            lastComma < 0 -> {
                val dots = text.count { it == '.' }
                val trailing = text.length - lastDot - 1
                if (dots > 1 || trailing == 3 && dots == 1 && looksLikeGrouping(text, '.')) {
                    text.replace(".", "")
                } else {
                    text
                }
            }

            // Only commas present.
            lastDot < 0 -> {
                val commas = text.count { it == ',' }
                val trailing = text.length - lastComma - 1
                if (commas > 1 || trailing == 3 && looksLikeGrouping(text, ',')) {
                    text.replace(",", "")
                } else {
                    text.replace(',', '.')
                }
            }

            // Both present: the later one is the decimal separator.
            lastComma > lastDot -> text.replace(".", "").replace(',', '.')
            else -> text.replace(",", "")
        }
    }

    /**
     * True when [separator] is being used to group thousands, i.e. every group
     * after the first is exactly three digits.
     *
     * This is what distinguishes `1,234` (one thousand two hundred) from `1,23`
     * (a malformed decimal) and from `12,34` (European decimal notation).
     */
    private fun looksLikeGrouping(text: String, separator: Char): Boolean {
        val parts = text.split(separator)
        if (parts.size < 2) return false
        if (parts.first().isEmpty() || parts.first().length > 3) return parts.drop(1).all { it.length == 3 }
        return parts.drop(1).all { it.length == 3 }
    }
}
