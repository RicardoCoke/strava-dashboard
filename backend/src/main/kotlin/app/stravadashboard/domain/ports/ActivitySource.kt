package app.stravadashboard.domain.ports

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.SpeedStream
import java.time.Instant
import java.time.LocalDateTime

data class ActivitySummary(
    val id: ActivityId,
    val name: String,
    val sportType: String,
    val startUtc: Instant,
    val startLocal: LocalDateTime,
    val distance: Meters,
    val averageHeartRateBpm: Double?,
    val trainer: Boolean,
)

data class ActivityDetail(
    val description: String?,
    val laps: List<Lap>,
)

/** Outcome of a call to the activity source. Expected failures are values, not exceptions. */
sealed interface SourceResult<out T> {
    data class Success<T>(
        val value: T,
    ) : SourceResult<T>

    /** The resource doesn't exist (or, for streams, has no usable speed data). */
    data object NotFound : SourceResult<Nothing>

    data class RateLimited(
        val until: Instant,
    ) : SourceResult<Nothing>

    data object Unavailable : SourceResult<Nothing>

    /** The connection was revoked; stored tokens have been deleted. */
    data object ReauthRequired : SourceResult<Nothing>
}

interface ActivitySource {
    suspend fun listActivities(
        after: Instant,
        page: Int,
        perPage: Int,
    ): SourceResult<List<ActivitySummary>>

    suspend fun activityDetail(id: ActivityId): SourceResult<ActivityDetail>

    suspend fun speedStream(id: ActivityId): SourceResult<SpeedStream>
}
