package app.stravadashboard.config

import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.SecondsPerKm

/** The optional `MARATHON_GOAL_PACE` setting (feature 012), as `m:ss` per km. Invalid values don't stop the app. */
sealed interface MarathonPaceSetting {
    data object NotConfigured : MarathonPaceSetting

    data object Invalid : MarathonPaceSetting

    data class Valid(
        val pace: SecondsPerKm,
    ) : MarathonPaceSetting {
        override fun toString(): String = "Valid(<redacted>)"
    }

    companion object {
        const val MIN_GOAL_PACE_S_PER_KM = 150
        const val MAX_GOAL_PACE_S_PER_KM = 480
        private val PACE_PATTERN = Regex("""^(\d{1,2}):([0-5]\d)$""")

        fun parse(text: String?): MarathonPaceSetting {
            val trimmed = text?.trim().orEmpty()
            val seconds = secondsOf(trimmed)?.takeIf { it in MIN_GOAL_PACE_S_PER_KM..MAX_GOAL_PACE_S_PER_KM }
            return when {
                trimmed.isEmpty() -> NotConfigured
                seconds == null -> Invalid
                else -> Valid(SecondsPerKm(seconds))
            }
        }

        private fun secondsOf(text: String): Int? =
            PACE_PATTERN.matchEntire(text)?.destructured?.let { (minutes, seconds) ->
                minutes.toInt() * SECONDS_PER_MINUTE + seconds.toInt()
            }
    }
}
