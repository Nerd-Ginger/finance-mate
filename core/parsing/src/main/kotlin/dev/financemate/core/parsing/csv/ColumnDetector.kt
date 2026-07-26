package dev.financemate.core.parsing.csv

import dev.financemate.core.parsing.AmountParser
import dev.financemate.core.parsing.DateParser

/**
 * Works out how to read a CSV whose layout is not a known bank profile.
 *
 * Two strategies, in order:
 *
 * 1. **Header names.** If the first row looks like headers, match its labels
 *    against the vocabulary banks actually use ("Posting Date", "Payee",
 *    "Withdrawal", …).
 * 2. **Column content.** Failing that, look at what the data *is*: the column
 *    where most values parse as dates is the date, the column where most parse
 *    as amounts is the amount, and the widest free-text column is the
 *    description.
 *
 * The result is always shown to the user for confirmation before import. This is
 * a starting point for the mapping screen, not an authority — a wrong guess that
 * imports silently is much worse than one the user corrects in five seconds.
 */
public object ColumnDetector {

    private val DATE_HEADERS = setOf(
        "date", "posting date", "post date", "posted date", "transaction date",
        "trans date", "value date", "effective date", "date posted", "activity date",
    )

    private val DESCRIPTION_HEADERS = setOf(
        "description", "payee", "merchant", "name", "memo", "details", "narrative",
        "transaction", "reference", "particulars", "original description",
    )

    private val AMOUNT_HEADERS = setOf(
        "amount", "transaction amount", "amt", "value",
    )

    private val DEBIT_HEADERS = setOf(
        "debit", "withdrawal", "withdrawals", "money out", "paid out", "charge", "debit amount",
    )

    private val CREDIT_HEADERS = setOf(
        "credit", "deposit", "deposits", "money in", "paid in", "payment", "credit amount",
    )

    private val BALANCE_HEADERS = setOf(
        "balance", "running bal", "running balance", "ending balance", "bal",
    )

    /**
     * Detects a mapping for [rows], or returns null when the file does not look
     * like a transaction list at all.
     */
    public fun detect(rows: List<CsvRow>): Detection? {
        if (rows.isEmpty()) return null

        val header = rows.first()
        val looksLikeHeader = isHeaderRow(header)
        val dataRows = if (looksLikeHeader) rows.drop(1) else rows
        if (dataRows.isEmpty()) return null

        val fromHeader = if (looksLikeHeader) detectFromHeader(header, dataRows) else null
        val mapping = fromHeader ?: detectFromContent(dataRows, hasHeaderRow = looksLikeHeader)
        ?: return null

        return Detection(
            mapping = mapping,
            confidence = if (fromHeader != null) Confidence.HEADER_MATCH else Confidence.CONTENT_GUESS,
            headerFields = if (looksLikeHeader) header.fields else emptyList(),
        )
    }

    public data class Detection(
        val mapping: ColumnMapping,
        val confidence: Confidence,
        val headerFields: List<String>,
    )

    public enum class Confidence {
        /** Column labels were recognised. Usually right. */
        HEADER_MATCH,

        /** Inferred from the shape of the data. Always worth confirming. */
        CONTENT_GUESS,
    }

    /**
     * A header row is one where no field parses as a date or an amount. Real
     * headers are words; a first data row is not.
     */
    private fun isHeaderRow(row: CsvRow): Boolean =
        row.fields.none { field ->
            field.isNotBlank() &&
                (DateParser.parse(field) != null || AmountParser.parseDecimal(field) != null)
        }

    private fun detectFromHeader(header: CsvRow, dataRows: List<CsvRow>): ColumnMapping? {
        val labels = header.fields.map { BankProfiles.normaliseHeader(it) }

        val dateColumn = labels.indexOfFirstIn(DATE_HEADERS) ?: return null
        val amountColumn = labels.indexOfFirstIn(AMOUNT_HEADERS)
        val debitColumn = labels.indexOfFirstIn(DEBIT_HEADERS)
        val creditColumn = labels.indexOfFirstIn(CREDIT_HEADERS)
        val balanceColumn = labels.indexOfFirstIn(BALANCE_HEADERS)

        if (amountColumn == null && debitColumn == null && creditColumn == null) return null

        val descriptionColumns = labels.indicesOfAllIn(DESCRIPTION_HEADERS)
            .ifEmpty {
                // No recognised label: fall back to the widest text column that
                // is not already spoken for.
                val claimed = setOfNotNull(dateColumn, amountColumn, debitColumn, creditColumn, balanceColumn)
                listOfNotNull(widestTextColumn(dataRows, exclude = claimed))
            }
        if (descriptionColumns.isEmpty()) return null

        return ColumnMapping(
            dateColumn = dateColumn,
            descriptionColumns = descriptionColumns,
            amountColumn = amountColumn,
            debitColumn = if (amountColumn == null) debitColumn else null,
            creditColumn = if (amountColumn == null) creditColumn else null,
            balanceColumn = balanceColumn,
            hasHeaderRow = true,
            dateOrder = inferDateOrder(dataRows, dateColumn),
        )
    }

    private fun detectFromContent(dataRows: List<CsvRow>, hasHeaderRow: Boolean): ColumnMapping? {
        val width = dataRows.maxOf { it.size }
        if (width < 3) return null

        val sample = dataRows.take(50)

        // Score every column for how consistently it parses as a date / amount.
        val dateScores = (0 until width).map { column ->
            column to sample.count { row -> row[column]?.let { DateParser.parse(it) } != null }
        }
        val amountScores = (0 until width).map { column ->
            column to sample.count { row -> row[column]?.let { AmountParser.parseDecimal(it) } != null }
        }

        val threshold = (sample.size * 0.6).toInt().coerceAtLeast(1)

        val dateColumn = dateScores.filter { it.second >= threshold }.maxByOrNull { it.second }?.first
            ?: return null

        // The amount is the numeric column with the most *varied* values; a
        // running balance also parses as a number, but a balance column tends to
        // move monotonically while amounts scatter. Prefer the later column only
        // when scores tie, since balance usually sits at the end.
        val amountCandidates = amountScores
            .filter { it.first != dateColumn && it.second >= threshold }
            .sortedByDescending { it.second }
        val amountColumn = amountCandidates.firstOrNull()?.first ?: return null

        val claimed = setOf(dateColumn, amountColumn)
        val descriptionColumn = widestTextColumn(sample, exclude = claimed) ?: return null

        return ColumnMapping(
            dateColumn = dateColumn,
            descriptionColumns = listOf(descriptionColumn),
            amountColumn = amountColumn,
            hasHeaderRow = hasHeaderRow,
            dateOrder = inferDateOrder(dataRows, dateColumn),
        )
    }

    /** The column with the most alphabetic content — descriptions are the wordy one. */
    private fun widestTextColumn(rows: List<CsvRow>, exclude: Set<Int>): Int? {
        val width = rows.maxOfOrNull { it.size } ?: return null
        return (0 until width)
            .filter { it !in exclude }
            .map { column ->
                column to rows.sumOf { row ->
                    row[column]?.count { it.isLetter() } ?: 0
                }
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun inferDateOrder(rows: List<CsvRow>, dateColumn: Int): DateParser.DateOrder =
        DateParser.inferOrder(rows.mapNotNull { it[dateColumn] })

    private fun List<String>.indexOfFirstIn(vocabulary: Set<String>): Int? =
        indexOfFirst { it in vocabulary }.takeIf { it >= 0 }

    private fun List<String>.indicesOfAllIn(vocabulary: Set<String>): List<Int> =
        mapIndexedNotNull { index, label -> index.takeIf { label in vocabulary } }
}
