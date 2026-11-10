package app.stravadashboard

import app.stravadashboard.api.ErrorResponse
import app.stravadashboard.api.XsrfProtection
import app.stravadashboard.api.forPerson
import app.stravadashboard.api.frontendRoutes
import app.stravadashboard.api.healthRoutes
import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.Person
import app.stravadashboard.domain.ports.TrainingPlanSource
import app.stravadashboard.persistence.TrainingPlanFileSource
import app.stravadashboard.weather.OpenMeteoClient
import app.stravadashboard.weather.openMeteoHttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.CachingOptions
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.ZoneId

private val log = LoggerFactory.getLogger("app.stravadashboard.Application")

private const val HASHED_ASSET_MAX_AGE_S = 365 * 24 * 60 * 60

fun main() {
    val config = AppConfig.fromEnvironment()
    embeddedServer(Netty, host = config.host, port = config.port) {
        module(config, syncAtStartup = true)
    }.start(wait = true)
}

// The engines, plan source and startup sync are test seams; every other dependency is built here.
// syncAtStartup is off by default so a test app (which has a Garmin key) never races a sync it did not start.
@Suppress("LongParameterList")
fun Application.module(
    config: AppConfig,
    clock: Clock = Clock.systemUTC(),
    zone: ZoneId = ZoneId.systemDefault(),
    trainingPlanSource: TrainingPlanSource = TrainingPlanFileSource(config.trainingPlanCsvPath),
    garminEngine: HttpClientEngine? = null,
    weatherEngine: HttpClientEngine? = null,
    syncAtStartup: Boolean = false,
) {
    installServerPlugins()

    val weatherSource = if (config.weatherHome == null) null else openMeteoSource(weatherEngine)
    val deps =
        SharedDeps(config.weatherHome, weatherSource, backgroundSyncScope(), garminEngine, clock, zone)

    val ricardoStores = PersonStores(config.dbPath)
    val marinaSettings = config.marina
    val marinaStores = marinaSettings?.let { PersonStores(it.dbPath) }
    val ricardo = personStack(config.ricardo, ricardoStores, trainingPlanSource, deps)
    val marina =
        marinaSettings?.let {
            val planSource = TrainingPlanFileSource(it.trainingPlanCsvPath)
            personStack(it, requireNotNull(marinaStores), planSource, deps)
        }
    // Fresh data as soon as the Pi boots, without waiting for a page to open (feature 022).
    if (syncAtStartup) launch { listOfNotNull(ricardo, marina).forEach { it.otherSync.startIfConfigured() } }

    routing {
        config.frontendDir?.let { frontendRoutes(it) }
        healthRoutes()
        forPerson(Person.RICARDO) { ricardo.routes(this, marina?.otherSync) }
        forPerson(Person.MARINA) {
            if (marina != null) {
                marina.routes(this, ricardo.otherSync)
            } else {
                route("/api/{...}") {
                    handle { call.respond(HttpStatusCode.NotFound, ErrorResponse.PERSON_NOT_CONFIGURED) }
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

private fun Application.installServerPlugins() {
    install(ContentNegotiation) { json() }
    install(CallLogging)
    install(Compression)
    // Built JS and CSS have a content hash in their name, so they never change; index.html is always re-checked.
    install(CachingHeaders) {
        options { _, content ->
            when (content.contentType?.withoutParameters()) {
                ContentType.Text.JavaScript, ContentType.Application.JavaScript, ContentType.Text.CSS ->
                    CachingOptions(CacheControl.MaxAge(maxAgeSeconds = HASHED_ASSET_MAX_AGE_S))
                ContentType.Text.Html -> CachingOptions(CacheControl.NoCache(null))
                else -> null
            }
        }
    }
    installStatusPages()
    // Served over plain HTTP on the home network (feature 021), so the XSRF cookie can't be Secure.
    install(XsrfProtection) { secureCookies = false }
}

private fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Path only: never the query string.
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
