package dev.financemate.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.financemate.core.data.import.ImportCheckpointBuilder
import dev.financemate.core.data.import.ImportPipeline
import dev.financemate.core.data.mapper.toEntity
import dev.financemate.core.model.Account
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.AccountType
import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The checkpoint's job is to tell the truth about what pressing Import will do.
 * These tests check the forecast against what the pipeline actually does, since
 * a forecast that disagrees with the writer is worse than no forecast at all.
 */
@RunWith(RobolectricTestRunner::class)
class ImportCheckpointTest {

    private lateinit var database: FinanceMateDatabase
    private lateinit var builder: ImportCheckpointBuilder
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
        builder = ImportCheckpointBuilder(database)
        pipeline = ImportPipeline(database, idGenerator = { "id-${idCounter++}" })

        database.accountDao().upsert(
            Account(
                id = accountId,
                displayName = "Everyday Checking",
                institution = "Test Bank",
                type = AccountType.CHECKING,
                currency = usd,
            ).toEntity(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun everythingIsNewOnAnEmptyLedger() = runTest {
        val checkpoint = builder.build(accountId, result(spend("GREENFIELD MARKET", 6318, 2)))

        checkpoint.rowsToAdd shouldBe 1
        checkpoint.alreadyInLedger shouldBe 0
        checkpoint.addsNothing shouldBe false
    }

    @Test
    fun reImportingTheSameFileAddsNothing() = runTest {
        val parsed = result(
            spend("GREENFIELD MARKET", 6318, 2),
            spend("CHORDLY FAMILY", 1699, 5),
            earn("PAYROLL HALDEN CO", 241_000, 3),
        )
        pipeline.import(accountId, parsed)

        val checkpoint = builder.build(accountId, parsed)

        checkpoint.rowsToAdd shouldBe 0
        checkpoint.alreadyInLedger shouldBe 3
        checkpoint.addsNothing shouldBe true
    }

    @Test
    fun anOverlappingExportCountsOnlyTheNewRows() = runTest {
        // The realistic case: the user re-downloads a wider date range rather
        // than the exact same file.
        pipeline.import(accountId, result(spend("GREENFIELD MARKET", 6318, 2)))

        val checkpoint = builder.build(
            accountId,
            result(
                spend("GREENFIELD MARKET", 6318, 2),
                spend("CHORDLY FAMILY", 1699, 5),
            ),
        )

        checkpoint.rowsToAdd shouldBe 1
        checkpoint.alreadyInLedger shouldBe 1
    }

    @Test
    fun theForecastMatchesWhatTheImportActuallyDoes() = runTest {
        pipeline.import(accountId, result(spend("GREENFIELD MARKET", 6318, 2)))
        val incoming = result(
            spend("GREENFIELD MARKET", 6318, 2),
            spend("CHORDLY FAMILY", 1699, 5),
            earn("PAYROLL HALDEN CO", 241_000, 3),
        )

        val checkpoint = builder.build(accountId, incoming)
        val outcome = pipeline.import(accountId, incoming)

        // The whole point. If these two ever diverge, the checkpoint is lying.
        checkpoint.rowsToAdd shouldBe outcome.importedCount
        checkpoint.alreadyInLedger shouldBe outcome.duplicateCount
    }

    @Test
    fun identicalRowsInOneFileAreBothRealTransactions() = runTest {
        // Two identical charges on the same day are two coffees, not one
        // double-counted coffee. DedupHasher numbers them so they survive, and
        // the checkpoint must forecast both rather than helpfully collapsing
        // them into one.
        val incoming = result(
            spend("BRIGHT COFFEE", 375, 2),
            spend("BRIGHT COFFEE", 375, 2),
        )

        val checkpoint = builder.build(accountId, incoming)
        val outcome = pipeline.import(accountId, incoming)

        checkpoint.rowsToAdd shouldBe 2
        checkpoint.alreadyInLedger shouldBe 0
        checkpoint.rowsToAdd shouldBe outcome.importedCount
    }

    @Test
    fun repeatedRowsAreStillRecognisedOnReImport() = runTest {
        // The other half of the same rule: both coffees must be recognised the
        // second time round, or a re-import would silently add a third.
        val incoming = result(
            spend("BRIGHT COFFEE", 375, 2),
            spend("BRIGHT COFFEE", 375, 2),
        )
        pipeline.import(accountId, incoming)

        val checkpoint = builder.build(accountId, incoming)

        checkpoint.rowsToAdd shouldBe 0
        checkpoint.alreadyInLedger shouldBe 2
    }

    @Test
    fun reportsTheDateRangeAcrossTheFile() = runTest {
        val checkpoint = builder.build(
            accountId,
            result(
                spend("CHORDLY FAMILY", 1699, 14),
                spend("GREENFIELD MARKET", 6318, 2),
                earn("PAYROLL HALDEN CO", 241_000, 30),
            ),
        )

        checkpoint.earliest shouldBe LocalDate.of(2026, 6, 2)
        checkpoint.latest shouldBe LocalDate.of(2026, 6, 30)
    }

    @Test
    fun anEmptyFileHasNoDateRange() = runTest {
        val checkpoint = builder.build(accountId, result())

        checkpoint.rowsToAdd shouldBe 0
        checkpoint.earliest.shouldBeNull()
        checkpoint.latest.shouldBeNull()
    }

    @Test
    fun skippedRowsAreSurfacedNotSwallowed() = runTest {
        val checkpoint = builder.build(
            accountId,
            ParseResult(
                transactions = listOf(spend("GREENFIELD MARKET", 6318, 2)),
                source = ImportSource.CSV,
                problems = listOf(
                    ParseProblem("line 4", "No date", ParseProblem.Severity.ERROR),
                    ParseProblem("line 9", "No date", ParseProblem.Severity.ERROR),
                    // A warning is not a skip; the row was still imported.
                    ParseProblem("line 12", "Unusual amount", ParseProblem.Severity.WARNING),
                ),
            ),
        )

        checkpoint.skipped.map { it.location }.shouldContainExactly("line 4", "line 9")
    }

    @Test
    fun theSampleIncludesACreditSoAnInvertedSignIsVisible() = runTest {
        // Four debits first, then the pay cheque. Taking the first three rows
        // would show only spending, and every sign in the file could be
        // backwards without a single one of them looking wrong.
        val checkpoint = builder.build(
            accountId,
            result(
                spend("GREENFIELD MARKET", 6318, 2),
                spend("CHORDLY FAMILY", 1699, 5),
                spend("NORTHGATE PARKING", 950, 6),
                spend("BRIGHT COFFEE", 425, 7),
                earn("PAYROLL HALDEN CO", 241_000, 8),
            ),
        )

        checkpoint.sample.size shouldBe 3
        checkpoint.sample.count { it.amount.minorUnits > 0 } shouldBe 1
        checkpoint.sample.map { it.postedDate } shouldBe checkpoint.sample
            .map { it.postedDate }
            .sorted()
    }

    @Test
    fun aFileOfOnlySpendingStillSamplesThreeRows() = runTest {
        val checkpoint = builder.build(
            accountId,
            result(
                spend("GREENFIELD MARKET", 6318, 2),
                spend("CHORDLY FAMILY", 1699, 5),
                spend("NORTHGATE PARKING", 950, 6),
                spend("BRIGHT COFFEE", 425, 7),
            ),
        )

        checkpoint.sample.size shouldBe 3
    }

    // --- Fixtures ----------------------------------------------------------

    private fun result(vararg transactions: ParsedTransaction) = ParseResult(
        transactions = transactions.toList(),
        source = ImportSource.CSV,
    )

    private fun spend(description: String, minor: Long, day: Int) = ParsedTransaction(
        postedDate = LocalDate.of(2026, 6, day),
        amount = Money(-minor, usd),
        rawDescription = description,
    )

    private fun earn(description: String, minor: Long, day: Int) = ParsedTransaction(
        postedDate = LocalDate.of(2026, 6, day),
        amount = Money(minor, usd),
        rawDescription = description,
    )
}
