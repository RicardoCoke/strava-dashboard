package app.stravadashboard.auth

import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.strava.StravaOAuthClient
import app.stravadashboard.strava.StravaResult

sealed interface ConnectResult {
    data class Connected(
        val athleteId: Long,
    ) : ConnectResult

    data object TokenExchangeFailed : ConnectResult

    data object WrongAthlete : ConnectResult
}

/** The athlete a person's local store belongs to, or null before their first connect. */
class AthleteOwner(
    private val tokens: TokenRepository,
    private val syncState: SyncStateRepository,
) {
    suspend fun id(): Long? = syncState.get().ownerAthleteId ?: tokens.find()?.athleteId
}

/**
 * Exchanges the OAuth code and stores one person's tokens. Each store belongs to one athlete: anyone else is rejected
 * and nothing changes (feature 019), so connecting can never destroy data.
 */
class ConnectService(
    private val oauth: StravaOAuthClient,
    private val tokens: TokenRepository,
    private val syncState: SyncStateRepository,
    private val otherOwners: List<AthleteOwner>,
) {
    suspend fun connect(
        code: String,
        grantedScope: String,
    ): ConnectResult {
        val grant = (oauth.exchangeCode(code) as? StravaResult.Success)?.value
        val athleteId = grant?.athleteId ?: return ConnectResult.TokenExchangeFailed

        val owner = AthleteOwner(tokens, syncState).id()
        val belongsElsewhere = (owner != null && owner != athleteId) || otherOwners.any { it.id() == athleteId }
        return if (belongsElsewhere) {
            ConnectResult.WrongAthlete
        } else {
            val state = syncState.get()
            if (state.ownerAthleteId == null) syncState.save(state.copy(ownerAthleteId = athleteId))
            tokens.save(StoredTokens(athleteId, grant.accessToken, grant.refreshToken, grant.expiresAt, grantedScope))
            ConnectResult.Connected(athleteId)
        }
    }
}
