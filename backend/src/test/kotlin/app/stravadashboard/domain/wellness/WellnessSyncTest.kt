package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.ports.WellnessRepository
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.testsupport.FakeWellnessSource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.WELLNESS_TODAY
import app.stravadashboard.testsupport.tempDbPath
import app.stravadashboard.testsupport.wellnessDay
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WellnessSyncTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = MutableClock(now)
    private val repo = ExposedWellnessRepository(DatabaseFactory.connect(tempDbPath()))
    private val source = FakeWellnessSource()
    private val sync = WellnessSync(source, repo, clock, TEST_ZONE)

    private val cached = listOf(wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 50, hrv = 61.5))

    private fun success(vararg days: WellnessDay) = WellnessFetchResult.Success(days.toList())

    private fun stored(): List<WellnessDay> =
        runBlocking {
            repo.findBetween(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1))
        }

    private fun state(): WellnessState = runBlocking { repo.getState() }

    @Test
    fun `R3 - first refresh backfills 121 days and later ones re-fetch the last week`() =
        runBlocking {
            sync.refreshIfDue()
            clock.advance(Duration.ofDays(1))
            sync.refreshIfDue()

            assertEquals(
                listOf(
                    LocalDate.of(2026, 7, 3) to WELLNESS_TODAY,
                    LocalDate.of(2026, 10, 25) to LocalDate.of(2026, 11, 1),
                ),
                source.ranges,
            )
        }

    @Test
    fun `R3 - refresh is skipped until 15 minutes have passed`() =
        runBlocking {
            sync.refreshIfDue()
            clock.advance(Duration.ofMinutes(14))
            sync.refreshIfDue()
            assertEquals(1, source.ranges.size)
            clock.advance(Duration.ofMinutes(1))
            sync.refreshIfDue()
            assertEquals(2, source.ranges.size)
        }

    @Test
    fun `R3 - success replaces range, nulls non-positive values and prunes`() =
        runBlocking {
            val old = wellnessDay(LocalDate.of(2026, 7, 1), restingHr = 55)
            repo.replaceRange(old.date, WELLNESS_TODAY, listOf(old) + cached, now)
            source.result =
                success(
                    wellnessDay(LocalDate.of(2026, 10, 30), restingHr = 0, hrv = 58.0),
                    wellnessDay(LocalDate.of(2026, 10, 31), restingHr = -1, hrv = 0.0),
                    wellnessDay(LocalDate.of(2026, 11, 2), restingHr = 50),
                )

            sync.refreshIfDue()

            // 2026-10-29 was fetched and not returned, so it is gone; 2026-07-01 is older than today − 120.
            assertEquals(listOf(wellnessDay(LocalDate.of(2026, 10, 30), hrv = 58.0)), stored())
            assertEquals(WellnessState(now, now, WellnessFetchStatus.OK, null, historyDays = 120), state())
        }

    @Test
    fun `R3 - late HRV for a past day is picked up`() =
        runBlocking {
            source.result = success(wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 50))
            sync.refreshIfDue()
            clock.advance(Duration.ofMinutes(15))
            source.result = success(wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 50, hrv = 55.0))
            sync.refreshIfDue()

            assertEquals(listOf(wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 50, hrv = 55.0)), stored())
        }

    @Test
    fun `R4 - key rejected, rate limited and unavailable keep cached rows`() =
        runBlocking {
            repo.replaceRange(WELLNESS_TODAY.minusDays(10), WELLNESS_TODAY, cached, now)
            val until = now.plus(Duration.ofMinutes(2))
            val outcomes =
                listOf(
                    WellnessFetchResult.KeyRejected to WellnessFetchStatus.KEY_REJECTED,
                    WellnessFetchResult.RateLimited(until) to WellnessFetchStatus.RATE_LIMITED,
                    WellnessFetchResult.Unavailable to WellnessFetchStatus.UNAVAILABLE,
                )
            outcomes.forEach { (result, status) ->
                clock.advance(Duration.ofMinutes(15))
                source.result = result
                sync.refreshIfDue()

                assertEquals(cached, stored())
                assertEquals(status, state().lastStatus)
                assertEquals(clock.now, state().lastAttemptAt)
                assertNull(state().lastSuccessAt)
                // 023 AC4.5: the backfill is only marked done after a success, so it is retried.
                assertNull(state().historyDays)
                assertEquals(until.takeIf { status == WellnessFetchStatus.RATE_LIMITED }, state().retryAfter)
            }
        }

    @Test
    fun `R4 - unexpected exception is contained and logged by class name`() {
        val failing =
            object : WellnessRepository by repo {
                override suspend fun replaceRange(
                    from: LocalDate,
                    to: LocalDate,
                    days: List<WellnessDay>,
                    fetchedAt: Instant,
                ): Unit = error("leaky 61.5 detail")
            }
        source.result = success(*cached.toTypedArray())
        val logged = captureLogs { runBlocking { WellnessSync(source, failing, clock, TEST_ZONE).refreshIfDue() } }

        assertEquals(WellnessFetchStatus.UNAVAILABLE, state().lastStatus)
        assertTrue("IllegalStateException" in logged)
        assertFalse("61.5" in logged)
    }

    @Test
    fun `R3 - finish log has status and count only`() {
        source.result = success(wellnessDay(WELLNESS_TODAY, restingHr = 47, hrv = 63.25))
        val logged = captureLogs { runBlocking { sync.refreshIfDue() } }

        assertTrue("Wellness refresh finished: status=OK, days=1" in logged)
        assertFalse("47" in logged)
        assertFalse("63.25" in logged)
    }

    private fun captureLogs(block: () -> Unit): String {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            block()
        } finally {
            root.detachAppender(appender)
        }
        return appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
    }
}
