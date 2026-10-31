package app.stravadashboard.domain

import app.stravadashboard.domain.SyncPolicy.MAX_ACTIVITY_CALLS_PER_SYNC
import app.stravadashboard.domain.SyncPolicy.PAGE_SIZE
import app.stravadashboard.domain.SyncPolicy.failureOf
import app.stravadashboard.domain.SyncPolicy.pendingCount
import app.stravadashboard.domain.model.RUN_SPORT_TYPE
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.SyncStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

data class SyncInfo(
    val status: SyncStatus,
    val lastSyncedAt: Instant?,
    val retryAfter: Instant?,
    val runsPendingDetail: Int,
)

sealed interface SyncOutcome {
    data class Done(
        val info: SyncInfo,
    ) : SyncOutcome

    data object ReauthRequired : SyncOutcome
}

/**
 * Keeps the local run store in step with Strava: a one-time 5-month import, then incremental list passes,
 * detail calls, speed-stream calls for outdoor workouts and pruning. Only one sync runs at a time.
 * The rules for when and what to call live in [SyncPolicy].
 */
class SyncService(
    private val source: ActivitySource,
    private val runs: RunRepository,
    private val syncState: SyncStateRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    private val mutex = Mutex()

    // Guarded by [mutex]: how many sync attempts have finished, and the outcome of the last one.
    @Volatile private var finishedAttempts = 0L
    private var lastOutcome: SyncOutcome? = null

    /**
     * Syncs if due. A request that had to wait for another request's sync reuses that outcome instead of starting a
     * second sync straight after it (which, while an import is partial or Strava is down, would still be "due").
     */
    suspend fun syncIfDue(): SyncOutcome {
        val attemptsBeforeWaiting = finishedAttempts
        return mutex.withLock {
            val reusable = lastOutcome.takeIf { finishedAttempts != attemptsBeforeWaiting }
            reusable ?: syncOrReport()
        }
    }

    private suspend fun syncOrReport(): SyncOutcome {
        val now = clock.instant()
        val state = syncState.get()
        val pending = pendingCount(runs.findAll())
        if (!SyncPolicy.isDue(state, pending, now)) return SyncOutcome.Done(SyncPolicy.idleInfo(state, pending, now))

        return sync(now).also {
            lastOutcome = it
            finishedAttempts++
        }
    }

    private suspend fun sync(now: Instant): SyncOutcome {
        val failure = listPass(now) ?: activityCalls(now)
        if (failure != null) return recordFailure(failure)

        runs.pruneBefore(SyncPolicy.pruneCutoff(now, zone))
        val pending = pendingCount(runs.findAll())
        val status = if (pending > 0) SyncStatus.PARTIAL else SyncStatus.OK
        syncState.save(syncState.get().copy(lastSuccessfulSyncAt = now, rateLimitedUntil = null, lastStatus = status))
        log.info("Strava sync finished: status={}, runsPending={}", status, pending)
        return SyncOutcome.Done(SyncInfo(status, now, null, pending))
    }

    private suspend fun recordFailure(failure: SourceResult<Nothing>): SyncOutcome {
        val state = syncState.get()
        val pending = pendingCount(runs.findAll())
        log.info("Strava sync stopped: {}", failure::class.simpleName)
        return when (failure) {
            is SourceResult.RateLimited -> {
                syncState.save(state.copy(rateLimitedUntil = failure.until, lastStatus = SyncStatus.RATE_LIMITED))
                SyncOutcome.Done(SyncInfo(SyncStatus.RATE_LIMITED, state.lastSuccessfulSyncAt, failure.until, pending))
            }
            SourceResult.ReauthRequired -> SyncOutcome.ReauthRequired
            else -> {
                syncState.save(state.copy(lastStatus = SyncStatus.STRAVA_UNAVAILABLE))
                SyncOutcome.Done(SyncInfo(SyncStatus.STRAVA_UNAVAILABLE, state.lastSuccessfulSyncAt, null, pending))
            }
        }
    }

    /** Lists every page after the cursor; the cursor is only set once all pages have been read. */
    private suspend fun listPass(now: Instant): SourceResult<Nothing>? {
        val after = SyncPolicy.listAfter(syncState.get().listCursor, now, zone)
        var page = 1
        do {
            val result = source.listActivities(after, page++, PAGE_SIZE)
            if (result !is SourceResult.Success) return failureOf(result)
            storePage(result.value)
        } while (result.value.size == PAGE_SIZE)

        val cursor = runs.newestStartUtc() ?: SyncPolicy.backfillStart(now, zone)
        syncState.save(syncState.get().copy(listCursor = cursor))
        return null
    }

    private suspend fun storePage(items: List<ActivitySummary>) {
        val (stored, others) = items.partition { it.sportType == RUN_SPORT_TYPE }
        runs.upsertSummaries(stored)
        runs.delete(others.map { it.id })
    }

    /** Detail and stream calls, sharing one cap, in priority order. */
    private suspend fun activityCalls(now: Instant): SourceResult<Nothing>? {
        var remaining = MAX_ACTIVITY_CALLS_PER_SYNC
        for (phase in SyncPhase.entries) {
            val calls = SyncPolicy.plan(phase, runs.findAll(), now).take(remaining)
            remaining -= calls.size
            val failure = calls.firstNotNullOfOrNull { execute(it, now) }
            if (failure != null) return failure
        }
        return null
    }

    private suspend fun execute(
        call: ActivityCall,
        now: Instant,
    ): SourceResult<Nothing>? {
        val workout = call.streamFor
        return if (workout != null) fetchStream(call.run, workout, now) else fetchDetail(call.run, now)
    }

    /** A 404 means the activity was deleted on Strava, so it is removed locally too. */
    private suspend fun fetchDetail(
        run: RunActivity,
        now: Instant,
    ): SourceResult<Nothing>? =
        when (val result = source.activityDetail(run.id)) {
            is SourceResult.Success -> null.also { runs.saveDetail(run.id, result.value, now) }
            SourceResult.NotFound -> null.also { runs.delete(listOf(run.id)) }
            else -> failureOf(result)
        }

    /** Computes the rep pace in memory; only the derived result is stored. */
    private suspend fun fetchStream(
        run: RunActivity,
        workout: RunType.Workout,
        now: Instant,
    ): SourceResult<Nothing>? {
        val stored =
            when (val result = source.speedStream(run.id)) {
                is SourceResult.Success -> {
                    val pace = StreamRepPaceFinder.find(result.value, workout)
                    StreamPaceResult(workout.key, StreamPaceStatus.COMPUTED, pace)
                }
                SourceResult.NotFound -> StreamPaceResult(workout.key, StreamPaceStatus.UNAVAILABLE, null)
                else -> return failureOf(result)
            }
        runs.saveStreamPace(run.id, stored, now)
        return null
    }

    private companion object {
        val log = LoggerFactory.getLogger(SyncService::class.java)
    }
}
