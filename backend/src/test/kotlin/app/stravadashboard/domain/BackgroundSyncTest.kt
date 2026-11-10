package app.stravadashboard.domain

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.domain.ports.WellnessSource
import app.stravadashboard.domain.wellness.WellnessFetchResult
import app.stravadashboard.domain.wellness.WellnessFetchStatus
import app.stravadashboard.domain.wellness.WellnessSync
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.testsupport.FakeActivitySource
import app.stravadashboard.testsupport.FakeWellnessSource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.NO_CUT_OVER
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fake Strava whose list call waits for [release], so a test can look at a sync while it runs. */
class GatedActivitySource(
    private val fake: FakeActivitySource = FakeActivitySource(),
) : ActivitySource by fake {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val calls: List<String> get() = fake.calls

    override suspend fun listActivities(
        after: Instant,
        page: Int,
        perPage: Int,
    ) = fake.listActivities(after, page, perPage).also {
        entered.complete(Unit)
        release.await()
    }

    override suspend fun activityDetail(id: ActivityId) = fake.activityDetail(id)

    fun stored(vararg ids: Long) {
        fake.activities = ids.map { summary(it, NOW.minus(Duration.ofDays(it))) }
    }

    fun failWith(failure: SourceResult<Nothing>) {
        fake.failFromCall = 1
        fake.failure = failure
    }
}

class BackgroundSyncTest {
    private val database = DatabaseFactory.connect(tempDbPath())
    private val runs = ExposedRunRepository(database)
    private val syncState = ExposedSyncStateRepository(database)
    private val clock = MutableClock()
    private val budget = CallBudget(clock)
    private val source = GatedActivitySource()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sync =
        BackgroundSync(
            SyncService(source, runs, syncState, clock, TEST_ZONE, NO_CUT_OVER, budget),
            runs,
            syncState,
            budget,
            clock,
            scope,
            NO_CUT_OVER,
        )

    @AfterTest
    fun stop() = scope.cancel()

    private fun <T> run(block: suspend () -> T): T = runBlocking { withTimeout(TIMEOUT_MS) { block() } }

    @Test
    fun `R2 - post sync starts a background job and returns syncing at once`() =
        run {
            source.stored(1)
            val started = sync.startIfDue()

            assertEquals(SyncStatus.SYNCING, started.info.status)
            assertNull(started.nextSyncIn)
            source.entered.await()
            assertEquals(listOf("list:1"), source.calls)
            source.release.complete(Unit)
            sync.join()
        }

    @Test
    fun `R2 - second start while running does not start another job`() =
        run {
            source.stored(1)
            sync.startIfDue()
            source.entered.await()

            assertEquals(SyncStatus.SYNCING, sync.startIfDue().info.status)
            source.release.complete(Unit)
            sync.join()

            assertEquals(1, source.calls.count { it.startsWith("list") })
        }

    @Test
    fun `R2 - start when not due returns idle status`() =
        run {
            syncState.save(SyncState(listCursor = NOW, lastSuccessfulSyncAt = NOW, lastStatus = SyncStatus.OK))
            clock.advance(Duration.ofMinutes(5))

            val idle = sync.startIfDue()

            assertEquals(SyncStatus.OK, idle.info.status)
            assertEquals(Duration.ofMinutes(10), idle.nextSyncIn)
            assertEquals(idle, sync.status())
            assertTrue(source.calls.isEmpty())
        }

    @Test
    fun `R2 - status is syncing while running and finished status afterwards`() =
        run {
            source.stored(1, 2)
            runs.upsertSummaries(listOf(1L, 2L).map { summary(it, NOW.minus(Duration.ofDays(it))) })
            sync.startIfDue()
            source.entered.await()
            assertEquals(SyncStatus.SYNCING, sync.status().info.status)
            assertEquals(2, sync.status().info.runsPendingDetail)

            source.release.complete(Unit)
            sync.join()

            val finished = sync.status()
            assertEquals(SyncStatus.OK, finished.info.status)
            assertEquals(NOW, finished.info.lastSyncedAt)
            assertEquals(Duration.ofMinutes(15), finished.nextSyncIn)
        }

    @Test
    fun `R2 - cancelling the scope cancels the running job`() =
        run {
            source.stored(1)
            sync.startIfDue()
            source.entered.await()

            scope.cancel()
            sync.join()

            assertFalse(SyncStatus.SYNCING == sync.status().info.status)
            assertTrue(source.calls.none { it.startsWith("detail") })
        }

    @Test
    fun `021 R8 - an unavailable source ends the job with garminUnavailable`() =
        run {
            source.release.complete(Unit)
            source.failWith(SourceResult.Unavailable)

            sync.startIfDue()
            sync.join()

            assertEquals(SyncStatus.SOURCE_UNAVAILABLE, sync.status().info.status)
        }

    private val wellnessRepo = ExposedWellnessRepository(database)
    private val wellnessSource = FakeWellnessSource()

