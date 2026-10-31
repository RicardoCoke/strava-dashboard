package app.stravadashboard.auth

import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/** True when the request has a valid session for the athlete whose tokens are stored. */
suspend fun ApplicationCall.hasConnectedSession(tokens: TokenRepository): Boolean {
    val session = sessions.get<UserSession>() ?: return false
    return tokens.find()?.athleteId == session.athleteId
}
