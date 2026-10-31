package app.stravadashboard.domain

import app.stravadashboard.domain.SyncPolicy.SYNC_INTERVAL
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStatus
import java.time.Instant

/** The pure rules for when the next sync may start and what status to report while none runs (feature 009). */
internal object SyncSchedule {
    /**
     * The instant the next sync may start (feature 009): 15 minutes after Strava was unreachable, straight away without
     * a full list pass or with runs pending, otherwise 15 minutes after the last successful sync; never before the
     * rate-limit retry time or, with the call budget used up, before the next budget window.
     */
    fun nextDueAt(
        state: SyncState,
        pending: Int,
        budget: BudgetView,
        now: Instant,
    ): Instant {
        val base =
            when {
                state.lastStatus == SyncStatus.STRAVA_UNAVAILABLE -> state.lastAttemptAt?.plus(SYNC_INTERVAL) ?: now
                state.listCursor == null || pending > 0 -> now
                else -> state.lastSuccessfulSyncAt?.plus(SYNC_INTERVAL) ?: now
            }
        val afterRateLimit = state.rateLimitedUntil?.takeIf { it > now }?.let { maxOf(base, it) } ?: base
        return if (budget.remaining <= 0) maxOf(afterRateLimit, budget.windowEnd) else afterRateLimit
    }

    fun isDue(
        state: SyncState,
        pending: Int,
        budget: BudgetView,
        now: Instant,
    ): Boolean = now >= nextDueAt(state, pending, budget, now)

    /** Status reported when no sync is running. */
    fun idleInfo(
        state: SyncState,
        pending: Int,
        now: Instant,
    ): SyncInfo {
        val until = state.rateLimitedUntil?.takeIf { now < it }
        val status =
            when {
                until != null -> SyncStatus.RATE_LIMITED
                state.lastStatus == SyncStatus.STRAVA_UNAVAILABLE -> SyncStatus.STRAVA_UNAVAILABLE
                pending > 0 -> SyncStatus.PARTIAL
                else -> SyncStatus.OK
            }
        return SyncInfo(status, state.lastSuccessfulSyncAt, until, pending)
    }
}
