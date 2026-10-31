package app.stravadashboard.persistence

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object StravaTokensTable : Table("strava_tokens") {
    val athleteId = long("athlete_id")
    val accessToken = text("access_token")
    val refreshToken = text("refresh_token")
    val expiresAt = long("expires_at")
    val scope = text("scope")
    override val primaryKey = PrimaryKey(athleteId)
}

object RunsTable : Table("runs") {
    val id = long("id")
    val name = text("name")
    val startDateUtc = long("start_date_utc").index()
    val startDateLocal = text("start_date_local")
    val distanceM = double("distance_m")
    val averageHeartrate = double("average_heartrate").nullable()
    val trainer = bool("trainer")
    val description = text("description").nullable()
    val detailFetchedAt = long("detail_fetched_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object RunLapsTable : Table("run_laps") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val lapIndex = integer("lap_index")
    val elapsedTimeS = integer("elapsed_time_s")
    val distanceM = double("distance_m")
    override val primaryKey = PrimaryKey(runId, lapIndex)
}

object RunStreamPacesTable : Table("run_stream_paces") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val typeKey = text("type_key")
    val status = text("status")
    val repPaceSPerKm = integer("rep_pace_s_per_km").nullable()
    val computedAt = long("computed_at")
    override val primaryKey = PrimaryKey(runId)
}

object SyncStateTable : Table("sync_state") {
    val id = integer("id")
    val listCursor = long("list_cursor").nullable()
    val lastSuccessfulSyncAt = long("last_successful_sync_at").nullable()
    val rateLimitedUntil = long("rate_limited_until").nullable()
    val lastStatus = text("last_status").nullable()
    val ownerAthleteId = long("owner_athlete_id").nullable()
    override val primaryKey = PrimaryKey(id)
}
