package app.stravadashboard.api

import app.stravadashboard.module
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.startSync
import app.stravadashboard.testsupport.syncAndWait
import app.stravadashboard.testsupport.testConfig
import app.stravadashboard.testsupport.xsrfToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Feature 009: the read endpoints never wait for Strava, and `/api/sync` reports and starts background syncs. */
class SyncRoutesTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val stravaRequests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val listRelease = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var gate = listRelease
    private var revoked = false

    private suspend fun MockRequestHandleScope.strava(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            "/oauth/token" ->
                if (revoked) {
                    respond("{}", HttpStatusCode.BadRequest, jsonHeaders)
                } else {
                    respond(
                        """{"access_token":"a","refresh_token":"r","expires_at":${now.epochSecond + 99_999},
                           "athlete":{"id":7}}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                }
            "/api/v3/athlete/activities" -> {
                gate.await()
                if (revoked) {
                    respond(
                        "{}",
                        HttpStatusCode.Unauthorized,
                        jsonHeaders,
                    )
                } else {
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                }
            }
            else -> respond("""{"id":1,"laps":[]}""", HttpStatusCode.OK, jsonHeaders)
        }

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            val engine =
                MockEngine { request ->
                    stravaRequests += request
                    strava(request)
                }
            application { module(testConfig(), fixedClock(now), TEST_ZONE, engine) }
            block()
        }

    private fun ApplicationTestBuilder.browser(): HttpClient =
        createClient {
            install(HttpCookies)
            followRedirects = false
        }

    private suspend fun ApplicationTestBuilder.connectedBrowser(): HttpClient {
        val browser = browser()
        val login = browser.get("/auth/strava/login")
        val state = Url(requireNotNull(login.headers[HttpHeaders.Location])).parameters["state"]
        browser.get("/auth/strava/callback?state=$state&code=invented&scope=read,activity:read_all")
        stravaRequests.clear()
        return browser
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun listCalls(): Int = stravaRequests.count { it.url.encodedPath == "/api/v3/athlete/activities" }

    @Test
    fun `R1 - read endpoints make no Strava calls when sync is due`() =
        app {
            val browser = connectedBrowser()
            listOf("/api/dashboard/running", "/api/vo2max").forEach {
                assertEquals(HttpStatusCode.OK, browser.get(it).status, it)
            }
            assertTrue(stravaRequests.isEmpty())
        }

    @Test
    fun `R1 - read endpoints answer while a sync is running`() =
        app {
            val browser = connectedBrowser()
            gate = CompletableDeferred()

            assertEquals(
                "syncing",
                browser
                    .startSync()
                    .json()["status"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(HttpStatusCode.OK, browser.get("/api/dashboard/running").status)
            assertEquals(HttpStatusCode.OK, browser.get("/api/vo2max").status)
            assertEquals(
                "syncing",
                browser
                    .get("/api/sync")
                    .json()["status"]
                    ?.jsonPrimitive
                    ?.content,
            )

            gate.complete(Unit)
            assertEquals(
                "ok",
                browser
                    .syncAndWait()
                    .json()["status"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun `R2 - get sync returns status without starting a sync`() =
        app {
            val response = connectedBrowser().get("/api/sync")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()
            assertEquals(
                setOf("status", "lastSyncedAt", "retryAfter", "runsPendingDetail", "nextSyncInSeconds"),
                body.keys,
            )
            assertEquals("ok", body["status"]?.jsonPrimitive?.content)
            assertEquals("0", body["nextSyncInSeconds"]?.jsonPrimitive?.content)
            assertTrue(stravaRequests.isEmpty())
        }

    @Test
    fun `R2 - post sync starts a background job and reports when it is next due`() =
        app {
            val browser = connectedBrowser()
            val finished = browser.syncAndWait().json()

            assertEquals(1, listCalls())
            assertEquals("ok", finished["status"]?.jsonPrimitive?.content)
            assertEquals("2026-10-31T10:00:00Z", finished["lastSyncedAt"]?.jsonPrimitive?.content)
            assertEquals("900", finished["nextSyncInSeconds"]?.jsonPrimitive?.content)

            browser.startSync()
            assertEquals(1, listCalls())
        }

    @Test
    fun `R2 - sync without session returns not_connected`() =
        app {
            assertEquals(
                "not_connected",
                client
                    .get("/api/sync")
                    .json()["error"]
                    ?.jsonPrimitive
                    ?.content,
            )
            val browser = browser()
            val post = browser.startSync()
            assertEquals(HttpStatusCode.Unauthorized, post.status)
            assertEquals("not_connected", post.json()["error"]?.jsonPrimitive?.content)
        }

    @Test
    fun `R2 - sync after revoked connection returns strava_reauth_required`() =
        app {
            val browser = connectedBrowser()
            revoked = true

            val response = browser.syncAndWait()

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("strava_reauth_required", response.json()["error"]?.jsonPrimitive?.content)
        }

    @Test
    fun `R6 - api response sets xsrf cookie when missing`() =
        app {
            val response = client.get("/api/sync")
            val cookie =
                assertNotNull(response.headers.getAll(HttpHeaders.SetCookie)?.single { it.startsWith("XSRF-TOKEN=") })
            val value = cookie.substringAfter("XSRF-TOKEN=").substringBefore(';')
            assertEquals(43, value.length)
            assertTrue("Path=/" in cookie)
            assertTrue("SameSite=Strict" in cookie)
            assertTrue("HttpOnly" !in cookie)
            assertTrue("Secure" !in cookie)
        }

    @Test
    fun `R6 - post sync without matching xsrf header returns 403`() =
        app {
            val browser = connectedBrowser()
            browser.xsrfToken()

            listOf(null, "not-the-token").forEach { header ->
                val response = browser.post("/api/sync") { header?.let { header(XSRF_HEADER, it) } }
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertEquals(
                    """{"error":"xsrf_invalid","message":"Request could not be verified. Reload the page."}""",
                    response.bodyAsText(),
                )
            }
            assertTrue(stravaRequests.isEmpty())
        }

    @Test
    fun `R6 - post sync with matching xsrf header starts sync`() =
        app {
            val response = connectedBrowser().startSync()
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("syncing", response.json()["status"]?.jsonPrimitive?.content)
        }
}
