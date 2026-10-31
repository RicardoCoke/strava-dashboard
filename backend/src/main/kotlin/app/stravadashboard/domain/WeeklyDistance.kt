package app.stravadashboard.domain

import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.toKmOneDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WeekTotal(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val distanceKm: Double,
    val runCount: Int,
    val isCurrentWeek: Boolean,
)

/** Running km per Monday-Sunday week, bucketed by the run's local start date. */
object WeeklyDistance {
    const val WEEK_COUNT = 9
    private const val DAYS_PER_WEEK = 7L

    /** The Monday of the week [date] falls in; every weekly figure uses this rule. */
    fun weekStartOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun compute(
        runs: List<RunActivity>,
        today: LocalDate,
    ): List<WeekTotal> {
        val currentWeekStart = weekStartOf(today)
        val runsByWeek =
            runs
                .filter { it.isRun }
                .groupBy { weekStartOf(it.localDate) }
        return (WEEK_COUNT - 1 downTo 0).map { weeksAgo ->
            val weekStart = currentWeekStart.minusWeeks(weeksAgo.toLong())
            val weekRuns = runsByWeek[weekStart].orEmpty()
            WeekTotal(
                weekStart = weekStart,
                weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1),
                distanceKm = Meters(weekRuns.sumOf { it.distance.value }).toKmOneDecimal(),
                runCount = weekRuns.size,
                isCurrentWeek = weeksAgo == 0,
            )
        }
    }
}
