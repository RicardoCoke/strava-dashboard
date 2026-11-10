package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.CallBudget
import app.stravadashboard.domain.SyncInfo
import app.stravadashboard.domain.SyncOutcome
import app.stravadashboard.domain.SyncPhase
import app.stravadashboard.domain.SyncPolicy
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.testsupport.FakeActivitySource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.NO_CUT_OVER
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.run
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All runs and streams here are invented.
class LongRunDriftSyncTest {
    private val database = DatabaseFactory.connect(tempDbPath())
    private val runs = ExposedRunRepository(database)
    private val syncState = ExposedSyncStateRepository(database)
    private val source = FakeActivitySource()
    private val clock = MutableClock()

    private fun service(callsPerWindow: Int = CallBudget.CALLS_PER_WINDOW) =
        SyncService(source, runs, syncState, clock, TEST_ZONE, NO_CUT_OVER, CallBudget(clock, callsPerWindow))

    private val defaultService = service()

    private fun daysAgo(days: Long): Instant = NOW.minus(Duration.ofDays(days))

    private fun sync(service: SyncService = defaultService): SyncInfo =
        assertIs<SyncOutcome.Done>(runBlocking { service.syncIfDue() }).info

    private fun longRun(
        id: Long,
        start: Instant = daysAgo(20),
        trainer: Boolean = false,
    ): ActivitySummary = summary(id, start, name = "Sunday long run", distanceMeters = 25_000.0, trainer = trainer)

    /** 2 hours at 3.0 m/s; HR 140 for the first hour and 147 for the second, so drift is about 4.8%. */
    private fun driftStream(): DriftStream {
        val bpm = List(3600) { 140 } + List(3600) { 147 }
        return DriftStream(bpm.indices.map(::Seconds), bpm.map { MetersPerSecond(3.0) }, bpm.map(::Bpm), null)
    }

