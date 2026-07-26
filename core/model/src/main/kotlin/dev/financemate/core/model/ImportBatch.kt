package dev.financemate.core.model

import java.time.Instant

/**
 * A record of one import run.
 *
 * Every transaction remembers the batch that created it, so a bad import — wrong
 * account picked, wrong column mapping, wrong sign convention — can be undone
 * wholesale instead of being unpicked row by row. Statement imports go wrong
 * often enough that this is a requirement, not a nicety.
 */
public data class ImportBatch(
    val id: ImportBatchId,
    val accountId: AccountId,
    val source: ImportSource,
    val importedAt: Instant,
    /** Original file name, for the user to recognise. Not a path. */
    val fileName: String? = null,
    val rowsParsed: Int,
    val rowsImported: Int,
    /** Rows recognised as already present and therefore skipped. */
    val rowsDuplicate: Int,
    val rowsFailed: Int,
) {
    init {
        require(rowsParsed >= 0 && rowsImported >= 0 && rowsDuplicate >= 0 && rowsFailed >= 0) {
            "Import batch row counts must not be negative"
        }
    }

    /**
     * A re-import of an overlapping statement should be almost entirely
     * duplicates. If it is not, the dedup key is not doing its job and the user
     * is about to get double-counted spending.
     */
    val isLikelyReimport: Boolean
        get() = rowsParsed > 0 && rowsDuplicate.toDouble() / rowsParsed > 0.9
}
