package dev.financemate.core.data.import

import androidx.room.withTransaction
import dev.financemate.core.data.FinanceMateDatabase
import dev.financemate.core.data.mapper.toEntity
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.ImportBatch
import dev.financemate.core.model.ImportBatchId
import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.model.Transaction
import dev.financemate.core.model.TransactionId
import dev.financemate.core.parsing.DedupHasher
import dev.financemate.core.parsing.MerchantNormaliser
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Turns a [ParseResult] into ledger rows.
 *
 * ```
 * ParsedTransaction -> normalise merchant -> fingerprint -> insert-or-ignore -> Transaction
 * ```
 *
 * The whole import runs in one database transaction. A statement that fails
 * halfway through must leave nothing behind: a partial import is worse than a
 * failed one, because the user cannot tell it happened and will re-import,
 * producing a mess that is tedious to unpick.
 */
public class ImportPipeline(
    private val database: FinanceMateDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    public suspend fun import(
        accountId: AccountId,
        parseResult: ParseResult,
        fileName: String? = null,
    ): ImportOutcome = database.withTransaction {
        val batchId = ImportBatchId(idGenerator())
        val hashes = DedupHasher.assignHashes(accountId, parseResult.transactions)

        val candidates = parseResult.transactions.mapIndexed { index, parsed ->
            parsed.toTransaction(
                accountId = accountId,
                dedupHash = hashes[index],
                batchId = batchId,
            )
        }

        // Insert-or-ignore does the de-duplication in the database, using the
        // unique indices on dedupHash and (accountId, institutionTransactionId).
        // Doing it here rather than with a pre-flight SELECT means two concurrent
        // imports of the same file cannot both decide a row is new.
        val rowIds = database.transactionDao()
            .insertIgnoringDuplicates(candidates.map { it.toEntity() })

        val insertedCount = rowIds.count { it != IGNORED_ROW_ID }
        val duplicateCount = rowIds.size - insertedCount

        val batch = ImportBatch(
            id = batchId,
            accountId = accountId,
            source = parseResult.source,
            importedAt = Instant.now(clock),
            fileName = fileName,
            rowsParsed = parseResult.transactions.size,
            rowsImported = insertedCount,
            rowsDuplicate = duplicateCount,
            rowsFailed = parseResult.problems.count { it.severity == ERROR },
        )
        database.importBatchDao().insert(batch.toEntity())

        ImportOutcome(
            batch = batch,
            imported = candidates.filterIndexed { index, _ -> rowIds[index] != IGNORED_ROW_ID },
            requiresReview = parseResult.source.requiresReview,
            problems = parseResult.problems,
        )
    }

    /**
     * Removes every transaction created by [batchId].
     *
     * The reason imports are batched at all: a wrong account, a wrong column
     * mapping, or an inverted sign is easy to do and, without this, tedious to
     * undo one row at a time.
     */
    public suspend fun undo(batchId: ImportBatchId): Int = database.withTransaction {
        val removed = database.transactionDao().deleteByBatch(batchId.value)
        database.importBatchDao().delete(batchId.value)
        removed
    }

    private fun ParsedTransaction.toTransaction(
        accountId: AccountId,
        dedupHash: String,
        batchId: ImportBatchId,
    ): Transaction = Transaction(
        id = TransactionId(idGenerator()),
        accountId = accountId,
        postedDate = postedDate,
        amount = amount,
        rawDescription = rawDescription,
        merchantKey = MerchantNormaliser.normalise(rawDescription),
        dedupHash = dedupHash,
        importBatchId = batchId,
        institutionTransactionId = institutionTransactionId,
        isPending = isPending,
    )

    private companion object {
        /** Room returns -1 from an INSERT OR IGNORE that hit a unique constraint. */
        const val IGNORED_ROW_ID = -1L

        val ERROR = dev.financemate.core.model.ParseProblem.Severity.ERROR
    }
}

/**
 * What an import did.
 *
 * [ImportBatch.isLikelyReimport] is worth surfacing: a statement that is almost
 * entirely duplicates is the expected result of re-downloading an overlapping
 * date range, and telling the user that explicitly stops them thinking the
 * import failed.
 */
public data class ImportOutcome(
    val batch: ImportBatch,
    val imported: List<Transaction>,
    /**
     * True for OCR and PDF sources, where rows should be confirmed before being
     * treated as authoritative.
     */
    val requiresReview: Boolean,
    val problems: List<dev.financemate.core.model.ParseProblem>,
) {
    val importedCount: Int get() = batch.rowsImported
    val duplicateCount: Int get() = batch.rowsDuplicate
}
