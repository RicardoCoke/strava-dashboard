package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxResult
import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.normalizedVo2maxRoutes(
    tokens: TokenRepository,
    service: NormalizedVo2maxService,
) {
    get("/api/vo2max/normalized") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            NormalizedVo2maxResult.NotConnected ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            NormalizedVo2maxResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.VO2MAX_INVALID_CONFIG)
            is NormalizedVo2maxResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
