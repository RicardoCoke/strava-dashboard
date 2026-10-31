package app.stravadashboard.domain.model

sealed interface RunType {
    val key: String
    val label: String

    data object Easy : RunType {
        override val key: String = "easy"
        override val label: String = "Easy runs"
    }

    /** A non-workout run over 20 km. Named `LongRun` so it doesn't shadow `kotlin.Long`. */
    data object LongRun : RunType {
        override val key: String = "long"
        override val label: String = "Long runs"
    }

    data class Workout(
        val reps: Int,
        val minutes: Int,
    ) : RunType {
        override val key: String get() = "${reps}x${minutes}min"
        override val label: String get() = key
    }
}
