package app.stravadashboard.strava

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import java.time.Clock
import java.time.Instant

/** Tokens returned by Strava's token endpoint. [athleteId] is only present on a code exchange. */
data class TokenGrant(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
    val athleteId: Long?,
) {
    override fun toString(): String = "TokenGrant(<redacted>, expiresAt=$expiresAt)"
}

/** Strava's `POST /oauth/token`: code exchange and refresh. A 400 or 401 means the grant was rejected. */
class StravaOAuthClient(
    private val http: HttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val clock: Clock,
    private val tokenUrl: String = TOKEN_URL,
) {
    suspend fun exchangeCode(code: String): StravaResult<TokenGrant> =
        post(grantType = "authorization_code", key = "code", value = code)

    suspend fun refresh(refreshToken: String): StravaResult<TokenGrant> =
        post(grantType = "refresh_token", key = "refresh_token", value = refreshToken)

    private suspend fun post(
        grantType: String,
        key: String,
        value: String,
    ): StravaResult<TokenGrant> =
        stravaCall(
            clock = clock,
            unauthorizedStatuses = setOf(HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized),
            request = {
                http.submitForm(
                    url = tokenUrl,
                    formParameters =
                        parameters {
                            append("client_id", clientId)
                            append("client_secret", clientSecret)
                            append("grant_type", grantType)
                            append(key, value)
                        },
                )
            },
            decode = { response ->
                val dto = response.body<TokenResponseDto>()
                TokenGrant(dto.accessToken, dto.refreshToken, Instant.ofEpochSecond(dto.expiresAt), dto.athlete?.id)
            },
        )

    companion object {
        const val TOKEN_URL = "https://www.strava.com/oauth/token"
    }
}