    /** Stores runs as if their detail and HR-stream calls were already made, so only drift calls are pending. */
    private fun storeWithDetailAndHistogram(vararg summaries: ActivitySummary) =
        runBlocking {
            runs.upsertSummaries(summaries.toList())
            summaries.forEach {
                runs.saveDetail(it.id, ActivityDetail(null, emptyList(), Seconds(7200)), NOW)
                runs.saveHrHistogram(
                    it.id,
                    HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogram(mapOf(150 to 7200))),
                    NOW,
                )
            }
        }

    private fun driftCalls(): List<String> = source.calls.filter { it.startsWith("driftstream") }

    @Test
    fun `R2 - drift phase is last and plans newest long runs first`() {
        assertEquals(SyncPhase.MISSING_DRIFT_STREAMS, SyncPhase.entries.last())
        val old =
            run(1, date = NOW.minus(Duration.ofDays(30)).atZone(TEST_ZONE).toLocalDate(), distanceMeters = 25_000.0)
        val recent =
            run(2, date = NOW.minus(Duration.ofDays(3)).atZone(TEST_ZONE).toLocalDate(), distanceMeters = 25_000.0)
        val easy = run(3, distanceMeters = 10_000.0)

        val planned = SyncPolicy.plan(SyncPhase.MISSING_DRIFT_STREAMS, listOf(old, recent, easy), NOW, NO_CUT_OVER)

        assertEquals(listOf(2L, 1L), planned.map { it.run.id.value })
        assertTrue(planned.all { it.drift })
    }

    @Test
    fun `R2 - drift call stores derived result once`() {
        source.activities = listOf(longRun(1))
        source.driftStreams[1] = driftStream()

        sync()

        assertEquals(listOf("driftstream:1"), driftCalls())
        val stored = runBlocking { runs.findAll() }.single().hrDrift
        assertEquals(HrDriftStatus.COMPUTED, stored?.status)
        assertEquals(4.8, stored?.driftPct?.let { Math.round(it * 10) / 10.0 })

        source.calls.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(driftCalls().isEmpty())
    }

    @Test
    fun `R2 - missing stream stores no data and is not refetched`() {
        source.activities = listOf(longRun(1))

        sync()

        assertEquals(HrDriftResult(HrDriftStatus.NO_DATA, null), runBlocking { runs.findAll() }.single().hrDrift)
        source.calls.clear()
        clock.advance(Duration.ofMinutes(20))
        sync()
        assertTrue(driftCalls().isEmpty())
    }

    @Test
    fun `R2 - no drift call for treadmill, no HR, short, workout or detail-less runs`() {
        val start = daysAgo(20)
        source.activities =
            listOf(
                longRun(1, trainer = true),
                summary(2, start, name = "Sunday long run", distanceMeters = 25_000.0, heartRate = null),
                summary(3, start, name = "Sunday long run", distanceMeters = 20_000.0),
                summary(4, start, name = "5x6min", distanceMeters = 25_000.0),
            )
        sync()
        assertTrue(driftCalls().isEmpty())

        val detailLess = run(5, distanceMeters = 25_000.0, laps = null)
        assertTrue(!needsDriftStream(detailLess))
    }

    @Test
    fun `R2 - drift calls come after existing phases within the budget`() {
        val workout = summary(20, daysAgo(25), name = "5x6min", heartRate = null)
        val longRuns = (1..3L).map { longRun(it, daysAgo(30).plus(Duration.ofDays(it))) }
        val missingDetails = (10..11L).map { summary(it, daysAgo(40).plus(Duration.ofHours(it)), heartRate = null) }
        source.activities = longRuns + missingDetails + workout
        storeWithDetailAndHistogram(*longRuns.toTypedArray())
        runBlocking {
            runs.upsertSummaries(listOf(workout))
            runs.saveDetail(workout.id, ActivityDetail(null, emptyList(), Seconds(3000)), NOW)
        }

        // 1 list page leaves 4 calls.
        val info = sync(service(callsPerWindow = 5))

        assertEquals(listOf("detail:11", "detail:10", "stream:20", "driftstream:3"), source.activityCalls)
        assertEquals(SyncStatus.PARTIAL, info.status)
        assertEquals(2, info.runsPendingDetail)
    }

    @Test
    fun `R2 - 429 on drift call stops sync and keeps run pending`() {
        val until = NOW.plus(Duration.ofMinutes(15))
        source.activities = listOf(longRun(1))
        storeWithDetailAndHistogram(longRun(1))
        source.failFromCall = 2
        source.failure = SourceResult.RateLimited(until)

        val info = sync()

        assertEquals(listOf("list:1", "driftstream:1"), source.calls)
        assertEquals(SyncStatus.RATE_LIMITED, info.status)
        assertEquals(until, runBlocking { syncState.get() }.rateLimitedUntil)
        assertNull(runBlocking { runs.findAll() }.single().hrDrift)
        assertEquals(1, SyncPolicy.pendingCount(runBlocking { runs.findAll() }, NO_CUT_OVER))
    }

    @Test
    fun `R2 - unavailable or rate limited on drift call writes no row`() {
        source.activities = listOf(longRun(1))
        storeWithDetailAndHistogram(longRun(1))
        source.failFromCall = 2

        source.failure = SourceResult.Unavailable
        assertEquals(SyncStatus.SOURCE_UNAVAILABLE, sync().status)
        assertNull(runBlocking { runs.findAll() }.single().hrDrift)

        source.calls.clear()
        source.failure = SourceResult.RateLimited(NOW.plus(Duration.ofHours(1)))
        clock.advance(Duration.ofMinutes(20))
        assertEquals(
            SyncStatus.RATE_LIMITED,
            (runBlocking { defaultService.syncIfDue() } as SyncOutcome.Done).info.status,
        )
        assertNull(runBlocking { runs.findAll() }.single().hrDrift)
    }

    @Test
    fun `R2 - drift sync logs no stream data`() {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            source.activities = listOf(longRun(1))
            source.driftStreams[1] = driftStream()
            sync()
        } finally {
            logger.detachAppender(appender)
        }
        val logs = appender.list.joinToString("\n") { it.formattedMessage }
        assertTrue("147" !in logs && "4.76" !in logs && "drift" !in logs.lowercase(), logs)
    }

    @Test
    fun `R3 - drift backlog counts as pending and makes sync due`() {
        val longRuns = (1..5L).map { longRun(it, daysAgo(30).plus(Duration.ofDays(it))) }
        source.activities = longRuns
        storeWithDetailAndHistogram(*longRuns.toTypedArray())
        assertEquals(5, SyncPolicy.pendingCount(runBlocking { runs.findAll() }, NO_CUT_OVER))

        sync(service(callsPerWindow = 3))
        assertEquals(2, driftCalls().size)

        // Next window: due straight away, without waiting for the 15-minute interval.
        source.calls.clear()
        clock.advance(Duration.ofMinutes(1))
        val windowEnd = CallBudget(clock).windowEnd()
        clock.now = windowEnd
        val info = sync(service(callsPerWindow = 3))
        assertEquals(2, driftCalls().size)
        assertEquals(1, info.runsPendingDetail)
    }

    @Test
    fun `R3 - drift backlog fills in over windows within 80 calls each`() {
        val longRuns = (1..30L).map { longRun(it, daysAgo(60).plus(Duration.ofDays(it))) }
        source.activities = longRuns
        storeWithDetailAndHistogram(*longRuns.toTypedArray())
        val budgeted = service(callsPerWindow = 12)

        repeat(4) {
            source.calls.clear()
            sync(budgeted)
            assertTrue(source.calls.size <= 12)
            clock.now = CallBudget(clock).windowEnd()
        }

        val stored = runBlocking { runs.findAll() }
        assertTrue(stored.all { it.hrDrift != null })
        assertEquals(0, SyncPolicy.pendingCount(stored, NO_CUT_OVER))
    }

    @Test
    fun `R3 - no pending drift returns to 15 minute interval`() {
        source.activities = listOf(longRun(1))
        source.driftStreams[1] = driftStream()
        assertEquals(SyncStatus.OK, sync().status)

        source.calls.clear()
        clock.advance(Duration.ofMinutes(5))
        sync()
        assertTrue(source.calls.isEmpty())
        assertEquals(1, runBlocking { runs.findAll() }.count { it.id == ActivityId(1) })
    }
}
