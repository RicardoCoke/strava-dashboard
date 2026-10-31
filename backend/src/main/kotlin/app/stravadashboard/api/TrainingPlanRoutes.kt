package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.trainingplan.TrainingPlanResult
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.trainingPlanRoutes(
    tokens: TokenRepository,
    service: TrainingPlanService,
) {
    get("/api/training-plan") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            TrainingPlanResult.NotConfigured ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.TRAINING_PLAN_NOT_CONFIGURED)
            TrainingPlanResult.Unavailable ->
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse.TRAINING_PLAN_UNAVAILABLE)
            is TrainingPlanResult.Invalid ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.trainingPlanInvalid(result.reason))
            is TrainingPlanResult.Ready -> call.respond(result.calendar.toDto())
        }
    }
}
