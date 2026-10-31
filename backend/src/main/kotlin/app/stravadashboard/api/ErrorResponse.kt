package app.stravadashboard.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
) {
    companion object {
        val NOT_CONNECTED = ErrorResponse("not_connected", "Connect your Strava account.")
        val REAUTH_REQUIRED =
            ErrorResponse("strava_reauth_required", "Your Strava connection expired. Please connect again.")
        val INTERNAL = ErrorResponse("internal_error", "Something went wrong.")
        val TRAINING_PLAN_NOT_CONFIGURED =
            ErrorResponse("training_plan_not_configured", "No training plan is configured.")
        val TRAINING_PLAN_UNAVAILABLE =
            ErrorResponse("training_plan_unavailable", "The training plan file could not be read.")
        val TRAINING_LOAD_NOT_CONFIGURED =
            ErrorResponse("training_load_not_configured", "No threshold heart rate is configured.")

        /** Fixed text: never includes the configured value. */
        val TRAINING_LOAD_INVALID_CONFIG =
            ErrorResponse("training_load_invalid_config", "LTHR_BPM is not a whole number between 100 and 220.")

        /** [reason] comes from the plan parser and never contains the file path or cell values. */
        fun trainingPlanInvalid(reason: String): ErrorResponse = ErrorResponse("training_plan_invalid", reason)
    }
}
