package dev.financemate.core.parsing.csv

import dev.financemate.core.model.AccountId
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.core.parsing.DedupHasher
import dev.financemate.core.parsing.MerchantNormaliser
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate

class CsvStatementParserTest {

    private val usd = CurrencyCode.USD

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "Missing test fixture: $name"
        }.bufferedReader().readText()

    private fun usd(minor: Long) = Money(minor, usd)

    // --- Chase checking: standard signed-amount layout -----------------------------------

    @Test
    fun `parses a Chase checking export`() {
        val result = CsvStatementParser.parse(
            fixture("chase-checking.csv"),
            BankProfiles.CHASE_CHECKING.mapping,
        )

        result.transactions.size shouldBe 5

        val coffee = result.transactions.first()
        coffee.postedDate shouldBe LocalDate.of(2026, 3, 14)
        coffee.amount shouldBe usd(-475)
        coffee.rawDescription shouldBe "SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA"

        // Income stays positive, spending stays negative.
        result.transactions.first { it.rawDescription.contains("PAYROLL") }
            .amount shouldBe usd(250_000)
    }

    @Test
    fun `keeps a description containing a comma intact`() {
        val result = CsvStatementParser.parse(
            fixture("chase-checking.csv"),
            BankProfiles.CHASE_CHECKING.mapping,
        )
        val rent = result.transactions.first { it.rawDescription.contains("RENT") }
        rent.rawDescription shouldBe "SMITH, JOHN - RENT PAYMENT"
        rent.amount shouldBe usd(-145_000)
    }

    // --- Amex: charges are positive in the source ----------------------------------------

    @Test
    fun `inverts the sign for charge-positive issuers`() {
        // This is the assertion that matters most in the file. Without the flip,
        // every Amex purchase would be recorded as income.
        val result = CsvStatementParser.parse(fixture("amex.csv"), BankProfiles.AMEX.mapping)

        result.transactions.size shouldBe 3

        val purchase = result.transactions.first { it.rawDescription.contains("AMZN") }
        purchase.amount shouldBe usd(-4299)

        // A payment to the card is money in, so it flips the other way.
        val payment = result.transactions.first { it.rawDescription.contains("PAYMENT RECEIVED") }
        payment.amount shouldBe usd(50_000)
    }

    // --- Capital One: separate debit and credit columns -----------------------------------

    @Test
    fun `handles split debit and credit columns`() {
        val result = CsvStatementParser.parse(
            fixture("capital-one.csv"),
            BankProfiles.CAPITAL_ONE.mapping,
        )

        result.transactions.size shouldBe 3
        result.transactions.map { it.amount } shouldContainExactly listOf(
            usd(-575),
            usd(-6420),
            usd(12_000),
        )
    }

    @Test
    fun `reads ISO dates from Capital One`() {
        val result = CsvStatementParser.parse(
            fixture("capital-one.csv"),
            BankProfiles.CAPITAL_ONE.mapping,
        )
        // Posted date, not transaction date — it is what appears on the statement.
        result.transactions.first().postedDate shouldBe LocalDate.of(2026, 3, 15)
    }

    // --- Wells Fargo: no header row --------------------------------------------------------

    @Test
    fun `parses a headerless export`() {
        val result = CsvStatementParser.parse(
            fixture("wells-fargo-no-header.csv"),
            BankProfiles.WELLS_FARGO.mapping,
        )

        result.transactions.size shouldBe 3
        result.transactions.first().amount shouldBe usd(-1250)
        result.transactions.first().rawDescription shouldBe "STARBUCKS #567 SEATTLE WA"
        result.transactions.last().amount shouldBe usd(180_000)
    }

    // --- Problem handling ------------------------------------------------------------------

    @Test
    fun `skips bad rows without losing good ones`() {
        val content = """
            Date,Description,Amount
            03/14/2026,GOOD ROW,-10.00
            not-a-date,BROKEN ROW,-20.00
            03/16/2026,ANOTHER GOOD ROW,-30.00
        """.trimIndent()

        val result = CsvStatementParser.parse(
            content,
            ColumnMapping(dateColumn = 0, descriptionColumns = listOf(1), amountColumn = 2),
        )

        result.transactions.size shouldBe 2
        result.problems.size shouldBe 1
        result.problems.first().severity shouldBe ParseProblem.Severity.ERROR
        result.problems.first().location shouldBe "line 3"
    }

    @Test
    fun `treats summary rows as warnings not errors`() {
        val content = """
            Date,Description,Amount
            03/14/2026,COFFEE,-4.75
            03/31/2026,Ending Balance,1234.56
        """.trimIndent()

        val result = CsvStatementParser.parse(
            content,
            ColumnMapping(dateColumn = 0, descriptionColumns = listOf(1), amountColumn = 2),
        )

        result.transactions.size shouldBe 1
        result.problems.single().severity shouldBe ParseProblem.Severity.WARNING
    }

    @Test
    fun `reports the offending line number`() {
        val content = "Date,Description,Amount\n03/14/2026,SHORT ROW"
        val result = CsvStatementParser.parse(
            content,
            ColumnMapping(dateColumn = 0, descriptionColumns = listOf(1), amountColumn = 2),
        )
        result.transactions.size shouldBe 0
        result.problems.single().location shouldBe "line 2"
    }

    // --- The end-to-end property: re-import is a no-op --------------------------------------

    @Test
    fun `re-importing the same file produces identical dedup hashes`() {
        val account = AccountId("chase-checking")
        val mapping = BankProfiles.CHASE_CHECKING.mapping

        val first = CsvStatementParser.parse(fixture("chase-checking.csv"), mapping)
        val second = CsvStatementParser.parse(fixture("chase-checking.csv"), mapping)

        DedupHasher.assignHashes(account, first.transactions) shouldContainExactly
            DedupHasher.assignHashes(account, second.transactions)
    }

    @Test
    fun `parsed descriptions normalise to sensible merchants`() {
        val result = CsvStatementParser.parse(
            fixture("chase-checking.csv"),
            BankProfiles.CHASE_CHECKING.mapping,
        )
        val keys = result.transactions.map { MerchantNormaliser.normalise(it.rawDescription).value }

        keys.contains("BLUE BOTTLE COFFEE") shouldBe true
        keys.contains("NETFLIX") shouldBe true
        keys.contains("WHOLE FOODS") shouldBe true
    }

    // --- Profile matching ---------------------------------------------------------------------

    @Test
    fun `matches known bank profiles from their header row`() {
        val chaseHeader = CsvReader.parse(fixture("chase-checking.csv")).first().fields
        BankProfiles.matching(chaseHeader)?.id shouldBe "chase-checking"

        val capitalOneHeader = CsvReader.parse(fixture("capital-one.csv")).first().fields
        BankProfiles.matching(capitalOneHeader)?.id shouldBe "capital-one"
    }

    @Test
    fun `does not auto-match the headerless profile`() {
        // Wells Fargo has no header to match on. Guessing it from shape alone
        // would risk applying it to another bank's headerless export.
        val wellsFargoRows = CsvReader.parse(fixture("wells-fargo-no-header.csv"))
        BankProfiles.matching(wellsFargoRows.first().fields) shouldBe null
    }

    // --- Auto-detection for unknown layouts ----------------------------------------------------

    @Test
    fun `detects an unknown layout from its header names`() {
        val content = """
            Value Date,Narrative,Withdrawal,Deposit
            03/14/2026,COFFEE SHOP,4.75,
            03/15/2026,SALARY,,2500.00
        """.trimIndent()

        val detection = ColumnDetector.detect(CsvReader.parse(content))
        detection.shouldNotBeNull()
        detection.confidence shouldBe ColumnDetector.Confidence.HEADER_MATCH
        detection.mapping.dateColumn shouldBe 0
        detection.mapping.debitColumn shouldBe 2
        detection.mapping.creditColumn shouldBe 3

        val result = CsvStatementParser.parse(content, detection.mapping)
        result.transactions.map { it.amount } shouldContainExactly listOf(usd(-475), usd(250_000))
    }

    @Test
    fun `falls back to content inspection when headers are unrecognised`() {
        val content = """
            Col1,Col2,Col3
            03/14/2026,COFFEE SHOP DOWNTOWN,-4.75
            03/15/2026,GROCERY STORE UPTOWN,-52.10
            03/16/2026,PAYCHECK FROM WORK,2500.00
        """.trimIndent()

        val detection = ColumnDetector.detect(CsvReader.parse(content))
        detection.shouldNotBeNull()
        detection.confidence shouldBe ColumnDetector.Confidence.CONTENT_GUESS
        detection.mapping.dateColumn shouldBe 0
        detection.mapping.descriptionColumns shouldContainExactly listOf(1)
        detection.mapping.amountColumn shouldBe 2
    }

    @Test
    fun `detects day-first ordering from the data`() {
        val content = """
            Date,Description,Amount
            25/12/2026,CHRISTMAS SHOPPING,-120.00
            13/11/2026,NOVEMBER BILL,-45.00
        """.trimIndent()

        val detection = ColumnDetector.detect(CsvReader.parse(content))
        detection.shouldNotBeNull()

        val result = CsvStatementParser.parse(content, detection.mapping)
        result.transactions.first().postedDate shouldBe LocalDate.of(2026, 12, 25)
    }
}
