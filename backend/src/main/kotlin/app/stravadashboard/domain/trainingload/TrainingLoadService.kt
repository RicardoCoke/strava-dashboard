package app.stravadashboard.domain.trainingload

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.SyncOutcome
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface TrainingLoadResult {
    data object NotConnected : TrainingLoadResult

    data object NotConfigured : TrainingLoadResult

    data object InvalidConfig : TrainingLoadResult

    data object ReauthRequired : TrainingLoadResult

    data class Ready(
        val load: TrainingLoad,
        val ramp: Ramp,
        val zones: HrZones,
    ) : TrainingLoadResult
}

/** Checks the config, syncs if due, then computes the training load from the local store. */
class TrainingLoadService(
    private val lthr: LthrSetting,
    private val tokens: TokenRepository,
    private val syncService: SyncService,
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
        return when (syncService.syncIfDue()) {
            SyncOutcome.ReauthRequired -> TrainingLoadResult.ReauthRequired
            is SyncOutcome.Done -> {
                val stored = runs.findAll()
                val today = LocalDate.now(clock.withZone(zone))
                val loads = TrainingLoadCalculator.dailyLoads(stored, zones, today)
                TrainingLoadResult.Ready(
                    load = TrainingLoadCalculator.compute(stored, loads, today),
                    ramp = RampRate.compute(loads, today),
                    zones = zones,
                )
            }
        }
    }
}
