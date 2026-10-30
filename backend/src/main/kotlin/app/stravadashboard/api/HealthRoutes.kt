package app.stravadashboard.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get("/api/health") {
        call.respond(HealthResponse(status = "ok"))
    }
}
