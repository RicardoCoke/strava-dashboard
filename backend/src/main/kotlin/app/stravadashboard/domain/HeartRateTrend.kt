package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.model.toKmOneDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class HrPoint(
    val date: LocalDate,
    val averageHeartRateBpm: Double,
    val distanceKm: Double,
    val treadmill: Boolean,
    val rising: Boolean,
)

data class HrTrendLine(
    val startDate: LocalDate,
    val startBpm: Double,
    val endDate: LocalDate,
    val endBpm: Double,
    val changeBpm: Double,
)

data class HrTypeTrend(
    val type: RunType,
    val runCount: Int,
    val runsWithoutHeartRate: Int,
    val points: List<HrPoint>,
    val trend: HrTrendLine?,
) {
    val hasEnoughData: Boolean get() = trend != null
}

data class HrTrendResult(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val minRunsForComparison: Int,
    val types: List<HrTypeTrend>,
)

/** Whole-run average heart rate per run type over the last five months, with a least-squares trend line. */
object HeartRateTrend {
    const val MIN_POINTS_FOR_COMPARISON = 3
    private const val WINDOW_MONTHS = 5L

    fun compute(
        runs: List<RunActivity>,
        today: LocalDate,
    ): HrTrendResult {
        val windowStart = today.minusMonths(WINDOW_MONTHS)
        val inWindow = runs.filter { it.isRun && it.localDate in windowStart..today }
        val easyFirstThenMostRecent =
            compareBy<Map.Entry<RunType, List<RunActivity>>> { it.key != RunType.Easy }
                .thenByDescending { entry -> entry.value.maxOf { it.startUtc } }
        val types =
            inWindow
                .groupBy { RunClassifier.classify(it) }
                .entries
                .sortedWith(easyFirstThenMostRecent)
                .map { (type, typeRuns) -> typeTrend(type, typeRuns, windowStart) }
        return HrTrendResult(windowStart, today, MIN_POINTS_FOR_COMPARISON, types)
    }

    private fun typeTrend(
        type: RunType,
        runs: List<RunActivity>,
        windowStart: LocalDate,
    ): HrTypeTrend {
        val withHr = runs.filter { it.averageHeartRateBpm != null }.sortedBy { it.startLocal }
        val rising = HrRisingTrend.flags(withHr.map { requireNotNull(it.averageHeartRateBpm) })
        val points =
            withHr.mapIndexed { index, run ->
                HrPoint(
                    date = run.localDate,
                    averageHeartRateBpm = roundOneDecimal(requireNotNull(run.averageHeartRateBpm)),
                    distanceKm = run.distance.toKmOneDecimal(),
                    treadmill = run.trainer,
                    rising = rising[index],
                )
            }
        val trend = if (points.size >= MIN_POINTS_FOR_COMPARISON) trendLine(withHr, windowStart) else null
        return HrTypeTrend(type, runs.size, runs.size - withHr.size, points, trend)
    }

    /** Least squares of bpm (unrounded) against days since [windowStart]. */
    private fun trendLine(
        runs: List<RunActivity>,
        windowStart: LocalDate,
    ): HrTrendLine {
        val xs = runs.map { ChronoUnit.DAYS.between(windowStart, it.localDate).toDouble() }
        val ys = runs.map { requireNotNull(it.averageHeartRateBpm) }
        val meanX = xs.average()
        val meanY = ys.average()
        val sxx = xs.sumOf { (it - meanX) * (it - meanX) }
        val sxy = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) }
        val slope = if (sxx == 0.0) 0.0 else sxy / sxx
        val startBpm = meanY + slope * (xs.first() - meanX)
        val endBpm = meanY + slope * (xs.last() - meanX)
        return HrTrendLine(
            startDate = runs.first().localDate,
            startBpm = roundOneDecimal(startBpm),
            endDate = runs.last().localDate,
            endBpm = roundOneDecimal(endBpm),
            changeBpm = roundOneDecimal(endBpm - startBpm),
        )
    }
}
