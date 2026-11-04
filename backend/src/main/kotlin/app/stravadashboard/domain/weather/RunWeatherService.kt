package app.stravadashboard.domain.weather

import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.ports.WeatherSource
import app.stravadashboard.domain.trainingplan.TrainingPlanResult
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Fetches a fresh forecast on every call and stores nothing (018 R5, R6). The race is in Valencia, not at home. */
class RunWeatherService(
    private val home: WeatherLocation?,
    private val source: WeatherSource?,
    private val plan: TrainingPlanService,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): RunWeatherResult {
        if (home == null || source == null) return RunWeatherResult.NotConfigured
        return when (val forecast = source.fetch(home)) {
            WeatherFetchResult.Unavailable -> RunWeatherResult.Unavailable
            is WeatherFetchResult.Success -> {
                val today = LocalDate.now(clock.withZone(zone))
                val days = runWindowDays(forecast.hours, today)
                val marathon = marathonDate()?.takeIf { it in today..today.plusDays(FORECAST_DAYS - 1L) }
                RunWeatherResult.Ready(if (marathon == null) days else withValencia(source, days, marathon, today))
            }
        }
    }

    private suspend fun marathonDate(): LocalDate? =
        (plan.get() as? TrainingPlanResult.Ready)
            ?.calendar
            ?.weeks
            ?.flatMap { it.days }
            ?.firstOrNull { it.day.race?.isMarathon == true }
            ?.date

    /** Without Valencia data the marathon date is left out: home's weather would be for the wrong place. */
    private suspend fun withValencia(
        weather: WeatherSource,
        days: List<DayWeather>,
        marathon: LocalDate,
        today: LocalDate,
    ): List<DayWeather> {
        val race =
            (weather.fetch(VALENCIA) as? WeatherFetchResult.Success)
                ?.let { runWindowDays(it.hours, today) }
                ?.firstOrNull { it.date == marathon }
        return (days.filterNot { it.date == marathon } + listOfNotNull(race)).sortedBy { it.date }
    }

    private companion object {
        val VALENCIA = WeatherLocation(39.47, -0.38)
    }
}
