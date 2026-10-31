package app.stravadashboard.auth

import kotlinx.serialization.Serializable

/** Held in the signed, HttpOnly `sd_session` cookie. Contains no Strava token. */
@Serializable
data class UserSession(
    val athleteId: Long,
)
