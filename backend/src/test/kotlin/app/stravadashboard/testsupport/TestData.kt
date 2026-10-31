package app.stravadashboard.testsupport

import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RUN_SPORT_TYPE
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.ports.ActivitySummary
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// All data here is invented.

val TEST_ZONE: ZoneId = ZoneOffset.UTC

/** Thursday 2026-10-29, 10:00 UTC: the fixed "today" used by the spec examples. */
val NOW: Instant = Instant.parse("2026-10-29T10:00:00Z")
val TODAY: LocalDate = LocalDate.of(2026, 10, 29)

fun fixedClock(instant: Instant = NOW): Clock = Clock.fixed(instant, TEST_ZONE)

/** Test builder: every field has a default so each test names only what it cares about. */
@Suppress("LongParameterList")
fun run(
    id: Long = 1,
    date: LocalDate = TODAY,
    name: String = "Morning Run",
    distanceMeters: Double = 10_000.0,
    heartRate: Double? = 145.0,
    trainer: Boolean = false,
    description: String? = null,
    laps: List<Lap>? = emptyList(),
    sportType: String = RUN_SPORT_TYPE,
    streamPace: StreamPaceResult? = null,
    startLocal: LocalDateTime = date.atTime(7, 0),
): RunActivity =
    RunActivity(
        id = ActivityId(id),
        name = name,
        sportType = sportType,
        startUtc = startLocal.toInstant(ZoneOffset.UTC),
        startLocal = startLocal,
        distance = Meters(distanceMeters),
        averageHeartRateBpm = heartRate,
        trainer = trainer,
        description = description,
        laps = laps,
        detailFetchedAt = if (laps == null) null else startLocal.toInstant(ZoneOffset.UTC),
        streamPace = streamPace,
    )

fun summary(
    id: Long,
    start: Instant,
    name: String = "Morning Run",
    sportType: String = RUN_SPORT_TYPE,
    trainer: Boolean = false,
): ActivitySummary =
    ActivitySummary(
        id = ActivityId(id),
        name = name,
        sportType = sportType,
        startUtc = start,
        startLocal = LocalDateTime.ofInstant(start, ZoneOffset.UTC),
        distance = Meters(10_000.0),
        averageHeartRateBpm = 150.0,
        trainer = trainer,
    )

fun kmLap(
    index: Int,
    seconds: Int,
    meters: Double = 1000.0,
): Lap = Lap(index, Seconds(seconds), Meters(meters))

fun tempDbPath(): String = Files.createTempDirectory("strava-dashboard-test").resolve("test.db").toString()

fun testConfig(dbPath: String = tempDbPath()): AppConfig =
    AppConfig(
        host = "127.0.0.1",
        port = 8080,
        stravaClientId = "12345",
        stravaClientSecret = "test-client-secret",
        stravaRedirectUri = "http://localhost:4200/auth/strava/callback",
        sessionSecretHex = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
        dbPath = dbPath,
    )
