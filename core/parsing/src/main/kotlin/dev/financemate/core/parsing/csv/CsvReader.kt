package dev.financemate.core.parsing.csv

/**
 * A small RFC 4180 CSV reader.
 *
 * Bank CSV exports break naive `split(",")` parsing constantly, because
 * descriptions contain commas:
 *
 * ```
 * 03/14/2026,"SMITH, JOHN - RENT",-1450.00
 * ```
 *
 * A split on commas turns that into four fields and shifts the amount into the
 * description column — which then fails to parse, or worse, silently imports the
 * wrong number. So this handles the actual format: quoted fields, embedded
 * commas and newlines, and doubled quotes as an escape.
 *
 * Statements are small (a few MB at most), so the whole file is parsed at once
 * rather than streamed.
 */
public object CsvReader {

    /**
     * Delimiters tried during auto-detection, in preference order. Semicolon and
     * tab appear in exports from banks in comma-decimal locales.
     */
    private val CANDIDATE_DELIMITERS = listOf(',', ';', '\t', '|')

    /**
     * Parses [content] into rows of fields.
     *
     * @param delimiter field separator; when null it is auto-detected.
     */
    public fun parse(content: String, delimiter: Char? = null): List<CsvRow> {
        val text = content.removePrefix("﻿") // strip UTF-8 BOM
        if (text.isBlank()) return emptyList()

        val separator = delimiter ?: detectDelimiter(text)
        val rows = mutableListOf<CsvRow>()
        val field = StringBuilder()
        var fields = mutableListOf<String>()
        var inQuotes = false
        var index = 0
        var lineNumber = 1
        var rowStartLine = 1

        while (index < text.length) {
            val char = text[index]

            when {
                inQuotes && char == '"' -> {
                    // A doubled quote inside a quoted field is a literal quote.
                    if (index + 1 < text.length && text[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = false
                    }
                }

                char == '"' && field.isEmpty() -> inQuotes = true

                // A quote appearing mid-field is not valid RFC 4180, but some
                // exports contain it (e.g. 6" SUB). Treat it as a literal.
                char == '"' -> field.append('"')

                !inQuotes && char == separator -> {
                    fields.add(field.toString())
                    field.setLength(0)
                }

                !inQuotes && (char == '\n' || char == '\r') -> {
                    // Consume CRLF as one line break.
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index++
                    }
                    fields.add(field.toString())
                    field.setLength(0)
                    rows.addRowIfMeaningful(fields, rowStartLine)
                    fields = mutableListOf()
                    lineNumber++
                    rowStartLine = lineNumber
                }

                else -> {
                    if (char == '\n') lineNumber++
                    field.append(char)
                }
            }
            index++
        }

        // Final row, if the file does not end with a newline.
        fields.add(field.toString())
        rows.addRowIfMeaningful(fields, rowStartLine)

        return rows
    }

    private fun MutableList<CsvRow>.addRowIfMeaningful(fields: List<String>, line: Int) {
        // Skip rows that are entirely empty — trailing newlines, blank separator
        // lines some banks put between sections.
        if (fields.all { it.isBlank() }) return
        add(CsvRow(fields.map { it.trim() }, line))
    }

    /**
     * Guesses the delimiter by counting candidates outside quoted regions on the
     * first few lines and picking the one with the most consistent count per row.
     *
     * Consistency matters more than raw frequency: a description containing many
     * commas could outvote the real delimiter on frequency alone, but it will not
     * produce the same count on every row.
     */
    public fun detectDelimiter(content: String): Char {
        val sampleLines = content.lineSequence().filter { it.isNotBlank() }.take(10).toList()
        if (sampleLines.isEmpty()) return ','

        var best = ','
        var bestScore = -1.0

        for (candidate in CANDIDATE_DELIMITERS) {
            val counts = sampleLines.map { line -> countOutsideQuotes(line, candidate) }
            if (counts.all { it == 0 }) continue

            val mostCommon = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }
            val consistency = (mostCommon?.value ?: 0).toDouble() / counts.size
            // Weight by field count so a consistent 5-column comma file beats a
            // consistent 1-column pipe file.
            val score = consistency * (mostCommon?.key ?: 0)

            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    private fun countOutsideQuotes(line: String, delimiter: Char): Int {
        var count = 0
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> count++
            }
        }
        return count
    }
}

/**
 * One parsed CSV row.
 *
 * @property lineNumber 1-based line in the source file, so a parse failure can
 *   point the user at the row that caused it instead of failing anonymously.
 */
public data class CsvRow(
    val fields: List<String>,
    val lineNumber: Int,
) {
    val size: Int get() = fields.size

    /** Field at [index], or null when the row is short. Ragged rows are common. */
    public operator fun get(index: Int): String? = fields.getOrNull(index)?.takeIf { it.isNotBlank() }

    public fun isBlank(): Boolean = fields.all { it.isBlank() }
}
