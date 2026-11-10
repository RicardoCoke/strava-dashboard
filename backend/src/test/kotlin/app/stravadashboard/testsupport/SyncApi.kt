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

private const val MAX_POLLS = 3000
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

/** Starts a background sync the way the dashboard does, polls `GET /api/sync` until it has finished and returns it. */
suspend fun HttpClient.syncAndWait(): HttpResponse {
    if (startSync().isSyncing()) pollUntilIdle()
    return get("/api/sync")
}

private suspend fun HttpClient.pollUntilIdle() {
    repeat(MAX_POLLS) {
        if (!get("/api/sync").isSyncing()) return
        delay(POLL_MILLIS)
    }
    error("Background sync did not finish")
}

private suspend fun HttpResponse.isSyncing(): Boolean = status == HttpStatusCode.OK && "\"syncing\"" in bodyAsText()
