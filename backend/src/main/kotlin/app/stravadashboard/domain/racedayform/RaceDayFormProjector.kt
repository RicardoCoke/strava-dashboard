package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator.FATIGUE_DAYS
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator.FITNESS_DAYS
import app.stravadashboard.domain.trainingplan.BuildCalendar
import java.time.LocalDate

/**
 * Fitness, fatigue and form from 6 weeks ago to race day (the plan's end date): actual 005 load up to the last
 * actual day, planned load after it, with the same rolling averages over both.
 */
object RaceDayFormProjector {
    const val CHART_WEEKS = 6L

    fun project(
        runs: List<RunActivity>,
        zones: HrZones,
        calendar: BuildCalendar,
        today: LocalDate,
    ): RaceDayForm {
        val raceDate = calendar.endDate
        val actualLoads = TrainingLoadCalculator.dailyLoads(runs, zones, today)
        val rates = (LoadRateEstimator.estimate(runs, zones, today) as? RatesResult.Learned)?.rates
        // Without rates, or once the race is over, nothing is projected and today is as it stands (AC1.2, AC7.1).
        val ranToday = runs.any { it.isRun && it.localDate == today }
        val lastActual = if (rates == null || ranToday || today > raceDate) today else today.minusDays(1)
        val chartEnd = if (rates == null) today else maxOf(raceDate, today)
        val planned = rates?.let { plannedLoads(calendar, it, lastActual, chartEnd) }.orEmpty()
        val combined = actualLoads.filterKeys { it <= lastActual } + planned

        val days =
            today
                .minusWeeks(CHART_WEEKS)
                .datesUntil(chartEnd.plusDays(1))
                .map { date ->
                    FormDay(
                        date = date,
                        kind = if (date <= lastActual) DayKind.ACTUAL else DayKind.PLANNED,
                        load = combined[date] ?: 0.0,
                        fitness = TrainingLoadCalculator.rollingAverage(combined, date, FITNESS_DAYS),
                        fatigue = TrainingLoadCalculator.rollingAverage(combined, date, FATIGUE_DAYS),
                    )
                }.toList()
        return RaceDayForm(
            today = today,
            raceDate = raceDate,
            chartStart = days.first().date,
            chartEnd = chartEnd,
            lastActualDate = lastActual,
            status = if (rates == null) ProjectionStatus.NOT_ENOUGH_RUNS else ProjectionStatus.PROJECTED,
            subtEstimated = rates?.subtEstimated ?: false,
            days = days,
            raceDay = days.find { it.date == raceDate }?.let { summary(it, runs, actualLoads, today) },
        )
    }

    /** Planned load for every day after [lastActual]; race day itself is 0, so it describes the start line. */
    private fun plannedLoads(
        calendar: BuildCalendar,
        rates: LoadRates,
        lastActual: LocalDate,
        chartEnd: LocalDate,
    ): Map<LocalDate, Double> {
        val planDays = calendar.weeks.flatMap { it.days }.associate { it.date to it.day }
        val easyPace = PlannedLoad.easyPace(calendar.paces, rates)
        return lastActual.plusDays(1).datesUntil(chartEnd.plusDays(1)).toList().associateWith { date ->
            if (date == calendar.endDate) 0.0 else PlannedLoad.of(planDays[date], rates, easyPace)
        }
    }

    /** The peak is the highest actual 005 fitness of the last 6 months, the earliest day if tied. */
    private fun summary(
        raceDay: FormDay,
        runs: List<RunActivity>,
        actualLoads: Map<LocalDate, Double>,
        today: LocalDate,
    ): RaceDaySummary {
        val peak = TrainingLoadCalculator.compute(runs, actualLoads, today).days.maxBy { it.fitness }
        return RaceDaySummary(raceDay.date, raceDay.kind, raceDay.form, raceDay.fitness, peak.fitness, peak.date)
    }
}
