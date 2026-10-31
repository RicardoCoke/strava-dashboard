package app.stravadashboard.config

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
) {
    val sessionSecret: ByteArray get() = HexFormat.of().parseHex(sessionSecretHex)

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
        return "AppConfig(host=$host, port=$port, stravaRedirectUri=$stravaRedirectUri, dbPath=$dbPath, " +
            "trainingPlanCsv=$plan, trainingPlanStartDate=$trainingPlanStartDate, lthr=$lthrText, " +
            "maxHr=$maxHrText, secrets=<redacted>)"
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
                    env["TRAINING_PLAN_START_DATE"]?.takeIf { it.isNotBlank() }?.let(::validStartDate)
                        ?: DEFAULT_TRAINING_PLAN_START,
                lthr = LthrSetting.parse(env["LTHR_BPM"]),
                maxHr = MaxHrSetting.parse(env["MAX_HR_BPM"]),
            )

        private fun validStartDate(text: String): LocalDate {
            val date =
                requireNotNull(runCatching { LocalDate.parse(text.trim()) }.getOrNull()) {
                    "TRAINING_PLAN_START_DATE must be an ISO date (YYYY-MM-DD)"
                }
            require(date.dayOfWeek == DayOfWeek.MONDAY) { "TRAINING_PLAN_START_DATE must be a Monday" }
            return date
        }

        private fun Map<String, String>.required(key: String): String =
            requireNotNull(this[key]?.takeIf { it.isNotBlank() }) { "$key must be set (see .env.example)" }

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
