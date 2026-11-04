package app.stravadashboard.domain.weather

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

// All forecast values here are invented.
class RunWindowDaysTest {
    private val today = LocalDate.of(2026, 11, 4)

    private fun hour(
        date: LocalDate,
        hour: Int,
        rain: Double? = 0.0,
        cloud: Double? = 0.0,
    ) = HourlyWeather(date.atTime(hour, 0), rain, cloud)

    /** The window's three stamps on [date]: rain at the 10 and 11 stamps, cloud at all three. */
    private fun window(
        date: LocalDate = today,
        rain10: Double? = 0.0,
        rain11: Double? = 0.0,
        clouds: List<Double?> = listOf(0.0, 0.0, 0.0),
    ) = listOf(
        hour(date, 9, 0.0, clouds[0]),
        hour(date, 10, rain10, clouds[1]),
        hour(date, 11, rain11, clouds[2]),
    )

    private fun verdict(hours: List<HourlyWeather>): DayWeather = runWindowDays(hours, today).single()

    @Test
    fun `R3 - rain totals the 10 and 11 values`() {
        assertEquals(DayWeather(today, WeatherCondition.RAIN, 1.2), verdict(window(rain10 = 0.4, rain11 = 0.8)))
        val clear = window(rain10 = 0.0, rain11 = 0.1, clouds = listOf(100.0, 100.0, 100.0))
        assertEquals(DayWeather(today, WeatherCondition.RAIN, 0.1), verdict(clear))
        assertEquals(0.3, verdict(window(rain10 = 0.1, rain11 = 0.2)).rainMm)
    }

    @Test
    fun `R3 - dry days are cloud or sun`() {
        val overcast = window(clouds = listOf(80.0, 90.0, 100.0))
        assertEquals(DayWeather(today, WeatherCondition.CLOUD, null), verdict(overcast))
        val clear = window(clouds = listOf(10.0, 20.0, 30.0))
        assertEquals(DayWeather(today, WeatherCondition.SUN, null), verdict(clear))
    }

    @Test
    fun `R3 - cloud threshold at 50 percent`() {
        assertEquals(WeatherCondition.CLOUD, verdict(window(clouds = listOf(50.0, 50.0, 50.0))).condition)
        assertEquals(WeatherCondition.SUN, verdict(window(clouds = listOf(49.9, 49.9, 49.9))).condition)
    }

    @Test
    fun `R3 - hours outside the window are ignored`() {
        val hours =
            listOf(
                hour(today, 9, rain = 5.0),
                hour(today, 10),
                hour(today, 11),
                hour(today, 12, rain = 5.0, cloud = 100.0),
            )
        assertEquals(WeatherCondition.SUN, verdict(hours).condition)
    }

    @Test
    fun `R4 - range is today to the last forecast day`() {
        val hours = (0L until FORECAST_DAYS).flatMap { window(today.plusDays(it)) }
        val dates = runWindowDays(hours, today).map { it.date }
        assertEquals(today, dates.first())
        assertEquals(LocalDate.of(2026, 11, 19), dates.last())
        assertEquals(FORECAST_DAYS, dates.size)
    }

    @Test
    fun `R4 - past days are dropped`() {
        val hours = window(today.minusDays(1)) + window(today)
        assertEquals(listOf(today), runWindowDays(hours, today).map { it.date })
    }

    @Test
    fun `R4 - incomplete day is dropped`() {
        val incomplete =
            listOf(
                window(rain10 = null),
                window(rain11 = null),
                window(clouds = listOf(null, 0.0, 0.0)),
                window(clouds = listOf(0.0, null, 0.0)),
                window(clouds = listOf(0.0, 0.0, null)),
                window().drop(1),
            )
        incomplete.forEach { assertEquals(emptyList(), runWindowDays(it, today)) }
    }
}
