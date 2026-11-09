package app.stravadashboard.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

const val RUN_SPORT_TYPE = "Run"

/** [averageHeartRateBpm] is null when the lap has no heart rate, or its detail predates feature 008. */
data class Lap(
    val index: Int,
    val elapsed: Seconds,
    val distance: Meters,
    val averageHeartRateBpm: Double? = null,
)

/**
 * One per-km split of a run's detail (feature 016). Optional fields are null when the source leaves them out;
 * [gradeAdjustedSpeed] is Strava's grade-adjusted speed (Garmin FIT files have none).
 */
data class Split(
    val index: Int,
    val distance: Meters,
    val movingTime: Seconds,
    val elevationDifference: Meters? = null,
    val averageHeartRateBpm: Double? = null,
    val gradeAdjustedSpeed: MetersPerSecond? = null,
)

enum class StreamPaceStatus { COMPUTED, UNAVAILABLE }

/** Derived rep pace from a speed stream, valid only while the run's type key still equals [typeKey]. */
data class StreamPaceResult(
    val typeKey: String,
    val status: StreamPaceStatus,
    val pace: SecondsPerKm?,
)

/**
 * A stored Run. [laps] is null while the detail call hasn't been made yet, and [hrHistogram] while the heart-rate
 * stream call hasn't been made yet.
 */
data class RunActivity(
    val id: ActivityId,
    val name: String,
    val sportType: String,
    val startUtc: Instant,
    val startLocal: LocalDateTime,
    val distance: Meters,
    val averageHeartRateBpm: Double?,
    val trainer: Boolean,
    val description: String?,
    val laps: List<Lap>?,
    val detailFetchedAt: Instant?,
    val streamPace: StreamPaceResult?,
    val hrHistogram: HrHistogramResult? = null,
    /** Null until a detail call made since feature 008 (which also stores lap heart rate). */
    val movingTime: Seconds? = null,
    /** Null until the long-run drift stream call has been made (feature 013). */
    val hrDrift: HrDriftResult? = null,
    /** Null for a detail stored before feature 016; empty when the detail had no splits. */
    val splits: List<Split>? = null,
) {
    val localDate: LocalDate get() = startLocal.toLocalDate()
    val isRun: Boolean get() = sportType == RUN_SPORT_TYPE
}
