package app.stravadashboard.domain

import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.TokenRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

sealed interface DashboardResult {
    data object NotConnected : DashboardResult

    data class Ready(
        val weeks: List<WeekTotal>,
        val heartRate: HrTrendResult,
        val pace: PaceComparisonResult,
        val weekComparison: WeekComparisonResult,
    ) : DashboardResult
}

/** Builds the dashboard aggregates from the local store only; syncing runs in [BackgroundSync] (feature 009). */
class RunningDashboardService(
    private val tokens: TokenRepository,
    private val runs: RunRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): DashboardResult {
        if (tokens.find() == null) return DashboardResult.NotConnected
        val stored = runs.findAll()
        val today = LocalDate.now(clock.withZone(zone))
        val weeks = WeeklyDistance.compute(stored, today)
        return DashboardResult.Ready(
            weeks = weeks,
            heartRate = HeartRateTrend.compute(stored, today),
            pace = WorkoutPaceComparison.compute(stored, today),
            weekComparison = WeekComparison.compute(stored, weeks, today),
        )
    }
}
