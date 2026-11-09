package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.RunRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface NormalizedVo2maxResult {
    data object InvalidConfig : NormalizedVo2maxResult

    data class Ready(
        val report: NormalizedVo2maxReport,
    ) : NormalizedVo2maxResult
}

/** Checks the config, then computes the normalized VO2max report from the local store only. */
class NormalizedVo2maxService(
    private val maxHr: MaxHrSetting,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): NormalizedVo2maxResult =
        when (maxHr) {
            MaxHrSetting.Invalid -> NormalizedVo2maxResult.InvalidConfig
            MaxHrSetting.Default -> compute(Bpm(MaxHrSetting.DEFAULT_MAX_HR_BPM), configured = false)
            is MaxHrSetting.Configured -> compute(maxHr.bpm, configured = true)
        }

    private suspend fun compute(
        maxHr: Bpm,
        configured: Boolean,
    ): NormalizedVo2maxResult {
        val today = LocalDate.now(clock.withZone(zone))
        return NormalizedVo2maxResult.Ready(
            NormalizedVo2maxCalculator.compute(runs.findAll(), maxHr, configured, today),
        )
    }
}
