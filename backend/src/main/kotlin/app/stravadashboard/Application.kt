package app.stravadashboard

import app.stravadashboard.api.ErrorResponse
import app.stravadashboard.api.healthRoutes
import app.stravadashboard.api.runningDashboardRoutes
import app.stravadashboard.api.trainingPlanRoutes
import app.stravadashboard.auth.ConnectService
import app.stravadashboard.auth.OAuthState
import app.stravadashboard.auth.UserSession
import app.stravadashboard.auth.authRoutes
import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.RunningDashboardService
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.ports.TrainingPlanSource
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.persistence.ExposedTokenRepository
import app.stravadashboard.persistence.TrainingPlanFileSource
import app.stravadashboard.strava.StravaActivitySource
import app.stravadashboard.strava.StravaClient
import app.stravadashboard.strava.StravaOAuthClient
import app.stravadashboard.strava.StravaTokenProvider
import app.stravadashboard.strava.stravaHttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
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
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
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

fun Application.module(
    config: AppConfig,
    clock: Clock = Clock.systemUTC(),
    zone: ZoneId = ZoneId.systemDefault(),
    stravaEngine: HttpClientEngine = CIO.create(),
    trainingPlanSource: TrainingPlanSource = TrainingPlanFileSource(config.trainingPlanCsvPath),
) {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Path only: query strings can carry the OAuth code.
            log.error("Unhandled error on {}", call.request.path(), cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse.INTERNAL)
        }
    }
    installSessions(config)

    val database = DatabaseFactory.connect(config.dbPath)
    val tokens = ExposedTokenRepository(database)
    val runs = ExposedRunRepository(database)
    val syncState = ExposedSyncStateRepository(database)

    val http = stravaHttpClient(stravaEngine)
    monitor.subscribe(ApplicationStopped) { http.close() }
    val oauth = StravaOAuthClient(http, config.stravaClientId, config.stravaClientSecret, clock)
    val stravaClient = StravaClient(http, StravaTokenProvider(tokens, oauth, clock), clock)
    val syncService = SyncService(StravaActivitySource(stravaClient), runs, syncState, clock, zone)
    val dashboardService = RunningDashboardService(tokens, syncService, runs, clock, zone)
    val connectService = ConnectService(oauth, tokens, runs, syncState)
    val planService = TrainingPlanService(trainingPlanSource, config.trainingPlanStartDate, clock, zone)

    routing {
        healthRoutes()
        authRoutes(config, connectService)
        runningDashboardRoutes(tokens, dashboardService)
        trainingPlanRoutes(tokens, planService)
    }
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
