package app.stravadashboard.api

import app.stravadashboard.domain.marathonpace.MarathonPaceResult
import app.stravadashboard.domain.marathonpace.MarathonPaceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.marathonPaceRoutes(service: MarathonPaceService) {
    get("/api/marathon-pace") {
        when (val result = service.get()) {
            MarathonPaceResult.NotConfigured -> call.respond(HttpStatusCode.NotFound, MARATHON_PACE_NOT_CONFIGURED)
            MarathonPaceResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, MARATHON_PACE_INVALID_CONFIG)
            is MarathonPaceResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
