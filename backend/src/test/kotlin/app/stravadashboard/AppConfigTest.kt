package app.stravadashboard

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.model.Bpm
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppConfigTest {
    private val required =
        mapOf(
            "DB_PATH" to "data/test.db",
        )

    @Test
    fun `018 R1 - reads weather location or leaves it unset`() {
        val location = mapOf("WEATHER_LATITUDE" to "10.50", "WEATHER_LONGITUDE" to " 20.50 ")
        assertEquals(WeatherLocation(10.50, 20.50), AppConfig.fromEnvironment(required + location).weatherHome)
        assertNull(AppConfig.fromEnvironment(required).weatherHome)
        val blank = mapOf("WEATHER_LATITUDE" to " ", "WEATHER_LONGITUDE" to "")
        assertNull(AppConfig.fromEnvironment(required + blank).weatherHome)
    }

    @Test
    fun `018 R1 - partial or invalid location fails at startup`() {
        listOf(
            mapOf("WEATHER_LATITUDE" to "41.77") to "WEATHER_LONGITUDE",
            mapOf("WEATHER_LONGITUDE" to "-7.77") to "WEATHER_LATITUDE",
            mapOf("WEATHER_LATITUDE" to "north", "WEATHER_LONGITUDE" to "-7.77") to "WEATHER_LATITUDE",
            mapOf("WEATHER_LATITUDE" to "90.77", "WEATHER_LONGITUDE" to "-7.77") to "WEATHER_LATITUDE",
            mapOf("WEATHER_LATITUDE" to "41.77", "WEATHER_LONGITUDE" to "180.77") to "WEATHER_LONGITUDE",
        ).forEach { (env, key) ->
            val failure = assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(required + env) }
            assertTrue(key in failure.message.orEmpty())
            assertFalse(".77" in failure.message.orEmpty())
        }
    }

    @Test
    fun `018 R1 - toString hides coordinates`() {
        val location = mapOf("WEATHER_LATITUDE" to "41.77", "WEATHER_LONGITUDE" to "-7.77")
        val text = AppConfig.fromEnvironment(required + location).toString()
        assertTrue("weather=<set>" in text)
        assertFalse(".77" in text)
    }

    @Test
    fun `defaults to localhost 8080`() {
        val config = AppConfig.fromEnvironment(required)
        assertEquals("127.0.0.1", config.host)
        assertEquals(8080, config.port)
    }

    @Test
    fun `rejects non-numeric port`() {
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(required + ("PORT" to "abc")) }
    }

    @Test
    fun `fails fast when a required value is missing`() {
        required.keys.forEach { key ->
            assertFailsWith<IllegalArgumentException>(key) { AppConfig.fromEnvironment(required - key) }
        }
    }

    @Test
    fun `toString never prints secrets`() {
        val text = AppConfig.fromEnvironment(required + ("GARMIN_API_KEY" to "invented-garmin-key")).toString()
        assertFalse("invented-garmin-key" in text)
        assertTrue("garminApiKey=<set>" in text)
    }

    @Test
    fun `021 R7 - cut-over dates per person with default and validation`() {
        assertEquals(LocalDate.of(2026, 11, 10), AppConfig.fromEnvironment(required).garminActivitiesFrom)
        val marina =
            required + ("MARINA_DB_PATH" to "data/marina.db") + ("MARINA_GARMIN_ACTIVITIES_FROM" to "2026-11-12")
        val config = AppConfig.fromEnvironment(marina)
        assertEquals(LocalDate.of(2026, 11, 10), config.ricardo.garminActivitiesFrom)
        assertEquals(LocalDate.of(2026, 11, 12), config.marina?.garminActivitiesFrom)
        val failure =
            assertFailsWith<IllegalArgumentException> {
                AppConfig.fromEnvironment(required + ("GARMIN_ACTIVITIES_FROM" to "11/09/2026"))
            }
        assertEquals("GARMIN_ACTIVITIES_FROM must be an ISO date (YYYY-MM-DD)", failure.message)
    }

    @Test
    fun `021 R9 - starts with only DB_PATH and ignores Strava keys`() {
        val leftovers = mapOf("STRAVA_CLIENT_ID" to "1", "SESSION_SECRET" to "zz", "STRAVA_REDIRECT_URI" to "x")
        assertEquals(AppConfig.fromEnvironment(required), AppConfig.fromEnvironment(required + leftovers))
    }

    @Test
    fun `015 R1 - intervals key is optional, trimmed and redacted`() {
        listOf(null, "", "   ").forEach { value ->
            val env = if (value == null) required else required + ("GARMIN_API_KEY" to value)
            val config = AppConfig.fromEnvironment(env)
            assertNull(config.garminApiKey)
            assertTrue("garminApiKey=<unset>" in config.toString())
        }
        val config = AppConfig.fromEnvironment(required + ("GARMIN_API_KEY" to "  invented-key  "))
        assertEquals("invented-key", config.garminApiKey)
        assertTrue("garminApiKey=<set>" in config.toString())
        assertFalse("invented-key" in config.toString())
    }

    @Test
    fun `004 R1 - training plan path optional`() {
        assertNull(AppConfig.fromEnvironment(required).trainingPlanCsvPath)
        assertNull(AppConfig.fromEnvironment(required + ("TRAINING_PLAN_CSV" to "  ")).trainingPlanCsvPath)
        val config = AppConfig.fromEnvironment(required + ("TRAINING_PLAN_CSV" to "C:/plans/build.csv"))
        assertEquals("C:/plans/build.csv", config.trainingPlanCsvPath)
    }

    @Test
    fun `004 R1 - start date defaults and must be a Monday`() {
        assertEquals(LocalDate.of(2026, 8, 17), AppConfig.fromEnvironment(required).trainingPlanStartDate)
        val start = { value: String -> AppConfig.fromEnvironment(required + ("TRAINING_PLAN_START_DATE" to value)) }
        assertEquals(LocalDate.of(2026, 8, 17), start("").trainingPlanStartDate)
        assertEquals(LocalDate.of(2026, 8, 17), start("2026-08-17").trainingPlanStartDate)
        assertEquals(
            "TRAINING_PLAN_START_DATE must be a Monday",
            assertFailsWith<IllegalArgumentException> { start("2026-08-18") }.message,
        )
        assertEquals(
            "TRAINING_PLAN_START_DATE must be an ISO date (YYYY-MM-DD)",
            assertFailsWith<IllegalArgumentException> { start("17/08/2026") }.message,
        )
    }

    @Test
    fun `004 R1 - toString hides plan path`() {
        assertTrue("trainingPlanCsv=<unset>" in AppConfig.fromEnvironment(required).toString())
        val text = AppConfig.fromEnvironment(required + ("TRAINING_PLAN_CSV" to "C:/plans/build.csv")).toString()
        assertTrue("trainingPlanCsv=<set>" in text)
        assertFalse("build.csv" in text)
    }

    private fun lthr(value: String): LthrSetting = AppConfig.fromEnvironment(required + ("LTHR_BPM" to value)).lthr

    @Test
    fun `005 R1 - LTHR_BPM unset gives NotConfigured`() {
        assertEquals(LthrSetting.NotConfigured, AppConfig.fromEnvironment(required).lthr)
        assertEquals(LthrSetting.NotConfigured, lthr("  "))
    }

    @Test
    fun `005 R1 - LTHR_BPM valid range`() {
        assertEquals(LthrSetting.Valid(Bpm(175)), lthr(" 175 "))
        assertEquals(LthrSetting.Valid(Bpm(100)), lthr("100"))
        assertEquals(LthrSetting.Valid(Bpm(220)), lthr("220"))
    }

    @Test
    fun `005 R1 - LTHR_BPM invalid values do not stop startup`() {
        listOf("abc", "175.5", "99", "221").forEach { assertEquals(LthrSetting.Invalid, lthr(it), it) }
    }

    @Test
    fun `005 R1 - toString masks LTHR`() {
        assertTrue("lthr=<unset>" in AppConfig.fromEnvironment(required).toString())
        assertTrue("lthr=<invalid>" in AppConfig.fromEnvironment(required + ("LTHR_BPM" to "abc")).toString())
        val text = AppConfig.fromEnvironment(required + ("LTHR_BPM" to "187")).toString()
        assertTrue("lthr=<set>" in text)
        assertFalse("187" in text)
    }

    private fun maxHr(value: String): MaxHrSetting = AppConfig.fromEnvironment(required + ("MAX_HR_BPM" to value)).maxHr

    @Test
    fun `008 R1 - MAX_HR_BPM unset gives default 194`() {
        assertEquals(MaxHrSetting.Default, AppConfig.fromEnvironment(required).maxHr)
        assertEquals(MaxHrSetting.Default, maxHr("  "))
        assertEquals(194, MaxHrSetting.DEFAULT_MAX_HR_BPM)
    }

    @Test
    fun `008 R1 - MAX_HR_BPM valid range`() {
        assertEquals(MaxHrSetting.Configured(Bpm(190)), maxHr(" 190 "))
        assertEquals(MaxHrSetting.Configured(Bpm(120)), maxHr("120"))
        assertEquals(MaxHrSetting.Configured(Bpm(230)), maxHr("230"))
    }

    @Test
    fun `008 R1 - MAX_HR_BPM invalid values do not stop startup`() {
        listOf("abc", "190.5", "119", "231").forEach { assertEquals(MaxHrSetting.Invalid, maxHr(it), it) }
    }

    @Test
    fun `008 R1 - toString masks max HR`() {
        assertTrue("maxHr=<default>" in AppConfig.fromEnvironment(required).toString())
        assertTrue("maxHr=<invalid>" in AppConfig.fromEnvironment(required + ("MAX_HR_BPM" to "abc")).toString())
        val text = AppConfig.fromEnvironment(required + ("MAX_HR_BPM" to "193")).toString()
        assertTrue("maxHr=<set>" in text)
        assertFalse("193" in text)
        assertEquals("Configured(<redacted>)", MaxHrSetting.Configured(Bpm(193)).toString())
    }
}
