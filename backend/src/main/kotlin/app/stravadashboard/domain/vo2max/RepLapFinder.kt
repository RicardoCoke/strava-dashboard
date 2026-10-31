package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import kotlin.math.abs

/** Finds the rep laps of an `NxMmin` workout by duration, using only laps that have heart rate. */
object RepLapFinder {
    const val REP_DURATION_TOLERANCE = 0.15
    const val MIN_REP_SECONDS = 240

    /**
     * Laps within ±15 % of `M` minutes (and at least 4 min). When there are more than `N`, outdoor runs keep the
     * fastest and treadmill runs (whose lap distance is unreliable) the highest heart rate. Ties go to the lower
     * lap index. Returned in lap order.
     */
    fun find(
        laps: List<Lap>,
        workout: RunType.Workout,
        treadmill: Boolean,
    ): List<Lap> {
        val target = workout.minutes * SECONDS_PER_MINUTE.toDouble()
        val candidates =
            laps.filter { lap ->
                val elapsed = lap.elapsed.value
                abs(elapsed - target) <= REP_DURATION_TOLERANCE * target &&
                    elapsed >= MIN_REP_SECONDS &&
                    lap.averageHeartRateBpm != null &&
                    (treadmill || lap.distance.value > 0.0)
            }
        if (candidates.size <= workout.reps) return candidates.sortedBy { it.index }
        val strongestFirst =
            if (treadmill) {
                compareByDescending<Lap> { it.averageHeartRateBpm }
            } else {
                compareByDescending<Lap> { it.distance.value / it.elapsed.value }
            }
        return candidates
            .sortedWith(strongestFirst.thenBy { it.index })
            .take(workout.reps)
            .sortedBy { it.index }
    }
}
