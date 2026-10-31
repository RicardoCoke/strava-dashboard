package app.stravadashboard.strava

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.fixedClock
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StravaClientTest {
    private class InMemoryTokens(
        var tokens: StoredTokens?,
    ) : TokenRepository {
        override suspend fun find(): StoredTokens? = tokens

        override suspend fun save(tokens: StoredTokens) {
            this.tokens = tokens
        }

        override suspend fun delete() {
            tokens = null
        }
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val tokens =
        InMemoryTokens(StoredTokens(7, "access-old", "refresh-old", NOW.plus(Duration.ofHours(2)), "activity:read_all"))

    private fun resource(name: String): String = requireNotNull(javaClass.getResource("/strava/$name")).readText()

    private fun source(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): StravaActivitySource {
        val http =
            stravaHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            )
        val oauth = StravaOAuthClient(http, "12345", "client-secret", fixedClock())
        return StravaActivitySource(StravaClient(http, StravaTokenProvider(tokens, oauth, fixedClock()), fixedClock()))
    }

    private fun MockRequestHandleScope.refreshed(): HttpResponseData =
        respond(
            """{"access_token":"access-new","refresh_token":"refresh-new","expires_at":${NOW.epochSecond + 21600}}""",
            HttpStatusCode.OK,
            json,
        )

    @Test
    fun `R4 - list maps summaries and sends after page and per_page`() {
        val result =
            runBlocking {
                source { respond(resource("activities.json"), HttpStatusCode.OK, json) }
                    .listActivities(Instant.ofEpochSecond(1_780_000_000), 2, 200)
            }

        val list = assertIs<SourceResult.Success<List<app.stravadashboard.domain.ports.ActivitySummary>>>(result).value
        assertEquals(listOf("Run", "Ride"), list.map { it.sportType })
        assertEquals("2026-10-25T23:30", list.first().startLocal.toString())
        assertEquals(Instant.parse("2026-10-26T03:30:00Z"), list.first().startUtc)
        val url = requests.single().url
        assertEquals("/api/v3/athlete/activities", url.encodedPath)
        assertEquals("1780000000", url.parameters["after"])
        assertEquals("2", url.parameters["page"])
        assertEquals("200", url.parameters["per_page"])
        assertEquals("Bearer access-old", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `008 R2 - detail maps moving time and lap heart rate`() {
        val result =
            runBlocking {
                source {
                    respond(
                        resource("activity-detail.json"),
                        HttpStatusCode.OK,
                        json,
                    )
                }.activityDetail(ActivityId(111))
            }
        val detail = assertIs<SourceResult.Success<ActivityDetail>>(result).value
        assertEquals(3000, detail.movingTime.value)
        assertEquals(listOf(140.0, null), detail.laps.map { it.averageHeartRateBpm })
    }

    @Test
    fun `008 R2 - missing moving_time maps to zero`() {
        val result =
            runBlocking {
                source { respond("""{"id":1,"laps":[]}""", HttpStatusCode.OK, json) }.activityDetail(ActivityId(1))
            }
        assertEquals(0, assertIs<SourceResult.Success<ActivityDetail>>(result).value.movingTime.value)
    }

    @Test
    fun `R4 - streams request only time and velocity_smooth`() {
        val result =
            runBlocking {
                source {
                    respond(
                        resource("streams.json"),
                        HttpStatusCode.OK,
                        json,
                    )
                }.speedStream(ActivityId(9))
            }

        val stream = assertIs<SourceResult.Success<app.stravadashboard.domain.model.SpeedStream>>(result).value
        assertEquals(5, stream.times.size)
        assertEquals("time,velocity_smooth", requests.single().url.parameters["keys"])
        assertEquals("true", requests.single().url.parameters["key_by_type"])
    }

    @Test
    fun `005 R3 - HR stream request asks only time and heartrate`() {
        val result =
            runBlocking {
                source { respond(resource("streams-heartrate.json"), HttpStatusCode.OK, json) }
                    .heartRateStream(ActivityId(9))
            }

        val stream = assertIs<SourceResult.Success<HeartRateStream>>(result).value
        assertEquals(listOf(140, 140, 160, 180), stream.bpm.map { it.value })
        assertEquals("/api/v3/activities/9/streams", requests.single().url.encodedPath)
        assertEquals("time,heartrate", requests.single().url.parameters["keys"])
        assertEquals("true", requests.single().url.parameters["key_by_type"])
    }

    @Test
    fun `005 R3 - 404 or missing heartrate maps to not found`() {
        val missing = resource("streams-heartrate-missing.json")
        listOf(
            source { respond("{}", HttpStatusCode.NotFound, json) },
            source { respond(missing, HttpStatusCode.OK, json) },
            source { respond("""{"time":{"data":[0,1,2]},"heartrate":{"data":[140,150]}}""", HttpStatusCode.OK, json) },
            source { respond("""{"time":{"data":[0]},"heartrate":{"data":[140]}}""", HttpStatusCode.OK, json) },
        ).forEach { assertEquals(SourceResult.NotFound, runBlocking { it.heartRateStream(ActivityId(9)) }) }
    }

    @Test
    fun `005 R3 - 401 on HR stream refreshes once`() {
        var streamCalls = 0
        val result =
            runBlocking {
                source { request ->
                    if (request.url.encodedPath == "/oauth/token") {
                        refreshed()
                    } else {
                        streamCalls++
                        if (streamCalls == 1) {
                            respond("{}", HttpStatusCode.Unauthorized, json)
                        } else {
                            respond(resource("streams-heartrate.json"), HttpStatusCode.OK, json)
                        }
                    }
                }.heartRateStream(ActivityId(9))
            }
        assertIs<SourceResult.Success<HeartRateStream>>(result)
        assertEquals(2, streamCalls)
    }

    @Test
    fun `R4 - missing streams map to not found`() {
        assertEquals(
            SourceResult.NotFound,
            runBlocking { source { respond("{}", HttpStatusCode.NotFound, json) }.speedStream(ActivityId(9)) },
        )
        assertEquals(
            SourceResult.NotFound,
            runBlocking {
                source {
                    respond(
                        """{"time":{"data":[0,1]}}""",
                        HttpStatusCode.OK,
                        json,
                    )
                }.speedStream(ActivityId(9))
            },
        )
    }

    @Test
    fun `R3 - token near expiry is refreshed before api call`() {
        tokens.tokens = tokens.tokens?.copy(expiresAt = NOW.plus(Duration.ofMinutes(4)))

        runBlocking {
            source { request ->
                if (request.url.encodedPath == "/oauth/token") refreshed() else respond("[]", HttpStatusCode.OK, json)
            }.listActivities(NOW, 1, 200)
        }

        assertEquals(listOf("/oauth/token", "/api/v3/athlete/activities"), requests.map { it.url.encodedPath })
        assertEquals("Bearer access-new", requests.last().headers[HttpHeaders.Authorization])
        assertEquals("refresh-new", tokens.tokens?.refreshToken)
        assertEquals(Instant.ofEpochSecond(NOW.epochSecond + 21600), tokens.tokens?.expiresAt)
    }

    @Test
    fun `R3 - 401 triggers one refresh and one retry`() {
        val result =
            runBlocking {
                source { request ->
                    when {
                        request.url.encodedPath == "/oauth/token" -> refreshed()
                        request.headers[HttpHeaders.Authorization] == "Bearer access-old" ->
                            respond(
                                "",
                                HttpStatusCode.Unauthorized,
                            )
                        else -> respond("[]", HttpStatusCode.OK, json)
                    }
                }.listActivities(NOW, 1, 200)
            }

        assertIs<SourceResult.Success<*>>(result)
        assertEquals(3, requests.size)
    }

    @Test
    fun `R3 - failed refresh deletes tokens and returns reauth required`() {
        val result =
            runBlocking {
                source { request ->
                    if (request.url.encodedPath == "/oauth/token") {
                        respond("""{"message":"Bad Request"}""", HttpStatusCode.BadRequest, json)
                    } else {
                        respond("", HttpStatusCode.Unauthorized)
                    }
                }.listActivities(NOW, 1, 200)
            }

        assertEquals(SourceResult.ReauthRequired, result)
        assertNull(tokens.tokens)
    }

    @Test
    fun `R3 - retried call still 401 deletes tokens`() {
        val result =
            runBlocking {
                source { request ->
                    if (request.url.encodedPath ==
                        "/oauth/token"
                    ) {
                        refreshed()
                    } else {
                        respond("", HttpStatusCode.Unauthorized)
                    }
                }.listActivities(NOW, 1, 200)
            }

        assertEquals(SourceResult.ReauthRequired, result)
        assertNull(tokens.tokens)
    }

    @Test
    fun `R5 - 429 stops sync and sets retryAfter from rate limit headers`() {
        val limited =
            headersOf(
                "X-ReadRateLimit-Limit" to listOf("100,1000"),
                "X-ReadRateLimit-Usage" to listOf("100,400"),
            )
        val result =
            runBlocking {
                source {
                    respond(
                        "",
                        HttpStatusCode.TooManyRequests,
                        limited,
                    )
                }.activityDetail(ActivityId(1))
            }
        assertEquals(SourceResult.RateLimited(Instant.parse("2026-10-29T10:15:00Z")), result)

        val daily =
            headersOf("X-RateLimit-Limit" to listOf("200,2000"), "X-RateLimit-Usage" to listOf("150,2000"))
        assertEquals(Instant.parse("2026-10-30T00:00:00Z"), RateLimitHeaders.retryAfter(daily, NOW))
    }

    @Test
    fun `R5 - network failure and 5xx map to unavailable`() {
        assertEquals(
            SourceResult.Unavailable,
            runBlocking { source { throw IOException("connection reset") }.activityDetail(ActivityId(1)) },
        )
        assertEquals(
            SourceResult.Unavailable,
            runBlocking { source { respond("", HttpStatusCode.BadGateway) }.speedStream(ActivityId(1)) },
        )
    }

    @Test
    fun `R5 - timeout maps to unavailable`() {
        assertEquals(
            SourceResult.Unavailable,
            runBlocking {
                source { request -> throw HttpRequestTimeoutException(request.url.toString(), 15_000) }
                    .activityDetail(ActivityId(1))
            },
        )
    }

    @Test
    fun `R5 - malformed JSON maps to unavailable and logs no body`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        val result =
            try {
                runBlocking {
                    source {
                        respond(
                            """[{"id":5,"name":"Invented secret name","start_date":42""",
                            HttpStatusCode.OK,
                            json,
                        )
                    }.listActivities(NOW, 1, 200)
                }
            } finally {
                root.detachAppender(appender)
            }

        assertEquals(SourceResult.Unavailable, result)
        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        assertFalse("Invented secret name" in logged)
    }

    @Test
    fun `R3 - malformed token response is a failed refresh, not a crash`() {
        tokens.tokens = tokens.tokens?.copy(expiresAt = NOW)
        val result =
            runBlocking {
                source { request ->
                    if (request.url.encodedPath == "/oauth/token") {
                        respond("""{"access_token":"leaked-access","expires_at":"soon"}""", HttpStatusCode.OK, json)
                    } else {
                        respond("[]", HttpStatusCode.OK, json)
                    }
                }.listActivities(NOW, 1, 200)
            }
        assertEquals(SourceResult.Unavailable, result)
    }

    @Test
    fun `R3 - sync logs contain no tokens activity text or stream values`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            tokens.tokens = tokens.tokens?.copy(expiresAt = NOW)
            val src =
                source { request ->
                    when (request.url.encodedPath) {
                        "/oauth/token" -> refreshed()
                        "/api/v3/athlete/activities" -> respond(resource("activities.json"), HttpStatusCode.OK, json)
                        "/api/v3/activities/111/streams" -> respond(resource("streams.json"), HttpStatusCode.OK, json)
                        "/api/v3/activities/222/streams" ->
                            respond(resource("streams-heartrate.json"), HttpStatusCode.OK, json)
                        else -> respond(resource("activity-detail.json"), HttpStatusCode.OK, json)
                    }
                }
            runBlocking {
                src.listActivities(NOW, 1, 200)
                src.activityDetail(ActivityId(111))
                src.speedStream(ActivityId(111))
                src.heartRateStream(ActivityId(222))
            }
        } finally {
            root.detachAppender(appender)
        }

        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        (SECRETS_AND_TEXT + STREAM_VALUES).forEach { assertFalse(it in logged, "log leaked '$it'") }
        assertTrue(requests.isNotEmpty())
    }

    private companion object {
        val SECRETS_AND_TEXT =
            listOf(
                "access-old",
                "access-new",
                "refresh-old",
                "refresh-new",
                "Bearer",
                "Invented tempo",
                "Treadmill notes",
            )

        /** Speed and heart-rate values from the stream fixtures (feature 005 adds the bpm list). */
        val STREAM_VALUES = listOf("3.917", "140, 140, 160, 180", "140,140,160,180")
    }
}
