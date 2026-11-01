package app.stravadashboard.persistence

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.WellnessRepository
import app.stravadashboard.domain.wellness.HrvMs
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessFetchStatus
import app.stravadashboard.domain.wellness.WellnessState
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.LocalDate

/** Dates are stored as ISO strings, which sort and compare in date order. */
class ExposedWellnessRepository(
    private val database: Database,
) : WellnessRepository {
    override suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<WellnessDay> =
        database.query {
            WellnessDaysTable
                .selectAll()
                .where { WellnessDaysTable.date.between(from.toString(), to.toString()) }
                .orderBy(WellnessDaysTable.date, SortOrder.ASC)
                .map { row ->
                    WellnessDay(
                        date = LocalDate.parse(row[WellnessDaysTable.date]),
                        restingHr = row[WellnessDaysTable.restingHrBpm]?.let(::Bpm),
                        hrv = row[WellnessDaysTable.hrvMs]?.let(::HrvMs),
                    )
                }
        }

    override suspend fun replaceRange(
        from: LocalDate,
        to: LocalDate,
        days: List<WellnessDay>,
        fetchedAt: Instant,
    ) {
        database.query {
            WellnessDaysTable.deleteWhere { date.between(from.toString(), to.toString()) }
            WellnessDaysTable.batchInsert(days) { day ->
                this[WellnessDaysTable.date] = day.date.toString()
                this[WellnessDaysTable.restingHrBpm] = day.restingHr?.value
                this[WellnessDaysTable.hrvMs] = day.hrv?.value
                this[WellnessDaysTable.fetchedAt] = fetchedAt.epochSecond
            }
        }
    }

    override suspend fun pruneBefore(date: LocalDate) {
        database.query { WellnessDaysTable.deleteWhere { WellnessDaysTable.date less date.toString() } }
    }

    override suspend fun getState(): WellnessState =
        database.query {
            WellnessStateTable.selectAll().limit(1).firstOrNull()?.let { row ->
                WellnessState(
                    lastSuccessAt = row[WellnessStateTable.lastSuccessAt]?.let(Instant::ofEpochSecond),
                    lastAttemptAt = row[WellnessStateTable.lastAttemptAt]?.let(Instant::ofEpochSecond),
                    lastStatus = row[WellnessStateTable.lastStatus]?.let(::statusOf),
                    retryAfter = row[WellnessStateTable.retryAfter]?.let(Instant::ofEpochSecond),
                )
            } ?: WellnessState()
        }

    override suspend fun saveState(state: WellnessState) {
        database.query {
            WellnessStateTable.upsert {
                it[id] = SINGLE_ROW_ID
                it[lastSuccessAt] = state.lastSuccessAt?.epochSecond
                it[lastAttemptAt] = state.lastAttemptAt?.epochSecond
                it[lastStatus] = state.lastStatus?.name
                it[retryAfter] = state.retryAfter?.epochSecond
            }
        }
    }

    private fun statusOf(name: String): WellnessFetchStatus? = WellnessFetchStatus.entries.find { it.name == name }

    private companion object {
        const val SINGLE_ROW_ID = 1
    }
}
