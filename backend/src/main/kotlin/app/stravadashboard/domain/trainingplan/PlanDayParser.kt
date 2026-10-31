package app.stravadashboard.domain.trainingplan

import app.stravadashboard.domain.model.roundHalfUpToInt
import java.time.DayOfWeek

/** The rows of one week block that a day reads from (004 R4). */
internal data class WeekRows(
    val label: String,
    val header: List<String>,
    val type: List<String>,
    val session: List<String>,
    val easy: List<String>,
    val subt: List<String>,
    val volume: List<String>,
)

/** One day of a week block: session text, note, minutes, km, rest and race (004 R4, R5). */
internal object PlanDayParser {
    const val MARATHON_MIN_KM = 42.0
    private const val PRIMARY_COL_OFFSET = 2
    private const val COLS_PER_DAY = 2
    private const val RACE = "RACE"
    private val RACE_DISTANCE = Regex("""(\d+(?:[.,]\d+)?)\s*K\b""", RegexOption.IGNORE_CASE)

    fun parse(
        rows: WeekRows,
        dayOfWeek: DayOfWeek,
    ): PlanDay {
        val p = PRIMARY_COL_OFFSET + COLS_PER_DAY * (dayOfWeek.value - 1)
        val headerCell = rows.header.cell(p)
        if (!headerCell.uppercase().startsWith(dayOfWeek.name)) {
            invalidPlan("${rows.label}: expected ${dayOfWeek.name} in column $p")
        }
        val where = "${rows.label}, ${dayOfWeek.name}"
        val easyMinutes = roundHalfUpToInt(numberOrZero(rows.easy.cell(p), "$where: EASY"))
        val subtMinutes = roundHalfUpToInt(numberOrZero(rows.subt.cell(p), "$where: SUBT"))
        val distance = Kilometres(roundTwoDecimals(numberOrZero(rows.volume.cell(p), "$where: VOLUME")))
        val cells =
            SessionCells(rows.type.cell(p), rows.type.cell(p + 1), rows.session.cell(p), rows.session.cell(p + 1))
        val race = detectRace(cells.typePrimary) ?: detectRace(cells.main)
        val isRest = race == null && distance.value == 0.0 && easyMinutes + subtMinutes == 0
        val session =
            when {
                isRest -> "Rest"
                race != null && RACE in cells.typePrimary.uppercase() -> cells.typePrimary
                else -> sessionText(cells)
            }
        return PlanDay(dayOfWeek, session, dayNote(headerCell), easyMinutes, subtMinutes, distance, isRest, race)
    }

    /** The four cells a session is written in: type row and session row, primary and secondary column. */
    data class SessionCells(
        val typePrimary: String,
        val typeSecondary: String,
        val warmUp: String,
        val main: String,
    )

    /** "25' WU/CD + 3x10' subT" from the WU/CD minutes and the EASY/SUBT cell (AC4.6). */
    fun sessionText(cells: SessionCells): String {
        val kind =
            when {
                cells.typeSecondary.uppercase().startsWith("EASY") -> "easy"
                cells.typeSecondary.uppercase().startsWith("SUBT") -> "subT"
                else -> null
            }
        val warmUp = cells.warmUp
        val warmUpNumber = parseNumber(warmUp)
        val warmUpPart =
            when {
                warmUp.isEmpty() || warmUpNumber == 0.0 -> null
                warmUpNumber != null -> "$warmUp' WU/CD"
                else -> "$warmUp WU/CD"
            }
        val main = cells.main
        val mainNumber = parseNumber(main)
        val suffix = kind?.let { " $it" }.orEmpty()
        val mainPart =
            when {
                main.isEmpty() || mainNumber == 0.0 -> null
                mainNumber != null -> "$main'$suffix"
                RACE in main.uppercase() -> main
                else -> "$main$suffix"
            }
        return listOfNotNull(warmUpPart, mainPart).joinToString(" + ")
    }

    fun detectRace(cell: String): PlanRace? {
        if (RACE !in cell.uppercase()) return null
        val distanceKm =
            RACE_DISTANCE
                .find(cell)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDouble()
        return distanceKm?.let {
            val isMarathon = it >= MARATHON_MIN_KM
            val label = if (isMarathon) "Marathon · ${formatPlainNumber(it)} km" else "${formatPlainNumber(it)}K race"
            PlanRace(it, isMarathon, label)
        }
    }

    /** The text between the first "(" and the last ")" of a day header, e.g. "TUESDAY (75min progression)". */
    fun dayNote(header: String): String? {
        val open = header.indexOf('(')
        val close = header.lastIndexOf(')')
        return if (open in 0 until close) header.substring(open + 1, close).trim().ifEmpty { null } else null
    }

    private fun numberOrZero(
        cell: String,
        where: String,
    ): Double = if (cell.isEmpty()) 0.0 else parseNumber(cell) ?: invalidPlan("$where value is not a number")
}
