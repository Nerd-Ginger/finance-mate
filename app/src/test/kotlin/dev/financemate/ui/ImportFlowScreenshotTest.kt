package dev.financemate.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.financemate.core.data.import.ImportCheckpoint
import dev.financemate.core.model.Account
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.AccountType
import dev.financemate.core.model.ImportBatchId
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.ui.import.CheckpointScreen
import dev.financemate.ui.import.ImportUiState
import dev.financemate.ui.import.ResultScreen
import dev.financemate.ui.import.SourcePickerScreen
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ImportFlowScreenshotTest : ScreenshotTest() {

    private val usd = CurrencyCode.USD

    @Test
    fun sourcePicker() {
        capture("import-source") {
            SourcePickerScreen(onChooseFile = {})
        }
    }

    @Test
    fun checkpoint() {
        capture("import-checkpoint") {
            CheckpointScreen(
                fileName = "statement-jun.csv",
                formatDescription = "CSV — recognized as Chase (checking)",
                account = chaseChecking,
                checkpoint = sampleCheckpoint(),
                accounts = listOf(chaseChecking),
                onChangeAccount = {},
                onImport = {},
                onCancel = {},
            )
        }
    }

    /**
     * The same file imported twice.
     *
     * Worth its own image because the primary action disables and changes copy,
     * and a re-import must not look like a failure.
     */
    @Test
    fun checkpointWhenNothingIsNew() {
        capture("import-checkpoint-nothing-new") {
            CheckpointScreen(
                fileName = "statement-jun.csv",
                formatDescription = "CSV — recognized as Chase (checking)",
                account = chaseChecking,
                checkpoint = sampleCheckpoint(rowsToAdd = 0, alreadyInLedger = 240, skipped = emptyList()),
                accounts = listOf(chaseChecking),
                onChangeAccount = {},
                onImport = {},
                onCancel = {},
            )
        }
    }

    @Test
    fun result() {
        capture("import-result") {
            ResultScreen(
                state = ImportUiState.Done(
                    imported = 240,
                    duplicates = 0,
                    isLikelyReimport = false,
                    accountName = "Chase Checking",
                    batchId = ImportBatchId("batch-1"),
                    subtitle = "14 Jan – 30 Jun · 3 rows skipped",
                    recurringFound = 14,
                    unnamedMerchants = 6,
                    worthALook = 3,
                ),
                onSeeSavings = {},
                onUndo = {},
            )
        }
    }

    /**
     * An import that found nothing interesting.
     *
     * The honest version of the same screen: no orange row, and the primary
     * action stops promising things to look at. Captured so that state cannot
     * quietly acquire a celebratory tone it has not earned.
     */
    @Test
    fun resultWithNothingWorthALook() {
        capture("import-result-quiet") {
            ResultScreen(
                state = ImportUiState.Done(
                    imported = 31,
                    duplicates = 209,
                    isLikelyReimport = true,
                    accountName = "Chase Checking",
                    batchId = ImportBatchId("batch-2"),
                    subtitle = "1 Jun – 30 Jun",
                    recurringFound = 14,
                    unnamedMerchants = 0,
                    worthALook = 0,
                ),
                onSeeSavings = {},
                onUndo = {},
            )
        }
    }

    // --- Fixture -----------------------------------------------------------

    private val chaseChecking = Account(
        id = AccountId("acct-1"),
        displayName = "Chase Checking",
        institution = "Chase",
        type = AccountType.CHECKING,
        currency = usd,
        mask = "4321",
    )

    private fun sampleCheckpoint(
        rowsToAdd: Int = 240,
        alreadyInLedger: Int = 0,
        skipped: List<ParseProblem> = listOf(
            ParseProblem("line 4", "No date", ParseProblem.Severity.ERROR, "  ,,-12.00,"),
            ParseProblem("line 88", "No date", ParseProblem.Severity.ERROR),
            ParseProblem("line 91", "No date", ParseProblem.Severity.ERROR),
        ),
    ) = ImportCheckpoint(
        rowsToAdd = rowsToAdd,
        alreadyInLedger = alreadyInLedger,
        earliest = LocalDate.of(2026, 1, 14),
        latest = LocalDate.of(2026, 6, 30),
        skipped = skipped,
        sample = listOf(
            ParsedTransaction(
                postedDate = LocalDate.of(2026, 6, 2),
                amount = Money(-6318, usd),
                rawDescription = "Greenfield Market",
            ),
            ParsedTransaction(
                postedDate = LocalDate.of(2026, 6, 3),
                amount = Money(241_000, usd),
                rawDescription = "Payroll — Halden Co",
            ),
            ParsedTransaction(
                postedDate = LocalDate.of(2026, 6, 5),
                amount = Money(-1699, usd),
                rawDescription = "Chordly Family",
            ),
        ),
    )
}
