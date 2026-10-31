package app.stravadashboard.domain.trainingplan

import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.SecondsPerKm
import org.slf4j.LoggerFactory

/** Target paces from the rows above the first week block (004 R3). */
internal object PlanHeaderParser {
    private val log = LoggerFactory.getLogger(PlanHeaderParser::class.java)
    private val PACE_HEADER = Regex("""^(.+?)\s+PACE\b""", RegexOption.IGNORE_CASE)
    private val PACE_VALUE = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")
    private const val RANGE_MARKER = "(MIN-MAX)"
    private const val NOTES_HEADER = "NOTES"
    private const val SECONDS_PER_HOUR = 3600

    /** Paces from every header cell left of the NOTES column, so note text is never read as a pace. */
    fun paces(header: List<List<String>>): List<TargetPace> {
        val firstNotesColumn = notesColumn(header)?.second ?: Int.MAX_VALUE
        return header.indices.flatMap { r ->
            header[r].indices.filter { it < firstNotesColumn }.mapNotNull { c -> paceAt(header, r, c) }
        }
    }

    /** Row and column of the first NOTES header cell, or null when there is none. */
    private fun notesColumn(header: List<List<String>>): Pair<Int, Int>? {
        val row = header.indexOfFirst { cells -> cells.any { it.trim().uppercase() == NOTES_HEADER } }
        return if (row < 0) null else row to header[row].indexOfFirst { it.trim().uppercase() == NOTES_HEADER }
    }

    private fun paceAt(
        header: List<List<String>>,
        r: Int,
        c: Int,
    ): TargetPace? {
        val title = header[r].cell(c)
        val name = PACE_HEADER.find(title)?.groupValues?.get(1) ?: return null
        val label = if (name.uppercase() == "EASY") "Easy" else name
        val values = { row: Int, column: Int -> header.getOrNull(row)?.cell(column).orEmpty() }
        val pace =
            if (RANGE_MARKER in title.uppercase()) {
                val target = values(r + 2, c)
                rangePace(name, label, values(r + 1, c), values(r + 1, c + 1), target)
            } else {
                parsePaceSeconds(values(r + 1, c))?.let { TargetPace(name.lowercase(), label, it, null, null) }
            }
        // Column index only: the header text is user-written.
        if (pace == null) log.warn("Training plan pace in column {} could not be read", c)
        return pace
    }

    private fun rangePace(
        name: String,
        label: String,
        min: String,
        max: String,
        target: String,
    ): TargetPace? {
        val minPace = parsePaceSeconds(min)
        val maxPace = parsePaceSeconds(max)
        val targetPace = if (target.isEmpty()) null else parsePaceSeconds(target)
        val readable = minPace != null && maxPace != null && (target.isEmpty() || targetPace != null)
        return if (readable) TargetPace(name.lowercase(), label, targetPace, minPace, maxPace) else null
    }

    /** "0:05:38" or "5:38" to whole seconds per km; null when it isn't a pace. */
    fun parsePaceSeconds(text: String): SecondsPerKm? {
        val match = PACE_VALUE.matchEntire(text.trim()) ?: return null
        val (hours, minutes, seconds) = match.destructured
        val total =
            (hours.toIntOrNull() ?: 0) * SECONDS_PER_HOUR + minutes.toInt() * SECONDS_PER_MINUTE + seconds.toInt()
        return SecondsPerKm(total)
    }
}
