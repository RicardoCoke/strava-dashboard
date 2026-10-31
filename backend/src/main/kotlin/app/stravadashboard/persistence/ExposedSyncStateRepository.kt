package app.stravadashboard.persistence

import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.domain.ports.SyncStateRepository
import app.stravadashboard.domain.ports.SyncStatus
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert
import java.time.Instant

class ExposedSyncStateRepository(
    private val database: Database,
) : SyncStateRepository {
    override suspend fun get(): SyncState =
        database.query {
            SyncStateTable.selectAll().limit(1).firstOrNull()?.let { row ->
                SyncState(
                    listCursor = row[SyncStateTable.listCursor]?.let(Instant::ofEpochSecond),
                    lastSuccessfulSyncAt = row[SyncStateTable.lastSuccessfulSyncAt]?.let(Instant::ofEpochSecond),
                    rateLimitedUntil = row[SyncStateTable.rateLimitedUntil]?.let(Instant::ofEpochSecond),
                    lastStatus = row[SyncStateTable.lastStatus]?.let(::statusOf),
                    ownerAthleteId = row[SyncStateTable.ownerAthleteId],
                )
            } ?: SyncState()
        }

    override suspend fun save(state: SyncState) {
        database.query {
            SyncStateTable.upsert {
                it[id] = SINGLE_ROW_ID
                it[listCursor] = state.listCursor?.epochSecond
                it[lastSuccessfulSyncAt] = state.lastSuccessfulSyncAt?.epochSecond
                it[rateLimitedUntil] = state.rateLimitedUntil?.epochSecond
                it[lastStatus] = state.lastStatus?.name
                it[ownerAthleteId] = state.ownerAthleteId
            }
        }
    }

    private fun statusOf(name: String): SyncStatus? = SyncStatus.entries.find { it.name == name }

    private companion object {
        const val SINGLE_ROW_ID = 1
    }
}
