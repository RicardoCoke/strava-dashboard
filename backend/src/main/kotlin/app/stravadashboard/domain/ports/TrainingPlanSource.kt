package app.stravadashboard.domain.ports

/** Largest training-plan file the backend will read (004 AC2.5). */
const val MAX_PLAN_FILE_BYTES = 1_048_576L

sealed interface PlanFile {
    data object NotConfigured : PlanFile

    data object Unreadable : PlanFile

    data object TooLarge : PlanFile

    data class Content(
        val text: String,
    ) : PlanFile
}

/** Reads the training-plan CSV. Implementations never log the file path or its contents. */
interface TrainingPlanSource {
    suspend fun read(): PlanFile
}
