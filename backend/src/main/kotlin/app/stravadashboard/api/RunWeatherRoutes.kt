package app.stravadashboard.api

import app.stravadashboard.auth.hasConnectedSession
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.domain.weather.RunWeatherResult
import app.stravadashboard.domain.weather.RunWeatherService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.runWeatherRoutes(
    tokens: TokenRepository,
    service: RunWeatherService,
) {
    get("/api/run-weather") {
        if (!call.hasConnectedSession(tokens)) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED)
            return@get
        }
        when (val result = service.get()) {
            RunWeatherResult.NotConfigured ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.WEATHER_NOT_CONFIGURED)
            RunWeatherResult.Unavailable ->
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse.WEATHER_UNAVAILABLE)
            is RunWeatherResult.Ready -> call.respond(RunWeatherResponse(result.days.map { it.toDto() }))
        }
    }
}
