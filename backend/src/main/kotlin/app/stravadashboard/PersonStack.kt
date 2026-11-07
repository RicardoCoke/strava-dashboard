package app.stravadashboard

import app.stravadashboard.api.OtherPersonSync
import app.stravadashboard.api.efficiencyFactorRoutes
import app.stravadashboard.api.longRunDriftRoutes
import app.stravadashboard.api.marathonPaceRoutes
import app.stravadashboard.api.normalizedVo2maxRoutes
import app.stravadashboard.api.raceDayFormRoutes
import app.stravadashboard.api.runWeatherRoutes
import app.stravadashboard.api.runningDashboardRoutes
import app.stravadashboard.api.syncRoutes
import app.stravadashboard.api.trainingLoadRoutes
import app.stravadashboard.api.trainingPlanRoutes
import app.stravadashboard.api.vo2maxRoutes
import app.stravadashboard.api.weeklySummaryRoutes
import app.stravadashboard.api.wellnessRoutes
import app.stravadashboard.auth.AthleteOwner
import app.stravadashboard.auth.ConnectService
import app.stravadashboard.auth.PersonConnect
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.BackgroundSync
import app.stravadashboard.domain.RunningDashboardService
import app.stravadashboard.domain.StravaCallBudget
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.efficiency.EfficiencyFactorService
import app.stravadashboard.domain.longrundrift.LongRunDriftService
import app.stravadashboard.domain.marathonpace.MarathonPaceService
import app.stravadashboard.domain.ports.TrainingPlanSource
import app.stravadashboard.domain.racedayform.RaceDayFormService
import app.stravadashboard.domain.trainingload.TrainingLoadService
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import app.stravadashboard.domain.vo2max.Vo2maxService
import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxService
import app.stravadashboard.domain.weather.RunWeatherService
import app.stravadashboard.domain.weeklysummary.WeeklySummaryService
import app.stravadashboard.domain.wellness.WellnessService
import app.stravadashboard.domain.wellness.WellnessSync
import app.stravadashboard.garmin.GarminWellnessSource
import app.stravadashboard.garmin.garminHttpClient
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.persistence.ExposedTokenRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.strava.StravaActivitySource
import app.stravadashboard.strava.StravaClient
import app.stravadashboard.strava.StravaOAuthClient
import app.stravadashboard.strava.StravaTokenProvider
import app.stravadashboard.weather.OpenMeteoClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.Route
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import java.time.ZoneId

/** One person's database (feature 019). The tables are the same for everyone, and only the file differs. */
internal class PersonStores(
    dbPath: String,
) {
    private val database = DatabaseFactory.connect(dbPath)
    val tokens = ExposedTokenRepository(database)
    val runs = ExposedRunRepository(database)
    val syncState = ExposedSyncStateRepository(database)
    val wellness = ExposedWellnessRepository(database)
    val owner = AthleteOwner(tokens, syncState)
}

/** What both people share. */
@Suppress("LongParameterList")
internal class SharedDeps(
    val http: HttpClient,
    val weatherHome: WeatherLocation?,
    val weatherSource: OpenMeteoClient?,
    val scope: CoroutineScope,
    val garminEngine: HttpClientEngine?,
    val clock: Clock,
    val zone: ZoneId,
)

/** One person's complete backend: the same services, on their own store, Strava app and settings. */
internal class PersonStack(
    val connect: PersonConnect,
    val otherSync: OtherPersonSync,
    val routes: Route.(other: OtherPersonSync?) -> Unit,
)

// Wiring only, as module() was before it.
@Suppress("LongMethod")
internal fun Application.personStack(
    settings: PersonSettings,
    stores: PersonStores,
    otherStores: PersonStores?,
    planSource: TrainingPlanSource,
    deps: SharedDeps,
): PersonStack {
    val (clock, zone) = deps.clock to deps.zone
    val (tokens, runs) = stores.tokens to stores.runs
    val oauth = StravaOAuthClient(deps.http, settings.stravaClientId, settings.stravaClientSecret, clock)
    val stravaClient = StravaClient(deps.http, StravaTokenProvider(tokens, oauth, clock), clock)
    val budget = StravaCallBudget(clock)
    val syncService = SyncService(StravaActivitySource(stravaClient), runs, stores.syncState, clock, zone, budget)
    val wellnessSync = garminWellnessSync(settings.garminApiKey, deps.garminEngine, stores.wellness, clock, zone)
    val sync = BackgroundSync(syncService, runs, stores.syncState, budget, clock, deps.scope, wellnessSync)
    val service =
        ConnectService(oauth, tokens, stores.syncState, listOfNotNull(otherStores?.owner))
    val plan = TrainingPlanService(planSource, settings.trainingPlanStartDate, clock, zone)
    val load = TrainingLoadService(settings.lthr, tokens, runs, clock, zone)
    val marathonPace =
        MarathonPaceService(
            settings.marathonPace,
            settings.trainingPlanStartDate,
            settings.buildWeeks,
            tokens,
            runs,
            clock,
            zone,
        )
    return PersonStack(PersonConnect(settings.stravaClientId, service), OtherPersonSync(tokens, sync)) { other ->
        runningDashboardRoutes(tokens, RunningDashboardService(tokens, runs, clock, zone))
        syncRoutes(tokens, sync, other)
        trainingPlanRoutes(tokens, plan)
        trainingLoadRoutes(tokens, load)
        raceDayFormRoutes(tokens, RaceDayFormService(settings.lthr, tokens, runs, plan, clock, zone))
        vo2maxRoutes(tokens, Vo2maxService(settings.maxHr, tokens, runs, clock, zone))
        normalizedVo2maxRoutes(tokens, NormalizedVo2maxService(settings.maxHr, tokens, runs, clock, zone))
        longRunDriftRoutes(tokens, LongRunDriftService(tokens, runs, clock, zone))
        efficiencyFactorRoutes(tokens, EfficiencyFactorService(tokens, runs, clock, zone))
        marathonPaceRoutes(tokens, marathonPace)
        wellnessRoutes(tokens, WellnessService(wellnessSync != null, stores.wellness, load, clock, zone))
        weeklySummaryRoutes(
            tokens,
            WeeklySummaryService(settings.lthr, tokens, runs, stores.wellness, clock, zone),
        )
        runWeatherRoutes(tokens, RunWeatherService(deps.weatherHome, deps.weatherSource, plan, clock, zone))
    }
}

/**
 * Resting HR and HRV from Garmin Connect (feature 015), or null without an API key. Without a test [engine], a Java
 * engine is created only when a key is set; the client and that engine are closed when the application stops.
 */
private fun Application.garminWellnessSync(
    apiKey: String?,
    engine: HttpClientEngine?,
    repo: ExposedWellnessRepository,
    clock: Clock,
    zone: ZoneId,
): WellnessSync? {
    if (apiKey == null) return null
    // Java (JDK HttpClient), not CIO: CIO's own TLS stack fails the handshake with Garmin's servers.
    val ownEngine = if (engine == null) Java.create() else null
    val http = garminHttpClient(engine ?: requireNotNull(ownEngine))
    monitor.subscribe(ApplicationStopped) {
        http.close()
        ownEngine?.close()
    }
    return WellnessSync(GarminWellnessSource(http, apiKey, clock), repo, clock, zone)
}
