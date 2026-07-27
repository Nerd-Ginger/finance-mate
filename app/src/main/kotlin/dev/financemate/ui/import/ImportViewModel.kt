package dev.financemate.ui.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.financemate.AppContainer
import dev.financemate.core.data.import.ImportCheckpoint
import dev.financemate.core.data.import.ImportCheckpointBuilder
import dev.financemate.core.data.import.ImportOutcome
import dev.financemate.core.data.mapper.toDomain
import dev.financemate.core.data.mapper.toEntity
import dev.financemate.core.model.Account
import dev.financemate.core.model.ImportBatchId
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.AccountType
import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseResult
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.parsing.csv.BankProfiles
import dev.financemate.core.parsing.csv.ColumnDetector
import dev.financemate.core.parsing.csv.CsvReader
import dev.financemate.core.parsing.csv.CsvStatementParser
import dev.financemate.core.parsing.ofx.OfxParser
import dev.financemate.core.parsing.qif.QifParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Drives the import flow: pick a file, see what was found, confirm, commit.
 *
 * ## Why there is a confirmation step
 *
 * Parsing is shown before anything is written. A wrong bank profile or a
 * misdetected column mapping produces plausible-looking rows with the sign
 * inverted or the date field swapped, and once those are in the ledger they
 * corrupt every total silently. Two seconds of review is cheap; discovering
 * months later that spending was recorded as income is not.
 *
 * The file is read into memory and parsed **entirely on this device**. Nothing
 * from a statement is sent anywhere, in any mode.
 */
