package app.stravadashboard.config

/** A place to fetch the forecast for (feature 018). */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        private const val MAX_LATITUDE = 90.0
        private const val MAX_LONGITUDE = 180.0

        /** Null when both are unset (the feature is off); a partial or invalid pair stops the app at startup. */
        fun parse(
            latitude: String?,
            longitude: String?,
        ): WeatherLocation? =
            if (latitude.isNullOrBlank() && longitude.isNullOrBlank()) {
                null
            } else {
                WeatherLocation(
                    coordinate("WEATHER_LATITUDE", latitude, MAX_LATITUDE),
                    coordinate("WEATHER_LONGITUDE", longitude, MAX_LONGITUDE),
                )
            }

        private fun coordinate(
            key: String,
            text: String?,
            limit: Double,
        ): Double =
            requireNotNull(text?.trim()?.toDoubleOrNull()?.takeIf { it in -limit..limit }) {
                "$key must be a number between -$limit and $limit (both coordinates must be set)"
            }
    }
}
