package app.stravadashboard.config

import java.nio.file.Path
import java.time.LocalDate

/** What one person's backend stack needs. [toString] prints nothing of theirs. */
data class PersonSettings(
    val dbPath: String,
    val trainingPlanCsvPath: String?,
    val trainingPlanStartDate: LocalDate,
    val lthr: LthrSetting,
    val maxHr: MaxHrSetting,
    val marathonPace: MarathonPaceSetting,
    val garminApiKey: String?,
    val garminActivitiesFrom: LocalDate,
    val buildWeeks: Int,
) {
    override fun toString(): String = "PersonSettings(<redacted>)"
}

private const val MARINA_BUILD_WEEKS = 18

/** Marina's settings, or null when MARINA_DB_PATH isn't set (feature 021: no Strava app of her own any more). */
internal fun marinaSettings(
    env: Map<String, String>,
    ricardoDbPath: String,
): PersonSettings? {
    val dbPath = env.optional("MARINA_DB_PATH") ?: return null
    require(Path.of(dbPath).toAbsolutePath().normalize() != Path.of(ricardoDbPath).toAbsolutePath().normalize()) {
        "MARINA_DB_PATH must not be the same file as DB_PATH"
    }
    val csvPath = env.optional("MARINA_TRAINING_PLAN_CSV")
    val goalPace = env.optional("MARINA_MARATHON_GOAL_PACE")
    val startText = env.optional("MARINA_TRAINING_PLAN_START_DATE")
    // Her build dates mean nothing without a start, and there is no default for her.
    require(startText != null || (csvPath == null && goalPace == null)) {
        "MARINA_TRAINING_PLAN_START_DATE must be set (see .env.example)"
    }
    return PersonSettings(
        dbPath = dbPath,
        trainingPlanCsvPath = csvPath,
        trainingPlanStartDate =
            startText?.let { validStartDate(it, "MARINA_TRAINING_PLAN_START_DATE") }
                ?: AppConfig.DEFAULT_TRAINING_PLAN_START,
        lthr = LthrSetting.parse(env["MARINA_LTHR_BPM"]),
        maxHr = MaxHrSetting.parse(env["MARINA_MAX_HR_BPM"]),
        marathonPace = MarathonPaceSetting.parse(goalPace),
        garminApiKey = env.optional("MARINA_GARMIN_API_KEY"),
        garminActivitiesFrom = garminActivitiesFrom(env, "MARINA_GARMIN_ACTIVITIES_FROM"),
        buildWeeks = MARINA_BUILD_WEEKS,
    )
}

internal fun Map<String, String>.required(key: String): String =
    requireNotNull(this[key]?.takeIf { it.isNotBlank() }) { "$key must be set (see .env.example)" }

private fun Map<String, String>.optional(key: String): String? = this[key]?.trim()?.takeIf { it.isNotEmpty() }
