package app.stravadashboard.garmin

import kotlinx.serialization.Serializable

/** `GET /usersummary-service/v2/dailysummaries`; only the date and resting HR are read. */
@Serializable
internal data class GarminDailySummariesDto(
    val data: List<GarminDailySummaryDto>,
)

@Serializable
internal data class GarminDailySummaryDto(
    val calendarDate: String,
    val heartRate: GarminHeartRateDto? = null,
)

/** [restingValue] is bpm, decoded as a decimal so an unexpected fraction doesn't fail the whole response. */
@Serializable
internal data class GarminHeartRateDto(
    val restingValue: Double? = null,
)

/** `GET /hrv-service/hrv/daily/{from}/{to}`; [GarminHrvSummaryDto.lastNightAvg] is the overnight rMSSD in ms. */
@Serializable
internal data class GarminHrvDailyDto(
    val hrvSummaries: List<GarminHrvSummaryDto>,
)

@Serializable
internal data class GarminHrvSummaryDto(
    val calendarDate: String,
    val lastNightAvg: Double? = null,
)

/** `GET /wellness-service/wellness/dailySleep?date=`; [sleepTimeSeconds] is time asleep (naps excluded). */
@Serializable
internal data class GarminDailySleepDto(
    val sleepTimeSeconds: Int? = null,
)

/** `GET /wellness-service/wellness/dailyHeartRate?date=`; each value is `[epochMillis, bpm or null]`, ~2 min apart. */
@Serializable
internal data class GarminDailyHeartRateDto(
    val heartRateValues: List<List<Double?>>? = null,
)
