package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.SyncPolicy
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.ports.ActivitySource
import app.stravadashboard.domain.ports.RunRepository
import app.stravadashboard.domain.ports.SourceResult
import java.time.Instant

/**
 * The drift-streams call of a sync (feature 013): reduces the streams to one drift value in memory and stores only
 * that result. A 404 or unusable stream is stored as NO_DATA; any other failure stops the sync and writes nothing.
 */
internal suspend fun fetchDriftStream(
    source: ActivitySource,
    runs: RunRepository,
    run: RunActivity,
    now: Instant,
): SourceResult<Nothing>? {
    val stored =
        when (val result = source.driftStream(run.id)) {
            is SourceResult.Success -> HrDriftCalculator.compute(result.value)
            SourceResult.NotFound -> HrDriftResult(HrDriftStatus.NO_DATA, null)
            else -> return SyncPolicy.failureOf(result)
        }
    runs.saveHrDrift(run.id, stored, now)
    return null
}
