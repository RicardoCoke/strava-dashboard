package app.stravadashboard.api

import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxResult
import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.normalizedVo2maxRoutes(service: NormalizedVo2maxService) {
    get("/api/vo2max/normalized") {
        when (val result = service.get()) {
            NormalizedVo2maxResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.VO2MAX_INVALID_CONFIG)
            is NormalizedVo2maxResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
