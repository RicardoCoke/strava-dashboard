package app.stravadashboard.domain

import app.stravadashboard.api.toDto
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.testsupport.NOW
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Feature 009, R3: when the next sync is due. */
class SyncScheduleTest {
    private val fullBudget = BudgetView(80, NOW.plus(Duration.ofMinutes(15)))
    private val synced = SyncState(listCursor = NOW, lastStatus = SyncStatus.OK)

    private fun minutesAgo(minutes: Long): Instant = NOW.minus(Duration.ofMinutes(minutes))

    private fun nextDue(
        state: SyncState,
        pending: Int = 0,
        budget: BudgetView = fullBudget,
    ): Instant = SyncSchedule.nextDueAt(state, pending, budget, NOW)

    @Test
    fun `R3 - not due within 15 minutes and next sync in 60 seconds`() {
        val state = synced.copy(lastSuccessfulSyncAt = minutesAgo(14), lastAttemptAt = minutesAgo(14))
        assertFalse(SyncSchedule.isDue(state, 0, fullBudget, NOW))
        assertEquals(NOW.plusSeconds(60), nextDue(state))
        assertTrue(SyncSchedule.isDue(state.copy(lastSuccessfulSyncAt = minutesAgo(15)), 0, fullBudget, NOW))
    }

    @Test
    fun `R3 - due immediately without a completed list pass`() {
        assertEquals(NOW, nextDue(SyncState()))
        assertEquals(NOW, nextDue(SyncState(lastSuccessfulSyncAt = minutesAgo(1))))
    }

    @Test
    fun `R3 - due immediately with pending runs and budget left`() {
        assertEquals(NOW, nextDue(synced.copy(lastSuccessfulSyncAt = minutesAgo(1)), pending = 12))
    }

    @Test
    fun `R3 - after strava unavailable next sync waits 15 minutes`() {
        val state =
            synced.copy(
                lastSuccessfulSyncAt = minutesAgo(60),
                lastStatus = SyncStatus.STRAVA_UNAVAILABLE,
                lastAttemptAt = minutesAgo(5),
            )
        assertEquals(NOW.plus(Duration.ofMinutes(10)), nextDue(state, pending = 30))
        assertEquals(SyncStatus.STRAVA_UNAVAILABLE, SyncSchedule.idleInfo(state, 30, NOW).status)
    }

    @Test
    fun `R3 - rate limited sync waits until retry after`() {
        val until = NOW.plus(Duration.ofMinutes(7))
        val state = synced.copy(lastSuccessfulSyncAt = minutesAgo(30), rateLimitedUntil = until)
        assertEquals(until, nextDue(state, pending = 5))
        assertEquals(SyncStatus.RATE_LIMITED, SyncSchedule.idleInfo(state, 5, NOW).status)
    }

    @Test
    fun `R4 - exhausted budget makes next sync due at next window`() {
        val windowEnd = NOW.plus(Duration.ofMinutes(15))
        assertEquals(
            windowEnd,
            nextDue(synced.copy(lastSuccessfulSyncAt = minutesAgo(1)), 40, BudgetView(0, windowEnd)),
        )
    }

    @Test
    fun `R3 - next sync seconds rounded up and never negative`() {
        val info = SyncInfo(SyncStatus.OK, NOW, null, 0)
        assertEquals(60L, SyncSnapshot(info, Duration.ofMillis(59_001)).toDto().nextSyncInSeconds)
        assertEquals(0L, SyncSnapshot(info, Duration.ZERO).toDto().nextSyncInSeconds)
        assertEquals(null, SyncSnapshot(info.copy(status = SyncStatus.SYNCING), null).toDto().nextSyncInSeconds)
    }
}
