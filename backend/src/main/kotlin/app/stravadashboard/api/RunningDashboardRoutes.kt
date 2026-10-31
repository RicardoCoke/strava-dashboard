package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.DashboardResult
import app.stravadashboard.domain.RunningDashboardService
import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.runningDashboardRoutes(
    tokens: TokenRepository,
    service: RunningDashboardService,
) {
    get("/api/dashboard/running") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            DashboardResult.NotConnected -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            is DashboardResult.Ready -> call.respond(result.toDto())
        }
    }
}
