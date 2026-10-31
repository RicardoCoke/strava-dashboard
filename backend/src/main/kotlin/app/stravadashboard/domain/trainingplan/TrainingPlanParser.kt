package app.stravadashboard.domain.trainingplan

import java.time.DayOfWeek

/** Parses the rows of the training-plan CSV into paces, notes and week blocks (004 R3, R4). */
object TrainingPlanParser {
    private val WEEK_HEADER = Regex("""^WEEK\s+\d+$""")
    private const val RACE_WEEK = "RACE WEEK"
    private const val TYPE_ROW = 1
    private const val SESSION_ROW = 2
    private const val TOTAL_COLUMN = 1

    fun parse(rows: List<List<String>>): PlanParseResult =
        try {
            PlanParseResult.Valid(parsePlan(rows))
        } catch (e: PlanFormatException) {
            PlanParseResult.Invalid(e.reason)
        }

    private fun parsePlan(rows: List<List<String>>): TrainingPlan {
        val starts = rows.indices.filter { isWeekHeader(rows[it].cell(0)) }
        if (starts.isEmpty()) {
            invalidPlan("No week blocks found (expected rows starting with WEEK 1 … RACE WEEK).")
        }
        val header = rows.subList(0, starts.first())
        val weeks =
            starts.mapIndexed { i, start ->
                parseWeek(rows.subList(start, starts.getOrElse(i + 1) { rows.size }))
            }
        return TrainingPlan(PlanHeaderParser.paces(header), PlanHeaderParser.notes(header), weeks)
    }

    private fun isWeekHeader(cell: String): Boolean {
        val upper = cell.uppercase()
        return WEEK_HEADER.matches(upper) || upper == RACE_WEEK
    }

    private fun parseWeek(block: List<List<String>>): PlanWeek {
        val label = block.first().cell(0)
        val find = { name: String, matches: (String) -> Boolean ->
            block.drop(1).firstOrNull { matches(it.cell(0).uppercase()) } ?: invalidPlan("$label: $name row missing")
        }
        val rows =
            WeekRows(
                label = label,
                header = block.first(),
                type = block.getOrElse(TYPE_ROW) { emptyList() },
                session = block.getOrElse(SESSION_ROW) { emptyList() },
                easy = find("EASY") { it == "EASY" },
                subt = find("SUBT") { it == "SUBT" },
                volume = find("VOLUME") { it.startsWith("VOLUME") },
            )
        return PlanWeek(
            label = label,
            distance = Kilometres(total(rows.volume, label, "VOLUME")),
            easyPercent = total(rows.easy, label, "EASY"),
            subtPercent = total(rows.subt, label, "SUBT"),
            days = DayOfWeek.entries.map { PlanDayParser.parse(rows, it) },
        )
    }

    private fun total(
        row: List<String>,
        label: String,
        name: String,
    ): Double =
        parseNumber(row.cell(TOTAL_COLUMN))?.let(::roundTwoDecimals)
            ?: invalidPlan("$label: $name total is not a number")
}
