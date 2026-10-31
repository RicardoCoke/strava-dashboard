package app.stravadashboard.testsupport

import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.domain.ports.TrainingPlanSource
import app.stravadashboard.domain.trainingplan.PlanParseResult
import app.stravadashboard.domain.trainingplan.SemicolonCsv
import app.stravadashboard.domain.trainingplan.TrainingPlan
import app.stravadashboard.domain.trainingplan.TrainingPlanParser
import java.time.LocalDate

// Invented training plans laid out like the user's CSV (004 specs.md).

val PLAN_START: LocalDate = LocalDate.of(2026, 8, 17)

/** Three blocks: WEEK 1, WEEK 2 and RACE WEEK (src/test/resources/trainingplan/plan-sample.csv). */
fun samplePlanText(): String =
    requireNotNull(object {}.javaClass.getResource("/trainingplan/plan-sample.csv")).readText()

/** 15 numbered weeks (copies of the sample's WEEK 2) plus the sample's RACE WEEK. */
fun sixteenWeekPlanText(): String {
    val lines = samplePlanText().lines()
    val week2 = lines.indexOfFirst { it.startsWith("WEEK 2;") }
    val raceWeek = lines.indexOfFirst { it.startsWith("RACE WEEK;") }
    val header = lines.subList(0, lines.indexOfFirst { it.startsWith("WEEK 1;") })
    val weekBlock = lines.subList(week2, raceWeek)
    val numbered = (1..15).flatMap { n -> listOf(weekBlock.first().replace("WEEK 2", "WEEK $n")) + weekBlock.drop(1) }
    return (header + numbered + lines.subList(raceWeek, lines.size)).joinToString("\n")
}

fun parsePlan(text: String): TrainingPlan {
    val result = TrainingPlanParser.parse(SemicolonCsv.parse(text))
    check(result is PlanParseResult.Valid) { "fixture should parse: $result" }
    return result.plan
}

class FakePlanSource(
    var file: PlanFile = PlanFile.Content(sixteenWeekPlanText()),
) : TrainingPlanSource {
    override suspend fun read(): PlanFile = file
}
