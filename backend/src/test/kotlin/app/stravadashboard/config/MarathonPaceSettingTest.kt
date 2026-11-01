package app.stravadashboard.config

import app.stravadashboard.domain.model.SecondsPerKm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarathonPaceSettingTest {
    private val required =
        mapOf(
            "STRAVA_CLIENT_ID" to "12345",
            "STRAVA_CLIENT_SECRET" to "client-secret-value",
            "STRAVA_REDIRECT_URI" to "http://localhost:4200/auth/strava/callback",
            "SESSION_SECRET" to "ab".repeat(32),
            "DB_PATH" to "data/test.db",
        )

    private fun config(value: String?): AppConfig =
        AppConfig.fromEnvironment(if (value == null) required else required + ("MARATHON_GOAL_PACE" to value))

    @Test
    fun `R1 - MARATHON_GOAL_PACE unset is not configured`() {
        assertEquals(MarathonPaceSetting.NotConfigured, config(null).marathonPace)
        assertEquals(MarathonPaceSetting.NotConfigured, config("").marathonPace)
        assertEquals(MarathonPaceSetting.NotConfigured, config("   ").marathonPace)
    }

    @Test
    fun `R1 - MARATHON_GOAL_PACE valid range`() {
        assertEquals(MarathonPaceSetting.Valid(SecondsPerKm(255)), config(" 4:15 ").marathonPace)
        assertEquals(MarathonPaceSetting.Valid(SecondsPerKm(150)), config("2:30").marathonPace)
        assertEquals(MarathonPaceSetting.Valid(SecondsPerKm(480)), config("8:00").marathonPace)
    }

    @Test
    fun `R1 - MARATHON_GOAL_PACE invalid values do not stop startup`() {
        listOf("255", "4:5", "4:60", "4.15", "abc", "2:29", "8:01").forEach {
            assertEquals(MarathonPaceSetting.Invalid, config(it).marathonPace, it)
        }
    }

    @Test
    fun `R1 - toString masks marathon pace`() {
        assertTrue("marathonPace=<unset>" in config(null).toString())
        assertTrue("marathonPace=<invalid>" in config("abc").toString())
        val text = config("4:17").toString()
        assertTrue("marathonPace=<set>" in text)
        assertFalse("4:17" in text)
        assertFalse("257" in text)
        assertEquals("Valid(<redacted>)", MarathonPaceSetting.Valid(SecondsPerKm(257)).toString())
    }
}
