package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.trainingload.HrHistogramBuilder

private const val PERCENT = 100.0

/** One usable interval: its length and the speed and heart rate of the sample at its start. */
private data class Interval(
    val seconds: Int,
    val speed: Double,
    val bpm: Int,
)

/** Time-weighted sums over one half of a run. */
private class HalfSums {
    var seconds = 0.0
    var speedSeconds = 0.0
    var bpmSeconds = 0.0

    fun add(interval: Interval) {
        seconds += interval.seconds
        speedSeconds += interval.speed * interval.seconds
        bpmSeconds += interval.bpm * interval.seconds
    }

    val avgSpeed: Double get() = speedSeconds / seconds
    val avgBpm: Double get() = bpmSeconds / seconds
}

/**
 * Pa:Hr aerobic decoupling (feature 013): the efficiency (speed ÷ heart rate) of the first half of the usable moving
 * time against the second half. Positive means heart rate rose relative to pace.
 */
object HrDriftCalculator {
    const val MIN_USABLE_SECONDS = 3600

    /** The no-data checks run before the 60-minute check. */
    fun compute(stream: DriftStream): HrDriftResult {
        val intervals = usableIntervals(stream)
        val usable = intervals.sumOf { it.seconds }
        val (first, second) = split(intervals, usable)
        val noData =
            intervals.none { it.speed > 0 } || first.seconds == 0.0 || second.seconds == 0.0 || first.avgSpeed == 0.0
        return when {
            noData -> HrDriftResult(HrDriftStatus.NO_DATA, null)
            usable < MIN_USABLE_SECONDS -> HrDriftResult(HrDriftStatus.TOO_SHORT, null)
            else -> {
                val firstEf = first.avgSpeed / first.avgBpm
                val secondEf = second.avgSpeed / second.avgBpm
                HrDriftResult(HrDriftStatus.COMPUTED, (firstEf - secondEf) / firstEf * PERCENT)
            }
        }
    }

    /** Each interval takes the values of its start sample; long gaps, stops and HR dropouts are left out. */
    private fun usableIntervals(stream: DriftStream): List<Interval> =
        (0 until stream.times.size - 1).mapNotNull { i ->
            val dt = stream.times[i + 1].value - stream.times[i].value
            val moving = stream.moving?.getOrNull(i) ?: true
            val bpm = stream.bpm[i].value
            if (dt in 1..HrHistogramBuilder.MAX_SAMPLE_GAP_SECONDS && moving && bpm > 0) {
                Interval(dt, stream.speeds[i].value, bpm)
            } else {
                null
            }
        }

    /** An interval is in the first half while the usable time before it is under half of [usable]. */
    private fun split(
        intervals: List<Interval>,
        usable: Int,
    ): Pair<HalfSums, HalfSums> {
        val first = HalfSums()
        val second = HalfSums()
        var before = 0
        for (interval in intervals) {
            if (before * 2 < usable) first.add(interval) else second.add(interval)
            before += interval.seconds
        }
        return first to second
    }
}
