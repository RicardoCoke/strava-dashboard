package app.stravadashboard.api

import app.stravadashboard.domain.SyncSnapshot
import app.stravadashboard.domain.ports.SyncStatus
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

fun SyncSnapshot.toDto(): SyncStatusResponse =
    SyncStatusResponse(
        status =
            when (info.status) {
                SyncStatus.SYNCING -> "syncing"
                SyncStatus.OK -> "ok"
                SyncStatus.PARTIAL -> "partial"
                SyncStatus.RATE_LIMITED -> "rateLimited"
                SyncStatus.STRAVA_UNAVAILABLE -> "stravaUnavailable"
            },
        lastSyncedAt = info.lastSyncedAt?.toIsoSeconds(),
        retryAfter = info.retryAfter?.toIsoSeconds(),
        runsPendingDetail = info.runsPendingDetail,
        nextSyncInSeconds = nextSyncIn?.let(::wholeSecondsUp),
    )

/** Rounded up, so a sync is never reported as due before it is. */
private fun wholeSecondsUp(duration: Duration): Long =
    if (duration.nano == 0) duration.seconds else duration.seconds + 1

private fun Instant.toIsoSeconds(): String = truncatedTo(ChronoUnit.SECONDS).toString()
