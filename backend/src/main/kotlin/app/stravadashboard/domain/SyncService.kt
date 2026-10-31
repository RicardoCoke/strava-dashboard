package app.stravadashboard.domain

import app.stravadashboard.domain.SyncPolicy.MAX_ACTIVITY_CALLS_PER_SYNC
import app.stravadashboard.domain.SyncPolicy.PAGE_SIZE
import app.stravadashboard.domain.SyncPolicy.failureOf
import app.stravadashboard.domain.SyncPolicy.pendingCount
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.RUN_SPORT_TYPE
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.domain.trainingload.HrHistogramBuilder
import kotlinx.coroutines.CancellationException
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

/** How a list pass ended: every page read, the call budget used up, or a failure that stops the sync. */
private sealed interface ListPass {
    data object Complete : ListPass

    data object OutOfBudget : ListPass

    data class Failed(
        val failure: SourceResult<Nothing>,
    ) : ListPass
}

/**
 * Keeps the local run store in step with Strava: a one-time import from the history start (6 months + 41 days), then
 * incremental list passes, detail calls, speed-stream calls for outdoor workouts, heart-rate stream calls for runs
 * with heart rate, and pruning. Only one sync runs at a time, and every Strava call is taken from [budget].
 * The rules for when and what to call live in [SyncPolicy].
 */
class SyncService(
    private val source: ActivitySource,
    private val runs: RunRepository,
    private val syncState: SyncStateRepository,
    private val clock: Clock,
    private val zone: ZoneId,
    private val budget: StravaCallBudget = StravaCallBudget(clock),
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
            lastOutcome.takeIf { finishedAttempts != attemptsBeforeWaiting } ?: syncNowIfDue()
        }
    }

    private suspend fun syncNowIfDue(): SyncOutcome {
        val now = clock.instant()
        val state = syncState.get()
        val pending = pendingCount(runs.findAll())
        if (!SyncSchedule.isDue(state, pending, budget.view(), now)) {
            return SyncOutcome.Done(SyncSchedule.idleInfo(state, pending, now))
        }
        return sync(now).also {
            lastOutcome = it
            finishedAttempts++
        }
    }

    /** An unexpected error counts as Strava being unavailable; only its class name is logged (feature 009, AC5.4). */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun sync(now: Instant): SyncOutcome =
        try {
            when (val listed = listPass(now)) {
                is ListPass.Failed -> recordFailure(listed.failure, now)
                ListPass.OutOfBudget -> recordSuccess(now, outOfBudget = true)
                ListPass.Complete -> activityCalls(now)?.let { recordFailure(it, now) } ?: recordSuccess(now)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Strava sync failed unexpectedly: {}", e::class.simpleName)
            recordFailure(SourceResult.Unavailable, now)
        }

    /** Out of budget, the sync stops early and counts as partial; the next window carries on (feature 009, AC4.5). */
    private suspend fun recordSuccess(
        now: Instant,
        outOfBudget: Boolean = false,
    ): SyncOutcome {
        runs.pruneBefore(SyncPolicy.pruneCutoff(now, zone))
        val pending = pendingCount(runs.findAll())
        val status = if (outOfBudget || pending > 0) SyncStatus.PARTIAL else SyncStatus.OK
        syncState.save(
            syncState.get().copy(
                lastSuccessfulSyncAt = now,
                rateLimitedUntil = null,
                lastStatus = status,
                lastAttemptAt = now,
            ),
        )
        log.info("Strava sync finished: status={}, runsPending={}", status, pending)
        return SyncOutcome.Done(SyncInfo(status, now, null, pending))
    }

    private suspend fun recordFailure(
        failure: SourceResult<Nothing>,
        now: Instant,
    ): SyncOutcome {
        val state = syncState.get()
        val pending = pendingCount(runs.findAll())
        log.info("Strava sync stopped: {}", failure::class.simpleName)
        return when (failure) {
            is SourceResult.RateLimited -> {
                val saved = state.copy(rateLimitedUntil = failure.until, lastStatus = SyncStatus.RATE_LIMITED)
                syncState.save(saved.copy(lastAttemptAt = now))
                SyncOutcome.Done(SyncInfo(SyncStatus.RATE_LIMITED, state.lastSuccessfulSyncAt, failure.until, pending))
            }
            SourceResult.ReauthRequired -> SyncOutcome.ReauthRequired
            else -> {
                syncState.save(state.copy(lastStatus = SyncStatus.STRAVA_UNAVAILABLE, lastAttemptAt = now))
                SyncOutcome.Done(SyncInfo(SyncStatus.STRAVA_UNAVAILABLE, state.lastSuccessfulSyncAt, null, pending))
            }
        }
    }

    /**
     * Lists every page after the cursor; the cursor (and, for a pass from the history start, [SyncState.historyStart])
     * is only set once all pages have been read.
     */
    private suspend fun listPass(now: Instant): ListPass {
        val backfillStart = SyncPolicy.backfillStart(now, zone)
        val after = SyncPolicy.listAfter(syncState.get(), now, zone)
        var page = 1
        var stop: ListPass? = null
        do {
            val result = if (budget.tryConsume()) source.listActivities(after, page++, PAGE_SIZE) else null
            val listed = (result as? SourceResult.Success)?.value
            if (listed == null) {
                stop = result?.let { ListPass.Failed(failureOf(it)) } ?: ListPass.OutOfBudget
                break
            }
            val (stored, others) = listed.partition { it.sportType == RUN_SPORT_TYPE }
            runs.upsertSummaries(stored)
            runs.delete(others.map { it.id })
        } while (listed.size == PAGE_SIZE)
        stop?.let { return it }

        val cursor = runs.newestStartUtc() ?: backfillStart
        val state = syncState.get()
        val historyStart = if (after == backfillStart) after else state.historyStart
        syncState.save(state.copy(listCursor = cursor, historyStart = historyStart))
        return ListPass.Complete
    }

    /** Detail and stream calls, sharing one cap and what is left of the budget, in priority order. */
    private suspend fun activityCalls(now: Instant): SourceResult<Nothing>? {
        var remaining = minOf(MAX_ACTIVITY_CALLS_PER_SYNC, budget.remaining())
        for (phase in SyncPhase.entries) {
            val calls = SyncPolicy.plan(phase, runs.findAll(), now).take(remaining)
            remaining -= calls.size
            val failure =
                calls.firstNotNullOfOrNull { call ->
                    budget.tryConsume()
                    val workout = call.streamFor
                    when {
                        call.heartRate -> fetchHrStream(call.run, now)
                        workout != null -> fetchStream(call.run, workout, now)
                        else -> fetchDetail(call.run, now)
                    }
                }
            if (failure != null) return failure
        }
        return null
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

    /** Reduces the heart-rate stream to a histogram in memory; only the histogram is stored. */
    private suspend fun fetchHrStream(
        run: RunActivity,
        now: Instant,
    ): SourceResult<Nothing>? {
        val stored =
            when (val result = source.heartRateStream(run.id)) {
                is SourceResult.Success ->
                    HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogramBuilder.build(result.value))
                SourceResult.NotFound -> HrHistogramResult(HrHistogramStatus.UNAVAILABLE, null)
                else -> return failureOf(result)
            }
        runs.saveHrHistogram(run.id, stored, now)
        return null
    }

    private companion object {
        val log = LoggerFactory.getLogger(SyncService::class.java)
    }
}
