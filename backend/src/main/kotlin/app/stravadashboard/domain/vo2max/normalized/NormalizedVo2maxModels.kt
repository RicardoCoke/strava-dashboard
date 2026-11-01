package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.vo2max.CurrentVo2max
import app.stravadashboard.domain.vo2max.RacePrediction
import java.time.LocalDate

/** [vo2max] is the plain per-run mean, null exactly when [runCount] is 0; [originalVo2max] is the 008 value. */
data class NormalizedWeek(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val vo2max: Double?,
    val runCount: Int,
    val inProgress: Boolean,
    val originalVo2max: Double?,
)

data class NormalizedVo2maxReport(
    val maxHr: Bpm,
    val maxHrConfigured: Boolean,
    val current: CurrentVo2max?,
    val predictions: List<RacePrediction>,
    val weeks: List<NormalizedWeek>,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val runsPendingDetail: Int,
)
