package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.trainingload.TrainingLoadResult
import app.stravadashboard.domain.trainingload.TrainingLoadService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.trainingLoadRoutes(
    tokens: TokenRepository,
    service: TrainingLoadService,
) {
    get("/api/training-load") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            TrainingLoadResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            TrainingLoadResult.ReauthRequired ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse.REAUTH_REQUIRED)
            TrainingLoadResult.NotConfigured ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.TRAINING_LOAD_NOT_CONFIGURED)
            TrainingLoadResult.InvalidConfig ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.TRAINING_LOAD_INVALID_CONFIG)
            is TrainingLoadResult.Ready -> call.respond(result.toDto())
        }
    }
}
