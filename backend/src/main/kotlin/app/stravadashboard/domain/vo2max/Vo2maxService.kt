package app.stravadashboard.domain.vo2max

import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface Vo2maxResult {
    data object NotConnected : Vo2maxResult

    data object InvalidConfig : Vo2maxResult

    data class Ready(
        val report: Vo2maxReport,
    ) : Vo2maxResult
}

/** Checks the config, then computes the VO2max report from the local store. */
class Vo2maxService(
    private val maxHr: MaxHrSetting,
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): Vo2maxResult =
        when (maxHr) {
            MaxHrSetting.Invalid -> Vo2maxResult.InvalidConfig
            MaxHrSetting.Default -> compute(Bpm(MaxHrSetting.DEFAULT_MAX_HR_BPM), configured = false)
            is MaxHrSetting.Configured -> compute(maxHr.bpm, configured = true)
        }

    private suspend fun compute(
        maxHr: Bpm,
        configured: Boolean,
    ): Vo2maxResult {
        if (tokens.find() == null) return Vo2maxResult.NotConnected
        val today = LocalDate.now(clock.withZone(zone))
        return Vo2maxResult.Ready(Vo2maxCalculator.compute(runs.findAll(), maxHr, configured, today))
    }
}
