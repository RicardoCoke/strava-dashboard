package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.roundHalfUpToInt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Week-over-week load change for this week so far and for last week. A rise of more than 10 % is flagged, unless
 * the previous week was light compared with the weeks before it.
 */
object RampRate {
    const val RAMP_THRESHOLD_PERCENT = 10
    const val LIGHT_WEEK_FACTOR = 0.5
    const val BASELINE_WEEKS = 6
    private const val DAYS_PER_WEEK = 7L
    private const val PERCENT = 100.0

    fun compute(
        dailyLoads: Map<LocalDate, Double>,
        today: LocalDate,
    ): Ramp {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return Ramp(
            thisWeek = weekRamp(dailyLoads, thisMonday, today, weekToDate = true),
            lastWeek = weekRamp(dailyLoads, thisMonday.minusWeeks(1), today, weekToDate = false),
        )
    }

    private fun weekRamp(
        dailyLoads: Map<LocalDate, Double>,
        monday: LocalDate,
        today: LocalDate,
        weekToDate: Boolean,
    ): WeekRamp {
        fun weekLoad(weeksBack: Long): Double = weekLoad(dailyLoads, monday.minusWeeks(weeksBack), today)

        val current = weekLoad(0)
        val previous = weekLoad(1)
        val baseline = (2L..BASELINE_WEEKS + 1L).sumOf { weekLoad(it) } / BASELINE_WEEKS
        val changePercent = if (previous > 0.0) roundHalfUpToInt((current - previous) / previous * PERCENT) else null
        val light = previous < LIGHT_WEEK_FACTOR * baseline
        return WeekRamp(
            weekStart = monday,
            weekEnd = monday.plusDays(DAYS_PER_WEEK - 1),
            weekToDate = weekToDate,
            load = current,
            previousLoad = previous,
            changePercent = changePercent,
            previousWeekLight = light,
            warning = changePercent != null && changePercent > RAMP_THRESHOLD_PERCENT && !light,
        )
    }

    /** Sum of the daily loads from [monday] to the Sunday, but not after [today]. */
    private fun weekLoad(
        dailyLoads: Map<LocalDate, Double>,
        monday: LocalDate,
        today: LocalDate,
    ): Double {
        val end = minOf(monday.plusDays(DAYS_PER_WEEK - 1), today)
        if (end < monday) return 0.0
        return monday.datesUntil(end.plusDays(1)).mapToDouble { dailyLoads[it] ?: 0.0 }.sum()
    }
}
