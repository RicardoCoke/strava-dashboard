package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.marathonpace.MarathonPaceResult
import app.stravadashboard.domain.marathonpace.MarathonPaceService
import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.marathonPaceRoutes(
    tokens: TokenRepository,
    service: MarathonPaceService,
) {
    get("/api/marathon-pace") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            MarathonPaceResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            MarathonPaceResult.NotConfigured -> call.respond(HttpStatusCode.NotFound, MARATHON_PACE_NOT_CONFIGURED)
            MarathonPaceResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, MARATHON_PACE_INVALID_CONFIG)
            is MarathonPaceResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
