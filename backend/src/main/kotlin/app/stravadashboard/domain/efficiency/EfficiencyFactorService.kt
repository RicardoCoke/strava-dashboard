package app.stravadashboard.domain.efficiency

import app.stravadashboard.domain.ports.RunRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface EfficiencyFactorResult {
    data class Ready(
        val report: EfficiencyFactorReport,
    ) : EfficiencyFactorResult
}

/** Computes the efficiency factor report from the local store; never calls Garmin. */
class EfficiencyFactorService(
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): EfficiencyFactorResult {
        val today = LocalDate.now(clock.withZone(zone))
        return EfficiencyFactorResult.Ready(EfficiencyFactorCalculator.compute(runs.findAll(), today))
    }
}
