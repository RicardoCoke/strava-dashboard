package app.stravadashboard.strava

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.ports.SourceResult
import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.TokenRepository
import app.stravadashboard.testsupport.NOW
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
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

// All streams here are invented.
class DriftStreamClientTest {
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

    @Test
    fun `R2 - drift stream maps time speed heartrate moving`() {
        val result =
            runBlocking {
                source { respond(resource("drift-streams.json"), HttpStatusCode.OK, json) }.driftStream(ActivityId(9))
            }

        val stream = assertIs<SourceResult.Success<DriftStream>>(result).value
        assertEquals(listOf(0, 1, 2, 3), stream.times.map { it.value })
        assertEquals(listOf(3.0, 3.1, 2.9, 3.0), stream.speeds.map { it.value })
        assertEquals(listOf(140, 141, 143, 145), stream.bpm.map { it.value })
        assertEquals(listOf(true, true, false, true), stream.moving)
        assertEquals("/api/v3/activities/9/streams", requests.single().url.encodedPath)
        assertEquals("true", requests.single().url.parameters["key_by_type"])
    }

    @Test
    fun `R2 - streams request never asks for latlng`() {
        runBlocking {
            source {
                respond(
                    resource("drift-streams.json"),
                    HttpStatusCode.OK,
                    json,
                )
            }.driftStream(ActivityId(9))
        }
        val keys = requireNotNull(requests.single().url.parameters["keys"])
        assertEquals("time,velocity_smooth,heartrate,moving", keys)
        listOf("latlng", "altitude", "grade_smooth").forEach { assertEquals(false, it in keys) }
    }

    @Test
    fun `R2 - drift stream without velocity is not found`() {
        listOf(
            source { respond("{}", HttpStatusCode.NotFound, json) },
            source { respond("""{"time":{"data":[0,1]},"heartrate":{"data":[140,150]}}""", HttpStatusCode.OK, json) },
            source {
                respond(
                    """{"time":{"data":[0,1]},"velocity_smooth":{"data":[3.0,3.0]}}""",
                    HttpStatusCode.OK,
                    json,
                )
            },
            source {
                respond(
                    """{"time":{"data":[0,1,2]},"velocity_smooth":{"data":[3.0,3.0]},"heartrate":{"data":[1,2,3]}}""",
                    HttpStatusCode.OK,
                    json,
                )
            },
        ).forEach { assertEquals(SourceResult.NotFound, runBlocking { it.driftStream(ActivityId(9)) }) }
    }

    @Test
    fun `R2 - moving stream of another length is ignored`() {
        val body =
            """{"time":{"data":[0,1]},"velocity_smooth":{"data":[3.0,3.0]},"heartrate":{"data":[140,150]},""" +
                """"moving":{"data":[true]}}"""
        val result = runBlocking { source { respond(body, HttpStatusCode.OK, json) }.driftStream(ActivityId(9)) }
        assertNull(assertIs<SourceResult.Success<DriftStream>>(result).value.moving)
    }
}
