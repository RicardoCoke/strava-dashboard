package app.stravadashboard.api

import app.stravadashboard.domain.trainingload.TrainingLoadResult
import app.stravadashboard.domain.trainingload.TrainingLoadService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.trainingLoadRoutes(service: TrainingLoadService) {
    get("/api/training-load") {
        when (val result = service.get()) {
            TrainingLoadResult.NotConfigured ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.TRAINING_LOAD_NOT_CONFIGURED)
            TrainingLoadResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.TRAINING_LOAD_INVALID_CONFIG)
            is TrainingLoadResult.Ready -> call.respond(result.toDto())
        }
    }
}
