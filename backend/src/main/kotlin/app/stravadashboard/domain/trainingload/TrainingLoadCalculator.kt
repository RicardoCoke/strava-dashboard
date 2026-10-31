package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.SyncPolicy
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.RunActivity
import java.time.LocalDate

/** Daily load, fitness, fatigue and form over the last 6 months, from stored heart-rate histograms. */
object TrainingLoadCalculator {
    const val FITNESS_DAYS = 42
    const val FATIGUE_DAYS = 7

    fun windowStart(today: LocalDate): LocalDate = today.minusMonths(SyncPolicy.HISTORY_MONTHS)

    fun compute(
        runs: List<RunActivity>,
        zones: HrZones,
        today: LocalDate,
    ): TrainingLoad = compute(runs, dailyLoads(runs, zones, today), today)

    /** [loads] comes from [dailyLoads], so a caller that also needs it (the ramp rate) builds it only once. */
    fun compute(
        runs: List<RunActivity>,
        loads: Map<LocalDate, Double>,
        today: LocalDate,
    ): TrainingLoad {
        val start = windowStart(today)
        val days =
            start.datesUntil(today.plusDays(1)).map { date ->
                DailyLoad(
                    date = date,
                    load = loads[date] ?: 0.0,
                    fitness = rollingAverage(loads, date, FITNESS_DAYS),
                    fatigue = rollingAverage(loads, date, FATIGUE_DAYS),
                )
            }
        val inWindow = runs.filter { it.localDate in start..today }
        return TrainingLoad(start, today, days.toList(), countsOf(inWindow))
    }

    /** Sum of the loads of counted runs per local date, up to [today]. Dates without such runs are absent. */
    fun dailyLoads(
        runs: List<RunActivity>,
        zones: HrZones,
        today: LocalDate,
    ): Map<LocalDate, Double> =
        runs
            .filter { it.localDate <= today }
            .mapNotNull { run -> countedHistogram(run)?.let { run.localDate to TrimpCalculator.load(it, zones) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, loads) -> loads.sum() }

    private fun countedHistogram(run: RunActivity) =
        run.hrHistogram
            ?.takeIf { run.averageHeartRateBpm != null && it.status == HrHistogramStatus.COMPUTED }
            ?.histogram

    private fun rollingAverage(
        loads: Map<LocalDate, Double>,
        date: LocalDate,
        days: Int,
    ): Double = (0 until days).sumOf { loads[date.minusDays(it.toLong())] ?: 0.0 } / days

    private fun countsOf(runs: List<RunActivity>): RunCounts {
        val noHeartRate = runs.count { it.averageHeartRateBpm == null }
        val withHeartRate = runs.filter { it.averageHeartRateBpm != null }
        return RunCounts(
            counted = withHeartRate.count { it.hrHistogram?.status == HrHistogramStatus.COMPUTED },
            skippedNoHeartRate =
                noHeartRate + withHeartRate.count { it.hrHistogram?.status == HrHistogramStatus.UNAVAILABLE },
            pendingHeartRate = withHeartRate.count { it.hrHistogram == null },
        )
    }
}
