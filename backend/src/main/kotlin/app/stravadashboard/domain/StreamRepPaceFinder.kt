package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.paceOf
import kotlin.math.abs

/** One work rep found in a stream: its start, in stream seconds, and the distance covered in it. */
internal data class RepWindow(
    val startSeconds: Double,
    val distanceMeters: Double,
)

/** Distance since the first sample: `D[k] = D[k-1] + (v[k-1] + v[k]) / 2 * (t[k] - t[k-1])`, linear in between. */
internal class DistanceCurve(
    stream: SpeedStream,
) {
    val times: List<Double> = stream.times.map { it.value.toDouble() }
    val distance: DoubleArray = DoubleArray(times.size)

    init {
        val speeds = stream.speeds.map { it.value }
        for (k in 1 until times.size) {
            distance[k] = distance[k - 1] + (speeds[k - 1] + speeds[k]) / 2 * (times[k] - times[k - 1])
        }
    }

    fun at(seconds: Double): Double {
        val found = times.binarySearch(seconds)
        val index = (if (found >= 0) found else -found - 2).coerceIn(0, times.lastIndex)
        return interpolate(index, seconds)
    }

    /** [index] is the last sample before [time]. */
    fun interpolate(
        index: Int,
        time: Double,
    ): Double {
        val next = minOf(index + 1, times.lastIndex)
        val span = times[next] - times[index]
        if (span <= 0.0) return distance[next]
        val fraction = ((time - times[index]) / span).coerceIn(0.0, 1.0)
        return distance[index] + fraction * (distance[next] - distance[index])
    }
}

/**
 * Finds the work reps of an `NxMmin` workout in a speed stream: the N fastest non-overlapping
 * M-minute windows, picked greedily by distance covered.
 */
object StreamRepPaceFinder {
    private const val MIN_SAMPLES = 2

    fun find(
        stream: SpeedStream,
        workout: RunType.Workout,
    ): SecondsPerKm? =
        windows(stream, workout)?.let { picked ->
            paceOf(
                totalSeconds = workout.reps * windowSeconds(workout),
                totalMeters = picked.sumOf { it.distanceMeters },
            )
        }

    /** The reps in start order; null when fewer than N are found or they cover no distance. */
    internal fun windows(
        stream: SpeedStream,
        workout: RunType.Workout,
    ): List<RepWindow>? {
        if (stream.times.size < MIN_SAMPLES || stream.times.size != stream.speeds.size) return null
        val windowSeconds = windowSeconds(workout)
        val picked = pickWindows(candidateWindows(DistanceCurve(stream), windowSeconds), workout.reps, windowSeconds)
        return if (picked.size < workout.reps || picked.sumOf { it.distanceMeters } <= 0.0) {
            null
        } else {
            picked.sortedBy { it.startSeconds }
        }
    }

    private fun windowSeconds(workout: RunType.Workout): Double = (workout.minutes * SECONDS_PER_MINUTE).toDouble()

    /** One window per sample start that fits before the last sample; end distance is interpolated linearly. */
    private fun candidateWindows(
        curve: DistanceCurve,
        windowSeconds: Double,
    ): List<RepWindow> {
        val times = curve.times
        val last = times.last()
        val windows = mutableListOf<RepWindow>()
        var endIndex = 0
        for (i in times.indices) {
            val endTime = times[i] + windowSeconds
            if (endTime > last) break
            while (endIndex < times.lastIndex && times[endIndex + 1] < endTime) endIndex++
            windows += RepWindow(times[i], curve.interpolate(endIndex, endTime) - curve.distance[i])
        }
        return windows
    }

    private fun pickWindows(
        candidates: List<RepWindow>,
        count: Int,
        windowSeconds: Double,
    ): List<RepWindow> {
        val picked = mutableListOf<RepWindow>()
        for (candidate in candidates.sortedByDescending { it.distanceMeters }) {
            if (picked.size == count) break
            if (picked.none { abs(it.startSeconds - candidate.startSeconds) < windowSeconds }) picked += candidate
        }
        return picked
    }
}
