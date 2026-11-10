package app.stravadashboard.api

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.testsupport.GARMIN_LIST_PATH
import app.stravadashboard.testsupport.GARMIN_TOKEN_PATH
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.startSync
import app.stravadashboard.testsupport.tempDbPath
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Feature 022: one sync per configured person when the application starts, with no request to it. All invented. */
class StartupSyncTest {
    private val marina =
        PersonSettings(
            dbPath = tempDbPath(),
            trainingPlanCsvPath = null,
            trainingPlanStartDate = LocalDate.of(2026, 8, 3),
            lthr = LthrSetting.NotConfigured,
            maxHr = MaxHrSetting.Default,
            marathonPace = MarathonPaceSetting.NotConfigured,
            garminApiKey = MARINA_KEY,
            garminActivitiesFrom = LocalDate.of(2000, 1, 1),
            buildWeeks = 18,
        )
    private val config = testConfig().copy(marina = marina)
    private val ricardoDb = DatabaseFactory.connect(config.dbPath)
    private val marinaDb = DatabaseFactory.connect(marina.dbPath)
    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Each key gets its own access token, so a list call shows whose key it was made with. */
    private fun app(
        appConfig: app.stravadashboard.config.AppConfig = config,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val garmin =
            MockEngine { request ->
                requests += request
                when (request.url.encodedPath) {
                    GARMIN_TOKEN_PATH -> {
                        val key = request.headers[HttpHeaders.Authorization]?.removePrefix("ApiKey ")
                        respond("access-$key", HttpStatusCode.OK)
                    }
                    else -> respond("[]", HttpStatusCode.OK, json)
                }
            }
        application { module(appConfig, fixedClock(), TEST_ZONE, garminEngine = garmin) }
        startApplication()
        block()
    }

    private fun listedWith(key: String): Boolean =
        requests.any {
            it.url.encodedPath == GARMIN_LIST_PATH && it.headers[HttpHeaders.Authorization] == "Bearer access-$key"
        }

    private fun listCalls(): Int = requests.count { it.url.encodedPath == GARMIN_LIST_PATH }

    private fun synced(database: org.jetbrains.exposed.sql.Database): Boolean =
        runBlocking { ExposedSyncStateRepository(database).get() }.lastSuccessfulSyncAt != null

    private suspend fun waitUntil(condition: () -> Boolean): Boolean {
        repeat(500) {
            if (condition()) return true
            delay(10)
        }
        return condition()
    }

    @Test
    fun `R1 - each configured person lists Garmin activities at startup without any request to the app`() =
        app {
            assertTrue(waitUntil { listedWith(RICARDO_KEY) && listedWith(MARINA_KEY) })
            assertTrue(waitUntil { synced(ricardoDb) })
            assertTrue(waitUntil { synced(marinaDb) })
        }

    @Test
    fun `R2 - without Garmin keys no Garmin call happens at startup`() =
        app(testConfig().copy(garminApiKey = null)) {
            delay(300)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `R2 - only the configured person syncs at startup`() =
        app(config.copy(marina = marina.copy(garminApiKey = null))) {
            assertTrue(waitUntil { listedWith(RICARDO_KEY) })
            delay(200)
            assertFalse(requests.any { it.headers[HttpHeaders.Authorization] == "ApiKey $MARINA_KEY" })
        }

    @Test
    fun `R3 - a POST to api sync right after the startup sync starts no new job`() =
        app {
            assertTrue(waitUntil { synced(ricardoDb) && synced(marinaDb) })
            val before = listCalls()
            val browser = createClient { install(HttpCookies) }

            val body = browser.startSync().bodyAsText()

            assertFalse("\"syncing\"" in body)
            delay(200)
            assertEquals(before, listCalls())
        }

    private companion object {
        const val RICARDO_KEY = "invented-garmin-key"
        const val MARINA_KEY = "invented-marina-startup-key"
    }
}
