package dev.financemate.ui

import dev.financemate.core.money.Money
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Formats money for display.
 *
 * Conversion to a decimal happens only here, at the very edge of the app. Every
 * calculation upstream works in whole minor units, so no rounding has occurred
 * before this point — this is presentation, not arithmetic.
 */
public fun Money.formatted(locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    runCatching { Currency.getInstance(currency.code) }
        .getOrNull()
        ?.let { format.currency = it }
    format.maximumFractionDigits = currency.minorUnitScale
    format.minimumFractionDigits = currency.minorUnitScale
    return format.format(toBigDecimal())
}

/**
 * Formats without the fractional part when it is zero.
 *
 * Used for annual totals, where "$1,035" reads faster than "$1,034.88" and the
 * cents carry no decision-relevant information.
 */
public fun Money.formattedRounded(locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getCurrencyInstance(locale)
    runCatching { Currency.getInstance(currency.code) }
        .getOrNull()
        ?.let { format.currency = it }
    format.maximumFractionDigits = 0
    return format.format(toBigDecimal())
}
