package app.stravadashboard.api

import app.stravadashboard.domain.BackgroundSync
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/** Without a Garmin key nothing syncs; the cached runs are still served (feature 021, AC7.3). */
private val NOT_CONFIGURED = SyncStatusResponse("notConfigured", null, null, 0, null)

/**
 * `GET /api/sync` reports the status; `POST /api/sync` starts a background sync if one is due (feature 009). [sync] is
 * null when the person has no Garmin key.
 */
fun Route.syncRoutes(
    sync: BackgroundSync?,
    other: OtherPersonSync?,
) {
    get("/api/sync") {
        call.respond(sync?.status()?.toDto() ?: NOT_CONFIGURED)
    }
    post("/api/sync") {
        if (!call.hasValidXsrfToken()) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse.XSRF_INVALID)
            return@post
        }
        val status = sync?.startIfDue()?.toDto() ?: NOT_CONFIGURED
        other?.startIfConfigured()
        call.respond(status)
    }
}

/** The person not on screen: sync only runs while a dashboard is open, so a request starts theirs too (019 R6). */
class OtherPersonSync(
    private val sync: BackgroundSync?,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun startIfConfigured() {
        try {
            sync?.startIfDue()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not start the other person's sync: {}", e::class.simpleName)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(OtherPersonSync::class.java)
    }
}
