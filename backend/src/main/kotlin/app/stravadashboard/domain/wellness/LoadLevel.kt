package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.trainingload.TrainingLoadResult
import app.stravadashboard.domain.wellness.WellnessPolicy.HIGH_LOAD_FORM

/** Reads "load is high" from the unchanged training load result (feature 005); null when it isn't available. */
object LoadLevel {
    fun isHigh(result: TrainingLoadResult): Boolean? {
        val ready = result as? TrainingLoadResult.Ready ?: return null
        val form =
            ready.load.days
                .lastOrNull()
                ?.form
        val lowForm = form != null && form <= HIGH_LOAD_FORM
        return lowForm || ready.ramp.thisWeek.warning || ready.ramp.lastWeek.warning
    }
}
