package dev.financemate.core.parsing

import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.core.parsing.ofx.OfxParser
import dev.financemate.core.parsing.qif.QifParser
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class OfxQifParserTest {

    private val usd = CurrencyCode.USD
    private fun usd(minor: Long) = Money(minor, usd)

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing test fixture: $name"
        }.bufferedReader().readText()

    // --- OFX ------------------------------------------------------------------------------

    @Test
    fun `parses an OFX statement`() {
        val result = OfxParser.parse(fixture("statement.ofx"))

        result.problems shouldBe emptyList()
        result.transactions.size shouldBe 3
        result.source shouldBe ImportSource.OFX

        val coffee = result.transactions.first()
        coffee.postedDate shouldBe LocalDate.of(2026, 3, 14)
        coffee.amount shouldBe usd(-475)
        coffee.rawDescription shouldBe "SQ *BLUE BOTTLE COFFEE OAKLAND CA"
    }

    @Test
    fun `captures FITID as the institution transaction id`() {
        // This is the reason to prefer OFX: a bank-assigned id survives the bank
        // rewording a description or adjusting an amount, which defeats any
        // content-derived fingerprint.
        val result = OfxParser.parse(fixture("statement.ofx"))
        result.transactions.map { it.institutionTransactionId } shouldContainExactly listOf(
            "202603140001",
            "202603130002",
            "202603120003",
        )
    }

    @Test
    fun `reads currency and account mask from the file`() {
        val result = OfxParser.parse(fixture("statement.ofx"))
        result.transactions.first().amount.currency shouldBe usd
        // Only the last four digits are retained; the full account number is
        // deliberately never stored.
        result.declaredAccountMask shouldBe "4321"
    }

    @Test
    fun `keeps the statement date rather than shifting by timezone`() {
        // 20260314120000[-8:PST] converted to some other zone could land on the
        // 13th or 15th, moving the transaction into a different budget month.
        OfxParser.parseOfxDate("20260314120000[-8:PST]") shouldBe LocalDate.of(2026, 3, 14)
        OfxParser.parseOfxDate("20260314") shouldBe LocalDate.of(2026, 3, 14)
    }

    @Test
    fun `OFX amounts already use the expected sign convention`() {
        val result = OfxParser.parse(fixture("statement.ofx"))
        result.transactions.first { it.rawDescription.contains("PAYROLL") }
            .amount shouldBe usd(250_000)
        result.transactions.first { it.rawDescription.contains("NETFLIX") }
            .amount shouldBe usd(-1599)
    }

    @Test
    fun `parses OFX 2 XML with closing tags`() {
        // OFX 2.x is well-formed XML. The same scan handles it because a value
        // runs to the next '<' either way.
        val xml = """
            <OFX>
            <STMTRS>
            <CURDEF>USD</CURDEF>
            <BANKTRANLIST>
            <STMTTRN>
            <TRNTYPE>DEBIT</TRNTYPE>
            <DTPOSTED>20260314</DTPOSTED>
            <TRNAMT>-4.75</TRNAMT>
            <FITID>ABC123</FITID>
            <NAME>COFFEE SHOP</NAME>
            </STMTTRN>
            </BANKTRANLIST>
            </STMTRS>
            </OFX>
        """.trimIndent()

        val result = OfxParser.parse(xml)
        result.transactions.size shouldBe 1
        result.transactions.first().amount shouldBe usd(-475)
        result.transactions.first().institutionTransactionId shouldBe "ABC123"
        result.transactions.first().rawDescription shouldBe "COFFEE SHOP"
    }

    @Test
    fun `reports a file with no transactions rather than returning empty silently`() {
        val result = OfxParser.parse("<OFX><SIGNONMSGSRSV1></SIGNONMSGSRSV1></OFX>")
        result.transactions shouldBe emptyList()
        result.problems.single().severity shouldBe ParseProblem.Severity.ERROR
    }

    @Test
    fun `skips a malformed record without losing the rest`() {
        val ofx = """
            <OFX><STMTRS><BANKTRANLIST>
            <STMTTRN>
            <DTPOSTED>20260314
            <TRNAMT>-4.75
            <NAME>GOOD ONE
            </STMTTRN>
            <STMTTRN>
            <DTPOSTED>notadate
            <TRNAMT>-9.99
            <NAME>BAD DATE
            </STMTTRN>
            </BANKTRANLIST></STMTRS></OFX>
        """.trimIndent()

        val result = OfxParser.parse(ofx)
        result.transactions.size shouldBe 1
        result.problems.size shouldBe 1
        result.problems.single().location shouldBe "transaction 2"
    }

    // --- QIF ------------------------------------------------------------------------------

    @Test
    fun `parses a QIF statement`() {
        val result = QifParser.parse(fixture("statement.qif"))

        result.source shouldBe ImportSource.QIF
        result.transactions.size shouldBe 4

        val coffee = result.transactions.first()
        coffee.postedDate shouldBe LocalDate.of(2026, 3, 14)
        coffee.amount shouldBe usd(-475)
        coffee.rawDescription shouldBe "SQ *BLUE BOTTLE COFFEE OAKLAND CA"
    }

    @Test
    fun `parses QIF thousands separators`() {
        val result = QifParser.parse(fixture("statement.qif"))
        result.transactions.first { it.rawDescription.contains("PAYROLL") }
            .amount shouldBe usd(250_000)
    }

    @Test
    fun `falls back to the check number when there is no payee`() {
        val result = QifParser.parse(fixture("statement.qif"))
        val rent = result.transactions.first { it.postedDate == LocalDate.of(2026, 3, 11) }
        rent.rawDescription shouldBe "RENT PAYMENT"
    }

    @Test
    fun `QIF has no transaction ids`() {
        // Worth asserting: it is why OFX is preferred when a bank offers both.
        val result = QifParser.parse(fixture("statement.qif"))
        result.transactions.forEach { it.institutionTransactionId.shouldBeNull() }
    }

    @Test
    fun `infers date ordering across the whole file`() {
        // Nothing in a QIF states the ordering, so one unambiguous row has to
        // settle the interpretation of every other row.
        val qif = """
            !Type:Bank
            D01/02/2026
            T-10.00
            PAMBIGUOUS ROW
            ^
            D25/12/2026
            T-20.00
            PCHRISTMAS
            ^
        """.trimIndent()

        val result = QifParser.parse(qif)
        result.transactions.size shouldBe 2
        // 25/12 forces day-first, so 01/02 must read as 1 February.
        result.transactions.first().postedDate shouldBe LocalDate.of(2026, 2, 1)
    }

    @Test
    fun `handles apostrophe year notation`() {
        val qif = "!Type:Bank\nD03/14'26\nT-4.75\nPCOFFEE\n^"
        val result = QifParser.parse(qif)
        result.transactions.single().postedDate shouldBe LocalDate.of(2026, 3, 14)
    }

    @Test
    fun `handles a final record with no terminator`() {
        val qif = "!Type:Bank\nD03/14/2026\nT-4.75\nPCOFFEE"
        val result = QifParser.parse(qif)
        result.transactions.size shouldBe 1
    }

    @Test
    fun `reports records missing required fields`() {
        val qif = """
            !Type:Bank
            D03/14/2026
            PNO AMOUNT
            ^
            D03/15/2026
            T-9.99
            PFINE
            ^
        """.trimIndent()

        val result = QifParser.parse(qif)
        result.transactions.size shouldBe 1
        result.problems.size shouldBe 1
        result.problems.single().severity shouldBe ParseProblem.Severity.ERROR
    }

    // --- Cross-format consistency -------------------------------------------------------

    @Test
    fun `the same statement parses identically from OFX and QIF`() {
        // The two fixtures describe the same three transactions. Format should
        // not change what lands in the ledger.
        val ofx = OfxParser.parse(fixture("statement.ofx")).transactions
        val qif = QifParser.parse(fixture("statement.qif")).transactions.take(3)

        ofx.map { it.postedDate } shouldContainExactly qif.map { it.postedDate }
        ofx.map { it.amount } shouldContainExactly qif.map { it.amount }

        ofx.zip(qif).forEach { (fromOfx, fromQif) ->
            MerchantNormaliser.normalise(fromOfx.rawDescription) shouldBe
                MerchantNormaliser.normalise(fromQif.rawDescription)
        }
    }

    @Test
    fun `QFX is parsed as OFX`() {
        val result = OfxParser.parse(fixture("statement.ofx"), source = ImportSource.QFX)
        result.source shouldBe ImportSource.QFX
        result.transactions.size shouldBe 3
        result.transactions.first().institutionTransactionId.shouldNotBeNull()
    }
}
