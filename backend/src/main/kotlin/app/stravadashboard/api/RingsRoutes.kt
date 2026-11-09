package app.stravadashboard.api

import app.stravadashboard.domain.rings.RingsService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.ringsRoutes(service: RingsService) {
    get("/api/rings") {
        call.respond(service.get().toDto())
    }
}
