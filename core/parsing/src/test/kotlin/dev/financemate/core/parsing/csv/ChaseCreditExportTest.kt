package dev.financemate.core.parsing.csv

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

/**
 * A real Chase credit-card export, end to end.
 *
 * Written after this exact file failed on the device with an unhelpful
 * "Could not read that file (IllegalStateException)". The parser had unit tests
 * for its pieces but nothing that took a plausible whole export and asserted it
 * came out the other side, which is the only test that would have caught it.
 */
class ChaseCreditExportTest {

    private val export = """
        Transaction Date,Post Date,Description,Category,Type,Amount,Memo
        06/02/2026,06/02/2026,GREENFIELD MARKET #221,Groceries,Sale,-63.18,
        06/03/2026,06/03/2026,PAYROLL HALDEN CO DIRECT DEP,,Payment,2410.00,
        06/05/2026,06/05/2026,CHORDLY FAMILY,Entertainment,Sale,-16.99,
    """.trimIndent()

    @Test
    fun theHeaderIsRecognisedAsChaseCredit() {
        val rows = CsvReader.parse(export)
        val profile = BankProfiles.matching(rows.first().fields)

        profile.shouldNotBeNull()
        profile.id shouldBe "chase-credit"
    }

    @Test
    fun everyRowSurvivesParsing() {
        val rows = CsvReader.parse(export)
        val profile = BankProfiles.matching(rows.first().fields)!!

        val result = CsvStatementParser.parse(export, profile.mapping)

        result.transactions shouldHaveSize 3
        result.problems shouldHaveSize 0
    }

    @Test
    fun signsAndDatesComeOutTheRightWayRound() {
        val rows = CsvReader.parse(export)
        val profile = BankProfiles.matching(rows.first().fields)!!

        val result = CsvStatementParser.parse(export, profile.mapping)

        val spend = result.transactions.first()
        spend.postedDate shouldBe LocalDate.of(2026, 6, 2)
        spend.amount.minorUnits shouldBe -6318

        // The row that catches an inverted profile: pay must stay positive.
        val pay = result.transactions[1]
        pay.amount.minorUnits shouldBe 241_000
    }

    /**
     * A trailing empty field, which every Chase export has because of the Memo
     * column, must not make the row look short.
     */
    @Test
    fun theEmptyTrailingMemoColumnIsKept() {
        val rows = CsvReader.parse(export)

        rows.forEach { row -> row.fields shouldHaveSize 7 }
    }
}
