package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.CallBudget
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.testsupport.FakeActivitySource
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.Segment
import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.driftStream
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.run
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reads only: any write fails the test (R5, AC5.8). */
private class ReadOnlyRuns(
    var stored: List<RunActivity>,
) : RunRepository {
    override suspend fun findAll(): List<RunActivity> = stored

    override suspend fun newestStartUtc(): Instant? = stored.maxOfOrNull { it.startUtc }

    override suspend fun upsertSummaries(summaries: List<ActivitySummary>) = fail()

    override suspend fun saveDetail(
        id: ActivityId,
        detail: ActivityDetail,
        fetchedAt: Instant,
    ) = fail()

    override suspend fun saveStreamPace(
        id: ActivityId,
        result: StreamPaceResult,
        computedAt: Instant,
    ) = fail()

    override suspend fun saveHrHistogram(
        id: ActivityId,
        result: HrHistogramResult,
        computedAt: Instant,
    ) = fail()

    override suspend fun saveHrDrift(
        id: ActivityId,
        result: HrDriftResult,
        computedAt: Instant,
    ) = fail()

    override suspend fun delete(ids: Collection<ActivityId>) = fail()

    override suspend fun pruneBefore(date: LocalDate) = fail()

    private fun fail(): Nothing = error("compare must not write to the run store")
}

private class RecordingSyncState(
    var state: SyncState = SyncState(),
) : SyncStateRepository {
    var saves = 0

    override suspend fun get(): SyncState = state

    override suspend fun save(state: SyncState) {
        saves++
    }
}

// All runs and streams here are invented.
class WorkoutCompareServiceTest {
    private val clock = fixedClock()
    private val source = FakeActivitySource()
    private val syncState = RecordingSyncState()
    private val splits = listOf(Split(0, Meters(1000.0), Seconds(300)), Split(1, Meters(1000.0), Seconds(290)))
    private val earlier = run(id = 1, date = TODAY.minusDays(14), name = "5x6min", splits = splits)
    private val later = run(id = 2, date = TODAY, name = "8x3min", splits = splits)
    private val runs = ReadOnlyRuns(listOf(earlier, later))

    private fun service(
        budget: CallBudget = CallBudget(clock),
        activitiesFrom: LocalDate = LocalDate.of(2000, 1, 1),
        withSource: Boolean = true,
    ) = WorkoutCompareService(runs, source.takeIf { withSource }, budget, syncState, activitiesFrom, clock)

    private fun compare(service: WorkoutCompareService = service()): WorkoutComparison =
        assertIs<CompareOutcome.Found>(runBlocking { service.compare(later.id, earlier.id) }).comparison

    private fun streams() {
        source.driftStreams[1] = driftStream(Segment(600, 3.0, 150))
        source.driftStreams[2] = driftStream(Segment(600, 3.0, 150))
    }

    @Test
    fun `R2 - filter uses the run classifier`() {
        runs.stored =
            listOf(
                run(id = 1, name = "Sunday long", distanceMeters = 22_000.0),
                run(id = 2, name = "4x10min", distanceMeters = 22_000.0),
                run(id = 3, name = "10x3", distanceMeters = 8_000.0),
                run(id = 4, name = "Easy", distanceMeters = 8_000.0),
                run(id = 5, name = "Ride", sportType = "Ride"),
            )

        fun ids(filter: CompareFilter) = runBlocking { service().list(filter) }.map { it.id.value }.sorted()

        assertEquals(listOf(1L), ids(CompareFilter.LONG))
        assertEquals(listOf(4L), ids(CompareFilter.EASY))
        assertEquals(listOf(2L, 3L), ids(CompareFilter.WORKOUTS))
        assertEquals(listOf(1L, 2L, 3L, 4L), ids(CompareFilter.ALL))
    }

    @Test
    fun `R5 - both outdoor Garmin runs fetch one stream each`() {
        streams()
        val budget = CallBudget(clock)

        val result = compare(service(budget))

        assertEquals(listOf("driftstream:1", "driftstream:2"), source.calls)
        assertEquals(Resolution.STREAM, result.resolution)
        assertNull(result.coarse)
        assertEquals(earlier.id, result.earlier.run.id)
        assertEquals(CallBudget.CALLS_PER_WINDOW - 2, budget.remaining())
    }

