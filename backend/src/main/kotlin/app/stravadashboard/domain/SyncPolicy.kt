package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A detail call, or a streams call when [streamFor] is set. */
internal data class ActivityCall(
    val run: RunActivity,
    val streamFor: RunType.Workout? = null,
)

/** Activity-call phases, in priority order. They share one per-sync cap. */
internal enum class SyncPhase { MISSING_DETAILS, MISSING_STREAMS, RECHECKS }

/** The pure rules of [SyncService]: when a sync is due and which calls it makes. */
internal object SyncPolicy {
    const val MAX_ACTIVITY_CALLS_PER_SYNC = 80
    const val PAGE_SIZE = 200
    const val BACKFILL_MONTHS = 5L
    const val RETENTION_MONTHS = 6L
    val SYNC_INTERVAL: Duration = Duration.ofMinutes(15)
    val RECHECK_WINDOW: Duration = Duration.ofDays(7)
    val RECHECK_MIN_AGE: Duration = Duration.ofHours(1)

    fun isDue(
        state: SyncState,
        pending: Int,
        now: Instant,
    ): Boolean {
        val rateLimited = state.rateLimitedUntil?.let { now < it } ?: false
        val stale = state.lastSuccessfulSyncAt?.let { Duration.between(it, now) >= SYNC_INTERVAL } ?: true
        return !rateLimited && (stale || pending > 0 || state.listCursor == null)
    }

    /** Status reported when no sync runs for this request. */
    fun idleInfo(
        state: SyncState,
        pending: Int,
        now: Instant,
    ): SyncInfo {
        val until = state.rateLimitedUntil?.takeIf { now < it }
        val status =
            when {
                until != null -> SyncStatus.RATE_LIMITED
                pending > 0 -> SyncStatus.PARTIAL
                else -> SyncStatus.OK
            }
        return SyncInfo(status, state.lastSuccessfulSyncAt, until, pending)
    }

    /** Runs still waiting for a detail call, or outdoor workouts waiting for a streams call. */
    fun pendingCount(all: List<RunActivity>): Int =
        all.count { it.detailFetchedAt == null || RepPaceCalculator.needsStream(it) }

    fun plan(
        phase: SyncPhase,
        all: List<RunActivity>,
        now: Instant,
    ): List<ActivityCall> {
        val newestFirst = all.sortedByDescending { it.startUtc }
        return when (phase) {
            SyncPhase.MISSING_DETAILS -> newestFirst.filter { it.detailFetchedAt == null }.map { ActivityCall(it) }
            SyncPhase.MISSING_STREAMS ->
                newestFirst.filter(RepPaceCalculator::needsStream).mapNotNull { run ->
                    (RunClassifier.classify(run) as? RunType.Workout)?.let { ActivityCall(run, it) }
                }
            SyncPhase.RECHECKS -> newestFirst.filter { isRecheckDue(it, now) }.map { ActivityCall(it) }
        }
    }

    /** `after` for the list call: the backfill start until an import has finished, then cursor or 7 days ago. */
    fun listAfter(
        listCursor: Instant?,
        now: Instant,
        zone: ZoneId,
    ): Instant = listCursor?.let { minOf(it, now.minus(RECHECK_WINDOW)) } ?: backfillStart(now, zone)

    fun backfillStart(
        now: Instant,
        zone: ZoneId,
    ): Instant =
        LocalDate
            .ofInstant(now, zone)
            .minusMonths(BACKFILL_MONTHS)
            .minusDays(1)
            .atStartOfDay(zone)
            .toInstant()

    fun pruneCutoff(
        now: Instant,
        zone: ZoneId,
    ): LocalDate = LocalDate.ofInstant(now, zone).minusMonths(RETENTION_MONTHS)

    /** Collapses any non-success result into the failure that stops a sync. */
    fun failureOf(result: SourceResult<*>): SourceResult<Nothing> =
        when (result) {
            is SourceResult.RateLimited -> result
            SourceResult.ReauthRequired -> SourceResult.ReauthRequired
            else -> SourceResult.Unavailable
        }

    private fun isRecheckDue(
        run: RunActivity,
        now: Instant,
    ): Boolean {
        val fetchedAt = run.detailFetchedAt ?: return false
        return run.startUtc >= now.minus(RECHECK_WINDOW) && Duration.between(fetchedAt, now) > RECHECK_MIN_AGE
    }
}
