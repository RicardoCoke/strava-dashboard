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
    /** Opens (or creates) the SQLite file at [dbPath], creates missing tables and adds columns added since. */
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
            SchemaUtils.create(
                StravaTokensTable,
                RunsTable,
                RunLapsTable,
                RunStreamPacesTable,
                RunHrHistogramsTable,
                SyncStateTable,
            )
            addColumnIfMissing(SyncStateTable.tableName, SyncStateTable.historyStart.name, "INTEGER")
        }
        return database
    }

    /** `CREATE TABLE IF NOT EXISTS` doesn't add columns to an existing table, so files from before need this. */
    private fun Transaction.addColumnIfMissing(
        table: String,
        column: String,
        sqlType: String,
    ) {
        val existing = mutableListOf<String>()
        exec("PRAGMA table_info($table)") { rows -> while (rows.next()) existing += rows.getString("name") }
        if (column !in existing) exec("ALTER TABLE $table ADD COLUMN $column $sqlType")
    }
}

internal suspend fun <T> Database.query(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO, this) { block() }
