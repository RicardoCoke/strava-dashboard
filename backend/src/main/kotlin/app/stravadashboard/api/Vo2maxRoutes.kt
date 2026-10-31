package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.vo2max.Vo2maxResult
import app.stravadashboard.domain.vo2max.Vo2maxService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.vo2maxRoutes(
    tokens: TokenRepository,
    service: Vo2maxService,
) {
    get("/api/vo2max") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            Vo2maxResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            Vo2maxResult.ReauthRequired -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.REAUTH_REQUIRED)
            Vo2maxResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.VO2MAX_INVALID_CONFIG)
            is Vo2maxResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
