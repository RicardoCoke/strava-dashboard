package app.stravadashboard.api

import app.stravadashboard.domain.wellness.WellnessResult
import app.stravadashboard.domain.wellness.WellnessService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.wellnessRoutes(service: WellnessService) {
    get("/api/wellness") {
        when (val result = service.get()) {
            WellnessResult.NotConfigured -> call.respond(HttpStatusCode.NotFound, ErrorResponse.WELLNESS_NOT_CONFIGURED)
            is WellnessResult.Ready -> call.respond(result.toDto())
        }
    }
}
