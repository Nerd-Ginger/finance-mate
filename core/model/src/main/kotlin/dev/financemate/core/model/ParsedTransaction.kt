package dev.financemate.core.model

import dev.financemate.core.money.Money
import java.time.LocalDate

/**
 * A transaction as it came out of a parser, before it has been normalised,
 * de-duplicated, categorised, or given an identity in the ledger.
 *
 * Keeping this separate from [Transaction] means a parser cannot accidentally
 * invent a ledger entry: the import pipeline has to run the row through
 * normalisation and dedup before anything reaches the database.
 */
public data class ParsedTransaction(
    val postedDate: LocalDate,
    val amount: Money,
    val rawDescription: String,
    /** Bank-supplied stable id, when the format provides one (OFX `FITID`). */
    val institutionTransactionId: String? = null,
    val isPending: Boolean = false,
    /**
     * Where in the source this row came from — a CSV line number, a PDF page, an
     * OCR block index. Used to point the user at the offending row when a parse
     * is ambiguous, rather than failing with an unlocatable error.
     */
    val sourceLocation: String? = null,
    /**
     * Parser confidence, 0.0..1.0. Structured formats produce 1.0. OCR produces
     * less, and anything below the review threshold is shown to the user for
     * confirmation before it is written.
     */
    val confidence: Double = 1.0,
) {
    init {
        require(confidence in 0.0..1.0) { "Confidence must be within 0.0..1.0, got $confidence" }
        require(rawDescription.isNotBlank()) { "Parsed transaction must carry a description" }
    }
}

/** Where an import came from. Recorded so a bad batch can be traced and undone. */
public enum class ImportSource {
    CSV,
    OFX,
    QFX,
    QIF,
    PDF_TEXT,
    /** On-device OCR of a screenshot or scanned statement. Always needs review. */
    OCR,
    MANUAL,
    ;

    /**
     * True when the source is lossy enough that rows must be confirmed by the
     * user before they are written to the ledger.
     */
    public val requiresReview: Boolean
        get() = this == OCR || this == PDF_TEXT
}

/**
 * The outcome of parsing one file.
 *
 * Parsers report problems rather than throwing, because a single unreadable row
 * in a 900-row statement should not lose the other 899. The user sees what was
 * skipped and why.
 */
public data class ParseResult(
    val transactions: List<ParsedTransaction>,
    val source: ImportSource,
    val problems: List<ParseProblem> = emptyList(),
    /** Account identifier declared by the file itself, when the format carries one. */
    val declaredAccountMask: String? = null,
) {
    val hasProblems: Boolean get() = problems.isNotEmpty()
}

public data class ParseProblem(
    val location: String,
    val message: String,
    val severity: Severity,
    /**
     * The offending text. Kept for display to the user so they can see which row
     * failed — but note this is raw statement content, so it must never be
     * included in anything sent off-device.
     */
    val rawContent: String? = null,
) {
    public enum class Severity {
        /** Row was still imported, but something looked unusual. */
        WARNING,

        /** Row was skipped. */
        ERROR,
    }
}
