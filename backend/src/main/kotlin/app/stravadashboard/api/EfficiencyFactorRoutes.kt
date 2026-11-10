package app.stravadashboard.api

import app.stravadashboard.domain.efficiency.EfficiencyFactorService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.efficiencyFactorRoutes(service: EfficiencyFactorService) {
    get("/api/efficiency-factor") {
        call.respond(service.get().toDto())
    }
}
