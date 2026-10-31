package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType

/**
 * Classifies a run as an `NxMmin` workout (from the name, else the description), a long run (over 20 km)
 * or an easy run. A workout pattern wins over distance.
 */
object RunClassifier {
    const val LONG_RUN_MIN_EXCLUSIVE_METERS = 20_000.0
    private val WORKOUT_PATTERN =
        Regex("""(?<![\d.])(\d{1,2})\s*[x×]\s*(\d{1,3})\s*(?:min(?:ute)?s?|')(?![a-z])""", RegexOption.IGNORE_CASE)
    private val REPS_RANGE = 1..50
    private val MINUTES_RANGE = 1..120

    fun classify(run: RunActivity): RunType {
        val byText = classify(run.name, run.description)
        val isLong = byText == RunType.Easy && run.distance.value > LONG_RUN_MIN_EXCLUSIVE_METERS
        return if (isLong) RunType.LongRun else byText
    }

    /** Text only: returns [RunType.Workout] or [RunType.Easy] and ignores distance. */
    fun classify(
        name: String,
        description: String?,
    ): RunType {
        val match = WORKOUT_PATTERN.find(name) ?: description?.let { WORKOUT_PATTERN.find(it) }
        val reps =
            match
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it in REPS_RANGE }
        val minutes =
            match
                ?.groupValues
                ?.get(2)
                ?.toIntOrNull()
                ?.takeIf { it in MINUTES_RANGE }
        return if (reps != null && minutes != null) RunType.Workout(reps, minutes) else RunType.Easy
    }
}
