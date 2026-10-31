package app.stravadashboard.domain

import app.stravadashboard.domain.SyncPolicy.pendingCount
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/** The sync status for the dashboard: [nextSyncIn] is how long until a start would sync, null while one runs. */
data class SyncSnapshot(
    val info: SyncInfo,
    val nextSyncIn: Duration?,
)

/**
 * Runs [SyncService] as a background job in [scope], so the read endpoints never wait for Strava (feature 009).
 * At most one job runs at a time, across tabs.
 */
class BackgroundSync(
    private val syncService: SyncService,
    private val runs: RunRepository,
    private val syncState: SyncStateRepository,
    private val budget: StravaCallBudget,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private val running = AtomicBoolean(false)

    @Volatile private var job: Job? = null

    /** Set when the last job ended because the Strava connection was revoked; cleared when a new job starts. */
    @Volatile var reauthRequired: Boolean = false
        private set

    suspend fun status(): SyncSnapshot {
        val now = clock.instant()
        val state = syncState.get()
        val pending = pendingCount(runs.findAll())
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
        reauthRequired = false
        job =
            scope.launch {
                try {
                    reauthRequired = syncService.syncIfDue() == SyncOutcome.ReauthRequired
                } finally {
                    running.set(false)
                }
            }
        return status()
    }

    /** Waits for the running job, if any (tests and shutdown). */
    internal suspend fun join() {
        job?.join()
    }
}
