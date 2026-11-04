package app.stravadashboard.domain.ports

import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.weather.WeatherFetchResult

/** The hourly forecast for a location, in that location's local time. */
fun interface WeatherSource {
    suspend fun fetch(location: WeatherLocation): WeatherFetchResult
}
