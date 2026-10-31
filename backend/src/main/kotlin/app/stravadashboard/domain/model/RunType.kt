package app.stravadashboard.domain.model

sealed interface RunType {
    val key: String
    val label: String

    data object Easy : RunType {
        override val key: String = "easy"
        override val label: String = "Easy runs"
    }

    data class Workout(
        val reps: Int,
        val minutes: Int,
    ) : RunType {
        override val key: String get() = "${reps}x${minutes}min"
        override val label: String get() = key
    }
}
