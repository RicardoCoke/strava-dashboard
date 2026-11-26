package app.stravadashboard.api

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.workoutcompare.CompareFilter
import app.stravadashboard.domain.workoutcompare.CompareOutcome
import app.stravadashboard.domain.workoutcompare.WorkoutCompareService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val FILTERS = CompareFilter.entries.associateBy { it.name.lowercase() }

/** The list reads the store only; a comparison may call Garmin within the person's call budget (feature 025, R5). */
fun Route.workoutCompareRoutes(service: WorkoutCompareService) {
    get("/api/workout-compare/runs") {
        val filter = call.request.queryParameters["type"]?.let(FILTERS::get)
        if (filter == null) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse.INVALID_TYPE)
        } else {
            call.respond(service.list(filter).toCompareRunsDto())
        }
    }
    get("/api/workout-compare") {
        val a = call.request.queryParameters["a"]?.toLongOrNull()
        val b = call.request.queryParameters["b"]?.toLongOrNull()
        if (a == null || b == null || a == b) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse.INVALID_RUNS)
            return@get
        }
        when (val outcome = service.compare(ActivityId(a), ActivityId(b))) {
            is CompareOutcome.Found -> call.respond(outcome.comparison.toDto())
            CompareOutcome.RunNotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse.RUN_NOT_FOUND)
        }
    }
}
