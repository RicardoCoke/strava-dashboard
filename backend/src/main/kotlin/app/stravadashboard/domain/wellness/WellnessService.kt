package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.ports.WellnessRepository
import app.stravadashboard.domain.trainingload.TrainingLoadService
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface WellnessResult {
    data object NotConfigured : WellnessResult

    data class Ready(
        val view: WellnessView,
        val note: String?,
        val loadHigh: Boolean?,
        val state: WellnessState,
    ) : WellnessResult
}

/** Builds the wellness card from the local cache and the training load result; never calls Intervals.icu. */
class WellnessService(
    private val configured: Boolean,
    private val repo: WellnessRepository,
    private val trainingLoad: TrainingLoadService,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): WellnessResult {
        if (!configured) return WellnessResult.NotConfigured
        val today = LocalDate.now(clock.withZone(zone))
        val stored = repo.findBetween(WellnessPolicy.pruneCutoff(today), today)
        val view = WellnessSeries.build(stored, today)
        val loadHigh = LoadLevel.isHigh(trainingLoad.get())
        return WellnessResult.Ready(view, ReadinessNote.build(view, loadHigh), loadHigh, repo.getState())
    }
}
