package app.stravadashboard.persistence

import app.stravadashboard.domain.ports.MAX_PLAN_FILE_BYTES
import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.domain.ports.TrainingPlanSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Reads the plan CSV from [path] on every call: UTF-8 (BOM allowed), falling back to Windows-1252. */
class TrainingPlanFileSource(
    private val path: String?,
) : TrainingPlanSource {
    private val log = LoggerFactory.getLogger(TrainingPlanFileSource::class.java)

    override suspend fun read(): PlanFile {
        val configured = path ?: return PlanFile.NotConfigured
        return withContext(Dispatchers.IO) {
            try {
                val file = Path.of(configured)
                if (Files.size(file) > MAX_PLAN_FILE_BYTES) {
                    PlanFile.TooLarge
                } else {
                    PlanFile.Content(decode(Files.readAllBytes(file)))
                }
            } catch (e: IOException) {
                unreadable(e)
            } catch (e: InvalidPathException) {
                unreadable(e)
            }
        }
    }

    private fun unreadable(e: Exception): PlanFile {
        // Class name only: the message would contain the path.
        log.warn("Training plan file could not be read (TRAINING_PLAN_CSV): {}", e.javaClass.simpleName)
        return PlanFile.Unreadable
    }

    private fun decode(bytes: ByteArray): String {
        val text =
            try {
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                String(bytes, Charset.forName("windows-1252"))
            }
        return text.removePrefix(BYTE_ORDER_MARK)
    }

    private companion object {
        const val BYTE_ORDER_MARK_CODE = 0xFEFF
        val BYTE_ORDER_MARK: String = Char(BYTE_ORDER_MARK_CODE).toString()
    }
}
