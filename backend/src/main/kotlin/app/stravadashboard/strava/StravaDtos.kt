package app.stravadashboard.strava

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields this app uses are declared. Maps, coordinates and polylines are never deserialized.

@Serializable
internal data class SummaryActivityDto(
    val id: Long,
    val name: String = "",
    @SerialName("sport_type") val sportType: String = "",
    @SerialName("start_date") val startDate: String,
    @SerialName("start_date_local") val startDateLocal: String,
    val distance: Double = 0.0,
    @SerialName("average_heartrate") val averageHeartrate: Double? = null,
    val trainer: Boolean = false,
)

@Serializable
internal data class DetailedActivityDto(
    val id: Long,
    val description: String? = null,
    val laps: List<LapDto> = emptyList(),
)

@Serializable
internal data class LapDto(
    @SerialName("lap_index") val lapIndex: Int,
    @SerialName("elapsed_time") val elapsedTime: Int,
    val distance: Double,
)

/** Response of `/streams?keys=time,velocity_smooth&key_by_type=true`. Other stream keys are ignored. */
@Serializable
internal data class StreamSetDto(
    val time: IntStreamDto? = null,
    @SerialName("velocity_smooth") val velocitySmooth: DoubleStreamDto? = null,
)

@Serializable
internal data class IntStreamDto(
    val data: List<Int> = emptyList(),
)

@Serializable
internal data class DoubleStreamDto(
    val data: List<Double> = emptyList(),
)

@Serializable
internal data class TokenResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    val athlete: AthleteDto? = null,
) {
    override fun toString(): String = "TokenResponseDto(<redacted>)"
}

@Serializable
internal data class AthleteDto(
    val id: Long,
)
