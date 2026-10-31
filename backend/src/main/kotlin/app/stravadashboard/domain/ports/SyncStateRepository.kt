package app.stravadashboard.domain.ports

import java.time.Instant

enum class SyncStatus { OK, PARTIAL, RATE_LIMITED, STRAVA_UNAVAILABLE }

/**
 * [listCursor] is null until a list pass has read every page. [ownerAthleteId] is the athlete the stored runs belong
 * to; it outlives the tokens, so a different athlete connecting after a revoke still gets a clean store.
 */
data class SyncState(
    val listCursor: Instant? = null,
    val lastSuccessfulSyncAt: Instant? = null,
    val rateLimitedUntil: Instant? = null,
    val lastStatus: SyncStatus? = null,
    val ownerAthleteId: Long? = null,
)

interface SyncStateRepository {
    suspend fun get(): SyncState

    suspend fun save(state: SyncState)
}
