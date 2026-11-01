package app.stravadashboard.domain.efficiency

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.paceOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class EfRunType { EASY, LONG }

/** One plotted run. [efficiency] and [rollingEfficiency] are unrounded metres per minute per beat. */
data class EfPoint(
    val date: LocalDate,
    val startLocal: LocalDateTime,
    val runType: EfRunType,
    val efficiency: Double,
    val rollingEfficiency: Double?,
    val rollingRunCount: Int,
    val distance: Meters,
    val movingTime: Seconds,
    val pace: SecondsPerKm,
    val averageHeartRateBpm: Double,
)

data class EfCurrent(
    val efficiency: Double,
    val date: LocalDate,
    val runCount: Int,
)

/** [percent] is positive when the efficiency factor went up (an improvement). */
data class EfChange(
    val percent: Double,
    val from: EfCurrent,
    val to: EfCurrent,
)

data class EfficiencyFactorReport(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val current: EfCurrent?,
    val change: EfChange?,
    val points: List<EfPoint>,
    val runsPendingDetail: Int,
)

/**
 * Efficiency factor (average speed in m/min over average heart rate) of easy and long outdoor runs, with a
 * 4-week rolling mean per run (see docs/features/014-easy-efficiency-factor/specs.md).
 */
object EfficiencyFactorCalculator {
    const val WINDOW_MONTHS = 6L
    const val ROLLING_DAYS = 28L
    const val MIN_RUNS_FOR_ROLLING = 3
    const val MIN_MOVING_SECONDS = 1200
    private const val PERCENT = 100.0

    private data class Sample(
        val run: RunActivity,
        val type: EfRunType,
        val movingTime: Seconds,
        val heartRate: Double,
        val efficiency: Double,
    )

    fun compute(
        runs: List<RunActivity>,
        today: LocalDate,
    ): EfficiencyFactorReport {
        val windowStart = today.minusMonths(WINDOW_MONTHS)
        val samples = runs.mapNotNull { sampleOf(it, today) }.sortedBy { it.run.startLocal }
        val points =
            samples.indices
                .filter { !samples[it].run.localDate.isBefore(windowStart) }
                .map { pointOf(samples, it) }
        val current = currentOf(points, today)
        return EfficiencyFactorReport(
            windowStart = windowStart,
            windowEnd = today,
            current = current,
            change = changeOf(points, current),
            points = points,
            runsPendingDetail = runs.count { isPendingDetail(it, windowStart, today) },
        )
    }

    private fun sampleOf(
        run: RunActivity,
        today: LocalDate,
    ): Sample? {
        val type = efTypeOf(run)
        val heartRate = run.averageHeartRateBpm?.takeIf { it > 0 }
        val movingTime = run.movingTime?.takeIf { it.value >= MIN_MOVING_SECONDS }
        val excluded = run.trainer || run.distance.value <= 0 || run.localDate.isAfter(today)
        if (type == null || heartRate == null || movingTime == null || excluded) return null
        val metersPerMinute = run.distance.value / (movingTime.value.toDouble() / SECONDS_PER_MINUTE)
        return Sample(run, type, movingTime, heartRate, metersPerMinute / heartRate)
    }

    private fun efTypeOf(run: RunActivity): EfRunType? {
        if (!run.isRun) return null
        return when (RunClassifier.classify(run)) {
            RunType.Easy -> EfRunType.EASY
            RunType.LongRun -> EfRunType.LONG
            is RunType.Workout -> null
        }
    }

    /** The rolling set: every earlier-or-same sample dated within the 28 days ending on this run's date. */
    private fun pointOf(
        samples: List<Sample>,
        index: Int,
    ): EfPoint {
        val sample = samples[index]
        val rollingStart = sample.run.localDate.minusDays(ROLLING_DAYS - 1)
        val rolling = samples.subList(0, index + 1).filter { !it.run.localDate.isBefore(rollingStart) }
        return EfPoint(
            date = sample.run.localDate,
            startLocal = sample.run.startLocal,
            runType = sample.type,
            efficiency = sample.efficiency,
            rollingEfficiency = rolling.takeIf { it.size >= MIN_RUNS_FOR_ROLLING }?.map { it.efficiency }?.average(),
            rollingRunCount = rolling.size,
            distance = sample.run.distance,
            movingTime = sample.movingTime,
            pace = paceOf(sample.movingTime.value.toDouble(), sample.run.distance.value),
            averageHeartRateBpm = sample.heartRate,
        )
    }

    private fun EfPoint.rollingValue(): EfCurrent? = rollingEfficiency?.let { EfCurrent(it, date, rollingRunCount) }

    private fun currentOf(
        points: List<EfPoint>,
        today: LocalDate,
    ): EfCurrent? =
        points
            .lastOrNull()
            ?.takeIf { !it.date.isBefore(today.minusDays(ROLLING_DAYS - 1)) }
            ?.rollingValue()

    private fun changeOf(
        points: List<EfPoint>,
        current: EfCurrent?,
    ): EfChange? {
        val from = points.firstNotNullOfOrNull { it.rollingValue() }
        if (current == null || from == null || ChronoUnit.DAYS.between(from.date, current.date) < ROLLING_DAYS) {
            return null
        }
        return EfChange((current.efficiency - from.efficiency) / from.efficiency * PERCENT, from, current)
    }

    private fun isPendingDetail(
        run: RunActivity,
        windowStart: LocalDate,
        today: LocalDate,
    ): Boolean =
        efTypeOf(run) != null &&
            !run.trainer &&
            run.averageHeartRateBpm != null &&
            run.movingTime == null &&
            !run.localDate.isBefore(windowStart) &&
            !run.localDate.isAfter(today)
}
