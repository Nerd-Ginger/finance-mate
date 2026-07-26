package dev.financemate.core.parsing

import dev.financemate.core.parsing.DateParser.DateOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class DateParserTest {

    private fun parse(raw: String, order: DateOrder = DateOrder.MONTH_FIRST) =
        DateParser.parse(raw, order)

    // --- Unambiguous formats ------------------------------------------------------------

    @Test
    fun `parses ISO dates`() {
        parse("2026-03-14") shouldBe LocalDate.of(2026, 3, 14)
        parse("2026/03/14") shouldBe LocalDate.of(2026, 3, 14)
    }

    @Test
    fun `parses named-month formats`() {
        parse("14-Mar-2026") shouldBe LocalDate.of(2026, 3, 14)
        parse("Mar 14, 2026") shouldBe LocalDate.of(2026, 3, 14)
        parse("14 Mar 2026") shouldBe LocalDate.of(2026, 3, 14)
    }

    // --- The ambiguous case that quietly wrecks monthly budgets -------------------------

    @Test
    fun `respects the stated field order`() {
        // 01/02/2026 is 2 January in the US and 1 February elsewhere. Guessing
        // wrong moves transactions between months while each one still looks fine.
        parse("01/02/2026", DateOrder.MONTH_FIRST) shouldBe LocalDate.of(2026, 1, 2)
        parse("01/02/2026", DateOrder.DAY_FIRST) shouldBe LocalDate.of(2026, 2, 1)
    }

    @Test
    fun `an impossible month overrides the stated order`() {
        // 25 cannot be a month, so this is unambiguous regardless of preference.
        parse("25/12/2026", DateOrder.MONTH_FIRST) shouldBe LocalDate.of(2026, 12, 25)
        parse("12/25/2026", DateOrder.DAY_FIRST) shouldBe LocalDate.of(2026, 12, 25)
    }

    @Test
    fun `infers order from a column when any row is unambiguous`() {
        // A single row with a day above 12 settles the whole column.
        DateParser.inferOrder(listOf("01/02/2026", "13/02/2026")) shouldBe DateOrder.DAY_FIRST
        DateParser.inferOrder(listOf("01/02/2026", "02/13/2026")) shouldBe DateOrder.MONTH_FIRST
    }

    @Test
    fun `falls back to the default when every row is ambiguous`() {
        DateParser.inferOrder(listOf("01/02/2026", "03/04/2026")) shouldBe DateOrder.MONTH_FIRST
        DateParser.inferOrder(
            listOf("01/02/2026"),
            default = DateOrder.DAY_FIRST,
        ) shouldBe DateOrder.DAY_FIRST
    }

    @Test
    fun `ISO rows contribute no ordering evidence`() {
        DateParser.inferOrder(listOf("2026-01-02", "2026-03-04")) shouldBe DateOrder.MONTH_FIRST
    }

    // --- Two-digit years ------------------------------------------------------------------

    @Test
    fun `expands two-digit years around the pivot`() {
        parse("03/14/26") shouldBe LocalDate.of(2026, 3, 14)
        parse("03/14/99") shouldBe LocalDate.of(1999, 3, 14)
    }

    // --- Robustness -------------------------------------------------------------------------

    @Test
    fun `ignores trailing time and timezone`() {
        parse("03/14/2026 14:32:00") shouldBe LocalDate.of(2026, 3, 14)
    }

    @Test
    fun `handles dash and dot separators`() {
        parse("03-14-2026") shouldBe LocalDate.of(2026, 3, 14)
        parse("03.14.2026") shouldBe LocalDate.of(2026, 3, 14)
    }

    @Test
    fun `handles quoted and padded values`() {
        parse("\"03/14/2026\"") shouldBe LocalDate.of(2026, 3, 14)
        parse("  03/14/2026  ") shouldBe LocalDate.of(2026, 3, 14)
    }

    @Test
    fun `returns null for non-dates`() {
        parse("").shouldBeNull()
        parse("Date").shouldBeNull()
        parse("N/A").shouldBeNull()
    }

    @Test
    fun `returns null for impossible dates`() {
        // 31 February must not silently roll into March.
        parse("02/31/2026").shouldBeNull()
        parse("13/32/2026").shouldBeNull()
    }
}
