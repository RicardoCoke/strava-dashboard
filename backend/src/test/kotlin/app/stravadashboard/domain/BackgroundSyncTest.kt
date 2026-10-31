package app.stravadashboard.domain

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStatus
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.testsupport.FakeActivitySource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.NOW
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
    private val budget = StravaCallBudget(clock)
    private val source = GatedActivitySource()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sync =
        BackgroundSync(
            SyncService(source, runs, syncState, clock, TEST_ZONE, budget),
            runs,
            syncState,
            budget,
            clock,
            scope,
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
            sync.startIfDue()
            source.entered.await()
            assertEquals(SyncStatus.SYNCING, sync.status().info.status)

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
    fun `R5 - revoked token ends job with reauth required`() =
        run {
            source.release.complete(Unit)
            source.failWith(SourceResult.ReauthRequired)

            sync.startIfDue()
            sync.join()

            assertTrue(sync.reauthRequired)
        }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
