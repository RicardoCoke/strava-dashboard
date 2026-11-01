package app.stravadashboard.domain.racedayform

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingplan.TrainingPlanResult
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface RaceDayFormResult {
    data object NotConnected : RaceDayFormResult

    data object MissingPlan : RaceDayFormResult

    data object MissingLthr : RaceDayFormResult

    data object MissingPlanAndLthr : RaceDayFormResult

    data object InvalidLthr : RaceDayFormResult

    data object InvalidPlan : RaceDayFormResult

    data object PlanUnavailable : RaceDayFormResult

    data class Ready(
        val form: RaceDayForm,
    ) : RaceDayFormResult
}

/** Checks the session and config, reads the plan (on every call, 004) and projects from the local store only. */
class RaceDayFormService(
    private val lthr: LthrSetting,
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val planService: TrainingPlanService,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): RaceDayFormResult {
        if (tokens.find() == null) return RaceDayFormResult.NotConnected
        val plan = planService.get()
        val zones = (lthr as? LthrSetting.Valid)?.let { HrZones.of(it.lthr) }
        val calendar = (plan as? TrainingPlanResult.Ready)?.calendar
        return if (zones != null && calendar != null) {
            val today = LocalDate.now(clock.withZone(zone))
            RaceDayFormResult.Ready(RaceDayFormProjector.project(runs.findAll(), zones, calendar, today))
        } else {
            problemOf(plan)
        }
    }

    /** A missing setting wins over an invalid one, and the LTHR is checked before the plan (AC10.3, AC10.4). */
    private fun problemOf(plan: TrainingPlanResult): RaceDayFormResult {
        val planMissing = plan == TrainingPlanResult.NotConfigured
        val lthrMissing = lthr == LthrSetting.NotConfigured
        return when {
            planMissing && lthrMissing -> RaceDayFormResult.MissingPlanAndLthr
            planMissing -> RaceDayFormResult.MissingPlan
            lthrMissing -> RaceDayFormResult.MissingLthr
            lthr == LthrSetting.Invalid -> RaceDayFormResult.InvalidLthr
            plan is TrainingPlanResult.Invalid -> RaceDayFormResult.InvalidPlan
            else -> RaceDayFormResult.PlanUnavailable
        }
    }
}
