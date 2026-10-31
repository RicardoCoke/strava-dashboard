package app.stravadashboard.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

const val RUN_SPORT_TYPE = "Run"

data class Lap(
    val index: Int,
    val elapsed: Seconds,
    val distance: Meters,
)

enum class StreamPaceStatus { COMPUTED, UNAVAILABLE }

/** Derived rep pace from a speed stream, valid only while the run's type key still equals [typeKey]. */
data class StreamPaceResult(
    val typeKey: String,
    val status: StreamPaceStatus,
    val pace: SecondsPerKm?,
)

/**
 * A stored Run. [laps] is null while the detail call hasn't been made yet.
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
) {
    val localDate: LocalDate get() = startLocal.toLocalDate()
    val isRun: Boolean get() = sportType == RUN_SPORT_TYPE
}
