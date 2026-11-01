package app.stravadashboard.domain.efficiency

import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface EfficiencyFactorResult {
    data object NotConnected : EfficiencyFactorResult

    data class Ready(
        val report: EfficiencyFactorReport,
    ) : EfficiencyFactorResult
}

/** Computes the efficiency factor report from the local store; never calls Strava. */
class EfficiencyFactorService(
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): EfficiencyFactorResult {
        if (tokens.find() == null) return EfficiencyFactorResult.NotConnected
        val today = LocalDate.now(clock.withZone(zone))
        return EfficiencyFactorResult.Ready(EfficiencyFactorCalculator.compute(runs.findAll(), today))
    }
}