    private fun syncWithWellness(source: ActivitySource = this.source) =
        BackgroundSync(
            SyncService(source, runs, syncState, clock, TEST_ZONE, NO_CUT_OVER, budget),
            runs,
            syncState,
            budget,
            clock,
            scope,
            NO_CUT_OVER,
            WellnessSync(wellnessSource, wellnessRepo, clock, TEST_ZONE),
        )

    @Test
    fun `015 R3 - wellness refresh runs after the activity step with any outcome`() =
        run {
            val done = syncWithWellness()
            source.release.complete(Unit)
            done.startIfDue()
            done.join()
            assertEquals(1, wellnessSource.ranges.size)
            assertEquals(WellnessFetchStatus.OK, wellnessRepo.getState().lastStatus)

            clock.advance(Duration.ofMinutes(15))
            source.failWith(SourceResult.Unavailable)
            done.startIfDue()
            done.join()
            assertEquals(SyncStatus.SOURCE_UNAVAILABLE, syncState.get().lastStatus)
            assertEquals(2, wellnessSource.ranges.size)

            clock.advance(Duration.ofMinutes(15))
            val throwing =
                object : ActivitySource by FakeActivitySource() {
                    override suspend fun listActivities(
                        after: Instant,
                        page: Int,
                        perPage: Int,
                    ): SourceResult<List<ActivitySummary>> = error("invented")
                }
            val failed = syncWithWellness(throwing)
            failed.startIfDue()
            failed.join()
            assertEquals(3, wellnessSource.ranges.size)
            assertFalse(SyncStatus.SYNCING == failed.status().info.status)
        }

    @Test
    fun `015 R3 - status stays syncing until the wellness step is done`() =
        run {
            val gate = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val slow =
                object : WellnessSource by wellnessSource {
                    override suspend fun fetch(
                        oldest: LocalDate,
                        newest: LocalDate,
                    ): WellnessFetchResult {
                        entered.complete(Unit)
                        gate.await()
                        return wellnessSource.fetch(oldest, newest)
                    }
                }
            val sync =
                BackgroundSync(
                    SyncService(source, runs, syncState, clock, TEST_ZONE, NO_CUT_OVER, budget),
                    runs,
                    syncState,
                    budget,
                    clock,
                    scope,
                    NO_CUT_OVER,
                    WellnessSync(slow, wellnessRepo, clock, TEST_ZONE),
                )
            source.release.complete(Unit)
            sync.startIfDue()
            entered.await()
            assertEquals(SyncStatus.SYNCING, sync.status().info.status)
            gate.complete(Unit)
            sync.join()
            assertEquals(SyncStatus.OK, sync.status().info.status)
        }

    @Test
    fun `015 R4 - wellness failure leaves the activity sync state untouched`() =
        run {
            source.release.complete(Unit)
            source.stored(1)
            wellnessSource.result = WellnessFetchResult.KeyRejected
            val withWellness = syncWithWellness()
            withWellness.startIfDue()
            withWellness.join()

            assertEquals(WellnessFetchStatus.KEY_REJECTED, wellnessRepo.getState().lastStatus)
            assertEquals(listOf(1L), runs.findAll().map { it.id.value })
            assertEquals(SyncStatus.OK, syncState.get().lastStatus)
            assertEquals(NOW, syncState.get().lastSuccessfulSyncAt)
            assertEquals(SyncStatus.OK, withWellness.status().info.status)
        }

    /** The activity sync state after the same one-run sync in a separate database, with no wellness step. */
    private suspend fun plainSyncState(): SyncState {
        val database = DatabaseFactory.connect(tempDbPath())
        val plainRuns = ExposedRunRepository(database)
        val plainState = ExposedSyncStateRepository(database)
        val plainBudget = CallBudget(clock)
        val plainSource =
            GatedActivitySource().apply {
                release.complete(Unit)
                stored(1)
            }
        val plain =
            BackgroundSync(
                SyncService(plainSource, plainRuns, plainState, clock, TEST_ZONE, NO_CUT_OVER, plainBudget),
                plainRuns,
                plainState,
                plainBudget,
                clock,
                scope,
                NO_CUT_OVER,
            )
        plain.startIfDue()
        plain.join()
        return plainState.get()
    }

    @Test
    fun `015 R4 - a throwing wellness step leaves the activity sync state untouched`() =
        run {
            source.release.complete(Unit)
            source.stored(1)
            val throwing =
                object : WellnessSource {
                    override suspend fun fetch(
                        oldest: LocalDate,
                        newest: LocalDate,
                    ): WellnessFetchResult = error("invented")
                }
            val sync =
                BackgroundSync(
                    SyncService(source, runs, syncState, clock, TEST_ZONE, NO_CUT_OVER, budget),
                    runs,
                    syncState,
                    budget,
                    clock,
                    scope,
                    NO_CUT_OVER,
                    WellnessSync(throwing, wellnessRepo, clock, TEST_ZONE),
                )
            sync.startIfDue()
            sync.join()

            assertEquals(WellnessFetchStatus.UNAVAILABLE, wellnessRepo.getState().lastStatus)
            assertEquals(listOf(1L), runs.findAll().map { it.id.value })
            assertEquals(plainSyncState(), syncState.get())
            assertEquals(SyncStatus.OK, sync.status().info.status)
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
