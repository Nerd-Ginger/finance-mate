package dev.financemate.core.data.import

import dev.financemate.core.data.FinanceMateDatabase
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.parsing.DedupHasher
import java.time.LocalDate

/**
 * What a file would do to the ledger, worked out before anything is written.
 *
 * The import itself already de-duplicates, and does it more reliably than this
 * can — [ImportPipeline] uses insert-or-ignore inside a transaction, so two
 * concurrent imports cannot both decide a row is new. This is not a replacement
 * for that. It exists because "240 rows to add, 0 already in your ledger" is
 * information the user needs *before* deciding, and the alternative is finding
 * out afterwards.
 *
 * The two can disagree in one direction: a row counted as new here could be
 * inserted by another import in between. That is fine. This is a forecast shown
 * to a human, not a decision the writer depends on.
 */
public class ImportCheckpointBuilder(
    private val database: FinanceMateDatabase,
) {

    public suspend fun build(
        accountId: AccountId,
        parseResult: ParseResult,
    ): ImportCheckpoint {
        val transactions = parseResult.transactions

        // The same hashing the pipeline will use, so the forecast and the write
        // agree about what counts as the same transaction.
        val hashes = DedupHasher.assignHashes(accountId, transactions)
        val existing = if (hashes.isEmpty()) {
            emptySet()
        } else {
            database.transactionDao().existingHashes(hashes).toSet()
        }

        // A plain membership count, with no special handling for rows that repeat
        // within the file. That is not an oversight: `assignHashes` numbers
        // identical rows in source order precisely so two $3.75 coffees on the
        // same day stay two transactions. Collapsing them here would forecast one
        // row where the pipeline correctly writes two.
        val alreadyPresent = hashes.count { it in existing }

        val dates = transactions.map { it.postedDate }

        return ImportCheckpoint(
            rowsToAdd = transactions.size - alreadyPresent,
            alreadyInLedger = alreadyPresent,
            earliest = dates.minOrNull(),
            latest = dates.maxOrNull(),
            skipped = parseResult.problems.filter { it.severity == ParseProblem.Severity.ERROR },
            sample = sampleFor(transactions),
        )
    }

    /**
     * Up to three rows chosen to make an inverted sign obvious.
     *
     * Not the first three. A statement usually opens with a run of spending, so
     * the first three rows are all negative and look perfectly reasonable even
     * when every sign in the file is backwards. Including a credit — the pay
     * cheque, typically — is what lets a human catch it, and an inverted sign is
     * the one import error that does plausible, invisible damage: it corrupts
     * every total silently and looks fine on the way in.
     */
    private fun sampleFor(transactions: List<ParsedTransaction>): List<ParsedTransaction> {
        if (transactions.size <= SAMPLE_SIZE) return transactions

        val firstCredit = transactions.firstOrNull { it.amount.minorUnits > 0 }
        val firstDebit = transactions.firstOrNull { it.amount.minorUnits < 0 }

        val chosen = LinkedHashSet<ParsedTransaction>()
        firstDebit?.let(chosen::add)
        firstCredit?.let(chosen::add)
        transactions.forEach { row ->
            if (chosen.size < SAMPLE_SIZE) chosen.add(row)
        }

        return chosen.take(SAMPLE_SIZE).sortedBy { it.postedDate }
    }

    private companion object {
        const val SAMPLE_SIZE = 3
    }
}

/**
 * The forecast shown at the checkpoint.
 *
 * @property skipped rows the parser could not read at all. Surfaced rather than
 *   silently dropped — a user who exported 243 rows and imported 240 deserves to
 *   know where the other three went.
 */
public data class ImportCheckpoint(
    val rowsToAdd: Int,
    val alreadyInLedger: Int,
    val earliest: LocalDate?,
    val latest: LocalDate?,
    val skipped: List<ParseProblem>,
    val sample: List<ParsedTransaction>,
) {
    /** True when the file adds nothing — almost always a re-import of the same export. */
    val addsNothing: Boolean get() = rowsToAdd == 0
}