public class ImportViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    public val state: StateFlow<ImportUiState> = _state.asStateFlow()

    public fun onFilePicked(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { ImportUiState.Parsing }
            runCatching {
                val parsed = readAndParse(context, uri)
                // Parsing and forecasting are one step from the user's point of
                // view: they picked a file and want to know what it will do.
                // Showing a row count first and the real numbers a moment later
                // would just be two loading states.
                checkpointFor(parsed)
            }
                .onSuccess { preview -> _state.update { preview } }
                .onFailure { error ->
                    logWhereItFailed(error)
                    _state.update {
                        ImportUiState.Failed(
                            "Could not read that file (${error.javaClass.simpleName}). " +
                                "Try exporting it again as CSV, OFX, QFX, or QIF.",
                        )
                    }
                }
        }
    }

    /**
     * Re-reads the file against a different account.
     *
     * The counts have to be recomputed, not adjusted: dedup fingerprints include
     * the account, so the same rows can be entirely new in one account and
     * entirely duplicate in another. Keeping the old numbers would make the
     * checkpoint lie in precisely the situation the user changed the account to
     * resolve.
     */
    public fun changeAccount(account: Account) {
        val current = _state.value as? ImportUiState.Preview ?: return
        viewModelScope.launch {
            _state.update { current.copy(account = account, checkpoint = null) }
            runCatching {
                ImportCheckpointBuilder(container.database()).build(account.id, current.parseResult)
            }.onSuccess { checkpoint ->
                _state.update { current.copy(account = account, checkpoint = checkpoint) }
            }
        }
    }

    public fun confirm(preview: ImportUiState.Preview) {
        val accountId = preview.account?.id ?: return
        viewModelScope.launch {
            _state.update { ImportUiState.Importing }
            runCatching {
                container.importPipeline().import(
                    accountId = accountId,
                    parseResult = preview.parseResult,
                    fileName = preview.fileName,
                )
            }.onSuccess { outcome ->
                _state.update { doneStateFor(preview, outcome) }
            }.onFailure { error ->
                _state.update {
                    ImportUiState.Failed("Import failed (${error.javaClass.simpleName}).")
                }
            }
        }
    }

    /**
     * Removes the batch this import just wrote.
     *
     * Returns to the source picker rather than to the checkpoint. Undo is
     * pressed because something about the file was wrong, and offering to import
     * the same wrong file again would be the least useful next step available.
     */
    public fun undo(batchId: ImportBatchId) {
        viewModelScope.launch {
            runCatching { container.importPipeline().undo(batchId) }
                .onSuccess { _state.update { ImportUiState.Idle } }
                .onFailure { error ->
                    _state.update {
                        ImportUiState.Failed("Could not undo (${error.javaClass.simpleName}).")
                    }
                }
        }
    }

    public fun reset() {
        _state.update { ImportUiState.Idle }
    }

    private suspend fun doneStateFor(
        preview: ImportUiState.Preview,
        outcome: ImportOutcome,
    ): ImportUiState.Done {
        val report = container.savingsRepository().analyse()
        val untagged = container.savingsRepository().untaggedRecurringMerchants()

        return ImportUiState.Done(
            imported = outcome.importedCount,
            duplicates = outcome.duplicateCount,
            isLikelyReimport = outcome.batch.isLikelyReimport,
            accountName = preview.account?.displayName ?: "your ledger",
            batchId = outcome.batch.id,
            subtitle = subtitleFor(preview),
            recurringFound = report.subscriptions.size,
            unnamedMerchants = untagged.size,
            // What the user is actually here for: things the app noticed that
            // they had not. Duplicates and price rises both qualify; a merchant
            // it simply could not name does not, which is why that count sits in
            // its own neutral row rather than being folded in here.
            worthALook = report.duplicates.size + report.priceIncreases.size,
        )
    }

    private fun subtitleFor(preview: ImportUiState.Preview): String? {
        val checkpoint = preview.checkpoint ?: return null
        val parts = buildList {
            val from = checkpoint.earliest
            val to = checkpoint.latest
            if (from != null && to != null) {
                add(if (from == to) from.format(SUBTITLE_DATE) else "${from.format(SUBTITLE_DATE)} – ${to.format(SUBTITLE_DATE)}")
            }
            when (checkpoint.skipped.size) {
                0 -> Unit
                1 -> add("1 row skipped")
                else -> add("${checkpoint.skipped.size} rows skipped")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /**
     * Logs *where* an import failed, never *what* it was reading.
     *
     * Exception messages are excluded deliberately. A parser message can quote
     * the offending row, and logcat is world-readable to anyone with a cable and
     * shipped to crash reporters — precisely the destinations the redaction gate
     * exists to keep statement content away from. Class name and stack frames
     * are enough to locate a bug and carry none of the user's money.
     */
    private fun logWhereItFailed(error: Throwable) {
        val frames = error.stackTrace.take(STACK_FRAMES).joinToString(separator = " <- ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
        }
        Log.w(LOG_TAG, "Import failed: ${error.javaClass.name} at $frames")
    }

    private suspend fun checkpointFor(parsed: ParsedFile): ImportUiState.Preview {
        val database = container.database()
        val accounts = database.accountDao().all().map { it.toDomain() }
        val account = accounts.firstOrNull() ?: createHoldingAccount(database)

        return ImportUiState.Preview(
            fileName = parsed.fileName,
            formatDescription = parsed.formatDescription,
            parseResult = parsed.parseResult,
            account = account,
            accounts = if (account in accounts) accounts else accounts + account,
            checkpoint = ImportCheckpointBuilder(database).build(account.id, parsed.parseResult),
        )
    }

    /**
     * The account a first-time user's statement lands in.
     *
     * Created up front rather than at import time so the checkpoint has a real
     * account to fingerprint against, and so "Read as:" names something the user
     * can then rename or change. It is written to the database immediately
     * because dedup hashes are keyed by account id, and an id that changed
     * between the forecast and the write would make the two disagree.
     */
    private suspend fun createHoldingAccount(
        database: dev.financemate.core.data.FinanceMateDatabase,
    ): Account {
        val account = Account(
            id = AccountId(UUID.randomUUID().toString()),
            displayName = "Imported transactions",
            institution = "Unspecified",
            type = AccountType.CHECKING,
            currency = CurrencyCode.USD,
        )
        database.accountDao().upsert(account.toEntity())
        return account
    }

    private suspend fun readAndParse(context: Context, uri: Uri): ParsedFile =
        withContext(Dispatchers.IO) {
            val fileName = displayNameOf(context, uri)
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Empty stream")

            val (parseResult, description) = parse(content, fileName)

            ParsedFile(
                fileName = fileName,
                formatDescription = description,
                parseResult = parseResult,
            )
        }

    /** A parsed file, before the ledger has been consulted about it. */
    private data class ParsedFile(
        val fileName: String?,
        val formatDescription: String,
        val parseResult: ParseResult,
    )

    /**
     * Chooses a parser from the content itself rather than trusting the file
     * extension, which is frequently wrong — banks hand out `.qfx` files
     * containing OFX, and browsers rename downloads freely.
     */
    private fun parse(content: String, fileName: String?): Pair<ParseResult, String> {
        val head = content.take(SNIFF_LENGTH).uppercase()

        return when {
            head.contains("<OFX>") || head.contains("OFXHEADER") -> {
                val source = if (fileName?.endsWith(".qfx", ignoreCase = true) == true) {
                    ImportSource.QFX
                } else {
                    ImportSource.OFX
                }
                OfxParser.parse(content, source) to "OFX/QFX — bank-supplied transaction ids"
            }

            head.trimStart().startsWith("!TYPE:") -> QifParser.parse(content) to "QIF"

            else -> parseCsv(content)
        }
    }

    private fun parseCsv(content: String): Pair<ParseResult, String> {
        val rows = CsvReader.parse(content)
        require(rows.isNotEmpty()) { "No rows found" }

        BankProfiles.matching(rows.first().fields)?.let { profile ->
            return CsvStatementParser.parse(content, profile.mapping) to
                "CSV — recognised as ${profile.displayName}"
        }

        val detection = ColumnDetector.detect(rows)
            ?: error("Could not work out which columns hold the date, description, and amount")

        val description = when (detection.confidence) {
            ColumnDetector.Confidence.HEADER_MATCH -> "CSV — columns matched by name"
            ColumnDetector.Confidence.CONTENT_GUESS ->
                "CSV — columns guessed from the data. Check these rows carefully."
        }
        return CsvStatementParser.parse(content, detection.mapping) to description
    }

    private fun displayNameOf(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    private companion object {
        const val SNIFF_LENGTH = 512
        const val LOG_TAG = "FinanceMateImport"
        const val STACK_FRAMES = 8
        val SUBTITLE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}

public sealed interface ImportUiState {

    public data object Idle : ImportUiState

    public data object Parsing : ImportUiState

    /**
     * The checkpoint: a file read, understood, and not yet written.
     *
     * [checkpoint] is null only while it is being recomputed after an account
     * change. Holding onto the previous figures during that moment would show
     * counts belonging to a different account, which is exactly the confusion
     * changing the account was meant to clear up.
     */
    public data class Preview(
        val fileName: String?,
        val formatDescription: String,
        val parseResult: ParseResult,
        val account: Account?,
        val accounts: List<Account>,
        val checkpoint: ImportCheckpoint?,
    ) : ImportUiState {
        val rowCount: Int get() = parseResult.transactions.size
        val errorCount: Int
            get() = parseResult.problems.count {
                it.severity == dev.financemate.core.model.ParseProblem.Severity.ERROR
            }
    }

    public data object Importing : ImportUiState

    /**
     * What the import did, plus what the analysis found immediately afterwards.
     *
     * The analysis counts are gathered here rather than left for the savings
     * screen because they are the reason the file was imported at all. A screen
     * that says only "240 transactions added" has told the user about
     * bookkeeping; "3 things worth a look" is the promise being kept.
     */
    public data class Done(
        val imported: Int,
        val duplicates: Int,
        val isLikelyReimport: Boolean,
        val accountName: String,
        val batchId: ImportBatchId,
        /** Date range and skipped rows, when there is anything to say. */
        val subtitle: String?,
        val recurringFound: Int,
        val unnamedMerchants: Int,
        val worthALook: Int,
    ) : ImportUiState

    public data class Failed(val message: String) : ImportUiState
}
