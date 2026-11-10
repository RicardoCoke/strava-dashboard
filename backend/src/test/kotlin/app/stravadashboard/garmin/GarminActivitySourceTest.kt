package app.stravadashboard.garmin

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RUN_SPORT_TYPE
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.testsupport.FitLap
import app.stravadashboard.testsupport.FitRecord
import app.stravadashboard.testsupport.GARMIN_LIST_PATH
import app.stravadashboard.testsupport.GARMIN_TOKEN_PATH
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fitZip
import app.stravadashboard.testsupport.fixedClock
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// All keys, tokens and activities here are invented.
class GarminActivitySourceTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private var fitFile: ByteArray? = fitZip(listOf(FitRecord(0, 0f, 3f, 140), FitRecord(1, 3f, 3f, 141)))
    private var detailJson = """{"description":"6x800m @ 3:45","summaryDTO":{"movingDuration":2712.6}}"""

    private fun resource(name: String): String = requireNotNull(javaClass.getResource("/garmin/$name")).readText()

    private fun MockRequestHandleScope.garmin(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            path == GARMIN_TOKEN_PATH -> respond("invented-access-token", HttpStatusCode.OK)
            path == GARMIN_LIST_PATH -> respond(resource("activity-list.json"), HttpStatusCode.OK, json)
            path.startsWith("/activity-service/") -> respond(detailJson, HttpStatusCode.OK, json)
            else ->
                fitFile?.let { respond(ByteReadChannel(it), HttpStatusCode.OK) }
                    ?: respond("", HttpStatusCode.NotFound)
        }
    }

    private fun source(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { garmin(it) },
    ): GarminActivitySource {
        val http =
            garminHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            )
        return GarminActivitySource(
            GarminConnect(http, "invented-garmin-key", fixedClock(now)),
            fixedClock(now),
            TEST_ZONE,
        )
    }

    private fun dataCalls(): List<String> = requests.map { it.url.encodedPath }.filter { it != GARMIN_TOKEN_PATH }

    @Test
    fun `R3 - list maps running types to Run summaries`() {
        val listed = runBlocking { source().listActivities(now.minus(Duration.ofDays(3)), 1, 200) }

        val summaries = assertIs<SourceResult.Success<List<ActivitySummary>>>(listed).value
        assertEquals(
            ActivitySummary(
                id = ActivityId(21_000_000_001),
                name = "Invented 6x800m",
                sportType = RUN_SPORT_TYPE,
                startUtc = Instant.parse("2026-11-10T06:30:00Z"),
                startLocal = LocalDateTime.parse("2026-11-10T07:30:00"),
                distance = Meters(9_800.5),
                averageHeartRateBpm = 152.0,
                trainer = false,
            ),
            summaries[0],
        )
        assertEquals(listOf(RUN_SPORT_TYPE, true), summaries[1].let { listOf(it.sportType, it.trainer) })
        assertEquals(listOf(RUN_SPORT_TYPE, false, ""), summaries[2].let { listOf(it.sportType, it.trainer, it.name) })
        assertEquals("cycling", summaries[3].sportType)
        // The one with an unreadable start time is skipped, not the page.
        assertEquals(4, summaries.size)
    }

    @Test
    fun `R3 - list query uses start limit and dates, and filters types itself`() {
        runBlocking { source().listActivities(Instant.parse("2026-11-10T00:00:00Z"), 3, 200) }

        val list = requests.single { it.url.encodedPath == GARMIN_LIST_PATH }
        assertEquals(
            "https://personal.connectapi.garmin.com$GARMIN_LIST_PATH",
            list.url.toString().substringBefore('?'),
        )
        assertEquals(
            mapOf(
                "start" to "400",
                "limit" to "200",
                "startDate" to "2026-11-10",
                "endDate" to "2026-11-13",
                "sortOrder" to "asc",
            ),
            list.url.parameters
                .names()
                .associateWith { list.url.parameters[it] },
        )
        assertEquals("Bearer invented-access-token", list.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `R4 - description is stored exactly as written, moving time half-up`() {
        val detail =
            assertIs<SourceResult.Success<ActivityDetail>>(runBlocking { source().activityDetail(ActivityId(1)) })
        assertEquals("6x800m @ 3:45", detail.value.description)
        assertEquals(Seconds(2713), detail.value.movingTime)
    }

    @Test
    fun `R4 - moving time falls back to duration, then zero`() {
        detailJson = """{"summaryDTO":{"duration":100.5}}"""
        assertEquals(
            Seconds(101),
            (
                runBlocking {
                    source().activityDetail(ActivityId(1))
                } as SourceResult.Success
            ).value.movingTime,
        )
        detailJson = """{}"""
        assertEquals(
            Seconds(0),
            (
                runBlocking {
                    source().activityDetail(ActivityId(1))
                } as SourceResult.Success
            ).value.movingTime,
        )
    }

    @Test
    fun `R4 - laps and splits come from the FIT file`() {
        fitFile =
            fitZip(
                records = (0..300).map { FitRecord(it, distance = it * 4f, heartRate = 150) },
                laps = listOf(FitLap(300f, 1200f, 150)),
            )
        val detail = (runBlocking { source().activityDetail(ActivityId(1)) } as SourceResult.Success).value
        assertEquals(1, detail.laps.size)
        assertEquals(listOf(1000.0, 200.0), detail.splits.map { it.distance.value })
    }

    @Test
    fun `R4 - missing or corrupt FIT gives empty laps and splits`() {
        for (file in listOf(null, "corrupt".toByteArray())) {
            fitFile = file
            val detail =
                assertIs<SourceResult.Success<ActivityDetail>>(runBlocking { source().activityDetail(ActivityId(1)) })
            assertEquals("6x800m @ 3:45", detail.value.description)
            assertEquals(emptyList(), detail.value.laps)
            assertEquals(emptyList(), detail.value.splits)
            assertEquals(SourceResult.NotFound, runBlocking { source().speedStream(ActivityId(1)) })
        }
    }

    @Test
    fun `R4 - detail 404 is not found`() {
        val result =
            runBlocking {
                source {
                    if (it.url.encodedPath ==
                        GARMIN_TOKEN_PATH
                    ) {
                        garmin(it)
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }.activityDetail(ActivityId(1))
            }
        assertEquals(SourceResult.NotFound, result)
    }

    @Test
    fun `R5 - FIT is downloaded once for detail and streams`() {
        val source = source()
        runBlocking {
            source.activityDetail(ActivityId(7))
            assertIs<SourceResult.Success<*>>(source.speedStream(ActivityId(7)))
            assertIs<SourceResult.Success<*>>(source.heartRateStream(ActivityId(7)))
            assertIs<SourceResult.Success<*>>(source.driftStream(ActivityId(7)))
        }
        assertEquals(1, dataCalls().count { it == "/download-service/files/activity/7" })
    }

    @Test
    fun `R8 - 429 maps to rateLimited with Retry-After`() {
        val limited =
            source {
                if (it.url.encodedPath == GARMIN_TOKEN_PATH) {
                    garmin(it)
                } else {
                    respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "120"))
                }
            }
        assertEquals(SourceResult.RateLimited(now.plusSeconds(120)), runBlocking { limited.speedStream(ActivityId(1)) })
    }

    @Test
    fun `R8 - rejected token is refreshed once then unavailable`() {
        var rejectData = false
        val source =
            source {
                if (rejectData &&
                    it.url.encodedPath != GARMIN_TOKEN_PATH
                ) {
                    respond("", HttpStatusCode.Unauthorized)
                } else {
                    garmin(it)
                }
            }
        runBlocking { source.listActivities(now, 1, 200) }
        rejectData = true
        requests.clear()

        assertEquals(SourceResult.Unavailable, runBlocking { source.listActivities(now, 1, 200) })
        // The cached token, one fresh token, one retry.
        assertEquals(listOf(GARMIN_LIST_PATH, GARMIN_TOKEN_PATH, GARMIN_LIST_PATH), requests.map { it.url.encodedPath })
    }

    @Test
    fun `R8 - network failure maps to garminUnavailable`() {
        val down = source { throw IOException("invented") }
        assertEquals(SourceResult.Unavailable, runBlocking { down.listActivities(now, 1, 200) })
    }
}
