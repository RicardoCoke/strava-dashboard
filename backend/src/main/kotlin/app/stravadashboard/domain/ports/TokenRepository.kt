package app.stravadashboard.domain.ports

import java.time.Instant

/** Strava OAuth credentials. Never logged, never sent to the browser. */
data class StoredTokens(
    val athleteId: Long,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val scope: String,
) {
    override fun toString(): String = "StoredTokens(athleteId=<redacted>, tokens=<redacted>, expiresAt=$expiresAt)"
}

interface TokenRepository {
    suspend fun find(): StoredTokens?

    /** Replaces any stored tokens (there is at most one row). */
    suspend fun save(tokens: StoredTokens)

    suspend fun delete()
}
