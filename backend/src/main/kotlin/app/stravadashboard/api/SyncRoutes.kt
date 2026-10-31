package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.BackgroundSync
import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** `GET /api/sync` reports the status; `POST /api/sync` starts a background sync if one is due (feature 009). */
fun Route.syncRoutes(
    tokens: TokenRepository,
    sync: BackgroundSync,
) {
    get("/api/sync") {
        if (call.rejectUnconnected(tokens, sync)) return@get
        call.respond(sync.status().toDto())
    }
    post("/api/sync") {
        if (!call.hasValidXsrfToken()) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse.XSRF_INVALID)
            return@post
        }
        if (call.rejectUnconnected(tokens, sync)) return@post
        call.respond(sync.startIfDue().toDto())
    }
}

/** A revoked connection found by the last background job reads as "reconnect" rather than "connect". */
private suspend fun ApplicationCall.rejectUnconnected(
    tokens: TokenRepository,
    sync: BackgroundSync,
): Boolean {
    if (hasConnectedSession(tokens)) return false
    val error = if (sync.reauthRequired) ErrorResponse.REAUTH_REQUIRED else ErrorResponse.NOT_CONNECTED
    respond(HttpStatusCode.Unauthorized, error)
    return true
}
