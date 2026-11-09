package app.stravadashboard.api

import app.stravadashboard.domain.weather.RunWeatherResult
import app.stravadashboard.domain.weather.RunWeatherService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.runWeatherRoutes(service: RunWeatherService) {
    get("/api/run-weather") {
        when (val result = service.get()) {
            RunWeatherResult.NotConfigured ->
                call.respond(HttpStatusCode.NotFound, ErrorResponse.WEATHER_NOT_CONFIGURED)
            RunWeatherResult.Unavailable ->
                call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse.WEATHER_UNAVAILABLE)
            is RunWeatherResult.Ready -> call.respond(RunWeatherResponse(result.days.map { it.toDto() }))
        }
    }
}
