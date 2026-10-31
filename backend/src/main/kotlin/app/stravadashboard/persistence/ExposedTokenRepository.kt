package app.stravadashboard.persistence

import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.TokenRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.Instant

class ExposedTokenRepository(
    private val database: Database,
) : TokenRepository {
    override suspend fun find(): StoredTokens? =
        database.query {
            StravaTokensTable.selectAll().limit(1).firstOrNull()?.let { row ->
                StoredTokens(
                    athleteId = row[StravaTokensTable.athleteId],
                    accessToken = row[StravaTokensTable.accessToken],
                    refreshToken = row[StravaTokensTable.refreshToken],
                    expiresAt = Instant.ofEpochSecond(row[StravaTokensTable.expiresAt]),
                    scope = row[StravaTokensTable.scope],
                )
            }
        }

    override suspend fun save(tokens: StoredTokens) {
        database.query {
            StravaTokensTable.deleteAll()
            StravaTokensTable.insert {
                it[athleteId] = tokens.athleteId
                it[accessToken] = tokens.accessToken
                it[refreshToken] = tokens.refreshToken
                it[expiresAt] = tokens.expiresAt.epochSecond
                it[scope] = tokens.scope
            }
        }
    }

    override suspend fun delete() {
        database.query { StravaTokensTable.deleteAll() }
    }
}
