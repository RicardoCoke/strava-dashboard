package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.efficiency.EfficiencyFactorResult
import app.stravadashboard.domain.efficiency.EfficiencyFactorService
import app.stravadashboard.domain.ports.TokenRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.efficiencyFactorRoutes(
    tokens: TokenRepository,
    service: EfficiencyFactorService,
) {
    get("/api/efficiency-factor") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            EfficiencyFactorResult.NotConnected ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse.NOT_CONNECTED,
                )
            is EfficiencyFactorResult.Ready -> call.respond(result.report.toDto())
        }
    }
}
