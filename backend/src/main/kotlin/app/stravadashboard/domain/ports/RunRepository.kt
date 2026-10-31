package app.stravadashboard.domain.ports

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.StreamPaceResult
import java.time.Instant
import java.time.LocalDate

interface RunRepository {
    /** Inserts or updates summary fields; keeps any stored description, laps and stream pace. */
    suspend fun upsertSummaries(summaries: List<ActivitySummary>)

    /** Replaces the description and laps of a stored run. */
    suspend fun saveDetail(
        id: ActivityId,
        detail: ActivityDetail,
        fetchedAt: Instant,
    )

    suspend fun saveStreamPace(
        id: ActivityId,
        result: StreamPaceResult,
        computedAt: Instant,
    )

    suspend fun saveHrHistogram(
        id: ActivityId,
        result: HrHistogramResult,
        computedAt: Instant,
    )

    suspend fun findAll(): List<RunActivity>

    suspend fun newestStartUtc(): Instant?

    /** Deletes runs with their laps, stream paces and heart-rate histograms; unknown ids are ignored. */
    suspend fun delete(ids: Collection<ActivityId>)

    /** Deletes runs whose local start date is before [date]. */
    suspend fun pruneBefore(date: LocalDate)

    suspend fun deleteAll()
}
