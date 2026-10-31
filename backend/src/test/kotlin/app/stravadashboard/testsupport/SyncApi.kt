package app.stravadashboard.testsupport

import app.stravadashboard.api.XSRF_COOKIE
import app.stravadashboard.api.XSRF_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay

private const val MAX_POLLS = 500
private const val POLL_MILLIS = 10L

/** The XSRF token the client's cookie jar holds, fetched with a `GET /api/sync` first if it has none yet. */
suspend fun HttpClient.xsrfToken(): String {
    cookies("http://localhost/").find { it.name == XSRF_COOKIE }?.let { return it.value }
    get("/api/sync")
    return cookies("http://localhost/").first { it.name == XSRF_COOKIE }.value
}

/** `POST /api/sync` with a matching XSRF header, as Angular sends it. */
suspend fun HttpClient.startSync(): HttpResponse {
    val token = xsrfToken()
    return post("/api/sync") { header(XSRF_HEADER, token) }
}

/**
 * Starts a background sync the way the dashboard does and polls `GET /api/sync` until it has finished.
 * Returns the last status response, which is a 401 when the job found the connection revoked.
 */
suspend fun HttpClient.syncAndWait(): HttpResponse {
    startSync()
    repeat(MAX_POLLS) {
        val status = get("/api/sync")
        if (status.status != HttpStatusCode.OK || "\"syncing\"" !in status.bodyAsText()) return status
        delay(POLL_MILLIS)
    }
    error("Background sync did not finish")
}
