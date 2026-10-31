package app.stravadashboard.domain.trainingplan

import app.stravadashboard.domain.model.SecondsPerKm
import java.time.DayOfWeek
import java.time.LocalDate

// The training plan read from the user's CSV (see docs/features/004-calendar-like-build/specs.md).

/** Kilometres as written in the plan, rounded half-up to two decimals. */
@JvmInline
value class Kilometres(
    val value: Double,
)

data class TargetPace(
    val key: String,
    val label: String,
    val target: SecondsPerKm?,
    val min: SecondsPerKm?,
    val max: SecondsPerKm?,
)

data class PlanRace(
    val distanceKm: Double,
    val isMarathon: Boolean,
    val label: String,
)

data class PlanDay(
    val dayOfWeek: DayOfWeek,
    val session: String,
    val note: String?,
    val easyMinutes: Int,
    val subtMinutes: Int,
    val distance: Kilometres,
    val isRest: Boolean,
    val race: PlanRace?,
) {
    val totalMinutes: Int get() = easyMinutes + subtMinutes
}

data class PlanWeek(
    val label: String,
    val distance: Kilometres,
    val easyPercent: Double,
    val subtPercent: Double,
    /** Monday to Sunday, always 7 days. */
    val days: List<PlanDay>,
)

data class TrainingPlan(
    val paces: List<TargetPace>,
    val weeks: List<PlanWeek>,
)

sealed interface PlanParseResult {
    data class Valid(
        val plan: TrainingPlan,
    ) : PlanParseResult

    /** [reason] is safe to log and to show: it names weeks, days and rows, never cell values. */
    data class Invalid(
        val reason: String,
    ) : PlanParseResult
}

enum class BuildPhase { NOT_STARTED, IN_PROGRESS, FINISHED }

data class BuildProgress(
    val phase: BuildPhase,
    val percentComplete: Int,
    val daysPassed: Int,
    val totalDays: Int,
    val currentWeekIndex: Int?,
    val weekCount: Int,
    val daysToGo: Int?,
    val daysUntilStart: Int?,
    val label: String,
)

data class DatedDay(
    val date: LocalDate,
    val day: PlanDay,
    val isToday: Boolean,
    val isPast: Boolean,
)

data class DatedWeek(
    val index: Int,
    val week: PlanWeek,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val isCurrentWeek: Boolean,
    val days: List<DatedDay>,
)

data class BuildCalendar(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val progress: BuildProgress,
    val paces: List<TargetPace>,
    val weeks: List<DatedWeek>,
)
