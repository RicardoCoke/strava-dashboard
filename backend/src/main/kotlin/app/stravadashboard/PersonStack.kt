package app.stravadashboard

import app.stravadashboard.api.OtherPersonSync
import app.stravadashboard.api.efficiencyFactorRoutes
import app.stravadashboard.api.longRunDriftRoutes
import app.stravadashboard.api.marathonPaceRoutes
import app.stravadashboard.api.normalizedVo2maxRoutes
import app.stravadashboard.api.raceDayFormRoutes
import app.stravadashboard.api.ringsRoutes
import app.stravadashboard.api.runWeatherRoutes
import app.stravadashboard.api.runningDashboardRoutes
import app.stravadashboard.api.syncRoutes
import app.stravadashboard.api.trainingLoadRoutes
import app.stravadashboard.api.trainingPlanRoutes
import app.stravadashboard.api.vo2maxRoutes
import app.stravadashboard.api.weeklySummaryRoutes
import app.stravadashboard.api.wellnessRoutes
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.BackgroundSync
import app.stravadashboard.domain.CallBudget
import app.stravadashboard.domain.RunningDashboardService
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.efficiency.EfficiencyFactorService
import app.stravadashboard.domain.longrundrift.LongRunDriftService
import app.stravadashboard.domain.marathonpace.MarathonPaceService
import app.stravadashboard.domain.ports.TrainingPlanSource
import app.stravadashboard.domain.racedayform.RaceDayFormService
import app.stravadashboard.domain.rings.RingsService
import app.stravadashboard.domain.rings.RingsSync
import app.stravadashboard.domain.trainingload.TrainingLoadService
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import app.stravadashboard.domain.vo2max.Vo2maxService
import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxService
import app.stravadashboard.domain.weather.RunWeatherService
import app.stravadashboard.domain.weeklysummary.WeeklySummaryService
import app.stravadashboard.domain.wellness.WellnessService
import app.stravadashboard.domain.wellness.WellnessSync
import app.stravadashboard.garmin.GarminActivitySource
import app.stravadashboard.garmin.GarminConnect
import app.stravadashboard.garmin.GarminWellnessSource
import app.stravadashboard.garmin.garminHttpClient
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRingDayRepository
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.weather.OpenMeteoClient
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
    val runs = ExposedRunRepository(database)
    val syncState = ExposedSyncStateRepository(database)
    val wellness = ExposedWellnessRepository(database)
    val ringDays = ExposedRingDayRepository(database)
}

/** What both people share. */
internal class SharedDeps(
    val weatherHome: WeatherLocation?,
    val weatherSource: OpenMeteoClient?,
    val scope: CoroutineScope,
    val garminEngine: HttpClientEngine?,
    val clock: Clock,
    val zone: ZoneId,
)

/** One person's complete backend: the same services, on their own store, Garmin key and settings. */
internal class PersonStack(
    val otherSync: OtherPersonSync,
    val routes: Route.(other: OtherPersonSync?) -> Unit,
)

// Wiring only, as module() was before it.
@Suppress("LongMethod")
internal fun Application.personStack(
    settings: PersonSettings,
    stores: PersonStores,
    planSource: TrainingPlanSource,
    deps: SharedDeps,
): PersonStack {
    val (clock, zone) = deps.clock to deps.zone
    val runs = stores.runs
    val garmin = settings.garminApiKey?.let { garminConnect(it, deps.garminEngine, clock) }
    val sync = garmin?.let { backgroundSync(it, settings, stores, deps) }
    val plan = TrainingPlanService(planSource, settings.trainingPlanStartDate, clock, zone)
    val load = TrainingLoadService(settings.lthr, runs, clock, zone)
    val marathonPace =
        MarathonPaceService(
            settings.marathonPace,
            settings.trainingPlanStartDate,
            settings.buildWeeks,
            runs,
            clock,
            zone,
        )
    return PersonStack(OtherPersonSync(sync)) { other ->
        runningDashboardRoutes(RunningDashboardService(runs, clock, zone))
        syncRoutes(sync, other)
        trainingPlanRoutes(plan)
        trainingLoadRoutes(load)
        raceDayFormRoutes(RaceDayFormService(settings.lthr, runs, plan, clock, zone))
        vo2maxRoutes(Vo2maxService(settings.maxHr, runs, clock, zone))
        normalizedVo2maxRoutes(NormalizedVo2maxService(settings.maxHr, runs, clock, zone))
        longRunDriftRoutes(LongRunDriftService(runs, clock, zone))
        efficiencyFactorRoutes(EfficiencyFactorService(runs, clock, zone))
        marathonPaceRoutes(marathonPace)
        wellnessRoutes(WellnessService(garmin != null, stores.wellness, load, clock, zone))
        ringsRoutes(RingsService(garmin != null, stores.wellness, stores.ringDays, clock, zone))
        weeklySummaryRoutes(WeeklySummaryService(settings.lthr, runs, stores.wellness, clock, zone))
        runWeatherRoutes(RunWeatherService(deps.weatherHome, deps.weatherSource, plan, clock, zone))
    }
}

/** Activities (feature 021), then resting HR, HRV (015) and ring days (020), all from the person's Garmin account. */
private fun backgroundSync(
    garmin: GarminConnect,
    settings: PersonSettings,
    stores: PersonStores,
    deps: SharedDeps,
): BackgroundSync {
    val (clock, zone) = deps.clock to deps.zone
    val budget = CallBudget(clock)
    val from = settings.garminActivitiesFrom
    val activities = GarminActivitySource(garmin, clock, zone)
    val syncService = SyncService(activities, stores.runs, stores.syncState, clock, zone, from, budget)
    val source = GarminWellnessSource(garmin)
    val rings = RingsSync(source, stores.ringDays, (settings.lthr as? LthrSetting.Valid)?.lthr, clock, zone)
    val wellness = WellnessSync(source, stores.wellness, clock, zone, rings)
    return BackgroundSync(syncService, stores.runs, stores.syncState, budget, clock, deps.scope, from, wellness)
}

/**
 * One Garmin Connect connection per person. Without a test [engine], a Java engine is created; the client and that
 * engine are closed when the application stops.
 */
private fun Application.garminConnect(
    apiKey: String,
    engine: HttpClientEngine?,
    clock: Clock,
): GarminConnect {
    // Java (JDK HttpClient), not CIO: CIO's own TLS stack fails the handshake with Garmin's servers.
    val ownEngine = if (engine == null) Java.create() else null
    val http = garminHttpClient(engine ?: requireNotNull(ownEngine))
    monitor.subscribe(ApplicationStopped) {
        http.close()
        ownEngine?.close()
    }
    return GarminConnect(http, apiKey, clock)
}
