package app.stravadashboard.testsupport

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class MutableClock(
    var now: Instant = NOW,
) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = TEST_ZONE

    override fun withZone(zone: ZoneId): Clock = this

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/** An in-memory Strava: lists [activities] after a cursor, oldest first, and records every call. */
class FakeActivitySource : ActivitySource {
    var activities: List<ActivitySummary> = emptyList()
    val details = mutableMapOf<Long, ActivityDetail>()
    val streams = mutableMapOf<Long, SpeedStream>()
    val hrStreams = mutableMapOf<Long, HeartRateStream>()
    val calls = mutableListOf<String>()
    val listAfters = mutableListOf<Instant>()

    /** When set, the call with this 1-based number (and every later one) returns [failure]. */
    var failFromCall: Int? = null
    var failure: SourceResult<Nothing> = SourceResult.Unavailable

    val activityCalls: List<String> get() = calls.filterNot { it.startsWith("list") }

    override suspend fun listActivities(
        after: Instant,
        page: Int,
        perPage: Int,
    ): SourceResult<List<ActivitySummary>> =
        record("list:$page") {
            listAfters += after
            val matching = activities.filter { it.startUtc > after }.sortedBy { it.startUtc }
            SourceResult.Success(matching.drop((page - 1) * perPage).take(perPage))
        }

    override suspend fun activityDetail(id: ActivityId): SourceResult<ActivityDetail> =
        record("detail:${id.value}") { SourceResult.Success(details[id.value] ?: ActivityDetail(null, emptyList())) }

    override suspend fun speedStream(id: ActivityId): SourceResult<SpeedStream> =
        record("stream:${id.value}") { streams[id.value]?.let { SourceResult.Success(it) } ?: SourceResult.NotFound }

    override suspend fun heartRateStream(id: ActivityId): SourceResult<HeartRateStream> =
        record("hrstream:${id.value}") {
            hrStreams[id.value]?.let { SourceResult.Success(it) } ?: SourceResult.NotFound
        }

    private fun <T> record(
        call: String,
        block: () -> SourceResult<T>,
    ): SourceResult<T> {
        calls += call
        val limit = failFromCall
        return if (limit != null && calls.size >= limit) failure else block()
    }
}
