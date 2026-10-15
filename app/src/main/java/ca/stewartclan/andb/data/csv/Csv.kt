package ca.stewartclan.andb.data.csv

/**
 * RFC 4180 CSV. Writing quotes only cells that need it and ends lines with CRLF; reading accepts
 * CRLF or LF, quoted cells spanning lines, and a leading UTF-8 byte-order mark.
 */
object Csv {
    /**
     * The byte-order mark that tells spreadsheet apps the file is UTF-8. Built from its code so
     * the invisible character can't be lost in editing.
     */
    val BOM: String = Char(0xFEFF).toString()

    /** Characters that make a cell need quoting. */
    private const val SPECIAL = ",\"\r\n"

    fun write(rows: List<List<String>>): String = buildString {
        append(BOM)
        rows.forEach { row ->
            append(row.joinToString(",") { quote(it) })
            append("\r\n")
        }
    }

    private fun quote(cell: String): String =
        if (cell.any { it in SPECIAL }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    /** The rows of [text]; blank lines (and a trailing line break) make no rows. */
    fun read(text: String): List<List<String>> = Reader(text.removePrefix(BOM)).rows()

    /** Reads one character at a time, inside or outside a quoted cell. */
    private class Reader(private val text: String) {
        private val rows = mutableListOf<List<String>>()
        private var row = mutableListOf<String>()
        private val cell = StringBuilder()
        private var quoted = false

        /** Whether the current row has any content yet, so blank lines aren't rows. */
        private var rowStarted = false
        private var i = 0

        fun rows(): List<List<String>> {
            while (i < text.length) {
                if (quoted) inQuotes(text[i]) else outsideQuotes(text[i])
                i++
            }
            endRow()
            return rows
        }

        private fun inQuotes(c: Char) {
            when {
                c != '"' -> cell.append(c)
                // A doubled quote inside quotes is one quote character.
                text.getOrNull(i + 1) == '"' -> cell.append('"').also { i++ }
                else -> quoted = false
            }
        }

        private fun outsideQuotes(c: Char) {
            when (c) {
                '"' -> {
                    quoted = true
                    rowStarted = true
                }
                ',' -> {
                    endCell()
                    rowStarted = true
                }
                '\r', '\n' -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    endRow()
                }
                else -> {
                    cell.append(c)
                    rowStarted = true
                }
            }
        }

        private fun endCell() {
            row.add(cell.toString())
            cell.clear()
        }

        private fun endRow() {
            if (rowStarted || cell.isNotEmpty()) {
                endCell()
                rows.add(row)
            }
            row = mutableListOf()
            cell.clear()
            rowStarted = false
        }
    }
}
