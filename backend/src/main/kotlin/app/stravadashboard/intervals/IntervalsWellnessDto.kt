package app.stravadashboard.intervals

import kotlinx.serialization.Serializable

/**
 * One element of `GET /api/v1/athlete/{id}/wellness` with `fields=id,restingHR,hrv`. [id] is the local date.
 * [restingHR] is decoded as a decimal so an unexpected fraction doesn't fail the whole response.
 */
@Serializable
internal data class IntervalsWellnessDto(
    val id: String,
    @Suppress("ConstructorParameterNaming") val restingHR: Double? = null,
    val hrv: Double? = null,
)
