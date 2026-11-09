package app.stravadashboard.garmin

import app.stravadashboard.domain.rings.DayFetchResult
import app.stravadashboard.domain.rings.DayReading
import app.stravadashboard.domain.rings.HrSample
import app.stravadashboard.domain.wellness.WellnessFetchResult
import app.stravadashboard.testsupport.fixedClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// All keys, tokens and values here are invented.
class GarminRingDayTest {
    private val day = LocalDate.of(2026, 11, 8)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val base = "https://personal.connectapi.garmin.com"

    private fun MockRequestHandleScope.fixtures(request: HttpRequestData): HttpResponseData =
        when {
            request.url.encodedPath.endsWith("/token") -> respond("invented-access-token", HttpStatusCode.OK)
            "dailySleep" in request.url.encodedPath ->
                respond(
                    """{"calendarDate":"2026-11-08","sleepTimeSeconds":26100,"napTimeSeconds":0}""",
                    HttpStatusCode.OK,
                    json,
                )
            else ->
                respond(
                    """{"calendarDate":"2026-11-08","heartRateValues":""" +
                        """[[1762588800000,58],[1762588920000,null],[1762589040000,101.6],[null,70]]}""",
                    HttpStatusCode.OK,
                    json,
                )
        }

    private fun fetch(
        withHeartRate: Boolean = true,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { fixtures(it) },
    ): DayFetchResult {
        val http =
            garminHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            )
        return runBlocking {
            GarminWellnessSource(GarminConnect(http, "invented-key", fixedClock())).fetchDay(day, withHeartRate)
        }
    }

    @Test
    fun `R2 - daily heart rate request shape`() {
        fetch()
        val (_, sleep, hr) = requests
        assertEquals("$base/wellness-service/wellness/dailySleep?date=2026-11-08", sleep.url.toString())
        assertEquals("$base/wellness-service/wellness/dailyHeartRate?date=2026-11-08", hr.url.toString())
        listOf(sleep, hr).forEach {
            assertEquals("Bearer invented-access-token", it.headers[HttpHeaders.Authorization])
            assertEquals("application/json", it.headers[HttpHeaders.Accept])
        }
    }

    @Test
    fun `R1 - maps sleep and heart rate samples`() {
        val t0 = Instant.ofEpochMilli(1_762_588_800_000)
        assertEquals(
            DayFetchResult.Ok(
                DayReading(
                    26_100,
                    listOf(HrSample(t0, 58), HrSample(t0.plusSeconds(120), null), HrSample(t0.plusSeconds(240), 102)),
                ),
            ),
            fetch(),
        )
    }

    @Test
    fun `R2 - without LTHR only sleep is requested`() {
        assertEquals(DayFetchResult.Ok(DayReading(26_100, null)), fetch(withHeartRate = false))
        assertEquals(2, requests.size)
    }

    @Test
    fun `R2 - 204 means no data`() {
        val result =
            fetch { request ->
                if (request.url.encodedPath.endsWith("/token")) {
                    respond("invented-access-token", HttpStatusCode.OK)
                } else {
                    respond("", HttpStatusCode.NoContent)
                }
            }
        assertEquals(DayFetchResult.Ok(DayReading(null, emptyList())), result)
    }

    @Test
    fun `R3 - 429 on heart rate is rate limited`() {
        val result =
            fetch { request ->
                if ("dailyHeartRate" in request.url.encodedPath) {
                    respond("{}", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "120"))
                } else {
                    fixtures(request)
                }
            }
        val failed = assertIs<DayFetchResult.Failed>(result)
        assertIs<WellnessFetchResult.RateLimited>(failed.result)
    }
}
