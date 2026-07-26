package dev.financemate.core.parsing

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parses the date formats banks put in statement exports.
 *
 * The dangerous case is `01/02/2026`. In a US export that is 2 January; in a UK
 * export it is 1 February. Nothing in the string says which, and guessing wrong
 * shifts transactions into the wrong month — quietly wrecking every monthly
 * budget total while every individual transaction still looks correct.
 *
 * So the ordering preference is explicit ([DateOrder]) rather than assumed, and
 * [inferOrder] can determine it from a whole column when at least one row is
 * unambiguous (any day value above 12).
 */
public object DateParser {

    public enum class DateOrder {
        /** US: MM/DD/YYYY. */
        MONTH_FIRST,

        /** Most of the rest of the world: DD/MM/YYYY. */
        DAY_FIRST,
    }

    /** Formats with no ambiguity — the field order is fixed by the format itself. */
    private val UNAMBIGUOUS_PATTERNS: List<DateTimeFormatter> = listOf(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "yyyyMMdd",
        "dd-MMM-yyyy",
        "dd MMM yyyy",
        "MMM dd, yyyy",
        "MMM d, yyyy",
        "d MMM yyyy",
        "MMMM d, yyyy",
        "dd-MMM-yy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.US) }

    private val SLASH_OR_DASH = Regex("""^(\d{1,4})[/\-.](\d{1,2})[/\-.](\d{2,4})$""")

    /**
     * Parses [raw], returning null when it is not a date.
     *
     * @param order how to read an ambiguous numeric date.
     * @param twoDigitYearPivot years below this map to 2000s, at or above to
     *   1900s. Statements are recent, so the default keeps `26` as 2026 while
     *   still reading `99` as 1999.
     */
    public fun parse(
        raw: String,
        order: DateOrder = DateOrder.MONTH_FIRST,
        twoDigitYearPivot: Int = 70,
    ): LocalDate? {
        val text = raw.trim().removeSurrounding("\"").trim()
        if (text.isEmpty()) return null

        // Some exports append a time or timezone; the date is the leading token.
        val dateToken = text.substringBefore(' ').takeIf { it.contains('/') || it.contains('-') }
            ?: text

        SLASH_OR_DASH.matchEntire(dateToken)?.let { match ->
            val (a, b, c) = match.destructured
            return fromNumericParts(a, b, c, order, twoDigitYearPivot)
        }

        for (formatter in UNAMBIGUOUS_PATTERNS) {
            runCatching { return LocalDate.parse(text, formatter) }
                .onFailure { if (it !is DateTimeParseException) throw it }
        }
        return null
    }

    /**
     * Infers the ordering used by a column of dates.
     *
     * A value above 12 in the first position can only be a day, and a value above
     * 12 in the second position can only be a month's day — so a single such row
     * settles the whole column. Falls back to [default] when every row is
     * ambiguous, which happens only if no date in the file falls after the 12th.
     */
    public fun inferOrder(
        samples: List<String>,
        default: DateOrder = DateOrder.MONTH_FIRST,
    ): DateOrder {
        var monthFirstEvidence = 0
        var dayFirstEvidence = 0

        for (sample in samples) {
            val match = SLASH_OR_DASH.matchEntire(sample.trim()) ?: continue
            val (first, second, _) = match.destructured
            // A four-digit leading value is a year: unambiguous, no evidence here.
            if (first.length == 4) continue

            val a = first.toIntOrNull() ?: continue
            val b = second.toIntOrNull() ?: continue

            if (a > 12 && b <= 12) dayFirstEvidence++
            if (b > 12 && a <= 12) monthFirstEvidence++
        }

        return when {
            monthFirstEvidence > dayFirstEvidence -> DateOrder.MONTH_FIRST
            dayFirstEvidence > monthFirstEvidence -> DateOrder.DAY_FIRST
            else -> default
        }
    }

    private fun fromNumericParts(
        first: String,
        second: String,
        third: String,
        order: DateOrder,
        pivot: Int,
    ): LocalDate? {
        // ISO-style: 2026-03-14
        if (first.length == 4) {
            val year = first.toIntOrNull() ?: return null
            val month = second.toIntOrNull() ?: return null
            val day = third.toIntOrNull() ?: return null
            return safeDate(year, month, day)
        }

        val year = expandYear(third.toIntOrNull() ?: return null, third.length, pivot)
        val a = first.toIntOrNull() ?: return null
        val b = second.toIntOrNull() ?: return null

        // Let an out-of-range value override the stated preference: if the first
        // field is 25 it cannot be a month, whatever the caller expected.
        val (month, day) = when {
            a > 12 && b <= 12 -> b to a
            b > 12 && a <= 12 -> a to b
            order == DateOrder.MONTH_FIRST -> a to b
            else -> b to a
        }

        return safeDate(year, month, day)
    }

    private fun expandYear(value: Int, digits: Int, pivot: Int): Int = when {
        digits >= 4 -> value
        value < pivot -> 2000 + value
        else -> 1900 + value
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()
}
