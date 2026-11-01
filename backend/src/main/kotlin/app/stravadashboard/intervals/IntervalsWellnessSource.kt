package app.stravadashboard.intervals

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.ports.WellnessSource
import app.stravadashboard.domain.wellness.HrvMs
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessFetchResult
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Maps Intervals.icu wellness DTOs to domain days; an element whose `id` isn't an ISO date is skipped. */
class IntervalsWellnessSource(
    private val client: IntervalsClient,
) : WellnessSource {
    override suspend fun fetch(
        oldest: LocalDate,
        newest: LocalDate,
    ): WellnessFetchResult =
        when (val result = client.wellness(oldest, newest)) {
            is IntervalsResult.Success -> WellnessFetchResult.Success(result.days.mapNotNull(::toDomain))
            IntervalsResult.KeyRejected -> WellnessFetchResult.KeyRejected
            is IntervalsResult.RateLimited -> WellnessFetchResult.RateLimited(result.until)
            IntervalsResult.Unavailable -> WellnessFetchResult.Unavailable
        }

    @Suppress("SwallowedException")
    private fun toDomain(dto: IntervalsWellnessDto): WellnessDay? {
        val date =
            try {
                LocalDate.parse(dto.id)
            } catch (e: DateTimeParseException) {
                return null
            }
        return WellnessDay(date, dto.restingHR?.let { Bpm(roundHalfUpToInt(it)) }, dto.hrv?.let(::HrvMs))
    }
}
