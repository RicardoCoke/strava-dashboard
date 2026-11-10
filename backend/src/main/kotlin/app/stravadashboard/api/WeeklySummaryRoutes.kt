package app.stravadashboard.api

import app.stravadashboard.domain.weeklysummary.WeeklySummaryService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.weeklySummaryRoutes(service: WeeklySummaryService) {
    get("/api/weekly-summaries") {
        call.respond(service.get().toDto())
    }
}
