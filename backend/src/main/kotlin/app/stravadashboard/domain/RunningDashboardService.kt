package app.stravadashboard.domain

import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface DashboardResult {
    data object NotConnected : DashboardResult

    data object ReauthRequired : DashboardResult

    data class Ready(
        val sync: SyncInfo,
        val weeks: List<WeekTotal>,
        val heartRate: HrTrendResult,
        val pace: PaceComparisonResult,
    ) : DashboardResult
}

/** Syncs if due, then builds the three dashboard aggregates from the local store. */
class RunningDashboardService(
    private val tokens: TokenRepository,
    private val syncService: SyncService,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): DashboardResult {
        if (tokens.find() == null) return DashboardResult.NotConnected
        return when (val outcome = syncService.syncIfDue()) {
            SyncOutcome.ReauthRequired -> DashboardResult.ReauthRequired
            is SyncOutcome.Done -> {
                val stored = runs.findAll()
                val today = LocalDate.now(clock.withZone(zone))
                DashboardResult.Ready(
                    sync = outcome.info,
                    weeks = WeeklyDistance.compute(stored, today),
                    heartRate = HeartRateTrend.compute(stored, today),
                    pace = WorkoutPaceComparison.compute(stored, today),
                )
            }
        }
    }
}
