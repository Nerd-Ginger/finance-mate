package dev.financemate.core.parsing.csv

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.Test

class CsvReaderTest {

    @Test
    fun `parses simple rows`() {
        val rows = CsvReader.parse("a,b,c\n1,2,3")
        rows.size shouldBe 2
        rows[0].fields shouldContainExactly listOf("a", "b", "c")
        rows[1].fields shouldContainExactly listOf("1", "2", "3")
    }

    @Test
    fun `keeps commas inside quoted fields`() {
        // The bug a naive split(",") produces: the amount shifts into the
        // description column and either fails to parse or imports as the wrong
        // number.
        val rows = CsvReader.parse("03/14/2026,\"SMITH, JOHN - RENT\",-1450.00")
        rows[0].fields shouldContainExactly listOf("03/14/2026", "SMITH, JOHN - RENT", "-1450.00")
    }

    @Test
    fun `handles doubled quotes as an escape`() {
        // Source is: a,"He said ""hi""",c
        // Escaped literals rather than raw strings here, because a raw string
        // cannot contain a run of three quotes without terminating itself.
        val rows = CsvReader.parse("a,\"He said \"\"hi\"\"\",c")
        rows[0].fields shouldContainExactly listOf("a", "He said \"hi\"", "c")
    }

    @Test
    fun `handles newlines inside quoted fields`() {
        val rows = CsvReader.parse("a,\"line one\nline two\",c\nd,e,f")
        rows.size shouldBe 2
        rows[0].fields[1] shouldBe "line one\nline two"
        rows[1].fields shouldContainExactly listOf("d", "e", "f")
    }

    @Test
    fun `treats CRLF as one line break`() {
        val rows = CsvReader.parse("a,b\r\nc,d\r\n")
        rows.size shouldBe 2
        rows[1].fields shouldContainExactly listOf("c", "d")
    }

    @Test
    fun `strips a UTF-8 byte order mark`() {
        // Exports opened and re-saved in Excel routinely gain a BOM, which would
        // otherwise become part of the first header name and break matching.
        val rows = CsvReader.parse("﻿Date,Amount\n03/14/2026,-5.00")
        rows[0].fields[0] shouldBe "Date"
    }

    @Test
    fun `skips entirely blank rows`() {
        val rows = CsvReader.parse("a,b\n\n\nc,d\n")
        rows.size shouldBe 2
    }

    @Test
    fun `records line numbers for error reporting`() {
        val rows = CsvReader.parse("a,b\nc,d\ne,f")
        rows[0].lineNumber shouldBe 1
        rows[2].lineNumber shouldBe 3
    }

    @Test
    fun `handles a final row with no trailing newline`() {
        val rows = CsvReader.parse("a,b\nc,d")
        rows.size shouldBe 2
        rows[1].fields shouldContainExactly listOf("c", "d")
    }

    @Test
    fun `tolerates ragged rows`() {
        val rows = CsvReader.parse("a,b,c\n1,2")
        rows[1].size shouldBe 2
        rows[1][5] shouldBe null
    }

    @Test
    fun `treats blank fields as null on access`() {
        val rows = CsvReader.parse("a,,c")
        rows[0][1] shouldBe null
        rows[0].fields[1] shouldBe ""
    }

    // --- Delimiter detection ------------------------------------------------------------

    @Test
    fun `detects semicolon delimiters`() {
        CsvReader.detectDelimiter("a;b;c\n1;2;3") shouldBe ';'
        val rows = CsvReader.parse("a;b;c\n1;2;3")
        rows[0].fields shouldContainExactly listOf("a", "b", "c")
    }

    @Test
    fun `detects tab delimiters`() {
        CsvReader.detectDelimiter("a\tb\tc\n1\t2\t3") shouldBe '\t'
    }

    @Test
    fun `commas inside quotes do not outvote the real delimiter`() {
        // The description has more commas than the file has semicolons, so a
        // frequency-only heuristic would pick the wrong one. Consistency across
        // rows is what settles it.
        val content = """
            date;description;amount
            03/14/2026;"a, b, c, d, e";-5.00
            03/15/2026;"f, g, h, i, j";-6.00
        """.trimIndent()
        CsvReader.detectDelimiter(content) shouldBe ';'
    }

    @Test
    fun `defaults to comma for a single column`() {
        CsvReader.detectDelimiter("justonecolumn\nanotherrow") shouldBe ','
    }

    @Test
    fun `returns nothing for empty input`() {
        CsvReader.parse("") shouldBe emptyList()
        CsvReader.parse("   \n  \n") shouldBe emptyList()
    }
}
