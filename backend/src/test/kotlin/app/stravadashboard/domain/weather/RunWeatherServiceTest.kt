package app.stravadashboard.domain.weather

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.domain.ports.WeatherSource
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.TEST_ZONE
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// All forecast values here are invented. The sixteen-week fixture has its marathon on 2026-12-06.
class RunWeatherServiceTest {
    private val home = WeatherLocation(10.5, 20.5)
    private val valencia = WeatherLocation(39.47, -0.38)
    private val marathon = LocalDate.of(2026, 12, 6)
    private val planSource = FakePlanSource()
    private val calls = mutableListOf<WeatherLocation>()
    private val results = mutableMapOf<WeatherLocation, WeatherFetchResult>()

    private val source =
        WeatherSource { location ->
            calls += location
            results.getValue(location)
        }

    private fun forecast(
        from: LocalDate,
        cloud: Double,
        days: Long = FORECAST_DAYS.toLong(),
    ): WeatherFetchResult =
        WeatherFetchResult.Success(
            (0 until days).flatMap { day ->
                (9..11).map { HourlyWeather(from.plusDays(day).atTime(it, 0), 0.0, cloud) }
            },
        )

    private fun get(
        today: LocalDate,
        time: String = "10:00:00",
    ): RunWeatherResult {
        val clock = Clock.fixed(Instant.parse("${today}T${time}Z"), TEST_ZONE)
        val plan = TrainingPlanService(planSource, AppConfig.DEFAULT_TRAINING_PLAN_START, clock, TEST_ZONE)
        return runBlocking { RunWeatherService(home, source, plan, clock, TEST_ZONE).get() }
    }

    private fun days(result: RunWeatherResult): List<DayWeather> = (result as RunWeatherResult.Ready).days

    private val inRange = LocalDate.of(2026, 11, 21)

    @Test
    fun `R4 - today stays after the window has passed`() {
        val today = LocalDate.of(2026, 11, 4)
        results[home] = forecast(today, cloud = 0.0)
        assertEquals(today, days(get(today, time = "15:00:00")).first().date)
    }

    @Test
    fun `R5 - Valencia fetched only when marathon is in range`() {
        results[home] = forecast(LocalDate.of(2026, 11, 4), cloud = 0.0)
        get(LocalDate.of(2026, 11, 4))
        assertEquals(listOf(home), calls)

        // The day before the marathon reaches the forecast's last day.
        calls.clear()
        results[home] = forecast(LocalDate.of(2026, 11, 20), cloud = 0.0)
        get(LocalDate.of(2026, 11, 20))
        assertEquals(listOf(home), calls)

        // A past marathon.
        calls.clear()
        results[home] = forecast(LocalDate.of(2026, 12, 7), cloud = 0.0)
        get(LocalDate.of(2026, 12, 7))
        assertEquals(listOf(home), calls)
    }

    @Test
    fun `R5 - marathon entry comes from Valencia`() {
        results[home] = forecast(inRange, cloud = 0.0)
        results[valencia] = forecast(inRange, cloud = 100.0)
        val days = days(get(inRange))

        assertEquals(listOf(home, valencia), calls)
        assertEquals(FORECAST_DAYS, days.size)
        assertEquals(days.map { it.date }.sorted(), days.map { it.date })
        assertEquals(WeatherCondition.CLOUD, days.single { it.date == marathon }.condition)
        assertEquals(setOf(WeatherCondition.SUN), days.filter { it.date != marathon }.map { it.condition }.toSet())
    }

    @Test
    fun `R5 - no Valencia data leaves the marathon blank`() {
        results[home] = forecast(inRange, cloud = 0.0)
        listOf(WeatherFetchResult.Unavailable, forecast(inRange, cloud = 100.0, days = 15)).forEach {
            results[valencia] = it
            val days = days(get(inRange))
            assertEquals(FORECAST_DAYS - 1, days.size)
            assertEquals(false, days.any { day -> day.date == marathon })
        }
    }

    @Test
    fun `R5 - plan problems still return home weather`() {
        results[home] = forecast(inRange, cloud = 0.0)
        listOf(PlanFile.NotConfigured, PlanFile.Unreadable, PlanFile.Content("WEEK 1;;MONDAY\n")).forEach {
            planSource.file = it
            calls.clear()
            assertEquals(FORECAST_DAYS, days(get(inRange)).size)
            assertEquals(listOf(home), calls)
        }
    }

    @Test
    fun `R6 - home failure is unavailable and skips Valencia`() {
        results[home] = WeatherFetchResult.Unavailable
        assertEquals(RunWeatherResult.Unavailable, get(inRange))
        assertEquals(listOf(home), calls)
    }

    @Test
    fun `R6 - each call uses fresh data`() {
        val today = LocalDate.of(2026, 11, 4)
        results[home] = forecast(today, cloud = 0.0)
        assertEquals(WeatherCondition.SUN, days(get(today)).first().condition)
        results[home] = forecast(today, cloud = 100.0)
        assertEquals(WeatherCondition.CLOUD, days(get(today)).first().condition)
    }
}
