package app.stravadashboard.api

import app.stravadashboard.domain.RunningDashboardService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.runningDashboardRoutes(service: RunningDashboardService) {
    get("/api/dashboard/running") {
        call.respond(service.get().toDto())
    }
}
