package app.stravadashboard

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
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
            "STRAVA_CLIENT_ID" to "12345",
            "STRAVA_CLIENT_SECRET" to "client-secret-value",
            "STRAVA_REDIRECT_URI" to "http://localhost:4200/auth/strava/callback",
            "SESSION_SECRET" to "ab".repeat(32),
            "DB_PATH" to "data/test.db",
        )

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
    fun `rejects a session secret shorter than 32 bytes or not hex`() {
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(required + ("SESSION_SECRET" to "ab")) }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnvironment(required + ("SESSION_SECRET" to "zz".repeat(32)))
        }
    }

    @Test
    fun `toString never prints secrets`() {
        val text = AppConfig.fromEnvironment(required).toString()
        assertFalse("client-secret-value" in text)
        assertFalse("ab".repeat(32) in text)
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

    @Test
    fun `secure cookies only for https redirect`() {
        assertFalse(AppConfig.fromEnvironment(required).secureCookies)
        val https = required + ("STRAVA_REDIRECT_URI" to "https://example.test/auth/strava/callback")
        assertTrue(AppConfig.fromEnvironment(https).secureCookies)
    }
}
