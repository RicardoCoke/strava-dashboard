package app.stravadashboard.garmin

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RUN_SPORT_TYPE
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/**
 * Garmin Connect activities behind the domain's [ActivitySource] (feature 021); Garmin DTOs don't leave this package.
 * The list and the description come from JSON; laps, per-km splits and the speed and heart-rate samples come from the
 * activity's original FIT file, kept parsed (without positions) for the last few activities, since a file doesn't
 * change after upload and a sync asks for the same run's detail and streams one call after another.
 */
class GarminActivitySource(
    private val garmin: GarminConnect,
    private val clock: Clock,
    private val zone: ZoneId,
) : ActivitySource {
    // Access-ordered LRU. Null means the activity has no readable file (404 or undecodable). Calls run one at a time
    // inside a person's sync job, so it needs no lock.
    private val fitCache =
        object : LinkedHashMap<ActivityId, FitData?>(FIT_CACHE_SIZE, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ActivityId, FitData?>?): Boolean =
                size > FIT_CACHE_SIZE
        }

    override suspend fun listActivities(
        after: Instant,
        page: Int,
        perPage: Int,
    ): SourceResult<List<ActivitySummary>> =
        garmin
            .getJson("list", { it.body<List<GarminActivityDto>>() }) {
                url(GarminConnect.BASE_URL + LIST_PATH)
                parameter("start", (page - 1) * perPage)
                parameter("limit", perPage)
                parameter("startDate", LocalDate.ofInstant(after, zone).toString())
                parameter("endDate", LocalDate.now(clock.withZone(zone)).plusDays(1).toString())
                parameter("sortOrder", "asc")
            }.toSource { dtos -> dtos.mapNotNull { it.toSummary() } }

    /** Description and moving time from the activity; laps and splits from its file, empty when it has none. */
    override suspend fun activityDetail(id: ActivityId): SourceResult<ActivityDetail> =
        garmin
            .getJson("detail", { it.body<GarminActivityDetailDto>() }) {
                url("${GarminConnect.BASE_URL}$DETAIL_PATH/${id.value}")
            }.then { detail -> fit(id).then { fit -> GarminCall.Ok(detail.toDetail(fit)) } }
            .toSource { it }

    override suspend fun speedStream(id: ActivityId): SourceResult<SpeedStream> =
        fit(id).toSource { it?.speedStream() }.notFoundIfNull()

    override suspend fun heartRateStream(id: ActivityId): SourceResult<HeartRateStream> =
        fit(id).toSource { it?.heartRateStream() }.notFoundIfNull()

    override suspend fun driftStream(id: ActivityId): SourceResult<DriftStream> =
        fit(id).toSource { it?.driftStream() }.notFoundIfNull()

    private suspend fun fit(id: ActivityId): GarminCall<FitData?> {
        if (fitCache.containsKey(id)) return GarminCall.Ok(fitCache[id])
        val download = garmin.getBytes("fit") { url("${GarminConnect.BASE_URL}$FIT_PATH/${id.value}") }
        // A manual activity has no file: Garmin answers 404, which is final like a file that doesn't decode.
        val result: GarminCall<FitData?> =
            if (download == GarminCall.NotFound) {
                GarminCall.Ok(null)
            } else {
                download.then { bytes -> GarminCall.Ok(GarminFit.parse(bytes)) }
            }
        if (result is GarminCall.Ok) fitCache[id] = result.value
        return result
    }

    private companion object {
        const val LIST_PATH = "/activitylist-service/activities/search/activities"
        const val DETAIL_PATH = "/activity-service/activity"
        const val FIT_PATH = "/download-service/files/activity"
        const val FIT_CACHE_SIZE = 8
        const val LOAD_FACTOR = 0.75f
        const val TREADMILL = "treadmill_running"
        val RUNNING_TYPES = setOf("running", TREADMILL, "trail_running")
    }

    /** Null when a start time isn't in Garmin's documented format; that activity is skipped, not the whole page. */
    private fun GarminActivityDto.toSummary(): ActivitySummary? {
        val local = dateTime(startTimeLocal)
        val utc = dateTime(startTimeGMT)
        if (local == null || utc == null) return null
        val typeKey = activityType?.typeKey.orEmpty()
        return ActivitySummary(
            id = ActivityId(activityId),
            name = activityName.orEmpty(),
            sportType = if (typeKey in RUNNING_TYPES) RUN_SPORT_TYPE else typeKey,
            startUtc = utc.toInstant(ZoneOffset.UTC),
            startLocal = local,
            distance = Meters(distance ?: 0.0),
            averageHeartRateBpm = averageHR,
            trainer = typeKey == TREADMILL,
        )
    }
}

private fun GarminActivityDetailDto.toDetail(fit: FitData?): ActivityDetail =
    ActivityDetail(
        description = description,
        laps = fit?.laps.orEmpty(),
        movingTime = Seconds(roundHalfUpToInt(summaryDTO?.movingDuration ?: summaryDTO?.duration ?: 0.0)),
        splits = fit?.splits.orEmpty(),
    )

/** Garmin sends `yyyy-MM-dd HH:mm:ss`, sometimes with a fraction. */
@Suppress("SwallowedException")
private fun dateTime(text: String): LocalDateTime? =
    try {
        LocalDateTime.parse(text.trim().replace(' ', 'T'))
    } catch (e: DateTimeParseException) {
        null
    }

/** A rejected key has already had its one retry with a fresh token, so it reads as the source being unavailable. */
private inline fun <T, R> GarminCall<T>.toSource(map: (T) -> R): SourceResult<R> =
    when (this) {
        is GarminCall.Ok -> SourceResult.Success(map(value))
        GarminCall.NotFound -> SourceResult.NotFound
        is GarminCall.RateLimited -> SourceResult.RateLimited(until)
        GarminCall.KeyRejected, GarminCall.Unavailable -> SourceResult.Unavailable
    }

private fun <T : Any> SourceResult<T?>.notFoundIfNull(): SourceResult<T> =
    when (this) {
        is SourceResult.Success -> value?.let { SourceResult.Success(it) } ?: SourceResult.NotFound
        SourceResult.NotFound -> SourceResult.NotFound
        is SourceResult.RateLimited -> this
        SourceResult.Unavailable -> SourceResult.Unavailable
    }
