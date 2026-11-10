package app.stravadashboard.api

import app.stravadashboard.domain.longrundrift.LongRunDriftService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Read-only: answers from the local store and never calls Garmin (feature 013, R4). */
fun Route.longRunDriftRoutes(service: LongRunDriftService) {
    get("/api/long-run-drift") {
        call.respond(service.get().toDto())
    }
}
