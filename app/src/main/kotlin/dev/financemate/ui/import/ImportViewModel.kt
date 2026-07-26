package dev.financemate.ui.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.financemate.AppContainer
import dev.financemate.core.data.mapper.toEntity
import dev.financemate.core.model.Account
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
            runCatching { readAndParse(context, uri) }
                .onSuccess { preview -> _state.update { preview } }
                .onFailure { error ->
                    _state.update {
                        ImportUiState.Failed(
                            "Could not read that file (${error.javaClass.simpleName}). " +
                                "Try exporting it again as CSV, OFX, QFX, or QIF.",
                        )
                    }
                }
        }
    }

    public fun confirm(preview: ImportUiState.Preview) {
        viewModelScope.launch {
            _state.update { ImportUiState.Importing }
            runCatching {
                val database = container.database()

                // First import creates a holding account. Per-account selection
                // arrives with the accounts screen; until then everything lands
                // somewhere real rather than being silently dropped.
                val accountId = ensureDefaultAccount(database)

                container.importPipeline().import(
                    accountId = accountId,
                    parseResult = preview.parseResult,
                    fileName = preview.fileName,
                )
            }.onSuccess { outcome ->
                _state.update {
                    ImportUiState.Done(
                        imported = outcome.importedCount,
                        duplicates = outcome.duplicateCount,
                        isLikelyReimport = outcome.batch.isLikelyReimport,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    ImportUiState.Failed("Import failed (${error.javaClass.simpleName}).")
                }
            }
        }
    }

    public fun reset() {
        _state.update { ImportUiState.Idle }
    }

    private suspend fun ensureDefaultAccount(
        database: dev.financemate.core.data.FinanceMateDatabase,
    ): AccountId {
        database.accountDao().all().firstOrNull()?.let { return AccountId(it.id) }

        val account = Account(
            id = AccountId(UUID.randomUUID().toString()),
            displayName = "Imported transactions",
            institution = "Unspecified",
            type = AccountType.CHECKING,
            currency = CurrencyCode.USD,
        )
        database.accountDao().upsert(account.toEntity())
        return account.id
    }

    private suspend fun readAndParse(context: Context, uri: Uri): ImportUiState.Preview =
        withContext(Dispatchers.IO) {
            val fileName = displayNameOf(context, uri)
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Empty stream")

            val (parseResult, description) = parse(content, fileName)

            ImportUiState.Preview(
                fileName = fileName,
                formatDescription = description,
                parseResult = parseResult,
            )
        }

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
    }
}

public sealed interface ImportUiState {

    public data object Idle : ImportUiState

    public data object Parsing : ImportUiState

    public data class Preview(
        val fileName: String?,
        val formatDescription: String,
        val parseResult: ParseResult,
    ) : ImportUiState {
        val rowCount: Int get() = parseResult.transactions.size
        val errorCount: Int
            get() = parseResult.problems.count {
                it.severity == dev.financemate.core.model.ParseProblem.Severity.ERROR
            }
    }

    public data object Importing : ImportUiState

    public data class Done(
        val imported: Int,
        val duplicates: Int,
        val isLikelyReimport: Boolean,
    ) : ImportUiState

    public data class Failed(val message: String) : ImportUiState
}
