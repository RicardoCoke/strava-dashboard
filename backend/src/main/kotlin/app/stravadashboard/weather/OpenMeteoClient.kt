package app.stravadashboard.weather

import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.ports.WeatherSource
import app.stravadashboard.domain.weather.FORECAST_DAYS
import app.stravadashboard.domain.weather.HourlyWeather
import app.stravadashboard.domain.weather.WeatherFetchResult
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 15_000L
private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

/** The Ktor `Logging` plugin is deliberately not installed: it would log the coordinates in the URL. */
fun openMeteoHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

@Serializable
private data class ForecastDto(
    val hourly: HourlyDto,
)

@Serializable
private data class HourlyDto(
    val time: List<String>,
    val precipitation: List<Double?>,
    @SerialName("cloud_cover") val cloudCover: List<Double?>,
)

/** One attempt, no retry. Only the status code or exception class is logged, never the URL or coordinates. */
class OpenMeteoClient(
    private val http: HttpClient,
) : WeatherSource {
    override suspend fun fetch(location: WeatherLocation): WeatherFetchResult =
        try {
            val response =
                http.get(FORECAST_URL) {
                    parameter("latitude", location.latitude)
                    parameter("longitude", location.longitude)
                    parameter("hourly", "precipitation,cloud_cover")
                    parameter("forecast_days", FORECAST_DAYS)
                    // Local to the coordinates, so 09:00 means 09:00 at that place.
                    parameter("timezone", "auto")
                }
            if (response.status.isSuccess()) {
                toResult(response.body<ForecastDto>().hourly)
            } else {
                log.warn("Open-Meteo returned status {}", response.status.value)
                WeatherFetchResult.Unavailable
            }
        } catch (e: IOException) {
            unavailable(e)
        } catch (e: UnresolvedAddressException) {
            unavailable(e)
        } catch (e: SerializationException) {
            unavailable(e)
        } catch (e: ContentConvertException) {
            unavailable(e)
        } catch (e: NoTransformationFoundException) {
            unavailable(e)
        } catch (e: DateTimeParseException) {
            unavailable(e)
        }

    private fun toResult(hourly: HourlyDto): WeatherFetchResult {
        if (hourly.precipitation.size != hourly.time.size || hourly.cloudCover.size != hourly.time.size) {
            log.warn("Open-Meteo returned hourly arrays of different lengths")
            return WeatherFetchResult.Unavailable
        }
        return WeatherFetchResult.Success(
            hourly.time.indices.map { i ->
                HourlyWeather(LocalDateTime.parse(hourly.time[i]), hourly.precipitation[i], hourly.cloudCover[i])
            },
        )
    }

    private fun unavailable(e: Exception): WeatherFetchResult {
        log.warn("Open-Meteo request failed: {}", e::class.simpleName)
        return WeatherFetchResult.Unavailable
    }

    private companion object {
        val log = LoggerFactory.getLogger(OpenMeteoClient::class.java)
    }
}
