package app.stravadashboard.domain.rings

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.ports.RingDaySource
import app.stravadashboard.domain.wellness.WellnessFetchResult
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// All data here is invented.
class RingsSyncTest {
    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 9)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    class FakeRingDaySource(
        private val failOn: Map<LocalDate, WellnessFetchResult> = emptyMap(),
    ) : RingDaySource {
        val calls = mutableListOf<Pair<LocalDate, Boolean>>()

        override suspend fun fetchDay(
            date: LocalDate,
            withHeartRate: Boolean,
        ): DayFetchResult {
            calls += date to withHeartRate
            failOn[date]?.let { return DayFetchResult.Failed(it) }
            val t0 = date.atStartOfDay().toInstant(ZoneOffset.UTC)
            val hr = listOf(HrSample(t0, 160), HrSample(t0.plusSeconds(120), 160))
            return DayFetchResult.Ok(DayReading(27_000, if (withHeartRate) hr else null))
        }
    }

    private class FakeRepo : RingDayRepository {
        val rows = mutableMapOf<LocalDate, RingDay>()
        val fetched = mutableMapOf<LocalDate, Instant>()
        var prunedBefore: LocalDate? = null

        override suspend fun findBetween(
            from: LocalDate,
            to: LocalDate,
        ) = rows.filterKeys { it in from..to }

        override suspend fun fetchedAt(
            from: LocalDate,
            to: LocalDate,
        ) = fetched.filterKeys { it in from..to }

        override suspend fun upsert(
            date: LocalDate,
            day: RingDay,
            fetchedAt: Instant,
        ) {
            rows[date] = day
            fetched[date] = fetchedAt
        }

        override suspend fun pruneBefore(date: LocalDate) {
            prunedBefore = date
        }
    }

    private fun sync(
        source: RingDaySource,
        repo: RingDayRepository,
        lthr: Bpm? = Bpm(170),
    ) = RingsSync(source, repo, lthr, clock, ZoneOffset.UTC)

    @Test
    fun `R2 - fetches missing days plus today and yesterday`() =
        runBlocking {
            val source = FakeRingDaySource()
            val repo = FakeRepo()
            assertNull(sync(source, repo).refresh(today))
            assertEquals(32, source.calls.size)
            assertEquals(today.minusDays(31), source.calls.first().first)
            assertEquals(27_000, repo.rows[today]?.sleepSeconds)
            assertEquals(today.minusDays(90), repo.prunedBefore)

            source.calls.clear()
            sync(source, repo).refresh(today)
            assertEquals(listOf(today.minusDays(1), today), source.calls.map { it.first })
        }

    @Test
    fun `R2 - a day fetched before it ended is fetched again`() =
        runBlocking {
            val source = FakeRingDaySource()
            val repo = FakeRepo()
            sync(source, repo).refresh(today)
            // Day D-5 was last fetched at 07:00 that same day: partial strain, maybe no sleep yet.
            val partial = today.minusDays(5)
            repo.fetched[partial] = partial.atTime(7, 0).toInstant(ZoneOffset.UTC)

            source.calls.clear()
            sync(source, repo).refresh(today)
            assertEquals(listOf(partial, today.minusDays(1), today), source.calls.map { it.first })
        }

    @Test
    fun `R2 - no heart rate calls without LTHR`() =
        runBlocking {
            val source = FakeRingDaySource()
            val repo = FakeRepo()
            sync(source, repo, lthr = null).refresh(today)
            assertEquals(setOf(false), source.calls.map { it.second }.toSet())
            assertNull(repo.rows[today]?.strain)
            assertEquals(27_000, repo.rows[today]?.sleepSeconds)
        }

    @Test
    fun `R2 - strain logs counts and status only`() {
        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        val logger = LoggerFactory.getLogger(RingsSync::class.java) as Logger
        logger.addAppender(appender)
        try {
            runBlocking { sync(FakeRingDaySource(), FakeRepo()).refresh(today) }
        } finally {
            logger.detachAppender(appender)
        }
        assertEquals(listOf("Rings refresh finished: days=32, stopped=NONE"), appender.list.map { it.formattedMessage })
    }

    @Test
    fun `R3 - strain failure stops loop and keeps rows`() =
        runBlocking {
            val source = FakeRingDaySource(mapOf(today.minusDays(29) to WellnessFetchResult.Unavailable))
            val repo = FakeRepo()
            assertNull(sync(source, repo).refresh(today))
            assertEquals(3, source.calls.size)
            assertEquals(2, repo.rows.size)
            assertNull(repo.prunedBefore)
        }

    @Test
    fun `R3 - 429 returns retryAfter`() =
        runBlocking {
            val until = now.plusSeconds(120)
            val source = FakeRingDaySource(mapOf(today.minusDays(31) to WellnessFetchResult.RateLimited(until)))
            assertEquals(until, sync(source, FakeRepo()).refresh(today))
            assertEquals(1, source.calls.size)
        }
}
