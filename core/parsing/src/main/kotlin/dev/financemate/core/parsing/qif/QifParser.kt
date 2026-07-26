package dev.financemate.core.parsing.qif

import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.parsing.AmountParser
import dev.financemate.core.parsing.DateParser

/**
 * Parses QIF (Quicken Interchange Format) files.
 *
 * QIF is line-oriented: each line starts with a single-character code, and `^`
 * ends a record.
 *
 * ```
 * !Type:Bank
 * D03/14/2026
 * T-4.75
 * PSQ *BLUE BOTTLE COFFEE
 * MOakland CA
 * ^
 * ```
 *
 * The format is old, loosely specified, and carries no currency, no account
 * identifier, and — most awkwardly — **no indication of date ordering**. A QIF
 * written by US software uses MM/DD/YYYY; one written elsewhere uses DD/MM/YYYY,
 * and nothing in the file says which. The whole date column is scanned first so
 * a single unambiguous row can settle it, exactly as for CSV.
 *
 * QIF also has no unique transaction id, so imports rely entirely on the
 * computed dedup fingerprint. Prefer OFX when the bank offers both.
 */
public object QifParser {

    private const val END_OF_RECORD = '^'

    public fun parse(
        content: String,
        currency: CurrencyCode = CurrencyCode.USD,
        dateOrder: DateParser.DateOrder? = null,
    ): ParseResult {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val problems = mutableListOf<ParseProblem>()

        // Determine ordering from every date in the file before parsing any of
        // them, so one unambiguous row fixes the interpretation of the rest.
        val resolvedOrder = dateOrder ?: DateParser.inferOrder(
            lines.filter { it.startsWith("D") }.map { it.drop(1).trim() },
        )

        val transactions = mutableListOf<ParsedTransaction>()
        var record = mutableMapOf<Char, String>()
        var recordStartLine = 1

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1

            when {
                // Header lines: !Type:Bank, !Account, !Clear:AutoSwitch
                line.startsWith("!") -> return@forEachIndexed

                line.first() == END_OF_RECORD -> {
                    flush(record, recordStartLine, currency, resolvedOrder, transactions, problems)
                    record = mutableMapOf()
                    recordStartLine = lineNumber + 1
                }

                else -> {
                    val code = line.first()
                    val value = line.drop(1).trim()
                    if (record.isEmpty()) recordStartLine = lineNumber
                    // Split categories (S/E/$ lines) repeat codes; keep the first,
                    // which is the transaction-level value.
                    record.putIfAbsent(code, value)
                }
            }
        }

        // A final record with no trailing '^'.
        if (record.isNotEmpty()) {
            flush(record, recordStartLine, currency, resolvedOrder, transactions, problems)
        }

        if (transactions.isEmpty() && problems.isEmpty()) {
            problems.add(
                ParseProblem(
                    location = "file",
                    message = "No QIF records found",
                    severity = ParseProblem.Severity.ERROR,
                ),
            )
        }

        return ParseResult(transactions, ImportSource.QIF, problems)
    }

    private fun flush(
        record: Map<Char, String>,
        line: Int,
        currency: CurrencyCode,
        dateOrder: DateParser.DateOrder,
        into: MutableList<ParsedTransaction>,
        problems: MutableList<ParseProblem>,
    ) {
        if (record.isEmpty()) return
        val location = "line $line"

        val rawDate = record['D']
        val date = rawDate?.let { DateParser.parse(normaliseQifDate(it), dateOrder) }
        if (date == null) {
            problems.add(
                ParseProblem(location, "Missing or unreadable date (D)", ParseProblem.Severity.ERROR, rawDate),
            )
            return
        }

        val rawAmount = record['T'] ?: record['U'] // U is a Quicken alias for T
        val amount = rawAmount?.let { AmountParser.parse(it, currency) }
        if (amount == null) {
            problems.add(
                ParseProblem(location, "Missing or unreadable amount (T)", ParseProblem.Severity.ERROR, rawAmount),
            )
            return
        }

        // P is payee, M is memo. Either may be absent; both absent is unusual but
        // recoverable via the check number.
        val description = listOfNotNull(record['P'], record['M'])
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { record['N']?.let { "CHECK $it" } ?: "UNKNOWN" }

        into.add(
            ParsedTransaction(
                postedDate = date,
                amount = amount,
                rawDescription = description,
                sourceLocation = location,
                confidence = 1.0,
            ),
        )
    }

    /**
     * QIF dates sometimes use an apostrophe for 2000s years (`03/14'26`) and pad
     * with spaces (`3/14/26`). Normalise both into something [DateParser] reads.
     */
    private fun normaliseQifDate(raw: String): String =
        raw.replace('\'', '/').replace(" ", "").trim()
}
