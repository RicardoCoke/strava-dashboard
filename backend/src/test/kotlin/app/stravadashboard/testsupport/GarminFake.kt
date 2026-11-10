package app.stravadashboard.testsupport

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder

const val GARMIN_LIST_PATH = "/activitylist-service/activities/search/activities"
const val GARMIN_TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"

/**
 * An in-memory Garmin Connect Personal API for app tests (all data invented): a token, [activitiesJson] as every
 * list page, a detail without description, no activity files (404) and no wellness data (204). When [failWith] is
 * set, every call but the token answers it.
 */
class GarminFake {
    val requests = mutableListOf<HttpRequestData>()
    var activitiesJson = "[]"
    var failWith: HttpStatusCode? = null
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    val engine =
        MockEngine { request ->
            requests += request
            val path = request.url.encodedPath
            val failure = failWith
            when {
                path == GARMIN_TOKEN_PATH -> respond("invented-access-token", HttpStatusCode.OK)
                failure != null -> respond("{}", failure, json)
                path == GARMIN_LIST_PATH ->
                    respond(
                        if (request.url.parameters["start"] ==
                            "0"
                        ) {
                            activitiesJson
                        } else {
                            "[]"
                        },
                        HttpStatusCode.OK,
                        json,
                    )
                path.startsWith("/activity-service/") -> respond("""{"summaryDTO":{}}""", HttpStatusCode.OK, json)
                path.startsWith("/download-service/") -> respond("", HttpStatusCode.NotFound)
                else -> respond("", HttpStatusCode.NoContent)
            }
        }

    val listCalls: Int get() = requests.count { it.url.encodedPath == GARMIN_LIST_PATH }

    /** Calls other than the token exchange. */
    val dataCalls: List<HttpRequestData> get() = requests.filter { it.url.encodedPath != GARMIN_TOKEN_PATH }
}

/** A browser with a cookie jar, as the dashboard has; there is no login any more (feature 021). */
fun ApplicationTestBuilder.browser(): HttpClient =
    createClient {
        install(HttpCookies)
        followRedirects = false
    }
