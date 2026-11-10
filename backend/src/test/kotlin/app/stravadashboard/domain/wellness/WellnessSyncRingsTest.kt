package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.rings.RingDay
import app.stravadashboard.domain.rings.RingsSync
import app.stravadashboard.domain.rings.RingsSyncTest.FakeRingDaySource
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRingDayRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.testsupport.FakeWellnessSource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.WELLNESS_TODAY
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All data here is invented. The ring step (feature 020) inside the 015 wellness refresh.
class WellnessSyncRingsTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = MutableClock(now)
    private val database = DatabaseFactory.connect(tempDbPath())
    private val repo = ExposedWellnessRepository(database)
    private val ringRepo = ExposedRingDayRepository(database)
    private val source = FakeWellnessSource()

    private fun sync(
        ringSource: FakeRingDaySource,
        rings: RingDayRepository = ringRepo,
    ) = WellnessSync(source, repo, clock, TEST_ZONE, RingsSync(ringSource, rings, Bpm(170), clock, TEST_ZONE))

    private fun state(): WellnessState = runBlocking { repo.getState() }

    @Test
    fun `R3 - 429 sets retryAfter and keeps status OK`() =
        runBlocking {
            val until = now.plusSeconds(120)
            val first = WELLNESS_TODAY.minusDays(92)
            sync(FakeRingDaySource(mapOf(first to WellnessFetchResult.RateLimited(until)))).refreshIfDue()
            assertEquals(WellnessFetchStatus.OK, state().lastStatus)
            assertEquals(until, state().retryAfter)
        }

    @Test
    fun `R3 - ring failure keeps status OK and stored rows`() =
        runBlocking {
            val ringSource = FakeRingDaySource(mapOf(WELLNESS_TODAY to WellnessFetchResult.Unavailable))
            sync(ringSource).refreshIfDue()
            assertEquals(WellnessFetchStatus.OK, state().lastStatus)
            assertNull(state().retryAfter)
            assertEquals(31, ringRepo.findBetween(WELLNESS_TODAY.minusDays(31), WELLNESS_TODAY).size)
        }

    @Test
    fun `R3 - unexpected ring error keeps status OK`() =
        runBlocking {
            val broken =
                object : RingDayRepository by ringRepo {
                    override suspend fun upsert(
                        date: LocalDate,
                        day: RingDay,
                        fetchedAt: Instant,
                    ): Unit = error("disk full")
                }
            sync(FakeRingDaySource(), broken).refreshIfDue()
            assertEquals(WellnessFetchStatus.OK, state().lastStatus)
        }

    @Test
    fun `R3 - no HR calls when summaries fail`() =
        runBlocking {
            source.result = WellnessFetchResult.Unavailable
            val ringSource = FakeRingDaySource()
            sync(ringSource).refreshIfDue()
            assertTrue(ringSource.calls.isEmpty())
        }

    @Test
    fun `R2 - ring days are pruned after 92 days`() =
        runBlocking {
            val old = WELLNESS_TODAY.minusDays(93)
            ringRepo.upsert(old, RingDay(28_800, 10.0), now)
            sync(FakeRingDaySource()).refreshIfDue()
            assertEquals(emptyMap(), ringRepo.findBetween(old, old))
            assertEquals(27_000, ringRepo.findBetween(WELLNESS_TODAY, WELLNESS_TODAY)[WELLNESS_TODAY]?.sleepSeconds)
        }
}
