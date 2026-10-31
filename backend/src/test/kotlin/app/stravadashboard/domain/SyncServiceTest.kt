package app.stravadashboard.domain

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.testsupport.FakeActivitySource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncServiceTest {
    private val database = DatabaseFactory.connect(tempDbPath())
    private val runs = ExposedRunRepository(database)
    private val syncState = ExposedSyncStateRepository(database)
    private val source = FakeActivitySource()
    private val clock = MutableClock()
    private val service = SyncService(source, runs, syncState, clock, TEST_ZONE)

    // History start for today 2026-10-29 is 2026-03-19 (6 months + 41 days back); the list starts the day before.
    private val backfillStart = Instant.parse("2026-03-18T00:00:00Z")
    private val firstKeptDay = Instant.parse("2026-03-19T00:00:00Z")

    private fun daysAgo(days: Long): Instant = NOW.minus(Duration.ofDays(days))

    private fun sync(): SyncInfo = assertIs<SyncOutcome.Done>(runBlocking { service.syncIfDue() }).info

    @Test
    fun `006 R7 - long easy run gets the same calls as an easy run`() {
        source.activities =
            listOf(
                summary(1, daysAgo(9), name = "Easy run"),
                summary(2, daysAgo(8), name = "Sunday long run", distanceMeters = 25_000.0),
            )
        sync()
        val callsPerRun = source.activityCalls.groupBy({ it.substringAfter(':') }, { it.substringBefore(':') })
        assertEquals(callsPerRun.getValue("1").sorted(), callsPerRun.getValue("2").sorted())
        assertTrue("detail" in callsPerRun.getValue("2"))
        assertTrue(source.calls.none { it.startsWith("stream") })
    }

    private fun stream(): SpeedStream {
        val speeds = List(600) { 3.0 } + List(5) { List(360) { 4.0 } + List(120) { 2.5 } }.flatten()
        return SpeedStream(speeds.indices.map(::Seconds), speeds.map(::MetersPerSecond))
    }

    @Test
    fun `R2 - first sync imports from history start with pagination`() {
        source.activities = (1..250L).map { summary(it, firstKeptDay.plus(Duration.ofHours(it))) }

        sync()

        assertEquals(listOf("list:1", "list:2"), source.calls.filter { it.startsWith("list") })
        assertEquals(listOf(backfillStart, backfillStart), source.listAfters)
        assertEquals(250, runBlocking { runs.findAll() }.size)
        assertNotNull(runBlocking { syncState.get() }.listCursor)
    }

    @Test
    fun `R4 - interrupted import restarts from backfill start`() {
        source.activities = (1..250L).map { summary(it, firstKeptDay.plus(Duration.ofHours(it))) }
        source.failFromCall = 2
        source.failure = SourceResult.RateLimited(NOW.plus(Duration.ofMinutes(5)))

        assertEquals(SyncStatus.RATE_LIMITED, sync().status)
        assertNull(runBlocking { syncState.get() }.listCursor)

        source.failFromCall = null
        source.listAfters.clear()
        clock.advance(Duration.ofMinutes(10))
        sync()

        assertEquals(backfillStart, source.listAfters.first())
        assertEquals(250, runBlocking { runs.findAll() }.size)
    }

    @Test
    fun `R4 - incremental sync lists after newest stored run or seven days ago`() {
        source.activities = listOf(summary(1, Instant.parse("2026-10-27T06:00:00Z")))
        sync()
        clock.advance(Duration.ofMinutes(20))
        source.listAfters.clear()
        sync()
        assertEquals(Instant.parse("2026-10-22T10:20:00Z"), source.listAfters.single())
    }

    @Test
    fun `R4 - after a long break the list starts at the newest stored run`() {
        val newest = Instant.parse("2026-10-01T06:00:00Z")
        source.activities = listOf(summary(1, newest))
        sync()
        clock.advance(Duration.ofMinutes(20))
        source.listAfters.clear()
        sync()
        assertEquals(newest, source.listAfters.single())
    }

    @Test
    fun `R4 - fresh DB within 15 minutes makes no Strava calls`() {
        source.activities = listOf(summary(1, daysAgo(2)))
        sync()
        source.calls.clear()
        clock.advance(Duration.ofMinutes(14))

        val info = sync()

        assertTrue(source.calls.isEmpty())
        assertEquals(SyncStatus.OK, info.status)
    }

    @Test
    fun `R4 - details fetched newest first`() {
        source.activities = listOf(summary(1, daysAgo(3)), summary(2, daysAgo(1)), summary(3, daysAgo(2)))
        source.details[2] = ActivityDetail("A description", listOf(kmLap(0, 300)))

        sync()

        assertEquals(
            listOf("detail:2", "detail:3", "detail:1"),
            source.activityCalls.filter { it.startsWith("detail") },
        )
        val stored = runBlocking { runs.findAll() }.single { it.id.value == 2L }
        assertEquals("A description", stored.description)
        assertEquals(listOf(kmLap(0, 300)), stored.laps)
    }

    @Test
    fun `R4 - outdoor workout gets one streams call and only derived pace is stored`() {
        source.activities = listOf(summary(1, daysAgo(10), name = "5x6min"))
        source.streams[1] = stream()

        sync()

        assertEquals(listOf("detail:1", "stream:1", "hrstream:1"), source.activityCalls)
        val stored = runBlocking { runs.findAll() }.single().streamPace
        assertEquals("5x6min", stored?.typeKey)
        assertEquals(StreamPaceStatus.COMPUTED, stored?.status)
        assertEquals(250, stored?.pace?.value)
    }

    @Test
    fun `R4 - missing streams stored as unavailable and not retried`() {
        source.activities = listOf(summary(1, daysAgo(10), name = "5x6min"))
        sync()
        assertEquals(StreamPaceStatus.UNAVAILABLE, runBlocking { runs.findAll() }.single().streamPace?.status)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(source.activityCalls.none { it.startsWith("stream") })
    }

    @Test
    fun `R4 - no streams calls for treadmill or easy runs`() {
        source.activities =
            listOf(summary(1, daysAgo(10), name = "5x6min", trainer = true), summary(2, daysAgo(9), name = "Easy run"))
        sync()
        assertTrue(source.calls.none { it.startsWith("stream") })
    }

    @Test
    fun `R4 - detail and stream calls share cap of 80 in priority order`() {
        // 60 workouts: 60 detail calls first, then only 20 of the 60 stream calls fit.
        source.activities =
            (1..60L).map { summary(it, daysAgo(30).plus(Duration.ofHours(it)), name = "5x6min", heartRate = null) }

        val info = sync()

        assertEquals(80, source.activityCalls.size)
        assertTrue(source.activityCalls.take(60).all { it.startsWith("detail") })
        assertEquals(listOf("stream:60", "stream:59"), source.activityCalls.drop(60).take(2))
        assertEquals(SyncStatus.PARTIAL, info.status)
        assertEquals(40, info.runsPendingDetail)
    }

    @Test
    fun `R4 - recent runs get detail refetched after one hour`() {
        source.activities = listOf(summary(1, daysAgo(2)), summary(2, daysAgo(10)))
        sync()
        source.details[1] = ActivityDetail("4:10 4:05", emptyList())
        source.calls.clear()
        clock.advance(Duration.ofMinutes(61))

        sync()

        assertEquals(listOf("detail:1"), source.activityCalls)
        assertEquals("4:10 4:05", runBlocking { runs.findAll() }.single { it.id.value == 1L }.description)
    }

    @Test
    fun `R4 - type key change triggers new streams call`() {
        source.activities = listOf(summary(1, daysAgo(2), name = "5x6min"))
        sync()
        source.activities = listOf(summary(1, daysAgo(2), name = "4x8min"))
        source.calls.clear()
        clock.advance(Duration.ofMinutes(61))

        sync()

        assertTrue("stream:1" in source.activityCalls)
        assertEquals("4x8min", runBlocking { runs.findAll() }.single().streamPace?.typeKey)
    }

    @Test
    fun `R4 - non runs are not stored and retyped runs are deleted`() {
        source.activities = listOf(summary(1, daysAgo(2)), summary(2, daysAgo(2), sportType = "Ride"))
        sync()
        assertEquals(listOf(1L), runBlocking { runs.findAll() }.map { it.id.value })

        source.activities = listOf(summary(1, daysAgo(2), sportType = "Walk"))
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(runBlocking { runs.findAll() }.isEmpty())
    }

    @Test
    fun `R4 - concurrent requests trigger one sync`() {
        source.activities = listOf(summary(1, daysAgo(2)))
        runBlocking { List(2) { async { service.syncIfDue() } }.awaitAll() }
        assertEquals(1, source.calls.count { it.startsWith("list") })
    }

    @Test
    fun `R4 - concurrent requests during a partial import share one sync`() {
        source.activities = (1..100L).map { summary(it, daysAgo(20).plus(Duration.ofHours(it))) }

        val outcomes = runBlocking { List(2) { async { service.syncIfDue() } }.awaitAll() }

        assertEquals(1, source.calls.count { it.startsWith("list") })
        assertEquals(80, source.activityCalls.size)
        assertEquals(outcomes.first(), outcomes.last())
    }

    @Test
    fun `R4 - remaining calls report partial status`() {
        source.activities = (1..100L).map { summary(it, daysAgo(20).plus(Duration.ofHours(it)), heartRate = null) }

        val first = sync()
        assertEquals(SyncStatus.PARTIAL, first.status)
        assertEquals(20, first.runsPendingDetail)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(1))
        val second = sync()
        assertEquals(20, source.activityCalls.size)
        assertEquals(SyncStatus.OK, second.status)
    }

    @Test
    fun `R5 - 429 stops sync and sets retryAfter`() {
        val until = Instant.parse("2026-10-29T10:15:00Z")
        source.activities = (1..5L).map { summary(it, daysAgo(it)) }
        source.failFromCall = 3
        source.failure = SourceResult.RateLimited(until)

        val info = sync()

        assertEquals(SyncStatus.RATE_LIMITED, info.status)
        assertEquals(until, info.retryAfter)
        assertEquals(3, source.calls.size)
        assertEquals(1, runBlocking { runs.findAll() }.count { it.detailFetchedAt != null })
    }

    @Test
    fun `R5 - no Strava calls before retryAfter`() {
        val until = NOW.plus(Duration.ofMinutes(15))
        source.activities = listOf(summary(1, daysAgo(1)))
        source.failFromCall = 1
        source.failure = SourceResult.RateLimited(until)
        sync()
        source.calls.clear()
        source.failFromCall = null
        clock.advance(Duration.ofMinutes(5))

        val info = sync()

        assertTrue(source.calls.isEmpty())
        assertEquals(SyncStatus.RATE_LIMITED, info.status)
        assertEquals(until, info.retryAfter)
    }

    @Test
    fun `R5 - network failure serves DB with stravaUnavailable`() {
        source.activities = listOf(summary(1, daysAgo(1)))
        sync()
        val lastSynced = runBlocking { syncState.get() }.lastSuccessfulSyncAt
        clock.advance(Duration.ofMinutes(20))
        source.failFromCall = 1
        source.failure = SourceResult.Unavailable

        val info = sync()

        assertEquals(SyncStatus.STRAVA_UNAVAILABLE, info.status)
        assertEquals(lastSynced, info.lastSyncedAt)
        assertEquals(1, runBlocking { runs.findAll() }.size)
    }

    private fun hrStream(): HeartRateStream =
        HeartRateStream(listOf(0, 1, 2, 3).map(::Seconds), listOf(140, 140, 160, 180).map(::Bpm))

    /** Stores runs as a pre-008 store would have them: detail fetched, but no moving time or lap HR. */
    private fun storeOldDetails(vararg summaries: ActivitySummary) {
        runBlocking {
            runs.upsertSummaries(summaries.toList())
            summaries.forEach { runs.saveDetail(it.id, ActivityDetail(null, emptyList()), daysAgo(1)) }
        }
        transaction(database) { exec("UPDATE runs SET moving_time_s = NULL") }
    }

    @Test
    fun `008 R2 - old details with HR are refetched once after missing details`() {
        val old = summary(1, daysAgo(10))
        val oldNoHr = summary(2, daysAgo(9), heartRate = null)
        val fresh = summary(3, daysAgo(2))
        storeOldDetails(old, oldNoHr)
        source.activities = listOf(old, oldNoHr, fresh)
        source.details[1] = ActivityDetail(null, listOf(kmLap(1, 300, heartRate = 160.0)), Seconds(3000))

        sync()

        assertEquals(listOf("detail:3", "detail:1"), source.activityCalls.filter { it.startsWith("detail") })
        val stored = runBlocking { runs.findAll() }.associateBy { it.id.value }
        assertEquals(Seconds(3000), stored.getValue(1).movingTime)
        assertEquals(listOf(160.0), stored.getValue(1).laps?.map { it.averageHeartRateBpm })
        assertNull(stored.getValue(2).movingTime)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(source.activityCalls.none { it == "detail:1" || it == "detail:2" })
    }

    @Test
    fun `008 R2 - refetch counts as pending and 429 stops the sync`() {
        storeOldDetails(summary(1, daysAgo(10)))
        source.activities = emptyList()
        source.failFromCall = 2
        source.failure = SourceResult.RateLimited(NOW.plus(Duration.ofMinutes(15)))

        val info = sync()

        assertEquals(listOf("list:1", "detail:1"), source.calls)
        assertEquals(SyncStatus.RATE_LIMITED, info.status)
        assertEquals(1, info.runsPendingDetail)
    }

    @Test
    fun `R2 - prune keeps the warm-up history`() {
        source.activities =
            listOf(
                summary(1, Instant.parse("2026-03-18T07:00:00Z")),
                summary(2, Instant.parse("2026-03-19T07:00:00Z")),
            )
        sync()
        assertEquals(listOf(2L), runBlocking { runs.findAll() }.map { it.id.value })
    }

    @Test
    fun `R2 - pre-005 database re-lists from history start once`() {
        source.activities = listOf(summary(1, daysAgo(2)), summary(2, Instant.parse("2026-04-01T07:00:00Z")))
        val cursor = daysAgo(2)
        runBlocking { syncState.save(SyncState(listCursor = cursor, lastSuccessfulSyncAt = NOW)) }
        clock.advance(Duration.ofMinutes(20))

        sync()

        assertEquals(listOf(backfillStart), source.listAfters)
        assertEquals(backfillStart, runBlocking { syncState.get() }.historyStart)
        assertEquals(setOf(1L, 2L), runBlocking { runs.findAll() }.map { it.id.value }.toSet())

        source.listAfters.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertEquals(listOf(Instant.parse("2026-10-22T10:40:00Z")), source.listAfters)
    }

    @Test
    fun `R3 - streams heart rate once for runs with HR and stores only the histogram`() {
        source.activities = listOf(summary(1, daysAgo(2)), summary(2, daysAgo(1), heartRate = null))
        source.hrStreams[1] = hrStream()

        sync()

        assertEquals(listOf("hrstream:1"), source.activityCalls.filter { it.startsWith("hrstream") })
        val stored = runBlocking { runs.findAll() }.associateBy { it.id.value }
        assertEquals(HrHistogramStatus.COMPUTED, stored.getValue(1).hrHistogram?.status)
        assertEquals(
            mapOf(140 to 2, 160 to 1),
            stored
                .getValue(1)
                .hrHistogram
                ?.histogram
                ?.secondsByBpm,
        )
        assertNull(stored.getValue(2).hrHistogram)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(source.calls.none { it.startsWith("hrstream") })
    }

    @Test
    fun `R3 - 404 or missing heartrate stores UNAVAILABLE and is not retried`() {
        source.activities = listOf(summary(1, daysAgo(2)))
        sync()
        val stored = runBlocking { runs.findAll() }.single().hrHistogram
        assertEquals(HrHistogramResult(HrHistogramStatus.UNAVAILABLE, null), stored)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(source.calls.none { it.startsWith("hrstream") })
    }

    @Test
    fun `R3 - HR streams share the call cap newest first and count as pending`() {
        source.activities = (1..50L).map { summary(it, daysAgo(20).plus(Duration.ofHours(it))) }

        val first = sync()

        assertEquals(80, source.activityCalls.size)
        assertTrue(source.activityCalls.take(50).all { it.startsWith("detail") })
        assertEquals(listOf("hrstream:50", "hrstream:49"), source.activityCalls.drop(50).take(2))
        assertEquals(SyncStatus.PARTIAL, first.status)
        assertEquals(20, first.runsPendingDetail)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(1))
        val second = sync()
        assertEquals(20, source.activityCalls.count { it.startsWith("hrstream") })
        assertEquals(SyncStatus.OK, second.status)
    }

    @Test
    fun `R3 - 429 on HR stream stops sync and leaves the run pending`() {
        val until = NOW.plus(Duration.ofMinutes(15))
        source.activities = listOf(summary(1, daysAgo(2)))
        source.failFromCall = 3
        source.failure = SourceResult.RateLimited(until)

        val info = sync()

        assertEquals(listOf("list:1", "detail:1", "hrstream:1"), source.calls)
        assertEquals(SyncStatus.RATE_LIMITED, info.status)
        assertNull(runBlocking { runs.findAll() }.single().hrHistogram)
        assertEquals(1, info.runsPendingDetail)
    }

    @Test
    fun `R3 - network failure on HR stream leaves the run pending`() {
        source.activities = listOf(summary(1, daysAgo(2)))
        source.failFromCall = 3
        source.failure = SourceResult.Unavailable

        assertEquals(SyncStatus.STRAVA_UNAVAILABLE, sync().status)
        assertNull(runBlocking { runs.findAll() }.single().hrHistogram)
    }

    @Test
    fun `R3 - revoked access returns reauth required`() {
        source.activities = listOf(summary(1, daysAgo(1)))
        source.failFromCall = 1
        source.failure = SourceResult.ReauthRequired
        assertIs<SyncOutcome.ReauthRequired>(runBlocking { service.syncIfDue() })
    }
}
