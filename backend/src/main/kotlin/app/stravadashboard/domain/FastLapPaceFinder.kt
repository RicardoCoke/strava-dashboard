package app.stravadashboard.domain

import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.paceOf

/** Fallback rep pace when no speed stream exists: the km laps clearly faster than the median lap. */
object FastLapPaceFinder {
    const val MIN_LAP_DISTANCE_M = 500.0
    const val MIN_LAPS_FOR_MEDIAN = 3
    const val FAST_LAP_SPEED_FACTOR = 1.08

    fun find(laps: List<Lap>): SecondsPerKm? {
        val usable = laps.filter { it.distance.value >= MIN_LAP_DISTANCE_M && it.elapsed.value > 0 }
        if (usable.size < MIN_LAPS_FOR_MEDIAN) return null

        val threshold = median(usable.map(::speedOf)) * FAST_LAP_SPEED_FACTOR
        val fast = usable.filter { speedOf(it) >= threshold }
        return if (fast.isEmpty()) {
            null
        } else {
            paceOf(
                totalSeconds = fast.sumOf { it.elapsed.value }.toDouble(),
                totalMeters = fast.sumOf { it.distance.value },
            )
        }
    }

    private fun speedOf(lap: Lap): Double = lap.distance.value / lap.elapsed.value

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
