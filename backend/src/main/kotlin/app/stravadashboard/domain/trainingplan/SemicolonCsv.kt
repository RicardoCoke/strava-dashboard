package app.stravadashboard.domain.trainingplan

/** Splits semicolon-separated text into rows of cells: RFC 4180-style quotes, `""` escapes, CRLF or LF. */
object SemicolonCsv {
    private const val SEPARATOR = ';'
    private const val QUOTE = '"'

    fun parse(text: String): List<List<String>> {
        val reader = Reader()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                reader.inQuotes && c == QUOTE && text.getOrNull(i + 1) == QUOTE -> reader.append(QUOTE).also { i++ }
                c == QUOTE -> reader.inQuotes = !reader.inQuotes
                reader.inQuotes -> reader.append(c)
                c == SEPARATOR -> reader.endCell()
                c == '\n' -> reader.endRow()
                c != '\r' -> reader.append(c)
            }
            i++
        }
        return reader.finish()
    }

    private class Reader {
        var inQuotes = false
        private val rows = mutableListOf<List<String>>()
        private var row = mutableListOf<String>()
        private val cell = StringBuilder()

        fun append(c: Char) {
            cell.append(c)
        }

        fun endCell() {
            row.add(cell.toString())
            cell.clear()
        }

        fun endRow() {
            endCell()
            rows.add(row)
            row = mutableListOf()
        }

        fun finish(): List<List<String>> {
            if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
            return rows
        }
    }
}
