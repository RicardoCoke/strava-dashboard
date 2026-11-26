package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.longrundrift.HrDriftCalculator
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.model.paceOf
import app.stravadashboard.domain.trainingload.HrHistogramBuilder

/** Time-weighted heart-rate sums over one window: bpm × seconds and the seconds counted. */
internal data class BpmSums(
    val bpmSeconds: Double,
    val seconds: Double,
) {
    operator fun plus(other: BpmSums): BpmSums = BpmSums(bpmSeconds + other.bpmSeconds, seconds + other.seconds)

    val mean: Double? get() = if (seconds > 0.0) bpmSeconds / seconds else null
}

/** Chart points and stats for one run of a comparison (feature 025, R6 and R8). Pure. */
object CompareCalculator {
    const val CHART_BUCKET_SECONDS = 10
    const val SLOWEST_PLOTTED_PACE_SECONDS_PER_KM = 600

    /** One point per 10-second bucket from the first sample; a bucket without a sample is a gap. */
    fun streamPoints(stream: RunStream): List<ComparePoint> {
        val times = stream.speed.times.map { it.value }
        if (times.size < 2 || times.size != stream.speed.speeds.size) return emptyList()
        val first = times.first()
        val last = times.last() - first
        var sample = 0
        return (0 until (last + CHART_BUCKET_SECONDS - 1) / CHART_BUCKET_SECONDS).map { k ->
            val start = k * CHART_BUCKET_SECONDS
            val end = minOf(start + CHART_BUCKET_SECONDS, last)
            while (sample < times.size && times[sample] - first < start) sample++
            val hasSample = sample < times.size && times[sample] - first < start + CHART_BUCKET_SECONDS
            val from = stream.curve.at((first + start).toDouble())
            val to = stream.curve.at((first + end).toDouble())
            ComparePoint(
                distance = Meters(to),
                time = Seconds(end),
                pace = if (hasSample && to > from) plotted(paceOf((end - start).toDouble(), to - from)) else null,
                heartRate =
                    if (hasSample) {
                        averageBpm(
                            stream.drift,
                            (first + start).toDouble(),
                            (first + end).toDouble(),
                        )
                    } else {
                        null
                    },
            )
        }
    }

    /** One point per stored per-km split, at its cumulative distance and moving time. */
    fun splitPoints(splits: List<Split>?): List<ComparePoint> {
        var meters = 0.0
        var seconds = 0
        return splits.orEmpty().sortedBy { it.index }.map { split ->
            meters += split.distance.value
            seconds += split.movingTime.value
            val hasPace = split.distance.value > 0 && split.movingTime.value > 0
            ComparePoint(
                distance = Meters(meters),
                time = Seconds(seconds),
                pace = if (hasPace) plotted(paceOf(split.movingTime.value.toDouble(), split.distance.value)) else null,
                heartRate = split.averageHeartRateBpm,
            )
        }
    }

    /** Moving time over distance; null when either is missing or 0 (list rows and stats share it). */
    fun averagePace(run: RunActivity): SecondsPerKm? =
        movingTimeOf(run)?.let { paceOf(it.value.toDouble(), run.distance.value) }

    private fun movingTimeOf(run: RunActivity): Seconds? =
        run.movingTime?.takeIf { it.value > 0 && run.distance.value > 0 }

    /** [drift] is this request's stream, used for drift when no stored long-run drift exists. */
    fun stats(
        run: RunActivity,
        drift: DriftStream?,
    ): CompareStats {
        val meters = run.distance.value
        val moving = movingTimeOf(run)
        val heartRate = run.averageHeartRateBpm?.takeIf { it > 0 }
        val minutes = moving?.let { it.value.toDouble() / SECONDS_PER_MINUTE }
        return CompareStats(
            distance = run.distance,
            movingTime = run.movingTime,
            pace = averagePace(run),
            averageHeartRateBpm = heartRate,
            elevationGain =
                run.splits
                    ?.mapNotNull { it.elevationDifference?.value }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { diffs -> Meters(diffs.sumOf { maxOf(it, 0.0) }) },
            efficiency = if (minutes != null && heartRate != null) meters / minutes / heartRate else null,
            driftPct =
                run.hrDrift?.takeIf { it.status == HrDriftStatus.COMPUTED }?.driftPct
                    ?: drift?.let(HrDriftCalculator::compute)?.takeIf { it.status == HrDriftStatus.COMPUTED }?.driftPct,
        )
    }

    /** Null without HR samples or when the window holds no usable ones. */
    internal fun averageBpm(
        drift: DriftStream?,
        from: Double,
        to: Double,
    ): Double? = drift?.let { bpmSums(it, from, to).mean }

    /**
     * Each sample's bpm holds until the next sample, clipped to the window; intervals over 30 s and samples at or
     * below 0 bpm are left out.
     */
    internal fun bpmSums(
        drift: DriftStream,
        from: Double,
        to: Double,
    ): BpmSums {
        val times = drift.times
        val found = times.binarySearchBy(from) { it.value.toDouble() }
        var i = (if (found >= 0) found else -found - 2).coerceAtLeast(0)
        var bpmSeconds = 0.0
        var seconds = 0.0
        while (i < times.lastIndex && times[i].value < to) {
            val start = times[i].value.toDouble()
            val end = times[i + 1].value.toDouble()
            val bpm = drift.bpm[i].value
            val overlap = minOf(end, to) - maxOf(start, from)
            if (end - start <= HrHistogramBuilder.MAX_SAMPLE_GAP_SECONDS && bpm > 0 && overlap > 0) {
                bpmSeconds += bpm * overlap
                seconds += overlap
            }
            i++
        }
        return BpmSums(bpmSeconds, seconds)
    }

    private fun plotted(pace: SecondsPerKm): SecondsPerKm? =
        pace.takeIf { it.value <= SLOWEST_PLOTTED_PACE_SECONDS_PER_KM }
}
