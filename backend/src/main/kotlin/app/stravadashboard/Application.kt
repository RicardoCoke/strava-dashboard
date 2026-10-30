package app.stravadashboard

import app.stravadashboard.api.healthRoutes
import app.stravadashboard.config.AppConfig
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("app.stravadashboard.Application")

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on {}", call.request.local.uri, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "internal_error", message = "Something went wrong."),
            )
        }
    }
    routing {
        healthRoutes()
    }
}

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)
