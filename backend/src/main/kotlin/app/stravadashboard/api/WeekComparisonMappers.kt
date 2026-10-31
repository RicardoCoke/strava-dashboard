package app.stravadashboard.api

import app.stravadashboard.domain.MissingIn
import app.stravadashboard.domain.WeekComparisonResult
import app.stravadashboard.domain.WeekHrStat
import app.stravadashboard.domain.WeekPaceStat

/** Feature 007: the `weekComparison` block of GET /api/dashboard/running. */
internal fun WeekComparisonResult.toDto(): WeekComparisonDto =
    WeekComparisonDto(
        thisWeek = thisWeek.toDto(),
        lastWeek = lastWeek.toDto(),
        distance = DistanceDifferenceDto(distance.thisWeekKm, distance.lastWeekKm, distance.differenceKm),
        easyHeartRate =
            EasyHrWeekComparisonDto(
                thisWeek = easyHeartRate.thisWeek?.toDto(),
                lastWeek = easyHeartRate.lastWeek?.toDto(),
                changeBpm = easyHeartRate.changeBpm,
                missingIn = easyHeartRate.missingIn?.toDto(),
            ),
        workoutPace =
            WorkoutPaceWeekComparisonDto(
                thisWeek = workoutPace.thisWeek?.toDto(),
                lastWeek = workoutPace.lastWeek?.toDto(),
                changeSecondsPerKm = workoutPace.changeSecondsPerKm,
                missingIn = workoutPace.missingIn?.toDto(),
            ),
    )

private fun WeekHrStat.toDto(): WeekHrStatDto = WeekHrStatDto(averageHeartRateBpm, runCount)

/** Labels are the generated `NxMmin` keys, never activity names. */
private fun WeekPaceStat.toDto(): WeekPaceStatDto =
    WeekPaceStatDto(averageRepPace.value, runCount, types.map { it.label })

private fun MissingIn.toDto(): String =
    when (this) {
        MissingIn.THIS_WEEK -> "thisWeek"
        MissingIn.LAST_WEEK -> "lastWeek"
        MissingIn.BOTH_WEEKS -> "bothWeeks"
    }
