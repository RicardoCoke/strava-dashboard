package app.stravadashboard.auth

import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.strava.StravaOAuthClient
import app.stravadashboard.strava.StravaResult

sealed interface ConnectResult {
    data class Connected(
        val athleteId: Long,
    ) : ConnectResult

    data object TokenExchangeFailed : ConnectResult
}

/**
 * Exchanges the OAuth code and stores the tokens. The local store belongs to one athlete: when a different athlete
 * connects (even after the previous tokens were revoked and deleted), runs and sync state are wiped first.
 */
class ConnectService(
    private val oauth: StravaOAuthClient,
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val syncState: SyncStateRepository,
) {
    suspend fun connect(
        code: String,
        grantedScope: String,
    ): ConnectResult {
        val grant = (oauth.exchangeCode(code) as? StravaResult.Success)?.value
        val athleteId = grant?.athleteId ?: return ConnectResult.TokenExchangeFailed

        val owner = syncState.get().ownerAthleteId ?: tokens.find()?.athleteId
        if (owner != athleteId) {
            runs.deleteAll()
            syncState.save(SyncState(ownerAthleteId = athleteId))
        }
        tokens.save(StoredTokens(athleteId, grant.accessToken, grant.refreshToken, grant.expiresAt, grantedScope))
        return ConnectResult.Connected(athleteId)
    }
}
