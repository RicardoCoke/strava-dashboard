package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface LongRunDriftResult {
    data object NotConnected : LongRunDriftResult

    data class Ready(
        val report: LongRunDriftReportData,
    ) : LongRunDriftResult
}

/** Builds the long-run drift report from the local store only; it never calls Strava. */
class LongRunDriftService(
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): LongRunDriftResult {
        if (tokens.find() == null) return LongRunDriftResult.NotConnected
        val today = LocalDate.now(clock.withZone(zone))
        return LongRunDriftResult.Ready(LongRunDriftReport.compute(runs.findAll(), today))
    }
}
