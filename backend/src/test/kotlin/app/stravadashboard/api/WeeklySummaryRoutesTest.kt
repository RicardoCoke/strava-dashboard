package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklySummaryRoutesTest {
    // Saturday 2026-10-31.
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val garmin = GarminFake()

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(config, clock, TEST_ZONE, garminEngine = garmin.engine) }
        block()
    }

    private fun ApplicationTestBuilder.connectedBrowser(): HttpClient = browser()

    /** Five 10 km runs, Mon 2026-10-26 to Fri 2026-10-30, named so a leak would show. */
    private fun seedWeek(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            runs.upsertSummaries(
                (0L..4L).map {
                    val start =
                        LocalDate
                            .of(2026, 10, 26)
                            .plusDays(it)
                            .atTime(7, 0)
                            .atZone(TEST_ZONE)
                            .toInstant()
                    summary(700 + it, start, name = "Secret Loop")
                },
            )
        }

    @Test
    fun `R10 - returns summaries json`() {
        val config = testConfig()
        app(config) {
            val response = connectedBrowser().also { seedWeek(config) }.get("/api/weekly-summaries")
            assertEquals(HttpStatusCode.OK, response.status)
            val summary =
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject
                    .getValue("summaries")
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals(setOf("weekStart", "weekEnd", "inProgress", "runCount", "revision", "notes"), summary.keys)
            assertEquals("2026-10-26", summary["weekStart"]?.jsonPrimitive?.content)
            assertEquals("2026-11-01", summary["weekEnd"]?.jsonPrimitive?.content)
            assertEquals("true", summary["inProgress"]?.jsonPrimitive?.content)
            assertEquals("5", summary["runCount"]?.jsonPrimitive?.content)
            assertEquals(
                """{"metric":"distance","tone":"neutral",""" +
                    """"text":"Volume 50.0 km over 5 runs (+50.0 km vs last week)"}""",
                summary
                    .getValue("notes")
                    .jsonArray
                    .first()
                    .toString(),
            )
        }
    }

    @Test
    fun `R10 - no names ids or tokens and no external calls`() {
        val config = testConfig()
        app(config) {
            val body = connectedBrowser().also { seedWeek(config) }.get("/api/weekly-summaries").bodyAsText()
            listOf("Secret Loop", "700", "access_token").forEach { assertTrue(it !in body, it) }
            assertTrue(garmin.requests.isEmpty())
        }
    }

    @Test
    fun `R10 - empty list`() =
        app(testConfig()) {
            assertEquals("""{"summaries":[]}""", connectedBrowser().get("/api/weekly-summaries").bodyAsText())
        }
}
