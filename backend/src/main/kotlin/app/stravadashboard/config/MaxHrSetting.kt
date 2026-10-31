package app.stravadashboard.config

import app.stravadashboard.domain.model.Bpm

/** The optional `MAX_HR_BPM` setting (feature 008). Unset uses an estimate; invalid values don't stop the app. */
sealed interface MaxHrSetting {
    data object Default : MaxHrSetting

    data object Invalid : MaxHrSetting

    data class Configured(
        val bpm: Bpm,
    ) : MaxHrSetting {
        override fun toString(): String = "Configured(<redacted>)"
    }

    companion object {
        const val DEFAULT_MAX_HR_BPM = 194
        const val MIN_MAX_HR_BPM = 120
        const val MAX_MAX_HR_BPM = 230

        fun parse(text: String?): MaxHrSetting {
            val trimmed = text?.trim().orEmpty()
            val bpm = trimmed.toIntOrNull()?.takeIf { it in MIN_MAX_HR_BPM..MAX_MAX_HR_BPM }
            return when {
                trimmed.isEmpty() -> Default
                bpm == null -> Invalid
                else -> Configured(Bpm(bpm))
            }
        }
    }
}
