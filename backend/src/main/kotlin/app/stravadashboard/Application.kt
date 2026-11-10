package app.stravadashboard

import app.stravadashboard.api.ErrorResponse
import app.stravadashboard.api.XsrfProtection
import app.stravadashboard.api.forPerson
import app.stravadashboard.api.frontendRoutes
import app.stravadashboard.api.healthRoutes
import app.stravadashboard.auth.OAuthState
import app.stravadashboard.auth.UserSession
import app.stravadashboard.auth.authRoutes
import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.Person
import app.stravadashboard.domain.ports.TrainingPlanSource
import app.stravadashboard.persistence.TrainingPlanFileSource
import app.stravadashboard.strava.stravaHttpClient
import app.stravadashboard.weather.OpenMeteoClient
import app.stravadashboard.weather.openMeteoHttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.java.Java
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.ZoneId

private val log = LoggerFactory.getLogger("app.stravadashboard.Application")

private const val SESSION_MAX_AGE_SECONDS = 30L * 24 * 60 * 60
private const val OAUTH_STATE_MAX_AGE_SECONDS = 600L

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config)
    }.start(wait = true)
}

// The engines and plan source are test seams; every other dependency is built here.
@Suppress("LongParameterList")
fun Application.module(
    config: AppConfig,
    clock: Clock = Clock.systemUTC(),
    zone: ZoneId = ZoneId.systemDefault(),
    stravaEngine: HttpClientEngine = CIO.create(),
    trainingPlanSource: TrainingPlanSource = TrainingPlanFileSource(config.trainingPlanCsvPath),
    garminEngine: HttpClientEngine? = null,
    weatherEngine: HttpClientEngine? = null,
) {
    installServerPlugins(config)

    val http = stravaHttpClient(stravaEngine)
    monitor.subscribe(ApplicationStopped) { http.close() }
    val weatherSource = if (config.weatherHome == null) null else openMeteoSource(weatherEngine)
    val deps =
        SharedDeps(http, config.weatherHome, weatherSource, backgroundSyncScope(), garminEngine, clock, zone)

    val ricardoStores = PersonStores(config.dbPath)
    val marinaSettings = config.marina
    val marinaStores = marinaSettings?.let { PersonStores(it.dbPath) }
    val ricardo = personStack(config.ricardo, ricardoStores, marinaStores, trainingPlanSource, deps)
    val marina =
        marinaSettings?.let {
            val planSource = TrainingPlanFileSource(it.trainingPlanCsvPath)
            personStack(it, requireNotNull(marinaStores), ricardoStores, planSource, deps)
        }
    // Fresh data as soon as the Pi boots, without waiting for a page to open (feature 022).
    launch { listOfNotNull(ricardo, marina).forEach { it.otherSync.startIfConnected() } }

    routing {
        config.frontendDir?.let { frontendRoutes(it) }
        healthRoutes()
        authRoutes(
            config,
            buildMap {
                put(Person.RICARDO, ricardo.connect)
                marina?.let { put(Person.MARINA, it.connect) }
            },
        )
        forPerson(Person.RICARDO) { ricardo.routes(this, marina?.otherSync) }
        forPerson(Person.MARINA) {
            if (marina != null) {
                marina.routes(this, ricardo.otherSync)
            } else {
                route("/api/{...}") {
                    handle { call.respond(HttpStatusCode.Unauthorized, ErrorResponse.NOT_CONNECTED) }
                }
            }
        }
    }
}

/** The Open-Meteo client (feature 018); like Garmin's, its own Java engine and client close when the app stops. */
private fun Application.openMeteoSource(engine: HttpClientEngine?): OpenMeteoClient {
    val ownEngine = if (engine == null) Java.create() else null
    val http = openMeteoHttpClient(engine ?: requireNotNull(ownEngine))
    monitor.subscribe(ApplicationStopped) {
        http.close()
        ownEngine?.close()
    }
    return OpenMeteoClient(http)
}

private fun Application.installServerPlugins(config: AppConfig) {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    installStatusPages()
    installSessions(config)
    install(XsrfProtection) { secureCookies = config.secureCookies }
}

private fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Path only: query strings can carry the OAuth code.
            log.error("Unhandled error on {}", call.request.path(), cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse.INTERNAL)
        }
    }
}

/** Background syncs run in a child scope of the application, so they are cancelled when it stops. */
private fun Application.backgroundSyncScope(): CoroutineScope {
    val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
    monitor.subscribe(ApplicationStopped) { scope.cancel() }
    return scope
}

private fun Application.installSessions(config: AppConfig) {
    val signer = SessionTransportTransformerMessageAuthentication(config.sessionSecret)
    install(Sessions) {
        cookie<UserSession>("sd_session") {
            cookie.path = "/"
            cookie.httpOnly = true
            cookie.secure = config.secureCookies
            cookie.maxAgeInSeconds = SESSION_MAX_AGE_SECONDS
            cookie.extensions["SameSite"] = "Lax"
            transform(signer)
        }
        cookie<OAuthState>("sd_oauth_state") {
            cookie.path = "/auth"
            cookie.httpOnly = true
            cookie.secure = config.secureCookies
            cookie.maxAgeInSeconds = OAUTH_STATE_MAX_AGE_SECONDS
            cookie.extensions["SameSite"] = "Lax"
            transform(signer)
        }
    }
}
