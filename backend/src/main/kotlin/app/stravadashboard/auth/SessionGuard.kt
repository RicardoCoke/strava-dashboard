package app.stravadashboard.auth

import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/**
 * True when this browser holds a signed session and the selected person's [tokens] are stored. One session opens both
 * people by design (feature 019), so it doesn't have to match the stored athlete.
 */
suspend fun ApplicationCall.hasConnectedSession(tokens: TokenRepository): Boolean =
    sessions.get<UserSession>() != null && tokens.find() != null
