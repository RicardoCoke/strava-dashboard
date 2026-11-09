package app.stravadashboard.garmin

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.ports.RingDaySource
import app.stravadashboard.domain.ports.WellnessSource
import app.stravadashboard.domain.rings.DayFetchResult
import app.stravadashboard.domain.rings.DayReading
import app.stravadashboard.domain.rings.HrSample
import app.stravadashboard.domain.wellness.HrvMs
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessFetchResult
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Daily resting HR and HRV (feature 015): resting HR from the daily summaries and HRV from the overnight HRV
 * summaries, merged by local date; and one day's sleep and heart rate for the rings (feature 020). The token and
 * error handling live in [GarminConnect].
 */
class GarminWellnessSource(
    private val garmin: GarminConnect,
) : WellnessSource,
    RingDaySource {
    override suspend fun fetch(
        oldest: LocalDate,
        newest: LocalDate,
    ): WellnessFetchResult {
        val days =
            garmin
                .getJson("summaries", { it.body<GarminDailySummariesDto>() }) {
                    url(GarminConnect.BASE_URL + SUMMARIES_PATH)
                    parameter("startDate", oldest.toString())
                    parameter("endDate", newest.toString())
                }.then { summaries ->
                    garmin
                        .getJson("hrv", { it.body<GarminHrvDailyDto>() }, empty = GarminHrvDailyDto(emptyList())) {
                            url("${GarminConnect.BASE_URL}$HRV_PATH/$oldest/$newest")
                        }.then { hrv -> GarminCall.Ok(summaries to hrv) }
                }
        return when (days) {
            is GarminCall.Ok -> {
                val (summaries, hrv) = days.value
                // Counts only: the values are health data.
                log.info(
                    "Garmin Connect returned {} summaries ({} with resting HR), {} HRV summaries ({} with HRV)",
                    summaries.data.size,
                    summaries.data.count { it.heartRate?.restingValue != null },
                    hrv.hrvSummaries.size,
                    hrv.hrvSummaries.count { it.lastNightAvg != null },
                )
                WellnessFetchResult.Success(merge(summaries, hrv))
            }
            else -> days.toWellness()
        }
    }

    override suspend fun fetchDay(
        date: LocalDate,
        withHeartRate: Boolean,
    ): DayFetchResult {
        val day =
            garmin
                .getJson("sleep", { it.body<GarminDailySleepDto>() }, empty = GarminDailySleepDto()) {
                    url(GarminConnect.BASE_URL + SLEEP_PATH)
                    parameter("date", date.toString())
                }.then { sleep ->
                    if (!withHeartRate) {
                        GarminCall.Ok(DayReading(sleep.sleepTimeSeconds, null))
                    } else {
                        garmin
                            .getJson(
                                "heart rate",
                                { it.body<GarminDailyHeartRateDto>() },
                                empty = GarminDailyHeartRateDto(),
                            ) {
                                url(GarminConnect.BASE_URL + HEART_RATE_PATH)
                                parameter("date", date.toString())
                            }.then { hr -> GarminCall.Ok(DayReading(sleep.sleepTimeSeconds, samples(hr))) }
                    }
                }
        return when (day) {
            is GarminCall.Ok -> DayFetchResult.Ok(day.value)
            else -> DayFetchResult.Failed(day.toWellness())
        }
    }

    private companion object {
        const val SUMMARIES_PATH = "/usersummary-service/v2/dailysummaries"
        const val HRV_PATH = "/hrv-service/hrv/daily"
        const val SLEEP_PATH = "/wellness-service/wellness/dailySleep"
        const val HEART_RATE_PATH = "/wellness-service/wellness/dailyHeartRate"
        val log = LoggerFactory.getLogger(GarminWellnessSource::class.java)

        /** One day per date seen in either response; an element whose date isn't ISO is skipped. */
        fun merge(
            summaries: GarminDailySummariesDto,
            hrv: GarminHrvDailyDto,
        ): List<WellnessDay> {
            val resting =
                summaries.data
                    .mapNotNull { dto ->
                        date(dto.calendarDate)?.let { it to dto.heartRate?.restingValue }
                    }.toMap()
            val overnight =
                hrv.hrvSummaries
                    .mapNotNull { dto ->
                        date(dto.calendarDate)?.let { it to dto.lastNightAvg }
                    }.toMap()
            return (resting.keys + overnight.keys).sorted().map { date ->
                WellnessDay(
                    date,
                    resting[date]?.let { Bpm(roundHalfUpToInt(it)) },
                    overnight[date]?.let(::HrvMs),
                )
            }
        }

        /** Malformed pairs are skipped; a null or fractional bpm is kept as no reading or rounded. */
        fun samples(dto: GarminDailyHeartRateDto): List<HrSample> =
            dto.heartRateValues.orEmpty().mapNotNull { pair ->
                val at = pair.getOrNull(0)?.toLong() ?: return@mapNotNull null
                HrSample(Instant.ofEpochMilli(at), pair.getOrNull(1)?.let(::roundHalfUpToInt))
            }

        @Suppress("SwallowedException")
        fun date(text: String): LocalDate? =
            try {
                LocalDate.parse(text)
            } catch (e: DateTimeParseException) {
                null
            }
    }
}

/** A failed call as the wellness result; 404 counts as unavailable, as it did before feature 021. */
private fun GarminCall<*>.toWellness(): WellnessFetchResult =
    when (this) {
        GarminCall.KeyRejected -> WellnessFetchResult.KeyRejected
        is GarminCall.RateLimited -> WellnessFetchResult.RateLimited(until)
        else -> WellnessFetchResult.Unavailable
    }
