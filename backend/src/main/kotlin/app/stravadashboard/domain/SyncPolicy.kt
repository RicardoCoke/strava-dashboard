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

/** A detail call, a speed-streams call when [streamFor] is set, or a heart-rate streams call when [heartRate]. */
internal data class ActivityCall(
    val run: RunActivity,
    val streamFor: RunType.Workout? = null,
    val heartRate: Boolean = false,
)

/** Activity-call phases, in priority order. They share one per-sync cap. */
internal enum class SyncPhase { MISSING_DETAILS, MISSING_STREAMS, MISSING_HR_STREAMS, RECHECKS }

/** A run with heart rate whose heart-rate stream hasn't been fetched yet (feature 005). */
internal fun needsHrStream(run: RunActivity): Boolean = run.averageHeartRateBpm != null && run.hrHistogram == null

/** The pure rules of [SyncService]: when a sync is due and which calls it makes. */
internal object SyncPolicy {
    const val MAX_ACTIVITY_CALLS_PER_SYNC = 80
    const val PAGE_SIZE = 200

    /** Runs are kept for the 6-month training load window plus 41 warm-up days for its 42-day average. */
    const val HISTORY_MONTHS = 6L
    const val LOAD_WARMUP_DAYS = 41L
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

    /** Runs waiting for a detail call, outdoor workouts waiting for a streams call, or runs waiting for HR streams. */
    fun pendingCount(all: List<RunActivity>): Int =
        all.count { it.detailFetchedAt == null || RepPaceCalculator.needsStream(it) || needsHrStream(it) }

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
            SyncPhase.MISSING_HR_STREAMS ->
                newestFirst.filter(::needsHrStream).map { ActivityCall(it, heartRate = true) }
            SyncPhase.RECHECKS -> newestFirst.filter { isRecheckDue(it, now) }.map { ActivityCall(it) }
        }
    }

    /**
     * `after` for the list call: the backfill start until an import from the history start has finished, then the
     * cursor or 7 days ago. A store imported with a shorter history ([SyncState.historyStart] null, from before
     * feature 005) is re-listed once from the backfill start.
     */
    fun listAfter(
        state: SyncState,
        now: Instant,
        zone: ZoneId,
    ): Instant {
        val required = backfillStart(now, zone)
        val cursor = state.listCursor
        val historyImported = state.historyStart?.let { it <= required } ?: false
        return if (cursor != null && historyImported) minOf(cursor, now.minus(RECHECK_WINDOW)) else required
    }

    /** The first local date kept: 6 months and 41 days before today. */
    fun historyStart(today: LocalDate): LocalDate = today.minusMonths(HISTORY_MONTHS).minusDays(LOAD_WARMUP_DAYS)

    fun backfillStart(
        now: Instant,
        zone: ZoneId,
    ): Instant =
        historyStart(LocalDate.ofInstant(now, zone))
            .minusDays(1)
            .atStartOfDay(zone)
            .toInstant()

    fun pruneCutoff(
        now: Instant,
        zone: ZoneId,
    ): LocalDate = historyStart(LocalDate.ofInstant(now, zone))

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
