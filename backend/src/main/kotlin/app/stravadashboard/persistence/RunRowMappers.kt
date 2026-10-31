package app.stravadashboard.persistence

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RUN_SPORT_TYPE
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import org.jetbrains.exposed.sql.ResultRow
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Fixed-width so lexical comparison in SQL matches chronological order. */
internal val RUN_LOCAL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

internal fun ResultRow.toLap(): Lap =
    Lap(
        index = this[RunLapsTable.lapIndex],
        elapsed = Seconds(this[RunLapsTable.elapsedTimeS]),
        distance = Meters(this[RunLapsTable.distanceM]),
    )

/** Stored as `computed` / `unavailable`, as in the spec's schema. */
internal fun StreamPaceStatus.toDbValue(): String = name.lowercase()

internal fun ResultRow.toStreamPace(): StreamPaceResult =
    StreamPaceResult(
        typeKey = this[RunStreamPacesTable.typeKey],
        status = StreamPaceStatus.valueOf(this[RunStreamPacesTable.status].uppercase()),
        pace = this[RunStreamPacesTable.repPaceSPerKm]?.let(::SecondsPerKm),
    )

/** Only Runs are stored, so the sport type is always [RUN_SPORT_TYPE]. Laps are null until the detail is fetched. */
internal fun ResultRow.toRun(
    laps: List<Lap>,
    streamPace: StreamPaceResult?,
): RunActivity {
    val detailFetchedAt = this[RunsTable.detailFetchedAt]?.let(Instant::ofEpochSecond)
    return RunActivity(
        id = ActivityId(this[RunsTable.id]),
        name = this[RunsTable.name],
        sportType = RUN_SPORT_TYPE,
        startUtc = Instant.ofEpochSecond(this[RunsTable.startDateUtc]),
        startLocal = LocalDateTime.parse(this[RunsTable.startDateLocal], RUN_LOCAL_FORMAT),
        distance = Meters(this[RunsTable.distanceM]),
        averageHeartRateBpm = this[RunsTable.averageHeartrate],
        trainer = this[RunsTable.trainer],
        description = this[RunsTable.description],
        laps = if (detailFetchedAt == null) null else laps,
        detailFetchedAt = detailFetchedAt,
        streamPace = streamPace,
    )
}
