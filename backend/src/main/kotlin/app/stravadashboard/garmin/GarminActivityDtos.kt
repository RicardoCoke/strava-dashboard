package app.stravadashboard.garmin

import kotlinx.serialization.Serializable

// Only the fields this app uses are declared; start/end coordinates and polylines are never deserialized.

/** One element of `GET /activitylist-service/activities/search/activities` (feature 021). */
@Serializable
internal data class GarminActivityDto(
    val activityId: Long,
    val activityName: String? = null,
    /** `yyyy-MM-dd HH:mm:ss`, local wall-clock time. */
    val startTimeLocal: String,
    /** `yyyy-MM-dd HH:mm:ss`, UTC. */
    val startTimeGMT: String,
    val activityType: GarminActivityTypeDto? = null,
    /** Metres. */
    val distance: Double? = null,
    val averageHR: Double? = null,
)

@Serializable
internal data class GarminActivityTypeDto(
    val typeKey: String = "",
)

/** `GET /activity-service/activity/{id}`: only the description and the moving time are read. */
@Serializable
internal data class GarminActivityDetailDto(
    val description: String? = null,
    val summaryDTO: GarminActivitySummaryDto? = null,
)

/** Seconds. */
@Serializable
internal data class GarminActivitySummaryDto(
    val movingDuration: Double? = null,
    val duration: Double? = null,
)
