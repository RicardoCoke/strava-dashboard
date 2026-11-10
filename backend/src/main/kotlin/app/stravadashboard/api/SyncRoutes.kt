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
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/** `GET /api/sync` reports the status; `POST /api/sync` starts a background sync if one is due (feature 009). */
fun Route.syncRoutes(
    tokens: TokenRepository,
    sync: BackgroundSync,
    other: OtherPersonSync?,
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
        val status = sync.startIfDue()
        other?.startIfConnected()
        call.respond(status.toDto())
    }
}

/** The person not on screen: sync only runs while a dashboard is open, so a request starts theirs too (019 R6). */
class OtherPersonSync(
    private val tokens: TokenRepository,
    private val sync: BackgroundSync,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun startIfConnected() {
        try {
            if (tokens.find() != null) sync.startIfDue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not start a background sync: {}", e::class.simpleName)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(OtherPersonSync::class.java)
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
