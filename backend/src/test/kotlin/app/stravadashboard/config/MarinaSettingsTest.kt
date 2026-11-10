package app.stravadashboard.config

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarinaSettingsTest {
    private val base =
        mapOf(
            "DB_PATH" to "data/ricardo.db",
        )
    private val group =
        mapOf(
            "MARINA_DB_PATH" to "data/marina.db",
        )

    private fun load(vararg extra: Pair<String, String>): AppConfig = AppConfig.fromEnvironment(base + group + extra)

    @Test
    fun `R1 - marina optional when group unset`() {
        assertNull(AppConfig.fromEnvironment(base).marina)
    }

    @Test
    fun `021 R7 - Marina is configured by her database path alone`() {
        val marina = AppConfig.fromEnvironment(base + mapOf("MARINA_DB_PATH" to "data/marina.db")).marina
        assertEquals("data/marina.db", marina?.dbPath)
        assertEquals(null, marina?.garminApiKey)
    }

    @Test
    fun `R1 - shared db path fails`() {
        assertFailsWith<IllegalArgumentException> { load("MARINA_DB_PATH" to "data/../data/ricardo.db") }
    }

    @Test
    fun `R1 - plan needs a monday start date`() {
        val csv = "MARINA_TRAINING_PLAN_CSV" to "marina.csv"
        val start = "MARINA_TRAINING_PLAN_START_DATE"
        assertFailsWith<IllegalArgumentException> { load(csv) }
        assertFailsWith<IllegalArgumentException> { load(csv, start to "soon") }
        assertFailsWith<IllegalArgumentException> { load(csv, start to "2026-08-04") }
        assertEquals(LocalDate.of(2026, 8, 3), load(csv, start to "2026-08-03").marina?.trainingPlanStartDate)
    }

    @Test
    fun `R1 - goal pace needs a start date`() {
        assertFailsWith<IllegalArgumentException> { load("MARINA_MARATHON_GOAL_PACE" to "4:15") }
    }

    @Test
    fun `R7 - marina build has 18 weeks and ricardo keeps 16`() {
        val config = load()
        assertEquals(18, config.marina?.buildWeeks)
        assertEquals(16, config.ricardo.buildWeeks)
    }

    @Test
    fun `R1 - toString redacts marina`() {
        val config =
            load(
                "MARINA_GARMIN_API_KEY" to "invented-garmin-key",
                "MARINA_LTHR_BPM" to "163",
                "MARINA_MAX_HR_BPM" to "187",
            )
        val marina = assertNotNull(config.marina)
        for (text in listOf(config.toString(), marina.toString())) {
            listOf("2222", "invented-secret", "invented-garmin-key", "marina.db", "163", "187").forEach {
                assertFalse(it in text, "toString leaked '$it'")
            }
        }
        assertTrue("marina=<set>" in config.toString())
    }
}
