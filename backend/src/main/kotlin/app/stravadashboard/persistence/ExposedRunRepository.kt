package app.stravadashboard.persistence

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrDriftResult
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
                it[splitsSaved] = true
            }
            RunLapsTable.deleteWhere { runId eq id.value }
            RunLapsTable.batchInsert(detail.laps) { lap ->
                this[RunLapsTable.runId] = id.value
                this[RunLapsTable.lapIndex] = lap.index
                this[RunLapsTable.elapsedTimeS] = lap.elapsed.value
                this[RunLapsTable.distanceM] = lap.distance.value
                this[RunLapsTable.averageHeartrate] = lap.averageHeartRateBpm
            }
            RunSplitsTable.deleteWhere { runId eq id.value }
            RunSplitsTable.batchInsert(detail.splits) { split ->
                this[RunSplitsTable.runId] = id.value
                this[RunSplitsTable.splitIndex] = split.index
                this[RunSplitsTable.distanceM] = split.distance.value
                this[RunSplitsTable.movingTimeS] = split.movingTime.value
                this[RunSplitsTable.elevationDifferenceM] = split.elevationDifference?.value
                this[RunSplitsTable.averageHeartrate] = split.averageHeartRateBpm
                this[RunSplitsTable.gradeAdjustedSpeedMps] = split.gradeAdjustedSpeed?.value
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

    override suspend fun saveHrDrift(
        id: ActivityId,
        result: HrDriftResult,
        computedAt: Instant,
    ) {
        database.query {
            RunHrDriftsTable.upsert {
                it[runId] = id.value
                it[status] = result.status.toDbValue()
                it[driftPct] = result.driftPct
                it[RunHrDriftsTable.computedAt] = computedAt.epochSecond
            }
        }
    }

    override suspend fun findAll(): List<RunActivity> =
        database.query {
            val laps = RunLapsTable.selectAll().groupBy({ it[RunLapsTable.runId] }, { it.toLap() })
            val paces = RunStreamPacesTable.selectAll().associate { it[RunStreamPacesTable.runId] to it.toStreamPace() }
            val histograms =
                RunHrHistogramsTable.selectAll().associate { it[RunHrHistogramsTable.runId] to it.toHrHistogram() }
            val drifts = RunHrDriftsTable.selectAll().associate { it[RunHrDriftsTable.runId] to it.toHrDrift() }
            val splits = RunSplitsTable.selectAll().groupBy({ it[RunSplitsTable.runId] }, { it.toSplit() })
            RunsTable.selectAll().map { row ->
                val id = row[RunsTable.id]
                val splitsSaved = row[RunsTable.splitsSaved] == true
                val runSplits = splits[id].orEmpty().sortedBy { it.index }.takeIf { splitsSaved }
                row.toRun(laps[id].orEmpty().sortedBy { it.index }, paces[id], histograms[id], drifts[id], runSplits)
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
            RunSplitsTable.deleteAll()
            RunStreamPacesTable.deleteAll()
            RunHrHistogramsTable.deleteAll()
            RunHrDriftsTable.deleteAll()
            RunsTable.deleteAll()
        }
    }
}

/** Deletes child rows explicitly too, so this doesn't depend on SQLite foreign-key enforcement. */
private fun Transaction.deleteRunsWhere(predicate: () -> Op<Boolean>) {
    val ids = RunsTable.select(RunsTable.id).where(predicate()).map { it[RunsTable.id] }
    if (ids.isEmpty()) return
    RunLapsTable.deleteWhere { runId inList ids }
    RunSplitsTable.deleteWhere { runId inList ids }
    RunStreamPacesTable.deleteWhere { runId inList ids }
    RunHrHistogramsTable.deleteWhere { runId inList ids }
    RunHrDriftsTable.deleteWhere { runId inList ids }
    RunsTable.deleteWhere { RunsTable.id inList ids }
}
