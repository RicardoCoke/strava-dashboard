package app.stravadashboard.domain.weather

import app.stravadashboard.domain.model.roundOneDecimal
import java.time.LocalDate
import java.time.LocalDateTime

const val FORECAST_DAYS = 16
const val RAIN_MIN_MM = 0.1
const val CLOUDY_MIN_PERCENT = 50.0
private const val WINDOW_START_HOUR = 9
private const val WINDOW_END_HOUR = 11

enum class WeatherCondition { SUN, CLOUD, RAIN }

data class DayWeather(
    val date: LocalDate,
    val condition: WeatherCondition,
    /** Set only for [WeatherCondition.RAIN]. */
    val rainMm: Double?,
)

data class HourlyWeather(
    val time: LocalDateTime,
    val precipitationMm: Double?,
    val cloudCoverPercent: Double?,
)

sealed interface WeatherFetchResult {
    data class Success(
        val hours: List<HourlyWeather>,
    ) : WeatherFetchResult

    data object Unavailable : WeatherFetchResult
}

sealed interface RunWeatherResult {
    data object NotConfigured : RunWeatherResult

    data object Unavailable : RunWeatherResult

    data class Ready(
        val days: List<DayWeather>,
    ) : RunWeatherResult
}

/** One verdict per day from [today] on, in date order. A day missing any needed value is left out. */
fun runWindowDays(
    hours: List<HourlyWeather>,
    today: LocalDate,
): List<DayWeather> {
    val byTime = hours.associateBy { it.time }
    return hours
        .map { it.time.toLocalDate() }
        .distinct()
        .filter { it >= today }
        .sorted()
        .mapNotNull { dayWeather(it, byTime) }
}

private fun dayWeather(
    date: LocalDate,
    byTime: Map<LocalDateTime, HourlyWeather>,
): DayWeather? {
    // Each precipitation value covers the hour before its stamp, so the 09:00 stamp is rain from before the window.
    val rain = ((WINDOW_START_HOUR + 1)..WINDOW_END_HOUR).map { byTime[date.atTime(it, 0)]?.precipitationMm }
    val clouds = (WINDOW_START_HOUR..WINDOW_END_HOUR).map { byTime[date.atTime(it, 0)]?.cloudCoverPercent }
    if (null in rain || null in clouds) return null
    val rainMm = roundOneDecimal(rain.filterNotNull().sum())
    return when {
        rainMm >= RAIN_MIN_MM -> DayWeather(date, WeatherCondition.RAIN, rainMm)
        clouds.filterNotNull().average() >= CLOUDY_MIN_PERCENT -> DayWeather(date, WeatherCondition.CLOUD, null)
        else -> DayWeather(date, WeatherCondition.SUN, null)
    }
}
