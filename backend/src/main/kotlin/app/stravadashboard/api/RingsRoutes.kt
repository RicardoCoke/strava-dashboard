package app.stravadashboard.api

import app.stravadashboard.domain.rings.RingsService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val HISTORY_RANGES = setOf(7, 30, 90)

fun Route.ringsRoutes(service: RingsService) {
    get("/api/rings") {
        call.respond(service.get().toDto())
    }
    get("/api/rings/history") {
        val days =
            call.request.queryParameters["days"]
                ?.toIntOrNull()
                ?.takeIf { it in HISTORY_RANGES }
        if (days == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse.INVALID_DAYS)
        } else {
            call.respond(service.history(days).toHistoryDto())
        }
    }
}
