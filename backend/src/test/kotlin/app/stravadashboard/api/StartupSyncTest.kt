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
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.startSync
import app.stravadashboard.testsupport.tempDbPath
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RICARDO_KEY = "ricardo-garmin"
private const val MARINA_KEY = "marina-garmin"

/**
 * Feature 022: one sync per person with a Garmin key when the application starts, with no request to it. Ported from
 * Strava tokens to Garmin keys for feature 021, where a key is what makes a person "connected". All invented.
 */
class StartupSyncTest {
    private fun marina(key: String?) =
        PersonSettings(
            dbPath = tempDbPath(),
            trainingPlanCsvPath = null,
            trainingPlanStartDate = LocalDate.of(2026, 8, 3),
            lthr = LthrSetting.NotConfigured,
            maxHr = MaxHrSetting.Default,
            marathonPace = MarathonPaceSetting.NotConfigured,
            garminApiKey = key,
            garminActivitiesFrom = LocalDate.of(2000, 1, 1),
            buildWeeks = 18,
        )

    private val requests = CopyOnWriteArrayList<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Each key gets its own access token, so every list call shows whose key it was made with. */
    private fun app(
        ricardoKey: String?,
        marinaKey: String?,
        block: suspend ApplicationTestBuilder.(ricardoDb: Database, marinaDb: Database) -> Unit,
    ) = testApplication {
        val marina = marina(marinaKey)
        val config = testConfig().copy(garminApiKey = ricardoKey, marina = marina)
        val garmin =
            MockEngine { request ->
                requests += request
                if (request.url.encodedPath == GARMIN_TOKEN_PATH) {
                    respond("access-" + request.headers[HttpHeaders.Authorization]?.removePrefix("ApiKey "))
                } else {
                    respond("[]", HttpStatusCode.OK, json)
                }
            }
        application { module(config, fixedClock(), TEST_ZONE, garminEngine = garmin, syncAtStartup = true) }
        startApplication()
        block(DatabaseFactory.connect(config.dbPath), DatabaseFactory.connect(marina.dbPath))
    }

    private fun listedWith(key: String): Boolean =
        requests.any {
            it.url.encodedPath == GARMIN_LIST_PATH && it.headers[HttpHeaders.Authorization] == "Bearer access-$key"
        }

    private fun synced(database: Database): Boolean =
        runBlocking { ExposedSyncStateRepository(database).get() }.lastSuccessfulSyncAt != null

    private suspend fun waitUntil(condition: () -> Boolean): Boolean {
        repeat(500) {
            if (condition()) return true
            delay(10)
        }
        return condition()
    }

    @Test
    fun `R1 - with Garmin keys an activities call happens at startup without any request to the app`() =
        app(RICARDO_KEY, MARINA_KEY) { ricardoDb, marinaDb ->
            assertTrue(waitUntil { listedWith(RICARDO_KEY) && listedWith(MARINA_KEY) })
            assertTrue(waitUntil { synced(ricardoDb) })
            assertTrue(waitUntil { synced(marinaDb) })
        }

    @Test
    fun `R2 - without Garmin keys no Garmin call happens at startup`() =
        app(null, null) { _, _ ->
            delay(300)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `R2 - only the person with a key syncs at startup`() =
        app(RICARDO_KEY, null) { _, _ ->
            assertTrue(waitUntil { listedWith(RICARDO_KEY) })
            delay(200)
            assertFalse(requests.any { it.headers[HttpHeaders.Authorization]?.contains(MARINA_KEY) == true })
        }

    @Test
    fun `R3 - a POST to api sync right after the startup sync starts no new job`() =
        app(RICARDO_KEY, null) { ricardoDb, _ ->
            assertTrue(waitUntil { synced(ricardoDb) })
            val listCalls = requests.count { it.url.encodedPath == GARMIN_LIST_PATH }

            val body = browser().startSync().bodyAsText()

            assertFalse("\"syncing\"" in body)
            delay(200)
            assertEquals(listCalls, requests.count { it.url.encodedPath == GARMIN_LIST_PATH })
        }
}
