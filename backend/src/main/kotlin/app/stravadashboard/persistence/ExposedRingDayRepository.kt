package app.stravadashboard.persistence

import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.rings.RingDay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.between
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import java.time.LocalDate

/** Dates are stored as ISO strings, which sort and compare in date order. */
class ExposedRingDayRepository(
    private val database: Database,
) : RingDayRepository {
    override suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, RingDay> =
        database.query {
            RingDaysTable
                .selectAll()
                .where { RingDaysTable.date.between(from.toString(), to.toString()) }
                .associate { row ->
                    LocalDate.parse(row[RingDaysTable.date]) to
                        RingDay(row[RingDaysTable.sleepS], row[RingDaysTable.strain])
                }
        }

    override suspend fun fetchedAt(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Instant> =
        database.query {
            RingDaysTable
                .selectAll()
                .where { RingDaysTable.date.between(from.toString(), to.toString()) }
                .associate {
                    LocalDate.parse(it[RingDaysTable.date]) to
                        Instant.ofEpochSecond(it[RingDaysTable.fetchedAt])
                }
        }

    override suspend fun upsert(
        date: LocalDate,
        day: RingDay,
        fetchedAt: Instant,
    ) {
        database.query {
            RingDaysTable.upsert {
                it[RingDaysTable.date] = date.toString()
                it[sleepS] = day.sleepSeconds
                it[strain] = day.strain
                it[RingDaysTable.fetchedAt] = fetchedAt.epochSecond
            }
        }
    }

    override suspend fun pruneBefore(date: LocalDate) {
        database.query { RingDaysTable.deleteWhere { RingDaysTable.date less date.toString() } }
    }
}