    @Test
    fun `R5 - a run without HR or a drift stream fetches speed only`() {
        runs.stored = listOf(earlier.copy(averageHeartRateBpm = null), later)
        val speed = driftStream(Segment(60, 3.0, 150)).let { SpeedStream(it.times, it.speeds) }
        source.streams[1] = speed
        source.streams[2] = speed

        val result = compare()

        assertEquals(listOf("stream:1", "driftstream:2", "stream:2"), source.calls)
        assertEquals(Resolution.STREAM, result.resolution)
    }

    @Test
    fun `R5 - up-front checks fall back to splits without calls`() {
        assertEquals(Coarse(CoarseReason.NOT_CONFIGURED), compare(service(withSource = false)).coarse)
        assertEquals(Coarse(CoarseReason.BEFORE_GARMIN, earlier.id), compare(service(activitiesFrom = TODAY)).coarse)

        runs.stored = listOf(earlier, later.copy(trainer = true))
        assertEquals(Coarse(CoarseReason.TREADMILL, later.id), compare().coarse)

        runs.stored = listOf(earlier, later)
        val until = NOW.plusSeconds(600)
        syncState.state = SyncState(rateLimitedUntil = until)
        val limited = compare()
        assertEquals(Coarse(CoarseReason.RATE_LIMITED, retryAfter = until), limited.coarse)
        assertEquals(Resolution.SPLITS, limited.resolution)

        assertEquals(emptyList(), source.calls)
    }

    @Test
    fun `R5 - exhausted budget falls back with call limit and window end`() {
        streams()
        val budget = CallBudget(clock, callsPerWindow = 1)

        val result = compare(service(budget))

        assertEquals(listOf("driftstream:1"), source.calls)
        assertEquals(Coarse(CoarseReason.CALL_LIMIT, retryAfter = budget.windowEnd()), result.coarse)
    }

    @Test
    fun `R5 - rate limit stops calls and leaves sync state alone`() {
        streams()
        val until = NOW.plusSeconds(900)
        source.failFromCall = 1
        source.failure = SourceResult.RateLimited(until)

        val result = compare()

        assertEquals(listOf("driftstream:1"), source.calls)
        assertEquals(Coarse(CoarseReason.RATE_LIMITED, retryAfter = until), result.coarse)
        assertEquals(0, syncState.saves)
    }

    @Test
    fun `R5 - unavailable and missing file fall back with reason`() {
        source.failFromCall = 1
        assertEquals(Coarse(CoarseReason.GARMIN_UNAVAILABLE), compare().coarse)

        source.failFromCall = null
        source.calls.clear()
        val missing = compare()
        assertEquals(Coarse(CoarseReason.NO_DETAIL_FILE, earlier.id), missing.coarse)
        assertEquals(listOf("driftstream:1", "stream:1"), source.calls)
    }

    @Test
    fun `R5 - both runs share one resolution`() {
        source.driftStreams[1] = driftStream(Segment(600, 3.0, 150))

        val result = compare()

        assertEquals(Coarse(CoarseReason.NO_DETAIL_FILE, later.id), result.coarse)
        assertEquals(Resolution.SPLITS, result.resolution)
        assertEquals(2, result.earlier.points.size) // the fetched stream isn't used: splits for both
        assertEquals(2, result.later.points.size)
    }

    @Test
    fun `R5 - compare stores nothing`() {
        streams()
        compare() // ReadOnlyRuns fails on any write
        assertEquals(0, syncState.saves)
    }

    @Test
    fun `R5 - compare logs only the reason code`() {
        val logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            source.driftStreams[1] = driftStream(Segment(600, 3.0, 157))
            compare()
        } finally {
            logger.detachAppender(appender)
        }
        val logs = appender.list.map { it.formattedMessage }
        assertEquals(1, logs.size, logs.toString())
        assertTrue("NO_DETAIL_FILE" in logs.single() && "157" !in logs.single() && "2" !in logs.single(), logs.single())
    }
}
