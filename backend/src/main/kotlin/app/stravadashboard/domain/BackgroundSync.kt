package app.stravadashboard.domain

import app.stravadashboard.domain.SyncPolicy.pendingCount
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.domain.wellness.WellnessSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/** The sync status for the dashboard: [nextSyncIn] is how long until a start would sync, null while one runs. */
data class SyncSnapshot(
    val info: SyncInfo,
    val nextSyncIn: Duration?,
)

/**
 * Runs [SyncService] as a background job in [scope], so the read endpoints never wait for the source (feature 009).
 * At most one job runs at a time, across tabs.
 */
@Suppress("LongParameterList")
class BackgroundSync(
    private val syncService: SyncService,
    private val runs: RunRepository,
    private val syncState: SyncStateRepository,
    private val budget: CallBudget,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val activitiesFrom: LocalDate,
    /** Refreshes resting HR and HRV after the activity step (feature 015). */
    private val wellness: WellnessSync? = null,
) {
    private val running = AtomicBoolean(false)

    @Volatile private var job: Job? = null

    suspend fun status(): SyncSnapshot {
        val now = clock.instant()
        val state = syncState.get()
        val pending = pendingCount(runs.findAll(), activitiesFrom)
        if (running.get()) {
            return SyncSnapshot(SyncInfo(SyncStatus.SYNCING, state.lastSuccessfulSyncAt, null, pending), null)
        }
        val dueAt = SyncSchedule.nextDueAt(state, pending, budget.view(), now)
        val wait = Duration.between(now, dueAt).takeIf { !it.isNegative } ?: Duration.ZERO
        return SyncSnapshot(SyncSchedule.idleInfo(state, pending, now), wait)
    }

    /** Starts a job if a sync is due and none is running, and returns at once. */
    suspend fun startIfDue(): SyncSnapshot {
        val idle = status()
        // nextSyncIn is null while a job runs, so "due" also means none was running a moment ago.
        val due = idle.nextSyncIn == Duration.ZERO
        if (!due || !running.compareAndSet(false, true)) return if (due) status() else idle
        job = scope.launch { runJob() }
        // Built from the state already read: the job may finish before another read, and a start must say "syncing".
        return SyncSnapshot(idle.info.copy(status = SyncStatus.SYNCING, retryAfter = null), null)
    }

    private suspend fun runJob() {
        try {
            activityStep()
            // After the activity step, whatever its outcome; catches its own failures (feature 015, R3 and R4).
            wellness?.refreshIfDue()
        } finally {
            running.set(false)
        }
    }

    /**
     * [SyncService] handles source failures itself; this is the last net for anything else (a locked database, say),
     * so the job never ends without a status and only the exception's class name is logged (AC5.4).
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun activityStep() {
        try {
            syncService.syncIfDue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Background sync failed unexpectedly: {}", e::class.simpleName)
            recordUnavailable()
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun recordUnavailable() {
        try {
            val now = clock.instant()
            syncState.save(syncState.get().copy(lastStatus = SyncStatus.SOURCE_UNAVAILABLE, lastAttemptAt = now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not record the failed sync: {}", e::class.simpleName)
        }
    }

    /** Waits for the running job, if any (tests and shutdown). */
    internal suspend fun join() {
        job?.join()
    }

    private companion object {
        val log = LoggerFactory.getLogger(BackgroundSync::class.java)
    }
}
