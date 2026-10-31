package app.stravadashboard.strava

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import java.time.Instant

/** Adapts [StravaClient] to the domain's [ActivitySource]; Strava DTOs don't leave this package. */
class StravaActivitySource(
    private val client: StravaClient,
) : ActivitySource {
    override suspend fun listActivities(
        after: Instant,
        page: Int,
        perPage: Int,
    ): SourceResult<List<ActivitySummary>> =
        client.listActivities(after, page, perPage).toSource { dtos -> dtos.map { it.toSummary() } }

    override suspend fun activityDetail(id: ActivityId): SourceResult<ActivityDetail> =
        client.activity(id.value).toSource { it.toDetail() }

    /** A stream without usable speed data counts as not found, like a 404. */
    override suspend fun speedStream(id: ActivityId): SourceResult<SpeedStream> =
        client.speedStreams(id.value).toSource { it.toSpeedStream() }.notFoundIfNull()

    /** A stream without usable heart-rate data counts as not found, like a 404. */
    override suspend fun heartRateStream(id: ActivityId): SourceResult<HeartRateStream> =
        client.heartRateStreams(id.value).toSource { it.toHeartRateStream() }.notFoundIfNull()

    private fun <T : Any> SourceResult<T?>.notFoundIfNull(): SourceResult<T> =
        when (this) {
            is SourceResult.Success -> value?.let { SourceResult.Success(it) } ?: SourceResult.NotFound
            SourceResult.NotFound -> SourceResult.NotFound
            is SourceResult.RateLimited -> this
            SourceResult.ReauthRequired -> SourceResult.ReauthRequired
            SourceResult.Unavailable -> SourceResult.Unavailable
        }

    private inline fun <T, R> StravaResult<T>.toSource(map: (T) -> R): SourceResult<R> =
        when (this) {
            is StravaResult.Success -> SourceResult.Success(map(value))
            StravaResult.NotFound -> SourceResult.NotFound
            StravaResult.Unauthorized -> SourceResult.ReauthRequired
            is StravaResult.RateLimited -> SourceResult.RateLimited(until)
            StravaResult.Unavailable -> SourceResult.Unavailable
        }
}
