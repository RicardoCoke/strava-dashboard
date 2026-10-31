package app.stravadashboard.persistence

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.domain.ports.RunRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExposedRunRepository(
    private val database: Database,
) : RunRepository {
    override suspend fun upsertSummaries(summaries: List<ActivitySummary>) {
        if (summaries.isEmpty()) return
        database.query {
            RunsTable.batchUpsert(summaries) { summary ->
                this[RunsTable.id] = summary.id.value
                this[RunsTable.name] = summary.name
                this[RunsTable.startDateUtc] = summary.startUtc.epochSecond
                this[RunsTable.startDateLocal] = summary.startLocal.format(RUN_LOCAL_FORMAT)
                this[RunsTable.distanceM] = summary.distance.value
                this[RunsTable.averageHeartrate] = summary.averageHeartRateBpm
                this[RunsTable.trainer] = summary.trainer
            }
        }
    }

    override suspend fun saveDetail(
        id: ActivityId,
        detail: ActivityDetail,
        fetchedAt: Instant,
    ) {
        database.query {
            RunsTable.update({ RunsTable.id eq id.value }) {
                it[description] = detail.description
                it[detailFetchedAt] = fetchedAt.epochSecond
                it[movingTimeS] = detail.movingTime.value
            }
            RunLapsTable.deleteWhere { runId eq id.value }
            RunLapsTable.batchInsert(detail.laps) { lap ->
                this[RunLapsTable.runId] = id.value
                this[RunLapsTable.lapIndex] = lap.index
                this[RunLapsTable.elapsedTimeS] = lap.elapsed.value
                this[RunLapsTable.distanceM] = lap.distance.value
                this[RunLapsTable.averageHeartrate] = lap.averageHeartRateBpm
            }
        }
    }

    override suspend fun saveStreamPace(
        id: ActivityId,
        result: StreamPaceResult,
        computedAt: Instant,
    ) {
        database.query {
            RunStreamPacesTable.upsert {
                it[runId] = id.value
                it[typeKey] = result.typeKey
                it[status] = result.status.toDbValue()
                it[repPaceSPerKm] = result.pace?.value
                it[RunStreamPacesTable.computedAt] = computedAt.epochSecond
            }
        }
    }

    override suspend fun saveHrHistogram(
        id: ActivityId,
        result: HrHistogramResult,
        computedAt: Instant,
    ) {
        database.query {
            RunHrHistogramsTable.upsert {
                it[runId] = id.value
                it[status] = result.status.toDbValue()
                it[histogram] = result.histogram?.encode()
                it[RunHrHistogramsTable.computedAt] = computedAt.epochSecond
            }
        }
    }

    override suspend fun findAll(): List<RunActivity> =
        database.query {
            val laps = RunLapsTable.selectAll().groupBy({ it[RunLapsTable.runId] }, { it.toLap() })
            val paces = RunStreamPacesTable.selectAll().associate { it[RunStreamPacesTable.runId] to it.toStreamPace() }
            val histograms =
                RunHrHistogramsTable.selectAll().associate { it[RunHrHistogramsTable.runId] to it.toHrHistogram() }
            RunsTable.selectAll().map { row ->
                val id = row[RunsTable.id]
                row.toRun(laps[id].orEmpty().sortedBy { it.index }, paces[id], histograms[id])
            }
        }

    override suspend fun newestStartUtc(): Instant? =
        database.query {
            val newest = RunsTable.startDateUtc.max()
            RunsTable
                .select(newest)
                .firstOrNull()
                ?.get(newest)
                ?.let(Instant::ofEpochSecond)
        }

    override suspend fun delete(ids: Collection<ActivityId>) {
        if (ids.isEmpty()) return
        val raw = ids.map { it.value }
        database.query { deleteRunsWhere { RunsTable.id inList raw } }
    }

    override suspend fun pruneBefore(date: LocalDate) {
        val cutoff = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        database.query { deleteRunsWhere { RunsTable.startDateLocal less cutoff } }
    }

    override suspend fun deleteAll() {
        database.query {
            RunLapsTable.deleteAll()
            RunStreamPacesTable.deleteAll()
            RunHrHistogramsTable.deleteAll()
            RunsTable.deleteAll()
        }
    }

    /** Deletes child rows explicitly too, so this doesn't depend on SQLite foreign-key enforcement. */
    private fun Transaction.deleteRunsWhere(predicate: () -> Op<Boolean>) {
        val ids = RunsTable.select(RunsTable.id).where(predicate()).map { it[RunsTable.id] }
        if (ids.isEmpty()) return
        RunLapsTable.deleteWhere { runId inList ids }
        RunStreamPacesTable.deleteWhere { runId inList ids }
        RunHrHistogramsTable.deleteWhere { runId inList ids }
        RunsTable.deleteWhere { RunsTable.id inList ids }
    }
}
