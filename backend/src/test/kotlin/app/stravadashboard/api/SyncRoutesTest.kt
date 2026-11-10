package app.stravadashboard.api

import app.stravadashboard.module
import app.stravadashboard.testsupport.GARMIN_LIST_PATH
import app.stravadashboard.testsupport.GARMIN_TOKEN_PATH
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.startSync
import app.stravadashboard.testsupport.syncAndWait
import app.stravadashboard.testsupport.testConfig
import app.stravadashboard.testsupport.xsrfToken
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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

/** Feature 009: the read endpoints never wait for the source, and `/api/sync` reports and starts background syncs. */
class SyncRoutesTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val garminRequests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val listRelease = CompletableDeferred<Unit>().apply { complete(Unit) }
    private var gate = listRelease
    private var listStatus = HttpStatusCode.OK

    private suspend fun MockRequestHandleScope.garmin(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            GARMIN_TOKEN_PATH -> respond("invented-access-token", HttpStatusCode.OK)
            GARMIN_LIST_PATH -> {
                gate.await()
                respond("[]", listStatus, jsonHeaders)
            }
            else -> respond("", HttpStatusCode.NoContent)
        }

    private fun app(
        garminKey: String? = "invented-garmin-key",
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                garminRequests += request
                garmin(request)
            }
        val config = testConfig().copy(garminApiKey = garminKey)
        application { module(config, fixedClock(now), TEST_ZONE, garminEngine = engine, syncAtStartup = false) }
        block()
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.status(): String? = json()["status"]?.jsonPrimitive?.content

    private fun listCalls(): Int = garminRequests.count { it.url.encodedPath == GARMIN_LIST_PATH }

    @Test
    fun `R1 - read endpoints make no Garmin calls when sync is due`() =
        app {
            val browser = browser()
            listOf("/api/dashboard/running", "/api/vo2max").forEach {
                assertEquals(HttpStatusCode.OK, browser.get(it).status, it)
            }
            assertTrue(garminRequests.isEmpty())
        }

    @Test
    fun `R1 - read endpoints answer while a sync is running`() =
        app {
            val browser = browser()
            gate = CompletableDeferred()

            assertEquals("syncing", browser.startSync().status())
            assertEquals(HttpStatusCode.OK, browser.get("/api/dashboard/running").status)
            assertEquals(HttpStatusCode.OK, browser.get("/api/vo2max").status)
            assertEquals("syncing", browser.get("/api/sync").status())

            gate.complete(Unit)
            assertEquals("ok", browser.syncAndWait().status())
        }

    @Test
    fun `R2 - get sync returns status without starting a sync`() =
        app {
            val response = browser().get("/api/sync")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()
            assertEquals(
                setOf("status", "lastSyncedAt", "retryAfter", "runsPendingDetail", "nextSyncInSeconds"),
                body.keys,
            )
            assertEquals("ok", body["status"]?.jsonPrimitive?.content)
            assertEquals("0", body["nextSyncInSeconds"]?.jsonPrimitive?.content)
            assertTrue(garminRequests.isEmpty())
        }

    @Test
    fun `R2 - post sync starts a background job and reports when it is next due`() =
        app {
            val browser = browser()
            val finished = browser.syncAndWait().json()

            assertEquals(1, listCalls())
            assertEquals("ok", finished["status"]?.jsonPrimitive?.content)
            assertEquals("2026-10-31T10:00:00Z", finished["lastSyncedAt"]?.jsonPrimitive?.content)
            assertEquals("900", finished["nextSyncInSeconds"]?.jsonPrimitive?.content)

            browser.startSync()
            assertEquals(1, listCalls())
        }

    @Test
    fun `021 R8 - a rejected key reports garminUnavailable`() =
        app {
            listStatus = HttpStatusCode.Unauthorized
            assertEquals("garminUnavailable", browser().syncAndWait().status())
        }

    @Test
    fun `021 R7 - sync without Garmin key reports notConfigured`() =
        app(garminKey = null) {
            val browser = browser()
            val expected =
                """{"status":"notConfigured","lastSyncedAt":null,"retryAfter":null,"runsPendingDetail":0,""" +
                    """"nextSyncInSeconds":null}"""
            assertEquals(expected, browser.get("/api/sync").bodyAsText())
            val post = browser.startSync()
            assertEquals(HttpStatusCode.OK, post.status)
            assertEquals(expected, post.bodyAsText())
            assertEquals(HttpStatusCode.OK, browser.get("/api/dashboard/running").status)
            assertTrue(garminRequests.isEmpty())
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
            // Served over plain HTTP on the home network (feature 021).
            assertTrue("Secure" !in cookie)
        }

    @Test
    fun `R6 - post sync without matching xsrf header returns 403`() =
        app {
            val browser = browser()
            browser.xsrfToken()

            listOf(null, "not-the-token").forEach { header ->
                val response = browser.post("/api/sync") { header?.let { header(XSRF_HEADER, it) } }
                assertEquals(HttpStatusCode.Forbidden, response.status)
                assertEquals(
                    """{"error":"xsrf_invalid","message":"Request could not be verified. Reload the page."}""",
                    response.bodyAsText(),
                )
            }
            assertTrue(garminRequests.isEmpty())
        }

    @Test
    fun `R6 - post sync with matching xsrf header starts sync`() =
        app {
            val response = browser().startSync()
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("syncing", response.status())
        }
}
