package app.stravadashboard.domain.racedayform

import java.time.LocalDate

// The race-day form projection (see docs/features/011-race-day-form/specs.md).

const val READY_LOW_PERCENT = 5
const val READY_HIGH_PERCENT = 25

enum class DayKind { ACTUAL, PLANNED }

enum class ProjectionStatus { PROJECTED, NOT_ENOUGH_RUNS }

/**
 * Load per moving minute learned from the user's runs. [easyPace] is the average pace of the sampled
 * easy runs, the fallback when the plan has no easy pace.
 */
data class LoadRates(
    val easyPerMinute: Double,
    val subtPerMinute: Double,
    val subtEstimated: Boolean,
    val easyPace: EasyPace?,
)

/** An easy pace in seconds per km, kept fractional (an average or the middle of a range). */
@JvmInline
value class EasyPace(
    val secondsPerKm: Double,
)

sealed interface RatesResult {
    data class Learned(
        val rates: LoadRates,
    ) : RatesResult

    data object NotEnoughEasyRuns : RatesResult
}

/** One chart day: [load] is actual or planned per [kind]; fitness and fatigue run over both (unrounded). */
data class FormDay(
    val date: LocalDate,
    val kind: DayKind,
    val load: Double,
    val fitness: Double,
    val fatigue: Double,
) {
    val form: Double get() = fitness - fatigue
    val readyLow: Double get() = fitness * READY_LOW_PERCENT / PERCENT
    val readyHigh: Double get() = fitness * READY_HIGH_PERCENT / PERCENT

    private companion object {
        const val PERCENT = 100.0
    }
}

/** Race-day values against the highest actual fitness of the last 6 months ([peakFitness] on [peakDate]). */
data class RaceDaySummary(
    val date: LocalDate,
    val kind: DayKind,
    val form: Double,
    val fitness: Double,
    val peakFitness: Double,
    val peakDate: LocalDate,
)

data class RaceDayForm(
    val today: LocalDate,
    val raceDate: LocalDate,
    val chartStart: LocalDate,
    val chartEnd: LocalDate,
    val lastActualDate: LocalDate,
    val status: ProjectionStatus,
    val subtEstimated: Boolean,
    val days: List<FormDay>,
    val raceDay: RaceDaySummary?,
)
