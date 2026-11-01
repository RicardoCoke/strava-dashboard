package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.racedayform.RaceDayFormResult
import app.stravadashboard.domain.racedayform.RaceDayFormService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.raceDayFormRoutes(
    tokens: TokenRepository,
    service: RaceDayFormService,
) {
    get("/api/race-day-form") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            RaceDayFormResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            RaceDayFormResult.MissingPlan ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.RACE_DAY_FORM_MISSING_PLAN)
            RaceDayFormResult.MissingLthr ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.RACE_DAY_FORM_MISSING_LTHR)
            RaceDayFormResult.MissingPlanAndLthr ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.RACE_DAY_FORM_MISSING_BOTH)
            RaceDayFormResult.InvalidLthr ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.RACE_DAY_FORM_INVALID_LTHR)
            RaceDayFormResult.InvalidPlan ->
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse.RACE_DAY_FORM_INVALID_PLAN)
            RaceDayFormResult.PlanUnavailable ->
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse.RACE_DAY_FORM_PLAN_UNAVAILABLE)
            is RaceDayFormResult.Ready -> call.respond(result.form.toDto())
        }
    }
}
