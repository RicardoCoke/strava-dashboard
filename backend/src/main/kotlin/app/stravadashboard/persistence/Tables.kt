package app.stravadashboard.persistence

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

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

    /** Written only by the detail call (feature 008); null for details stored before it. */
    val movingTimeS = integer("moving_time_s").nullable()

    /** Set to true only by the detail call since feature 016; null for details stored before it. */
    val splitsSaved = bool("splits_saved").nullable()
    override val primaryKey = PrimaryKey(id)
}

object RunLapsTable : Table("run_laps") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val lapIndex = integer("lap_index")
    val elapsedTimeS = integer("elapsed_time_s")
    val distanceM = double("distance_m")
    val averageHeartrate = double("average_heartrate").nullable()
    override val primaryKey = PrimaryKey(runId, lapIndex)
}

/** Per-km splits from the detail call (feature 016). */
object RunSplitsTable : Table("run_splits") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val splitIndex = integer("split_index")
    val distanceM = double("distance_m")
    val movingTimeS = integer("moving_time_s")
    val elevationDifferenceM = double("elevation_difference_m").nullable()
    val averageHeartrate = double("average_heartrate").nullable()
    val gradeAdjustedSpeedMps = double("grade_adjusted_speed_mps").nullable()
    override val primaryKey = PrimaryKey(runId, splitIndex)
}

object RunStreamPacesTable : Table("run_stream_paces") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val typeKey = text("type_key")
    val status = text("status")
    val repPaceSPerKm = integer("rep_pace_s_per_km").nullable()
    val computedAt = long("computed_at")
    override val primaryKey = PrimaryKey(runId)
}

/** One row per run once its heart-rate stream was fetched. [histogram] is `bpm:seconds,…`; null when unavailable. */
object RunHrHistogramsTable : Table("run_hr_histograms") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val status = text("status")
    val histogram = text("histogram").nullable()
    val computedAt = long("computed_at")
    override val primaryKey = PrimaryKey(runId)
}

/** One row per long run once its drift streams were fetched (feature 013). Only the derived drift % is kept. */
object RunHrDriftsTable : Table("run_hr_drifts") {
    val runId = long("run_id").references(RunsTable.id, onDelete = ReferenceOption.CASCADE)
    val status = text("status")
    val driftPct = double("drift_pct").nullable()
    val computedAt = long("computed_at")
    override val primaryKey = PrimaryKey(runId)
}

object SyncStateTable : Table("sync_state") {
    val id = integer("id")
    val listCursor = long("list_cursor").nullable()
    val lastSuccessfulSyncAt = long("last_successful_sync_at").nullable()
    val rateLimitedUntil = long("rate_limited_until").nullable()
    val lastStatus = text("last_status").nullable()
    val historyStart = long("history_start").nullable()
    val lastAttemptAt = long("last_attempt_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** One row per local date from Garmin Connect (feature 015). Both values may be null, never both. */
object WellnessDaysTable : Table("wellness_days") {
    val date = text("date")
    val restingHrBpm = integer("resting_hr_bpm").nullable()
    val hrvMs = double("hrv_ms").nullable()
    val fetchedAt = long("fetched_at")
    override val primaryKey = PrimaryKey(date)
}

/** One row per fetched local date for the rings (feature 020); null values mean Garmin had no data that day. */
object RingDaysTable : Table("ring_days") {
    val date = text("date")
    val sleepS = integer("sleep_s").nullable()
    val strain = double("strain").nullable()
    val fetchedAt = long("fetched_at")
    override val primaryKey = PrimaryKey(date)
}

object WellnessStateTable : Table("wellness_state") {
    val id = integer("id")
    val lastSuccessAt = long("last_success_at").nullable()
    val lastAttemptAt = long("last_attempt_at").nullable()
    val lastStatus = text("last_status").nullable()
    val retryAfter = long("retry_after").nullable()
    override val primaryKey = PrimaryKey(id)
}
