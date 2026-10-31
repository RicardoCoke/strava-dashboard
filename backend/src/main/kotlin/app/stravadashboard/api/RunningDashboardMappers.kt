package app.stravadashboard.api

import app.stravadashboard.domain.DashboardResult
import app.stravadashboard.domain.DateWindow
import app.stravadashboard.domain.HrTrendResult
import app.stravadashboard.domain.HrTypeTrend
import app.stravadashboard.domain.PaceComparisonResult
import app.stravadashboard.domain.PaceTypeComparison
import app.stravadashboard.domain.PaceWindowStat
import app.stravadashboard.domain.SyncInfo
import app.stravadashboard.domain.WeekTotal
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.ports.SyncStatus
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun DashboardResult.Ready.toDto(): RunningDashboardResponse =
    RunningDashboardResponse(
        sync = sync.toDto(),
        weeklyDistance = WeeklyDistanceDto(weeks.map { it.toDto() }),
        heartRateTrend = heartRate.toDto(),
        workoutPaceComparison = pace.toDto(),
    )

private fun SyncInfo.toDto(): SyncInfoDto =
    SyncInfoDto(
        status =
            when (status) {
                SyncStatus.OK -> "ok"
                SyncStatus.PARTIAL -> "partial"
                SyncStatus.RATE_LIMITED -> "rateLimited"
                SyncStatus.STRAVA_UNAVAILABLE -> "stravaUnavailable"
            },
        lastSyncedAt = lastSyncedAt?.toIsoSeconds(),
        retryAfter = retryAfter?.toIsoSeconds(),
        runsPendingDetail = runsPendingDetail,
    )

private fun WeekTotal.toDto(): WeekTotalDto =
    WeekTotalDto(
        weekStart = weekStart.toString(),
        weekEnd = weekEnd.toString(),
        label = weekStart.format(WEEK_LABEL),
        distanceKm = distanceKm,
        runCount = runCount,
        isCurrentWeek = isCurrentWeek,
    )

private fun HrTrendResult.toDto(): HeartRateTrendDto =
    HeartRateTrendDto(
        windowStart = windowStart.toString(),
        windowEnd = windowEnd.toString(),
        minRunsForComparison = minRunsForComparison,
        types = types.map { it.toDto() },
    )

private fun HrTypeTrend.toDto(): HrRunTypeDto =
    HrRunTypeDto(
        typeKey = type.key,
        label = type.label,
        kind = if (type is RunType.Easy) "easy" else "workout",
        runCount = runCount,
        runsWithoutHeartRate = runsWithoutHeartRate,
        hasEnoughData = hasEnoughData,
        points =
            points.map {
                HrPointDto(it.date.toString(), it.averageHeartRateBpm, it.distanceKm, it.treadmill, it.rising)
            },
        trend =
            trend?.let {
                HrTrendLineDto(it.startDate.toString(), it.startBpm, it.endDate.toString(), it.endBpm, it.changeBpm)
            },
    )

private fun PaceComparisonResult.toDto(): WorkoutPaceComparisonDto =
    WorkoutPaceComparisonDto(
        currentWindow = currentWindow.toDto(),
        previousWindow = previousWindow.toDto(),
        types = types.map { it.toDto() },
    )

private fun PaceTypeComparison.toDto(): PaceTypeComparisonDto =
    PaceTypeComparisonDto(
        typeKey = type.key,
        label = type.label,
        current = current?.toDto(),
        previous = previous?.toDto(),
        changeSecondsPerKm = changeSecondsPerKm,
        runsWithoutPace = runsWithoutPace,
        hasEnoughData = hasEnoughData,
    )

private fun PaceWindowStat.toDto(): PaceWindowStatDto = PaceWindowStatDto(averageRepPace.value, runCount)

private fun DateWindow.toDto(): DateWindowDto = DateWindowDto(start.toString(), end.toString())

private fun Instant.toIsoSeconds(): String = truncatedTo(ChronoUnit.SECONDS).toString()
