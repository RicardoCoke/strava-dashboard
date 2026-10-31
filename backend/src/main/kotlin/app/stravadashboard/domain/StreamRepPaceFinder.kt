package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.paceOf
import kotlin.math.abs

/**
 * Finds the work reps of an `NxMmin` workout in a speed stream: the N fastest non-overlapping
 * M-minute windows, picked greedily by distance covered.
 */
object StreamRepPaceFinder {
    private const val MIN_SAMPLES = 2

    private data class Window(
        val start: Double,
        val distanceMeters: Double,
    )

    fun find(
        stream: SpeedStream,
        workout: RunType.Workout,
    ): SecondsPerKm? {
        val times = stream.times.map { it.value.toDouble() }
        val speeds = stream.speeds.map { it.value }
        if (times.size < MIN_SAMPLES || times.size != speeds.size) return null

        val windowSeconds = (workout.minutes * SECONDS_PER_MINUTE).toDouble()
        val candidates = candidateWindows(times, cumulativeDistance(times, speeds), windowSeconds)
        val picked = pickWindows(candidates, workout.reps, windowSeconds)
        val totalMeters = picked.sumOf { it.distanceMeters }
        return if (picked.size < workout.reps || totalMeters <= 0.0) {
            null
        } else {
            paceOf(totalSeconds = workout.reps * windowSeconds, totalMeters = totalMeters)
        }
    }

    /** Trapezoid integration: `D[k] = D[k-1] + (v[k-1] + v[k]) / 2 * (t[k] - t[k-1])`. */
    private fun cumulativeDistance(
        times: List<Double>,
        speeds: List<Double>,
    ): DoubleArray {
        val distance = DoubleArray(times.size)
        for (k in 1 until times.size) {
            distance[k] = distance[k - 1] + (speeds[k - 1] + speeds[k]) / 2 * (times[k] - times[k - 1])
        }
        return distance
    }

    /** One window per sample start that fits before the last sample; end distance is interpolated linearly. */
    private fun candidateWindows(
        times: List<Double>,
        distance: DoubleArray,
        windowSeconds: Double,
    ): List<Window> {
        val last = times.last()
        val windows = mutableListOf<Window>()
        var endIndex = 0
        for (i in times.indices) {
            val endTime = times[i] + windowSeconds
            if (endTime > last) break
            while (endIndex < times.lastIndex && times[endIndex + 1] < endTime) endIndex++
            windows += Window(times[i], interpolate(times, distance, endIndex, endTime) - distance[i])
        }
        return windows
    }

    private fun interpolate(
        times: List<Double>,
        distance: DoubleArray,
        index: Int,
        time: Double,
    ): Double {
        val next = minOf(index + 1, times.lastIndex)
        val span = times[next] - times[index]
        if (span <= 0.0) return distance[next]
        val fraction = ((time - times[index]) / span).coerceIn(0.0, 1.0)
        return distance[index] + fraction * (distance[next] - distance[index])
    }

    private fun pickWindows(
        candidates: List<Window>,
        count: Int,
        windowSeconds: Double,
    ): List<Window> {
        val picked = mutableListOf<Window>()
        for (candidate in candidates.sortedByDescending { it.distanceMeters }) {
            if (picked.size == count) break
            if (picked.none { abs(it.start - candidate.start) < windowSeconds }) picked += candidate
        }
        return picked
    }
}
