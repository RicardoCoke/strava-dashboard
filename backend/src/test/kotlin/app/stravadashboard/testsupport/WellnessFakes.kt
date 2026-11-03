package app.stravadashboard.testsupport

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.WellnessSource
import app.stravadashboard.domain.wellness.HrvMs
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessFetchResult
import java.time.LocalDate

// All data here is invented.

/** Saturday 2026-10-31: "today" in the 015 spec examples. */
val WELLNESS_TODAY: LocalDate = LocalDate.of(2026, 10, 31)

fun wellnessDay(
    date: LocalDate,
    restingHr: Int? = null,
    hrv: Double? = null,
): WellnessDay = WellnessDay(date, restingHr?.let(::Bpm), hrv?.let(::HrvMs))

/** An in-memory Garmin Connect: returns [result] and records each requested range. */
class FakeWellnessSource(
    var result: WellnessFetchResult = WellnessFetchResult.Success(emptyList()),
) : WellnessSource {
    val ranges = mutableListOf<Pair<LocalDate, LocalDate>>()

    override suspend fun fetch(
        oldest: LocalDate,
        newest: LocalDate,
    ): WellnessFetchResult {
        ranges += oldest to newest
        return result
    }
}
