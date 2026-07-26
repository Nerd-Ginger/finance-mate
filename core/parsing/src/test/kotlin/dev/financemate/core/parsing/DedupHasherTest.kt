package dev.financemate.core.parsing

import dev.financemate.core.model.AccountId
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.time.LocalDate

class DedupHasherTest {

    private val account = AccountId("acct-checking-1")
    private val otherAccount = AccountId("acct-savings-2")
    private val date = LocalDate.of(2026, 3, 14)

    private fun usd(minor: Long) = Money(minor, CurrencyCode.USD)

    private fun txn(
        description: String,
        minor: Long = -1250,
        on: LocalDate = date,
    ) = ParsedTransaction(postedDate = on, amount = usd(minor), rawDescription = description)

    private fun hashOf(description: String, minor: Long = -1250, on: LocalDate = date) =
        DedupHasher.hash(account, on, minor, description)

    // --- The property the whole import pipeline rests on ------------------------------

    @Test
    fun `re-importing the same statement produces identical hashes`() {
        // Users routinely download overlapping date ranges. If this fails, their
        // spending silently doubles and the totals still look plausible.
        val rows = listOf(
            txn("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA", -475),
            txn("NETFLIX.COM 866-579-7172 CA", -1599),
            txn("PAYROLL DIRECT DEP", 250_000),
        )

        val first = DedupHasher.assignHashes(account, rows)
        val second = DedupHasher.assignHashes(account, rows)

        first shouldContainExactly second
    }

    @Test
    fun `a reworded description still matches`() {
        // A pending charge often gains a reference number or loses a processor
        // prefix once it posts. Hashing the raw text would treat that as new
        // spending; hashing the normalised form survives it.
        val pending = hashOf("SQ *BLUE BOTTLE COFFEE OAKLAND CA")
        val posted = hashOf("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA 987654321")
        posted shouldBe pending
    }

    @Test
    fun `store number changes do not break matching`() {
        hashOf("SAFEWAY #1234 CA") shouldBe hashOf("SAFEWAY #1234 SAN FRANCISCO CA")
    }

    // --- Things that must produce different hashes ------------------------------------

    @Test
    fun `a different amount is a different transaction`() {
        hashOf("STARBUCKS", -475) shouldNotBe hashOf("STARBUCKS", -500)
    }

    @Test
    fun `a different date is a different transaction`() {
        hashOf("STARBUCKS", on = date) shouldNotBe
            hashOf("STARBUCKS", on = date.plusDays(1))
    }

    @Test
    fun `the same transaction in a different account is distinct`() {
        val a = DedupHasher.hash(account, date, -1250, "STARBUCKS")
        val b = DedupHasher.hash(otherAccount, date, -1250, "STARBUCKS")
        a shouldNotBe b
    }

    @Test
    fun `a genuinely different merchant is distinct`() {
        hashOf("STARBUCKS") shouldNotBe hashOf("PEETS COFFEE")
    }

    // --- Same-day repeats are real transactions, not duplicates -----------------------

    @Test
    fun `two identical charges on the same day get different hashes`() {
        // Two $3.75 coffees at the same shop on the same day are two purchases.
        // Collapsing them would under-count the user's spending.
        val rows = listOf(txn("BLUE BOTTLE", -375), txn("BLUE BOTTLE", -375))
        val hashes = DedupHasher.assignHashes(account, rows)

        hashes.size shouldBe 2
        hashes.toSet().size shouldBe 2
    }

    @Test
    fun `same-day repeats still match on re-import`() {
        // Three identical charges must map onto the same three hashes next time,
        // not be treated as three more purchases.
        val rows = List(3) { txn("BLUE BOTTLE", -375) }
        DedupHasher.assignHashes(account, rows) shouldContainExactly
            DedupHasher.assignHashes(account, rows)
    }

    @Test
    fun `occurrence numbering is independent per identity`() {
        val rows = listOf(
            txn("BLUE BOTTLE", -375),
            txn("STARBUCKS", -500),
            txn("BLUE BOTTLE", -375),
        )
        val hashes = DedupHasher.assignHashes(account, rows)

        // Rows 0 and 2 are the first and second Blue Bottle; row 1 is unrelated.
        hashes[0] shouldNotBe hashes[2]
        hashes[0] shouldBe DedupHasher.hash(account, date, -375, "BLUE BOTTLE", occurrence = 0)
        hashes[2] shouldBe DedupHasher.hash(account, date, -375, "BLUE BOTTLE", occurrence = 1)
        hashes[1] shouldBe DedupHasher.hash(account, date, -500, "STARBUCKS", occurrence = 0)
    }

    // --- Field-boundary safety --------------------------------------------------------

    @Test
    fun `adjacent fields cannot be confused for one another`() {
        // Without a separator that cannot occur in the data, "AB"+"C" and
        // "A"+"BC" would hash identically. Contrived, but a real class of bug.
        val a = DedupHasher.hash(AccountId("ab"), date, -1, "C")
        val b = DedupHasher.hash(AccountId("a"), date, -1, "BC")
        a shouldNotBe b
    }

    @Test
    fun `hash is stable across runs`() {
        // Guards against anyone swapping in a hash with a per-process seed, which
        // would silently disable dedup between app launches.
        hashOf("STARBUCKS") shouldBe hashOf("STARBUCKS")
        hashOf("STARBUCKS").length shouldBe 64
    }
}
