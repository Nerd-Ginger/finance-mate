package dev.financemate.core.parsing

import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.math.BigDecimal

class AmountParserTest {

    private val usd = CurrencyCode.USD
    private fun dec(raw: String) = AmountParser.parseDecimal(raw)
    private fun money(raw: String, invert: Boolean = false) =
        AmountParser.parse(raw, usd, invertSign = invert)

    // --- Plain values -----------------------------------------------------------------

    @Test
    fun `parses plain decimals`() {
        dec("1234.56") shouldBe BigDecimal("1234.56")
        dec("-1234.56") shouldBe BigDecimal("-1234.56")
        dec("0.00") shouldBe BigDecimal("0.00")
    }

    @Test
    fun `strips currency symbols and codes`() {
        dec("$1,234.56") shouldBe BigDecimal("1234.56")
        dec("USD 1,234.56") shouldBe BigDecimal("1234.56")
        dec("-$1,234.56") shouldBe BigDecimal("-1234.56")
    }

    // --- Negative notations — getting these wrong flips the sign -----------------------

    @Test
    fun `parentheses mean negative`() {
        dec("(1,234.56)") shouldBe BigDecimal("-1234.56")
        dec("(0.99)") shouldBe BigDecimal("-0.99")
    }

    @Test
    fun `trailing minus means negative`() {
        dec("1234.56-") shouldBe BigDecimal("-1234.56")
    }

    @Test
    fun `leading plus is ignored`() {
        dec("+1234.56") shouldBe BigDecimal("1234.56")
    }

    // --- Separator disambiguation ------------------------------------------------------

    @Test
    fun `handles US thousands grouping`() {
        dec("1,234.56") shouldBe BigDecimal("1234.56")
        dec("1,234,567.89") shouldBe BigDecimal("1234567.89")
    }

    @Test
    fun `handles European decimal comma`() {
        dec("1.234,56") shouldBe BigDecimal("1234.56")
        dec("1.234.567,89") shouldBe BigDecimal("1234567.89")
        dec("12,34") shouldBe BigDecimal("12.34")
    }

    @Test
    fun `a single comma with three trailing digits is grouping not decimals`() {
        // "1,234" is one thousand two hundred and thirty-four, not 1.234.
        dec("1,234") shouldBe BigDecimal("1234")
    }

    @Test
    fun `a single comma with two trailing digits is a decimal separator`() {
        dec("1,23") shouldBe BigDecimal("1.23")
    }

    // --- Non-amounts --------------------------------------------------------------------

    @Test
    fun `returns null for things that are not amounts`() {
        // Statements contain section headers and blank cells; those are the
        // caller's problem to report, not this parser's to throw over.
        dec("").shouldBeNull()
        dec("   ").shouldBeNull()
        dec("Beginning balance").shouldBeNull()
        dec("N/A").shouldBeNull()
        dec("--").shouldBeNull()
    }

    // --- Money conversion ---------------------------------------------------------------

    @Test
    fun `converts to money in minor units`() {
        money("-1,234.56") shouldBe Money(-123_456, usd)
        money("0.07") shouldBe Money(7, usd)
    }

    @Test
    fun `sign inversion flips charge-positive statements`() {
        // Amex and Discover report purchases as positive. Flipping here at the
        // boundary is what lets everything downstream trust that negative
        // means money left the account.
        money("1234.56", invert = true) shouldBe Money(-123_456, usd)
        money("-500.00", invert = true) shouldBe Money(50_000, usd)
    }

    @Test
    fun `rounds sub-cent precision rather than dropping the row`() {
        // FX-derived amounts occasionally carry extra decimals. Losing a
        // fraction of a cent beats losing the whole transaction.
        money("10.0050") shouldBe Money(1001, usd)
        money("10.0049") shouldBe Money(1000, usd)
    }

    @Test
    fun `a single dot with exactly three trailing digits is read as grouping`() {
        // "10.005" is genuinely ambiguous: three decimal places, or European
        // thousands grouping? Monetary amounts carry exactly two decimals almost
        // without exception, so a three-digit trailing group is far more likely
        // to be grouping. Pinned here so the choice is deliberate and visible
        // rather than an accident of the heuristic.
        dec("10.005") shouldBe BigDecimal("10005")
        dec("1.500") shouldBe BigDecimal("1500")

        // Four or more trailing digits cannot be grouping, so they stay decimals.
        dec("10.0050") shouldBe BigDecimal("10.0050")
    }

    @Test
    fun `handles zero-decimal currencies`() {
        AmountParser.parse("1234", CurrencyCode.JPY) shouldBe Money(1234, CurrencyCode.JPY)
    }
}
