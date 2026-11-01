package app.stravadashboard.domain.marathonpace

import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface MarathonPaceResult {
    data object NotConnected : MarathonPaceResult

    data object NotConfigured : MarathonPaceResult

    data object InvalidConfig : MarathonPaceResult

    data class Ready(
        val report: MpReport,
    ) : MarathonPaceResult
}

/** Checks the config, then computes MP km per build week from the local store. Never calls Strava. */
class MarathonPaceService(
    private val setting: MarathonPaceSetting,
    private val buildStart: LocalDate,
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): MarathonPaceResult =
        when (setting) {
            MarathonPaceSetting.NotConfigured -> MarathonPaceResult.NotConfigured
            MarathonPaceSetting.Invalid -> MarathonPaceResult.InvalidConfig
            is MarathonPaceSetting.Valid -> compute(MpGoal(setting.pace))
        }

    private suspend fun compute(goal: MpGoal): MarathonPaceResult {
        if (tokens.find() == null) return MarathonPaceResult.NotConnected
        val today = LocalDate.now(clock.withZone(zone))
        return MarathonPaceResult.Ready(MarathonPaceVolume.compute(runs.findAll(), goal, buildStart, today))
    }
}
