package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.rings.RingDay
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRingDayRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.testConfig
import app.stravadashboard.testsupport.wellnessDay
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// All data here is invented.
class RingsRoutesTest {
    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val today = LocalDate.of(2026, 11, 9)
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val garminRequests = mutableListOf<HttpRequestData>()

    private fun config(key: String? = "invented-key"): AppConfig =
        testConfig().copy(lthr = LthrSetting.Valid(Bpm(170)), garminApiKey = key)

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val garmin =
            MockEngine { request ->
                garminRequests += request
                respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        application { module(config, clock, TEST_ZONE, garminEngine = garmin) }
        block()
    }

    private fun ApplicationTestBuilder.connectedBrowser(): HttpClient = browser()

    /** 3 baseline days and today: the RingsCalculatorTest recovery case plus strains. */
    private fun seed(config: AppConfig) =
        runBlocking {
            val database = DatabaseFactory.connect(config.dbPath)
            val wellness = (1L..3L).map { wellnessDay(today.minusDays(it), 50, 60.0) } + wellnessDay(today, 48, 66.0)
            ExposedWellnessRepository(database).replaceRange(today.minusDays(90), today, wellness, now)
            val rings = ExposedRingDayRepository(database)
            rings.upsert(today, RingDay(25_200, 12.94), now)
            rings.upsert(today.minusDays(1), RingDay(25_200, 14.0), now)
            rings.upsert(today.minusDays(2), RingDay(28_800, 10.0), now)
            rings.upsert(today.minusDays(3), RingDay(null, 9.0), now)
        }

    @Test
    fun `R7 - rings response shape and fill`() {
        val config = config()
        seed(config)
        app(config) {
            val browser = connectedBrowser()
            garminRequests.clear()
            val response = browser.get("/api/rings")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """{"date":"2026-11-09",""" +
                    """"sleep":{"percent":82,"fill":0.82,"deltaLabel":null},""" +
                    """"recovery":{"percent":75,"zone":"green","fill":0.75,"deltaLabel":null},""" +
                    """"strain":{"value":12.9,"fill":0.614,"deltaLabel":"+1.9 vs 30-day avg"}}""",
                response.bodyAsText(),
            )
            // R7 AC7.5: cache only, no external calls.
            assertTrue(garminRequests.isEmpty())
        }
    }

    @Test
    fun `R7 - no garmin key gives three nulls`() =
        app(config(key = null)) {
            val response = connectedBrowser().get("/api/rings")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"date":"2026-11-09","sleep":null,"recovery":null,"strain":null}""", response.bodyAsText())
        }

    @Test
    fun `R5 - history returns one entry per day ascending and today matches rings`() {
        val config = config()
        seed(config)
        app(config) {
            val browser = connectedBrowser()
            garminRequests.clear()
            val response = browser.get("/api/rings/history?days=7")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            val dates = Regex(""""date":"([0-9-]+)"""").findAll(body).map { it.groupValues[1] }.toList()
            assertEquals((6L downTo 0L).map { today.minusDays(it).toString() }, dates)
            assertTrue(
                body.endsWith(
                    """{"date":"2026-11-09","sleepPercent":82,"recoveryPercent":75,"recoveryZone":"green",""" +
                        """"strain":12.9}]}""",
                ),
                body,
            )
            // A day with no row at all is all nulls, not zeros.
            assertTrue(
                """{"date":"2026-11-03","sleepPercent":null,"recoveryPercent":null,"recoveryZone":null,"strain":null}"""
                    in body,
            )
            assertEquals(30, Regex("\"date\"").findAll(browser.get("/api/rings/history?days=30").bodyAsText()).count())
            assertEquals(90, Regex("\"date\"").findAll(browser.get("/api/rings/history?days=90").bodyAsText()).count())
            // AC5.9: cache only, no external calls.
            assertTrue(garminRequests.isEmpty())
        }
    }

    @Test
    fun `R5 - invalid days gives 400`() =
        app(config()) {
            val browser = connectedBrowser()
            for (query in listOf("", "?days=14", "?days=0", "?days=abc")) {
                val response = browser.get("/api/rings/history$query")
                assertEquals(HttpStatusCode.BadRequest, response.status, query)
                assertEquals(
                    """{"error":"invalid_days","message":"days must be 7, 30 or 90."}""",
                    response.bodyAsText(),
                )
            }
        }

    @Test
    fun `R5 - no garmin key gives all nulls`() =
        app(config(key = null)) {
            val body = connectedBrowser().get("/api/rings/history?days=7").bodyAsText()
            assertEquals(
                7,
                Regex(
                    """"sleepPercent":null,"recoveryPercent":null,"recoveryZone":null,"strain":null""",
                ).findAll(body).count(),
            )
        }

    @Test
    fun `R4 - rings response unchanged by older rows`() {
        val config = config()
        seed(config)
        runBlocking {
            val database = DatabaseFactory.connect(config.dbPath)
            val old = (100L..119L).map { wellnessDay(today.minusDays(it), 40, 90.0) }
            ExposedWellnessRepository(database).replaceRange(today.minusDays(119), today.minusDays(100), old, now)
            val rings = ExposedRingDayRepository(database)
            (63L..92L).forEach { rings.upsert(today.minusDays(it), RingDay(10_000, 20.0), now) }
        }
        app(config) {
            assertEquals(
                """{"date":"2026-11-09",""" +
                    """"sleep":{"percent":82,"fill":0.82,"deltaLabel":null},""" +
                    """"recovery":{"percent":75,"zone":"green","fill":0.75,"deltaLabel":null},""" +
                    """"strain":{"value":12.9,"fill":0.614,"deltaLabel":"+1.9 vs 30-day avg"}}""",
                connectedBrowser().get("/api/rings").bodyAsText(),
            )
        }
    }
}
