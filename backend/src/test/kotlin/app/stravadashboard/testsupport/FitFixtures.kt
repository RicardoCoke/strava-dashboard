package app.stravadashboard.testsupport

import com.garmin.fit.DateTime
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One invented FIT record; [lat] is a position the app must never read. */
data class FitRecord(
    val second: Int,
    val distance: Float? = null,
    val speed: Float? = null,
    val heartRate: Int? = null,
    val altitude: Float? = null,
    val lat: Int? = 123_456_789,
)

data class FitLap(
    val elapsed: Float,
    val distance: Float?,
    val heartRate: Int?,
)

private val START: Instant = Instant.parse("2026-11-10T07:00:00Z")

/** An activity file as Garmin's download endpoint returns it: a zip with one `.fit`, encoded with the FIT SDK. */
fun fitZip(
    records: List<FitRecord>,
    laps: List<FitLap> = emptyList(),
): ByteArray {
    val file = Files.createTempFile("invented", ".fit").toFile()
    val encoder = FileEncoder(file, Fit.ProtocolVersion.V2_0)
    encoder.write(
        FileIdMesg().apply {
            type = com.garmin.fit.File.ACTIVITY
            manufacturer = Manufacturer.DEVELOPMENT
            timeCreated = DateTime(START)
        },
    )
    records.forEach { r ->
        encoder.write(
            RecordMesg().apply {
                timestamp = DateTime(START.plusSeconds(r.second.toLong()))
                r.distance?.let { distance = it }
                r.speed?.let { enhancedSpeed = it }
                r.heartRate?.let { heartRate = it.toShort() }
                r.altitude?.let { enhancedAltitude = it }
                r.lat?.let {
                    positionLat = it
                    positionLong = it
                }
            },
        )
    }
    laps.forEach { lap ->
        encoder.write(
            LapMesg().apply {
                timestamp = DateTime(START)
                totalElapsedTime = lap.elapsed
                lap.distance?.let { totalDistance = it }
                lap.heartRate?.let { avgHeartRate = it.toShort() }
            },
        )
    }
    encoder.close()
    return zip("invented_ACTIVITY.fit", file.readBytes()).also { file.delete() }
}

fun zip(
    name: String,
    content: ByteArray,
): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use {
        it.putNextEntry(ZipEntry(name))
        it.write(content)
        it.closeEntry()
    }
    return out.toByteArray()
}
