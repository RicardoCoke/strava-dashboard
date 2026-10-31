package app.stravadashboard.strava

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HeartRateStream
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import java.time.Instant
import java.time.LocalDateTime

private const val MIN_STREAM_SAMPLES = 2

internal fun SummaryActivityDto.toSummary(): ActivitySummary =
    ActivitySummary(
        id = ActivityId(id),
        name = name,
        sportType = sportType,
        startUtc = Instant.parse(startDate),
        // Strava sends local wall-clock time with a misleading trailing "Z"; it must not be zone-converted.
        startLocal = LocalDateTime.parse(startDateLocal.removeSuffix("Z")),
        distance = Meters(distance),
        averageHeartRateBpm = averageHeartrate,
        trainer = trainer,
    )

internal fun DetailedActivityDto.toDetail(): ActivityDetail =
    ActivityDetail(
        description = description,
        laps = laps.map { Lap(index = it.lapIndex, elapsed = Seconds(it.elapsedTime), distance = Meters(it.distance)) },
    )

/** Null unless both `time` and `velocity_smooth` are present, of equal length and at least two samples. */
internal fun StreamSetDto.toSpeedStream(): SpeedStream? {
    val times = time?.data.orEmpty()
    val speeds = velocitySmooth?.data.orEmpty()
    return if (times.size >= MIN_STREAM_SAMPLES && times.size == speeds.size) {
        SpeedStream(times.map(::Seconds), speeds.map(::MetersPerSecond))
    } else {
        null
    }
}

/** Null unless both `time` and `heartrate` are present, of equal length and at least two samples. */
internal fun StreamSetDto.toHeartRateStream(): HeartRateStream? {
    val times = time?.data.orEmpty()
    val bpm = heartrate?.data.orEmpty()
    return if (times.size >= MIN_STREAM_SAMPLES && times.size == bpm.size) {
        HeartRateStream(times.map(::Seconds), bpm.map(::Bpm))
    } else {
        null
    }
}
