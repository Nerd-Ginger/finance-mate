package dev.financemate.core.parsing.csv

import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import dev.financemate.core.parsing.AmountParser
import dev.financemate.core.parsing.DateParser

/**
 * Turns a bank CSV into transactions, given a [ColumnMapping].
 *
 * Rows are handled independently: a row that cannot be read is reported as a
 * [ParseProblem] and skipped, rather than aborting the import. Statements
 * routinely carry trailing totals, section breaks, and disclaimer lines, and
 * losing 900 good rows to one bad one would be a poor trade.
 */
public object CsvStatementParser {

    /**
     * Rows whose description matches these are statement furniture rather than
     * transactions. Reported as warnings, not errors, so the user is not alarmed.
     */
    private val NON_TRANSACTION_ROWS = Regex(
        """^(?:total|totals|beginning balance|ending balance|opening balance|closing balance|balance forward|subtotal)\b""",
        RegexOption.IGNORE_CASE,
    )

    public fun parse(
        content: String,
        mapping: ColumnMapping,
        currency: CurrencyCode = CurrencyCode.USD,
    ): ParseResult {
        val rows = CsvReader.parse(content, mapping.delimiter)
        val problems = mutableListOf<ParseProblem>()
        val transactions = mutableListOf<ParsedTransaction>()

        val dataRows = if (mapping.hasHeaderRow) rows.drop(1) else rows

        for (row in dataRows) {
            if (row.isBlank()) continue

            when (val outcome = parseRow(row, mapping, currency)) {
                is RowOutcome.Parsed -> transactions.add(outcome.transaction)
                is RowOutcome.Skipped -> problems.add(
                    ParseProblem(
                        location = "line ${row.lineNumber}",
                        message = outcome.reason,
                        severity = outcome.severity,
                        rawContent = row.fields.joinToString(","),
                    ),
                )
            }
        }

        return ParseResult(
            transactions = transactions,
            source = ImportSource.CSV,
            problems = problems,
        )
    }

    private sealed interface RowOutcome {
        data class Parsed(val transaction: ParsedTransaction) : RowOutcome
        data class Skipped(
            val reason: String,
            val severity: ParseProblem.Severity,
        ) : RowOutcome
    }

    private fun parseRow(
        row: CsvRow,
        mapping: ColumnMapping,
        currency: CurrencyCode,
    ): RowOutcome {
        if (row.size < mapping.requiredWidth) {
            return RowOutcome.Skipped(
                "Row has ${row.size} columns but the mapping needs ${mapping.requiredWidth}",
                ParseProblem.Severity.ERROR,
            )
        }

        val description = mapping.descriptionColumns
            .mapNotNull { row[it] }
            .joinToString(" ")
            .trim()

        if (description.isBlank()) {
            return RowOutcome.Skipped("No description", ParseProblem.Severity.ERROR)
        }

        if (NON_TRANSACTION_ROWS.containsMatchIn(description)) {
            return RowOutcome.Skipped(
                "Statement summary row, not a transaction",
                ParseProblem.Severity.WARNING,
            )
        }

        val rawDate = row[mapping.dateColumn]
            ?: return RowOutcome.Skipped("Missing date", ParseProblem.Severity.ERROR)

        val date = DateParser.parse(rawDate, mapping.dateOrder)
            ?: return RowOutcome.Skipped(
                "Could not read '$rawDate' as a date",
                ParseProblem.Severity.ERROR,
            )

        val amount = resolveAmount(row, mapping, currency)
            ?: return RowOutcome.Skipped("Could not read an amount", ParseProblem.Severity.ERROR)

        return RowOutcome.Parsed(
            ParsedTransaction(
                postedDate = date,
                amount = amount,
                rawDescription = description,
                sourceLocation = "line ${row.lineNumber}",
                confidence = 1.0,
            ),
        )
    }

    /**
     * Reads the amount, from either a single signed column or a debit/credit pair.
     *
     * For split columns the debit value is negated regardless of how the bank
     * wrote it — some export debits as positive numbers, some as negative — since
     * the column itself already tells us the direction.
     */
    private fun resolveAmount(
        row: CsvRow,
        mapping: ColumnMapping,
        currency: CurrencyCode,
    ): Money? {
        mapping.amountColumn?.let { column ->
            val raw = row[column] ?: return null
            return AmountParser.parse(raw, currency, invertSign = mapping.invertSign)
        }

        val debit = mapping.debitColumn?.let { row[it] }?.let { AmountParser.parse(it, currency) }
        val credit = mapping.creditColumn?.let { row[it] }?.let { AmountParser.parse(it, currency) }

        return when {
            debit != null && !debit.isZero -> if (debit.isNegative) debit else -debit
            credit != null && !credit.isZero -> credit.abs()
            // Both blank or zero: a genuine zero-value row, which some banks emit
            // for authorisations. Keep it rather than dropping it.
            debit != null || credit != null -> Money.zero(currency)
            else -> null
        }
    }
}
