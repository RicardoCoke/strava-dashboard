package app.stravadashboard.domain.trainingload

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface TrainingLoadResult {
    data object NotConnected : TrainingLoadResult

    data object NotConfigured : TrainingLoadResult

    data object InvalidConfig : TrainingLoadResult

    data class Ready(
        val load: TrainingLoad,
        val ramp: Ramp,
        val zones: HrZones,
    ) : TrainingLoadResult
}

/** Checks the config, then computes the training load from the local store. */
class TrainingLoadService(
    private val lthr: LthrSetting,
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): TrainingLoadResult =
        when (lthr) {
            LthrSetting.NotConfigured -> TrainingLoadResult.NotConfigured
            LthrSetting.Invalid -> TrainingLoadResult.InvalidConfig
            is LthrSetting.Valid -> compute(HrZones.of(lthr.lthr))
        }

    private suspend fun compute(zones: HrZones): TrainingLoadResult {
        if (tokens.find() == null) return TrainingLoadResult.NotConnected
        val stored = runs.findAll()
        val today = LocalDate.now(clock.withZone(zone))
        val loads = TrainingLoadCalculator.dailyLoads(stored, zones, today)
        return TrainingLoadResult.Ready(
            load = TrainingLoadCalculator.compute(stored, loads, today),
            ramp = RampRate.compute(loads, today),
            zones = zones,
        )
    }
}
