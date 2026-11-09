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
import app.stravadashboard.testsupport.testConfig
import app.stravadashboard.testsupport.wellnessDay
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
    private val stravaRequests = mutableListOf<HttpRequestData>()

    private fun config(key: String? = "invented-key"): AppConfig =
        testConfig().copy(lthr = LthrSetting.Valid(Bpm(170)), garminApiKey = key)

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val strava =
            MockEngine { request ->
                stravaRequests += request
                if (request.url.encodedPath == "/oauth/token") {
                    respond(
                        """{"access_token":"a","refresh_token":"r","expires_at":${now.epochSecond + 99_999},
                           "athlete":{"id":7}}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                } else {
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                }
            }
        val garmin =
            MockEngine { request ->
                garminRequests += request
                respond("{}", HttpStatusCode.OK, jsonHeaders)
            }
        application { module(config, clock, TEST_ZONE, strava, garminEngine = garmin) }
        block()
    }

    private suspend fun ApplicationTestBuilder.connectedBrowser(): HttpClient {
        val browser =
            createClient {
                install(HttpCookies)
                followRedirects = false
            }
        val login = browser.get("/auth/strava/login")
        val state = Url(requireNotNull(login.headers[HttpHeaders.Location])).parameters["state"]
        browser.get("/auth/strava/callback?state=$state&code=invented&scope=read,activity:read_all")
        return browser
    }

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
            stravaRequests.clear()
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
            assertTrue(stravaRequests.isEmpty())
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
    fun `R7 - rings require session`() =
        app(config()) {
            val response = client.get("/api/rings")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue("not_connected" in response.bodyAsText())
        }
}
