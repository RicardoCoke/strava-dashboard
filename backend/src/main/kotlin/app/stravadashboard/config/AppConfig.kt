package app.stravadashboard.config

import app.stravadashboard.domain.marathonpace.MarathonPaceVolume
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.HexFormat

/**
 * Typed application config, loaded once at startup from environment variables.
 * Required values fail fast at startup. [toString] never prints secrets.
 */
data class AppConfig(
    val host: String,
    val port: Int,
    val stravaClientId: String,
    val stravaClientSecret: String,
    val stravaRedirectUri: String,
    val sessionSecretHex: String,
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
    /** Garmin Connect Personal API key for resting HR and HRV (feature 015), or null when not connected. */
    val garminApiKey: String? = null,
    /** Home location for the calendar's run weather (feature 018), or null when the feature is off. */
    val weatherHome: WeatherLocation? = null,
    /** Marina's own settings (feature 019), or null when she isn't set up. */
    val marina: PersonSettings? = null,
) {
    val sessionSecret: ByteArray get() = HexFormat.of().parseHex(sessionSecretHex)

    val ricardo: PersonSettings
        get() =
            PersonSettings(
                stravaClientId,
                stravaClientSecret,
                dbPath,
                trainingPlanCsvPath,
                trainingPlanStartDate,
                lthr,
                maxHr,
                marathonPace,
                garminApiKey,
                MarathonPaceVolume.BUILD_WEEKS,
            )

    val secureCookies: Boolean get() = stravaRedirectUri.startsWith("https://")

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
        return "AppConfig(host=$host, port=$port, stravaRedirectUri=$stravaRedirectUri, dbPath=$dbPath, " +
            "trainingPlanCsv=$plan, trainingPlanStartDate=$trainingPlanStartDate, lthr=$lthrText, " +
            "maxHr=$maxHrText, marathonPace=$marathonPaceText, " +
            "garminApiKey=${if (garminApiKey == null) "<unset>" else "<set>"}, " +
            "weather=${if (weatherHome == null) "<unset>" else "<set>"}, " +
            "marina=${if (marina == null) "<unset>" else "<set>"}, secrets=<redacted>)"
    }

    companion object {
        val DEFAULT_TRAINING_PLAN_START: LocalDate = LocalDate.of(2026, 8, 17)
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8080
        private const val MIN_SESSION_SECRET_BYTES = 32

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig =
            AppConfig(
                host = env["HOST"]?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST,
                port =
                    env["PORT"]?.takeIf { it.isNotBlank() }?.let {
                        requireNotNull(it.toIntOrNull()) { "PORT must be a number, was '$it'" }
                    } ?: DEFAULT_PORT,
                stravaClientId = env.required("STRAVA_CLIENT_ID"),
                stravaClientSecret = env.required("STRAVA_CLIENT_SECRET"),
                stravaRedirectUri = env.required("STRAVA_REDIRECT_URI"),
                sessionSecretHex = validSessionSecret(env.required("SESSION_SECRET")),
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
                weatherHome = WeatherLocation.parse(env["WEATHER_LATITUDE"], env["WEATHER_LONGITUDE"]),
                marina = marinaSettings(env, env.required("DB_PATH")),
            )

        private fun validSessionSecret(hex: String): String {
            val trimmed = hex.trim()
            val bytes = runCatching { HexFormat.of().parseHex(trimmed) }.getOrNull()
            require(bytes != null && bytes.size >= MIN_SESSION_SECRET_BYTES) {
                "SESSION_SECRET must be at least $MIN_SESSION_SECRET_BYTES bytes of hex (64 hex characters)"
            }
            return trimmed
        }
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
