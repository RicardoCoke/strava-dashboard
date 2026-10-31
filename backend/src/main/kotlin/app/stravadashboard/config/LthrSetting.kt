package app.stravadashboard.config

import app.stravadashboard.domain.model.Bpm

/** The optional `LTHR_BPM` setting (feature 005). Missing or invalid values don't stop the app. */
sealed interface LthrSetting {
    data object NotConfigured : LthrSetting

    data object Invalid : LthrSetting

    data class Valid(
        val lthr: Bpm,
    ) : LthrSetting {
        override fun toString(): String = "Valid(<redacted>)"
    }

    companion object {
        const val MIN_LTHR_BPM = 100
        const val MAX_LTHR_BPM = 220

        fun parse(text: String?): LthrSetting {
            val trimmed = text?.trim().orEmpty()
            val bpm = trimmed.toIntOrNull()?.takeIf { it in MIN_LTHR_BPM..MAX_LTHR_BPM }
            return when {
                trimmed.isEmpty() -> NotConfigured
                bpm == null -> Invalid
                else -> Valid(Bpm(bpm))
            }
        }
    }
}
