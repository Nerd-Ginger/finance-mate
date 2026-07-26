package dev.financemate.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.financemate.core.data.import.ImportPipeline
import dev.financemate.core.data.mapper.toDomain
import dev.financemate.core.data.mapper.toEntity
import dev.financemate.core.model.Account
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.AccountType
import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ImportPipelineTest {

    private lateinit var database: FinanceMateDatabase
    private lateinit var pipeline: ImportPipeline

    private val usd = CurrencyCode.USD
    private val accountId = AccountId("acct-1")
    private var idCounter = 0

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceMateDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Deterministic ids keep assertions readable; the pipeline only requires
        // that they are unique.
        pipeline = ImportPipeline(database, idGenerator = { "id-${idCounter++}" })

        database.accountDao().upsert(
            Account(
                id = accountId,
                displayName = "Everyday Checking",
                institution = "Test Bank",
                type = AccountType.CHECKING,
                currency = usd,
                mask = "4321",
            ).toEntity(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun usd(minor: Long) = Money(minor, usd)

    private fun txn(
        description: String,
        minor: Long,
        day: Int,
        fitId: String? = null,
    ) = ParsedTransaction(
        postedDate = LocalDate.of(2026, 3, day),
        amount = usd(minor),
        rawDescription = description,
        institutionTransactionId = fitId,
    )

    private fun result(vararg transactions: ParsedTransaction, source: ImportSource = ImportSource.CSV) =
        ParseResult(transactions.toList(), source)

    // --- The property the whole import design exists to guarantee ------------------------

    @Test
    fun `re-importing the same statement imports nothing the second time`() = runTest {
        val statement = result(
            txn("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA", -475, 14),
            txn("NETFLIX.COM 866-579-7172 CA", -1599, 13),
            txn("PAYROLL DIRECT DEP", 250_000, 12),
        )

        val first = pipeline.import(accountId, statement)
        first.importedCount shouldBe 3
        first.duplicateCount shouldBe 0

        val second = pipeline.import(accountId, statement)
        second.importedCount shouldBe 0
        second.duplicateCount shouldBe 3

        // The decisive assertion: spending did not double.
        database.transactionDao().count() shouldBe 3
    }

    @Test
    fun `an overlapping statement imports only the new rows`() = runTest {
        // The realistic case: the user downloads "last 90 days" every month, so
        // most of each export is already present.
        pipeline.import(
            accountId,
            result(
                txn("COFFEE SHOP", -475, 10),
                txn("GROCERIES", -8743, 11),
            ),
        )

        val outcome = pipeline.import(
            accountId,
            result(
                txn("GROCERIES", -8743, 11), // already have this
                txn("NEW PURCHASE", -2000, 12), // this one is new
            ),
        )

        outcome.importedCount shouldBe 1
        outcome.duplicateCount shouldBe 1
        database.transactionDao().count() shouldBe 3
    }

    @Test
    fun `duplicate detection survives the bank rewording a description`() = runTest {
        // A pending charge often gains a reference number once it posts. The
        // fingerprint is computed over the normalised merchant, so it still matches.
        pipeline.import(accountId, result(txn("SQ *BLUE BOTTLE COFFEE OAKLAND CA", -475, 14)))

        val outcome = pipeline.import(
            accountId,
            result(txn("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA 987654321", -475, 14)),
        )

        outcome.duplicateCount shouldBe 1
        database.transactionDao().count() shouldBe 1
    }

    @Test
    fun `the bank's own transaction id also prevents duplicates`() = runTest {
        // Where OFX supplies a FITID it is authoritative, and the unique index on
        // (accountId, institutionTransactionId) enforces it independently of the
        // computed fingerprint.
        pipeline.import(
            accountId,
            result(txn("COFFEE", -475, 14, fitId = "FIT-001"), source = ImportSource.OFX),
        )

        // Same FITID, but the amount was corrected by the bank — so the computed
        // fingerprint differs and only the bank id can catch this.
        val outcome = pipeline.import(
            accountId,
            result(txn("COFFEE", -500, 14, fitId = "FIT-001"), source = ImportSource.OFX),
        )

        outcome.duplicateCount shouldBe 1
        database.transactionDao().count() shouldBe 1
    }

    @Test
    fun `rows without a bank id do not collide on null`() = runTest {
        // SQLite treats NULLs as distinct in a unique index, which is what makes
        // the FITID constraint apply only to formats that supply one.
        val outcome = pipeline.import(
            accountId,
            result(
                txn("COFFEE", -475, 14),
                txn("LUNCH", -1200, 14),
                txn("PARKING", -300, 14),
            ),
        )
        outcome.importedCount shouldBe 3
    }

    @Test
    fun `genuine same-day repeats are kept as separate transactions`() = runTest {
        // Two identical coffees on the same day are two purchases. Collapsing
        // them would under-count spending.
        val outcome = pipeline.import(
            accountId,
            result(
                txn("BLUE BOTTLE", -375, 14),
                txn("BLUE BOTTLE", -375, 14),
            ),
        )

        outcome.importedCount shouldBe 2
        database.transactionDao().count() shouldBe 2
    }

    @Test
    fun `same-day repeats still deduplicate on re-import`() = runTest {
        val statement = result(
            txn("BLUE BOTTLE", -375, 14),
            txn("BLUE BOTTLE", -375, 14),
        )
        pipeline.import(accountId, statement)
        val second = pipeline.import(accountId, statement)

        second.duplicateCount shouldBe 2
        database.transactionDao().count() shouldBe 2
    }

    // --- Batch tracking and undo -----------------------------------------------------------

    @Test
    fun `import records a batch`() = runTest {
        val outcome = pipeline.import(
            accountId,
            result(txn("COFFEE", -475, 14)),
            fileName = "march.csv",
        )

        val batches = database.importBatchDao().forAccount(accountId.value)
        batches.size shouldBe 1
        batches.single().fileName shouldBe "march.csv"
        batches.single().rowsImported shouldBe 1
        outcome.batch.source shouldBe ImportSource.CSV
    }

    @Test
    fun `undo removes exactly the rows one import created`() = runTest {
        pipeline.import(accountId, result(txn("KEEP ME", -100, 10)))
        val second = pipeline.import(
            accountId,
            result(txn("REMOVE ME", -200, 11), txn("REMOVE ME TOO", -300, 12)),
        )

        val removed = pipeline.undo(second.batch.id)

        removed shouldBe 2
        database.transactionDao().count() shouldBe 1
        database.transactionDao().allChronological().single().rawDescription shouldBe "KEEP ME"
    }

    @Test
    fun `a mostly-duplicate import is flagged as a re-import`() = runTest {
        // Worth surfacing to the user: "0 of 3 imported" looks like a failure
        // unless you explain that it means the data was already there.
        val statement = result(
            txn("A", -100, 10),
            txn("B", -200, 11),
            txn("C", -300, 12),
        )
        pipeline.import(accountId, statement)
        val second = pipeline.import(accountId, statement)

        second.batch.isLikelyReimport shouldBe true
    }

    // --- Normalisation and persistence fidelity --------------------------------------------

    @Test
    fun `merchant keys are computed and stored`() = runTest {
        pipeline.import(
            accountId,
            result(txn("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA", -475, 14)),
        )

        database.transactionDao().allChronological()
            .single()
            .merchantKey shouldBe "BLUE BOTTLE COFFEE"
    }

    @Test
    fun `amounts round-trip exactly`() = runTest {
        pipeline.import(
            accountId,
            result(
                txn("BIG ONE", -123_456_789, 14),
                txn("ONE CENT", -1, 15),
                txn("INCOME", 250_000, 16),
            ),
        )

        val stored = database.transactionDao().allChronological().map { it.toDomain().amount }
        stored shouldContainExactly listOf(usd(-123_456_789), usd(-1), usd(250_000))
    }

    @Test
    fun `OCR imports are marked as needing review`() = runTest {
        val outcome = pipeline.import(
            accountId,
            result(txn("COFFEE", -475, 14), source = ImportSource.OCR),
        )
        outcome.requiresReview shouldBe true
    }

    @Test
    fun `CSV imports do not need review`() = runTest {
        val outcome = pipeline.import(accountId, result(txn("COFFEE", -475, 14)))
        outcome.requiresReview shouldBe false
    }

    @Test
    fun `uncategorised merchants can be listed for classification`() = runTest {
        // This is the input to the AI categorisation feature: merchant strings
        // only, deduplicated, with no amounts or dates attached.
        pipeline.import(
            accountId,
            result(
                txn("SQ *BLUE BOTTLE COFFEE #47 OAKLAND CA", -475, 14),
                txn("SQ *BLUE BOTTLE COFFEE #12 SAN FRANCISCO CA", -500, 15),
                txn("NETFLIX.COM", -1599, 16),
            ),
        )

        // The two coffee shops collapse to one merchant, so only two strings
        // would ever need to leave the device.
        database.transactionDao().uncategorisedMerchants() shouldContainExactly
            listOf("BLUE BOTTLE COFFEE", "NETFLIX")
    }
}
