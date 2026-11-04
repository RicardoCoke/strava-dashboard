package app.stravadashboard.api

import app.stravadashboard.domain.weather.DayWeather
import app.stravadashboard.domain.weather.WeatherCondition
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class RunWeatherResponse(
    val days: List<RunWeatherDayDto>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RunWeatherDayDto(
    val date: String,
    val condition: String,
    // Always in the JSON: "rainMm": null on dry days.
    @EncodeDefault val rainMm: Double? = null,
)

fun DayWeather.toDto(): RunWeatherDayDto =
    RunWeatherDayDto(
        date = date.toString(),
        condition =
            when (condition) {
                WeatherCondition.SUN -> "sun"
                WeatherCondition.CLOUD -> "cloud"
                WeatherCondition.RAIN -> "rain"
            },
        rainMm = rainMm,
    )
