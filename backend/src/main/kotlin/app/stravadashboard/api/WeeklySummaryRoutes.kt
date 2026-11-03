package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.weeklysummary.WeeklySummaryResult
import app.stravadashboard.domain.weeklysummary.WeeklySummaryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.weeklySummaryRoutes(
    tokens: TokenRepository,
    service: WeeklySummaryService,
) {
    get("/api/weekly-summaries") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            WeeklySummaryResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            is WeeklySummaryResult.Ready -> call.respond(result.summaries.toDto())
        }
    }
}
