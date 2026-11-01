package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.longrundrift.LongRunDriftResult
import app.stravadashboard.domain.longrundrift.LongRunDriftService
import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Read-only: answers from the local store and never calls Strava (feature 013, R4). */
fun Route.longRunDriftRoutes(
    tokens: TokenRepository,
    service: LongRunDriftService,
) {
    get("/api/long-run-drift") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            LongRunDriftResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            is LongRunDriftResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
