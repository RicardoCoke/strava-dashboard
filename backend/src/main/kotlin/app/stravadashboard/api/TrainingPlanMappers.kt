package app.stravadashboard.api

import app.stravadashboard.domain.trainingplan.BuildCalendar
import app.stravadashboard.domain.trainingplan.BuildPhase
import app.stravadashboard.domain.trainingplan.BuildProgress
import app.stravadashboard.domain.trainingplan.DatedDay
import app.stravadashboard.domain.trainingplan.DatedWeek
import app.stravadashboard.domain.trainingplan.TargetPace

fun BuildCalendar.toDto(): TrainingPlanResponse =
    TrainingPlanResponse(
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        progress = progress.toDto(),
        targetPaces = paces.map { it.toDto() },
        weeks = weeks.map { it.toDto() },
    )

private fun BuildProgress.toDto(): BuildProgressDto =
    BuildProgressDto(
        phase =
            when (phase) {
                BuildPhase.NOT_STARTED -> "notStarted"
                BuildPhase.IN_PROGRESS -> "inProgress"
                BuildPhase.FINISHED -> "finished"
            },
        percentComplete = percentComplete,
        daysPassed = daysPassed,
        totalDays = totalDays,
        currentWeekIndex = currentWeekIndex,
        weekCount = weekCount,
        daysToGo = daysToGo,
        daysUntilStart = daysUntilStart,
        label = label,
    )

private fun TargetPace.toDto(): TargetPaceDto = TargetPaceDto(key, label, target?.value, min?.value, max?.value)

private fun DatedWeek.toDto(): PlanWeekDto =
    PlanWeekDto(
        index = index,
        label = week.label,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        distanceKm = week.distance.value,
        easyPercent = week.easyPercent,
        subtPercent = week.subtPercent,
        isCurrentWeek = isCurrentWeek,
        days = days.map { it.toDto() },
    )

private fun DatedDay.toDto(): PlanDayDto =
    PlanDayDto(
        date = date.toString(),
        dayOfWeek = day.dayOfWeek.name,
        session = day.session,
        note = day.note,
        totalMinutes = day.totalMinutes,
        easyMinutes = day.easyMinutes,
        subtMinutes = day.subtMinutes,
        distanceKm = day.distance.value,
        isRest = day.isRest,
        race = day.race?.let { PlanRaceDto(it.label, it.distanceKm, it.isMarathon) },
        isToday = isToday,
        isPast = isPast,
    )
