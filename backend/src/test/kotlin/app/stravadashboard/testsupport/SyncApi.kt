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

/**
 * Starts a background sync the way the dashboard does and polls `GET /api/sync` until it has finished.
 * Returns the status after the job, which is a 401 `strava_reauth_required` when the job found the connection revoked.
 *
 * One poll can't be trusted on its own: a `GET` checks the tokens before it reads the status, so it can report idle
 * with a 200 just as the job revokes the connection, and between the token deletion and the end of the job it reports
 * `not_connected`. So a 200 that isn't "syncing" only proves the job has ended and the status is read once more,
 * while `strava_reauth_required` is only set once the job has finished, so it is final.
 */
suspend fun HttpClient.syncAndWait(): HttpResponse {
    val ended = if (startSync().isSyncing()) pollUntilJobEnded() else null
    return ended?.takeIf { it.status == HttpStatusCode.Unauthorized } ?: get("/api/sync")
}

/** The first poll that proves the job has ended: a 200 that isn't "syncing", or `strava_reauth_required`. */
private suspend fun HttpClient.pollUntilJobEnded(): HttpResponse {
    repeat(MAX_POLLS) {
        val status = get("/api/sync")
        val idle = status.status == HttpStatusCode.OK && !status.isSyncing()
        if (idle || "\"strava_reauth_required\"" in status.bodyAsText()) return status
        delay(POLL_MILLIS)
    }
    error("Background sync did not finish")
}

private suspend fun HttpResponse.isSyncing(): Boolean = status == HttpStatusCode.OK && "\"syncing\"" in bodyAsText()
