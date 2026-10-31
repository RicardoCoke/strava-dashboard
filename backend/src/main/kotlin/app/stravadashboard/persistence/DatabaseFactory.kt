package app.stravadashboard.persistence

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.createDirectories

object DatabaseFactory {
    /** Opens (or creates) the SQLite file at [dbPath] and creates the tables if they don't exist. */
    fun connect(dbPath: String): Database {
        Path
            .of(dbPath)
            .toAbsolutePath()
            .parent
            ?.createDirectories()
        val database =
            Database.connect(
                url = "jdbc:sqlite:$dbPath",
                driver = "org.sqlite.JDBC",
                setupConnection = { connection ->
                    connection.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
                },
                databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE },
            )
        transaction(database) {
            SchemaUtils.create(StravaTokensTable, RunsTable, RunLapsTable, RunStreamPacesTable, SyncStateTable)
        }
        return database
    }
}

internal suspend fun <T> Database.query(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, this) { block() }
