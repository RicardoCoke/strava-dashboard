package app.stravadashboard.config

import app.stravadashboard.domain.marathonpace.MarathonPaceVolume
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Typed application config, loaded once at startup from environment variables.
 * Required values fail fast at startup. [toString] never prints secrets.
 */
data class AppConfig(
    val host: String,
    val port: Int,
    val dbPath: String,
    /** Path to the training-plan CSV (feature 004), or null when no plan is configured. */
    val trainingPlanCsvPath: String? = null,
    val trainingPlanStartDate: LocalDate = DEFAULT_TRAINING_PLAN_START,
    /** Lactate-threshold heart rate for the training load chart (feature 005). */
    val lthr: LthrSetting = LthrSetting.NotConfigured,
    /** Maximum heart rate for the VO2max estimate (feature 008). */
    val maxHr: MaxHrSetting = MaxHrSetting.Default,
    /** Goal marathon pace for the marathon-pace card (feature 012). */
    val marathonPace: MarathonPaceSetting = MarathonPaceSetting.NotConfigured,
    /** Garmin Connect Personal API key for activities (feature 021) and resting HR/HRV (feature 015), or null. */
    val garminApiKey: String? = null,
    /** First local date whose activities come from Garmin; earlier runs are the cached Strava history (feature 021). */
    val garminActivitiesFrom: LocalDate = DEFAULT_GARMIN_ACTIVITIES_FROM,
    /** Home location for the calendar's run weather (feature 018), or null when the feature is off. */
    val weatherHome: WeatherLocation? = null,
    /** Marina's own settings (feature 019), or null when she isn't set up. */
    val marina: PersonSettings? = null,
) {
    val ricardo: PersonSettings
        get() =
            PersonSettings(
                dbPath,
                trainingPlanCsvPath,
                trainingPlanStartDate,
                lthr,
                maxHr,
                marathonPace,
                garminApiKey,
                garminActivitiesFrom,
                MarathonPaceVolume.BUILD_WEEKS,
            )

    override fun toString(): String {
        val plan = if (trainingPlanCsvPath == null) "<unset>" else "<set>"
        val lthrText =
            when (lthr) {
                LthrSetting.NotConfigured -> "<unset>"
                LthrSetting.Invalid -> "<invalid>"
                is LthrSetting.Valid -> "<set>"
            }
        val maxHrText =
            when (maxHr) {
                MaxHrSetting.Default -> "<default>"
                MaxHrSetting.Invalid -> "<invalid>"
                is MaxHrSetting.Configured -> "<set>"
            }
        val marathonPaceText =
            when (marathonPace) {
                MarathonPaceSetting.NotConfigured -> "<unset>"
                MarathonPaceSetting.Invalid -> "<invalid>"
                is MarathonPaceSetting.Valid -> "<set>"
            }
        return "AppConfig(host=$host, port=$port, dbPath=$dbPath, " +
            "trainingPlanCsv=$plan, trainingPlanStartDate=$trainingPlanStartDate, lthr=$lthrText, " +
            "maxHr=$maxHrText, marathonPace=$marathonPaceText, " +
            "garminApiKey=${if (garminApiKey == null) "<unset>" else "<set>"}, " +
            "garminActivitiesFrom=$garminActivitiesFrom, " +
            "weather=${if (weatherHome == null) "<unset>" else "<set>"}, " +
            "marina=${if (marina == null) "<unset>" else "<set>"})"
    }

    companion object {
        val DEFAULT_TRAINING_PLAN_START: LocalDate = LocalDate.of(2026, 8, 17)

        /** The day after the last Strava sync (feature 021). */
        val DEFAULT_GARMIN_ACTIVITIES_FROM: LocalDate = LocalDate.of(2026, 11, 10)
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8080

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig =
            AppConfig(
                host = env["HOST"]?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
                port =
                    env["PORT"]?.takeIf { it.isNotBlank() }?.let {
                        requireNotNull(it.toIntOrNull()) { "PORT must be a number, was '$it'" }
                    } ?: DEFAULT_PORT,
                dbPath = env.required("DB_PATH"),
                trainingPlanCsvPath = env["TRAINING_PLAN_CSV"]?.trim()?.takeIf { it.isNotEmpty() },
                trainingPlanStartDate =
                    env["TRAINING_PLAN_START_DATE"]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { validStartDate(it, "TRAINING_PLAN_START_DATE") }
                        ?: DEFAULT_TRAINING_PLAN_START,
                lthr = LthrSetting.parse(env["LTHR_BPM"]),
                maxHr = MaxHrSetting.parse(env["MAX_HR_BPM"]),
                marathonPace = MarathonPaceSetting.parse(env["MARATHON_GOAL_PACE"]),
                garminApiKey = env["GARMIN_API_KEY"]?.trim()?.takeIf { it.isNotEmpty() },
                garminActivitiesFrom = garminActivitiesFrom(env, "GARMIN_ACTIVITIES_FROM"),
                weatherHome = WeatherLocation.parse(env["WEATHER_LATITUDE"], env["WEATHER_LONGITUDE"]),
                marina = marinaSettings(env, env.required("DB_PATH")),
            )
    }
}

internal fun validStartDate(
    text: String,
    key: String,
): LocalDate {
    val date =
        requireNotNull(runCatching { LocalDate.parse(text.trim()) }.getOrNull()) {
            "$key must be an ISO date (YYYY-MM-DD)"
        }
    require(date.dayOfWeek == DayOfWeek.MONDAY) { "$key must be a Monday" }
    return date
}

/** An optional ISO date of any weekday, defaulting to [AppConfig.DEFAULT_GARMIN_ACTIVITIES_FROM] (feature 021). */
internal fun garminActivitiesFrom(
    env: Map<String, String>,
    key: String,
): LocalDate =
    env[key]?.trim()?.takeIf { it.isNotEmpty() }?.let {
        requireNotNull(runCatching { LocalDate.parse(it) }.getOrNull()) { "$key must be an ISO date (YYYY-MM-DD)" }
    } ?: AppConfig.DEFAULT_GARMIN_ACTIVITIES_FROM
