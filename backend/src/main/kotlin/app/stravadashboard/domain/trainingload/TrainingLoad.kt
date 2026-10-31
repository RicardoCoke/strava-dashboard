package app.stravadashboard.domain.trainingload

import java.time.LocalDate

/** Unrounded values for one day; [form] is fitness minus fatigue. */
data class DailyLoad(
    val date: LocalDate,
    val load: Double,
    val fitness: Double,
    val fatigue: Double,
) {
    val form: Double get() = fitness - fatigue
}

data class RunCounts(
    val counted: Int,
    val skippedNoHeartRate: Int,
    val pendingHeartRate: Int,
)

/** One entry per day from [windowStart] to [windowEnd] (today), in ascending order. */
data class TrainingLoad(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val days: List<DailyLoad>,
    val counts: RunCounts,
)

/** [changePercent] is null when the previous week had no load. */
data class WeekRamp(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val weekToDate: Boolean,
    val load: Double,
    val previousLoad: Double,
    val changePercent: Int?,
    val previousWeekLight: Boolean,
    val warning: Boolean,
)

data class Ramp(
    val thisWeek: WeekRamp,
    val lastWeek: WeekRamp,
)
