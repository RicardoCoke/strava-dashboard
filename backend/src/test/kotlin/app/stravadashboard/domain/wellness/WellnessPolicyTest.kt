package app.stravadashboard.domain.wellness

import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.WELLNESS_TODAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WellnessPolicyTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")

    @Test
    fun `R3 - refresh due after 15 minutes and not before retryAfter`() {
        assertTrue(WellnessPolicy.isDue(WellnessState(), now))
        assertFalse(WellnessPolicy.isDue(WellnessState(lastAttemptAt = now.minus(Duration.ofMinutes(14))), now))
        assertTrue(WellnessPolicy.isDue(WellnessState(lastAttemptAt = now.minus(Duration.ofMinutes(15))), now))
        val limited = WellnessState(lastAttemptAt = now.minus(Duration.ofHours(1)), retryAfter = now.plusSeconds(1))
        assertFalse(WellnessPolicy.isDue(limited, now))
        assertTrue(WellnessPolicy.isDue(limited.copy(retryAfter = now), now))
    }

    @Test
    fun `R3 - first fetch backfills 121 days`() {
        val range = WellnessPolicy.fetchRange(WellnessState(), WELLNESS_TODAY, TEST_ZONE)
        assertEquals(LocalDate.of(2026, 7, 3), range.start)
        assertEquals(WELLNESS_TODAY, range.endInclusive)
    }

    @Test
    fun `R3 - later fetch starts 6 days before last success`() {
        fun oldest(lastSuccess: String) =
            WellnessPolicy
                .fetchRange(
                    WellnessState(lastSuccessAt = Instant.parse(lastSuccess), historyDays = 120),
                    WELLNESS_TODAY,
                    TEST_ZONE,
                ).start

        assertEquals(LocalDate.of(2026, 10, 24), oldest("2026-10-30T06:00:00Z"))
        assertEquals(LocalDate.of(2026, 8, 26), oldest("2026-09-01T06:00:00Z"))
        assertEquals(LocalDate.of(2026, 7, 3), oldest("2026-03-01T06:00:00Z"))
    }

    @Test
    fun `R4 - full 120-day fetch until history is backfilled`() {
        val lastSuccess = Instant.parse("2026-10-30T06:00:00Z")

        fun oldest(historyDays: Int?) =
            WellnessPolicy
                .fetchRange(
                    WellnessState(lastSuccessAt = lastSuccess, historyDays = historyDays),
                    WELLNESS_TODAY,
                    TEST_ZONE,
                ).start

        assertEquals(LocalDate.of(2026, 7, 3), oldest(null))
        assertEquals(LocalDate.of(2026, 7, 3), oldest(90))
        assertEquals(LocalDate.of(2026, 10, 24), oldest(120))
    }
}
