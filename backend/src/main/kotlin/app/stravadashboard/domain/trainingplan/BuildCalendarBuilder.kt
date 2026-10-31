package app.stravadashboard.domain.trainingplan

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Gives each week block its dates and works out the build progress for today (004 R6, R7). */
object BuildCalendarBuilder {
    private const val DAYS_PER_WEEK = 7
    private const val PERCENT = 100

    fun build(
        plan: TrainingPlan,
        startDate: LocalDate,
        today: LocalDate,
    ): BuildCalendar {
        val weekCount = plan.weeks.size
        val endDate = startDate.plusDays(DAYS_PER_WEEK.toLong() * weekCount - 1)
        val lastDay =
            plan.weeks
                .lastOrNull()
                ?.days
                ?.last()
        val progress = progress(startDate, endDate, weekCount, today, lastDay)
        val weeks =
            plan.weeks.mapIndexed { i, week ->
                val weekStart = startDate.plusWeeks(i.toLong())
                DatedWeek(
                    index = i + 1,
                    week = week,
                    startDate = weekStart,
                    endDate = weekStart.plusDays(DAYS_PER_WEEK - 1L),
                    isCurrentWeek = progress.currentWeekIndex == i + 1,
                    days =
                        week.days.mapIndexed { d, day ->
                            val date = weekStart.plusDays(d.toLong())
                            DatedDay(date, day, isToday = date == today, isPast = date.isBefore(today))
                        },
                )
            }
        return BuildCalendar(startDate, endDate, progress, plan.paces, plan.notes, weeks)
    }

    private fun progress(
        startDate: LocalDate,
        endDate: LocalDate,
        weekCount: Int,
        today: LocalDate,
        lastDay: PlanDay?,
    ): BuildProgress {
        val totalDays = DAYS_PER_WEEK * weekCount
        val sinceStart = ChronoUnit.DAYS.between(startDate, today).toInt()
        val daysPassed = sinceStart.coerceIn(0, totalDays)
        val percent = if (totalDays == 0) 0 else daysPassed * PERCENT / totalDays
        val base =
            BuildProgress(BuildPhase.IN_PROGRESS, percent, daysPassed, totalDays, null, weekCount, null, null, "")
        return when {
            today.isBefore(startDate) -> {
                val untilStart = -sinceStart
                base.copy(
                    phase = BuildPhase.NOT_STARTED,
                    daysUntilStart = untilStart,
                    label = "$percent% · starts in ${days(untilStart)}",
                )
            }
            today.isAfter(endDate) -> base.copy(phase = BuildPhase.FINISHED, label = "$percent% · build complete")
            else -> {
                val week = sinceStart / DAYS_PER_WEEK + 1
                val toGo = ChronoUnit.DAYS.between(today, endDate).toInt()
                val ending =
                    when {
                        toGo > 0 -> "${days(toGo)} to go"
                        lastDay?.race?.isMarathon == true -> "race day"
                        else -> "last day"
                    }
                base.copy(
                    currentWeekIndex = week,
                    daysToGo = toGo,
                    label = "$percent% · week $week of $weekCount · $ending",
                )
            }
        }
    }

    private fun days(count: Int): String = if (count == 1) "1 day" else "$count days"
}
