package app.stravadashboard.strava

import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.TokenRepository
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

/**
 * Hands out a fresh access token, refreshing it shortly before expiry. A rejected refresh deletes the stored
 * tokens and returns [StravaResult.Unauthorized], which means the user has to connect again.
 */
class StravaTokenProvider(
    private val tokens: TokenRepository,
    private val oauth: StravaOAuthClient,
    private val clock: Clock,
) {
    suspend fun current(): StravaResult<String> {
        val stored = tokens.find() ?: return StravaResult.Unauthorized
        val expiresSoon = stored.expiresAt <= clock.instant().plus(REFRESH_MARGIN)
        return if (expiresSoon) refresh(stored) else StravaResult.Success(stored.accessToken)
    }

    /** Forces a refresh, used once after Strava answers 401. */
    suspend fun refreshed(): StravaResult<String> {
        val stored = tokens.find() ?: return StravaResult.Unauthorized
        return refresh(stored)
    }

    suspend fun revoke() {
        tokens.delete()
        log.info("Strava rejected the connection; stored tokens deleted")
    }

    private suspend fun refresh(stored: StoredTokens): StravaResult<String> =
        when (val result = oauth.refresh(stored.refreshToken)) {
            is StravaResult.Success -> {
                val grant = result.value
                tokens.save(
                    stored.copy(
                        accessToken = grant.accessToken,
                        refreshToken = grant.refreshToken,
                        expiresAt = grant.expiresAt,
                    ),
                )
                StravaResult.Success(grant.accessToken)
            }
            StravaResult.Unauthorized -> StravaResult.Unauthorized.also { revoke() }
            StravaResult.NotFound -> StravaResult.Unavailable
            is StravaResult.RateLimited -> StravaResult.RateLimited(result.until)
            StravaResult.Unavailable -> StravaResult.Unavailable
        }

    private companion object {
        val REFRESH_MARGIN: Duration = Duration.ofMinutes(5)
        val log = LoggerFactory.getLogger(StravaTokenProvider::class.java)
    }
}
