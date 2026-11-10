package app.stravadashboard.api

import app.stravadashboard.domain.vo2max.Vo2maxResult
import app.stravadashboard.domain.vo2max.Vo2maxService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.vo2maxRoutes(service: Vo2maxService) {
    get("/api/vo2max") {
        when (val result = service.get()) {
            Vo2maxResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.VO2MAX_INVALID_CONFIG)
            is Vo2maxResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
