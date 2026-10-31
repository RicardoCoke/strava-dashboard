package app.stravadashboard.domain.trainingplan

import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.domain.ports.TrainingPlanSource
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface TrainingPlanResult {
    data object NotConfigured : TrainingPlanResult

    data object Unavailable : TrainingPlanResult

    data class Invalid(
        val reason: String,
    ) : TrainingPlanResult

    data class Ready(
        val calendar: BuildCalendar,
    ) : TrainingPlanResult
}

/** Reads and parses the plan on every call, so edits to the CSV show without a restart (004 AC2.1). */
class TrainingPlanService(
    private val source: TrainingPlanSource,
    private val startDate: LocalDate,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    private val log = LoggerFactory.getLogger(TrainingPlanService::class.java)

    suspend fun get(): TrainingPlanResult =
        when (val file = source.read()) {
            PlanFile.NotConfigured -> TrainingPlanResult.NotConfigured
            PlanFile.Unreadable -> TrainingPlanResult.Unavailable
            PlanFile.TooLarge -> invalid("The plan file is larger than 1 MB.")
            is PlanFile.Content ->
                when (val parsed = TrainingPlanParser.parse(SemicolonCsv.parse(file.text))) {
                    is PlanParseResult.Invalid -> invalid(parsed.reason)
                    is PlanParseResult.Valid -> {
                        val today = LocalDate.now(clock.withZone(zone))
                        TrainingPlanResult.Ready(BuildCalendarBuilder.build(parsed.plan, startDate, today))
                    }
                }
        }

    private fun invalid(reason: String): TrainingPlanResult {
        // The reason names weeks, days and rows only, never cell values.
        log.warn("Training plan file is invalid: {}", reason)
        return TrainingPlanResult.Invalid(reason)
    }
}
