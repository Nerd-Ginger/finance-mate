package dev.financemate.core.parsing.ofx

import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.ParseProblem
import dev.financemate.core.model.ParseResult
import dev.financemate.core.model.ParsedTransaction
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.parsing.AmountParser
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses OFX and QFX statement files.
 *
 * ## Why OFX is the best import path
 *
 * Every transaction carries a `FITID` — an identifier the bank itself assigns
 * and keeps stable across downloads. That is a much stronger dedup signal than
 * any fingerprint we can compute: it survives the bank rewording a description,
 * correcting an amount, or shifting a posting date, all of which defeat a
 * content hash. When a file provides FITIDs, they should win.
 *
 * ## The format
 *
 * OFX 1.x is SGML, not XML. Container tags are closed but leaf values are not:
 *
 * ```
 * <STMTTRN>
 * <TRNTYPE>DEBIT
 * <DTPOSTED>20260314120000[-8:PST]
 * <TRNAMT>-4.75
 * <FITID>202603140001
 * <NAME>SQ *BLUE BOTTLE COFFEE
 * </STMTTRN>
 * ```
 *
 * An XML parser rejects this outright. OFX 2.x *is* well-formed XML. Rather than
 * carry two parsers, this reads both with the same scan: a value runs from the
 * end of its opening tag to the next `<`, which is correct whether or not a
 * closing tag follows.
 */
public object OfxParser {

    private val TAG = Regex("""<([A-Za-z0-9._]+)>([^<]*)""")
    private val STMTTRN_BLOCK = Regex("""<STMTTRN>(.*?)</STMTTRN>""", RegexOption.DOT_MATCHES_ALL)
    private val DATE_DIGITS = Regex("""^(\d{8})""")
    private val BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    public fun parse(content: String, source: ImportSource = ImportSource.OFX): ParseResult {
        val problems = mutableListOf<ParseProblem>()
        val body = content.substringAfter("<OFX>", missingDelimiterValue = content)

        val currency = firstValue(body, "CURDEF")
            ?.let { code -> runCatching { CurrencyCode(code.uppercase()) }.getOrNull() }
            ?: CurrencyCode.USD

        val accountMask = firstValue(body, "ACCTID")?.takeLast(4)

        val blocks = STMTTRN_BLOCK.findAll(body).toList()
        if (blocks.isEmpty()) {
            problems.add(
                ParseProblem(
                    location = "file",
                    message = "No <STMTTRN> transaction records found. " +
                        "The file may be an investment statement or not OFX at all.",
                    severity = ParseProblem.Severity.ERROR,
                ),
            )
            return ParseResult(emptyList(), source, problems, accountMask)
        }

        val transactions = mutableListOf<ParsedTransaction>()

        blocks.forEachIndexed { index, match ->
            val fields = readFields(match.groupValues[1])
            val location = "transaction ${index + 1}"

            val rawDate = fields["DTPOSTED"] ?: fields["DTUSER"] ?: fields["DTAVAIL"]
            val date = rawDate?.let { parseOfxDate(it) }
            if (date == null) {
                problems.add(
                    ParseProblem(
                        location,
                        "Missing or unreadable DTPOSTED",
                        ParseProblem.Severity.ERROR,
                        rawDate,
                    ),
                )
                return@forEachIndexed
            }

            val rawAmount = fields["TRNAMT"]
            // OFX signs amounts to the same convention FinanceMate uses:
            // negative is money out. No inversion needed.
            val amount = rawAmount?.let { AmountParser.parse(it, currency) }
            if (amount == null) {
                problems.add(
                    ParseProblem(
                        location,
                        "Missing or unreadable TRNAMT",
                        ParseProblem.Severity.ERROR,
                        rawAmount,
                    ),
                )
                return@forEachIndexed
            }

            // NAME is the merchant; MEMO often holds the location or a note.
            // Joining gives the normaliser more to work with, and PAYEE.NAME is
            // used when the richer payee aggregate is present.
            val description = listOfNotNull(
                fields["NAME"] ?: fields["PAYEE.NAME"],
                fields["MEMO"],
            ).filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")
                .ifBlank { fields["TRNTYPE"] ?: "UNKNOWN" }

            transactions.add(
                ParsedTransaction(
                    postedDate = date,
                    amount = amount,
                    rawDescription = description,
                    institutionTransactionId = fields["FITID"]?.takeIf { it.isNotBlank() },
                    sourceLocation = location,
                    confidence = 1.0,
                ),
            )
        }

        return ParseResult(transactions, source, problems, accountMask)
    }

    /**
     * Reads leaf values from one record.
     *
     * A value is everything from the end of its opening tag to the next `<`,
     * which handles both the SGML form (no closing tag) and the XML form
     * (closing tag immediately after the value).
     */
    private fun readFields(block: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var currentAggregate: String? = null

        for (match in TAG.findAll(block)) {
            val name = match.groupValues[1].uppercase()
            val value = match.groupValues[2].trim()

            when {
                name.startsWith("/") -> currentAggregate = null

                value.isEmpty() -> {
                    // An opening tag with no text is an aggregate such as <PAYEE>.
                    // Its children are recorded with a qualified name so PAYEE.NAME
                    // does not overwrite the record's own NAME.
                    currentAggregate = name
                }

                else -> {
                    val key = if (currentAggregate != null) "$currentAggregate.$name" else name
                    fields.putIfAbsent(key, value)
                    // Also record unqualified, so callers can fall back.
                    fields.putIfAbsent(name, value)
                }
            }
        }
        return fields
    }

    private fun firstValue(content: String, tag: String): String? =
        Regex("""<$tag>([^<\r\n]*)""", RegexOption.IGNORE_CASE)
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    /**
     * OFX dates are `YYYYMMDD` optionally followed by a time and a bracketed
     * timezone: `20260314120000[-8:PST]`.
     *
     * Only the date part is used. Converting the timestamp into the local zone
     * could shift a transaction across a day boundary, which would move it into
     * a different budget month — a real risk for late-evening purchases, and one
     * with no upside since the statement's own day is what the user reconciles
     * against.
     */
    internal fun parseOfxDate(raw: String): LocalDate? {
        val digits = DATE_DIGITS.find(raw.trim())?.groupValues?.get(1) ?: return null
        return runCatching { LocalDate.parse(digits, BASIC_DATE) }.getOrNull()
    }
}
