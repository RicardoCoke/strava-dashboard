package app.stravadashboard.domain.ports

import java.time.Instant

/** [SYNCING] is only reported while a background sync runs; it is never saved as [SyncState.lastStatus]. */
enum class SyncStatus { OK, PARTIAL, RATE_LIMITED, STRAVA_UNAVAILABLE, SYNCING }

/**
 * [listCursor] is null until a list pass has read every page. [ownerAthleteId] is the athlete the stored runs belong
 * to; it outlives the tokens, so a different athlete connecting after a revoke still gets a clean store.
 * [historyStart] is the `after` of the last list pass that read every page from the history start; null on stores
 * from before feature 005. [lastAttemptAt] is when the last sync attempt finished, successful or not (feature 009).
 */
data class SyncState(
    val listCursor: Instant? = null,
    val lastSuccessfulSyncAt: Instant? = null,
    val rateLimitedUntil: Instant? = null,
    val lastStatus: SyncStatus? = null,
    val ownerAthleteId: Long? = null,
    val historyStart: Instant? = null,
    val lastAttemptAt: Instant? = null,
)

interface SyncStateRepository {
    suspend fun get(): SyncState

    suspend fun save(state: SyncState)
}
