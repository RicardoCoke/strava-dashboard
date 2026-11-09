package app.stravadashboard.api

import app.stravadashboard.domain.DashboardResult
import app.stravadashboard.domain.RunningDashboardService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.runningDashboardRoutes(service: RunningDashboardService) {
    get("/api/dashboard/running") {
        when (val result = service.get()) {
            is DashboardResult.Ready -> call.respond(result.toDto())
        }
    }
}
