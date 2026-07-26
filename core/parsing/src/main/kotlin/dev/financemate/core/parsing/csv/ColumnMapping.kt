package dev.financemate.core.parsing.csv

import dev.financemate.core.parsing.DateParser

/**
 * Describes how one bank's CSV layout maps onto a transaction.
 *
 * There is no standard for statement CSVs. Column order differs, descriptions
 * are sometimes split across several columns, and — most consequentially — banks
 * disagree about what a positive number means. A mapping captures all of that in
 * one place so the parser itself stays simple.
 */
public data class ColumnMapping(
    val dateColumn: Int,

    /**
     * Columns whose text forms the description, joined in order.
     *
     * Several banks split it: Chase puts a type in one column and the merchant in
     * another; Capital One separates category from description. Concatenating
     * gives the normaliser more to work with.
     */
    val descriptionColumns: List<Int>,

    /** A single signed amount column. Mutually exclusive with debit/credit. */
    val amountColumn: Int? = null,

    /** Separate debit column, where the bank splits money out and money in. */
    val debitColumn: Int? = null,

    /** Separate credit column. */
    val creditColumn: Int? = null,

    val balanceColumn: Int? = null,

    val hasHeaderRow: Boolean = true,

    val dateOrder: DateParser.DateOrder = DateParser.DateOrder.MONTH_FIRST,

    /**
     * Set when a positive figure in the source means money *leaving* the account.
     *
     * American Express and Discover both report charges as positive. Recording
     * that here — and flipping at parse time — is what allows every downstream
     * calculation to assume negative means spent.
     */
    val invertSign: Boolean = false,

    /** Explicit delimiter, or null to auto-detect. */
    val delimiter: Char? = null,
) {
    init {
        require(dateColumn >= 0) { "Date column index must not be negative" }
        require(descriptionColumns.isNotEmpty()) { "At least one description column is required" }
        require(descriptionColumns.all { it >= 0 }) { "Description column indices must not be negative" }

        val hasSingleAmount = amountColumn != null
        val hasSplitAmount = debitColumn != null || creditColumn != null
        require(hasSingleAmount || hasSplitAmount) {
            "A mapping needs either an amount column or a debit/credit pair"
        }
        require(!(hasSingleAmount && hasSplitAmount)) {
            "A mapping cannot have both a signed amount column and a debit/credit pair"
        }
    }

    /** Highest column index this mapping refers to, used to validate row width. */
    public val requiredWidth: Int
        get() = listOfNotNull(
            dateColumn,
            amountColumn,
            debitColumn,
            creditColumn,
            descriptionColumns.maxOrNull(),
        ).max() + 1
}
