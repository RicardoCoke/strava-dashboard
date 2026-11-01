package app.stravadashboard.api

import app.stravadashboard.domain.marathonpace.MarathonPaceVolume
import app.stravadashboard.domain.marathonpace.MpGoal
import app.stravadashboard.domain.marathonpace.MpReport
import app.stravadashboard.domain.marathonpace.MpSummary
import app.stravadashboard.domain.marathonpace.MpWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WEEK_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

fun MpReport.toDto(): MarathonPaceResponse =
    MarathonPaceResponse(
        goal = goal.toDto(),
        buildStart = buildStart.toString(),
        buildEnd = buildEnd.toString(),
        weeks = weeks.map { it.toDto() },
        summary = summary.toDto(),
        runsPendingLaps = runsPendingLaps,
    )

private fun MpGoal.toDto(): MpGoalDto =
    MpGoalDto(
        paceSecondsPerKm = pace.value,
        toleranceSecondsPerKm = MarathonPaceVolume.TOLERANCE_S_PER_KM,
        fastestSecondsPerKm = fastest.value,
        slowestSecondsPerKm = slowest.value,
        minLapMeters = MarathonPaceVolume.MIN_LAP_METERS.toInt(),
    )

private fun MpWeek.toDto(): MpWeekDto =
    MpWeekDto(
        weekIndex = index,
        weekStart = weekStart.toString(),
        weekEnd = weekEnd.toString(),
        label = weekStart.format(WEEK_LABEL),
        mpKm = mpKm,
        isCurrentWeek = isCurrentWeek,
        isFuture = isFuture,
    )

private fun MpSummary.toDto(): MpSummaryDto =
    MpSummaryDto(
        totalMpKm = totalMpKm,
        thisWeekStart = thisWeek.start.toString(),
        thisWeekEnd = thisWeek.end.toString(),
        thisWeekMpKm = thisWeekMpKm,
        thisWeekTotalKm = thisWeekTotalKm,
        thisWeekSharePercent = thisWeekSharePercent,
    )
