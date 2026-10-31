package app.stravadashboard.persistence

import app.stravadashboard.domain.ports.MAX_PLAN_FILE_BYTES
import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.domain.trainingplan.TrainingPlanResult
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.PLAN_START
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.samplePlanText
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// All file content here is invented.
class TrainingPlanFileSourceTest {
    private val dir: Path = Files.createTempDirectory("plan-test")
    private val file: Path = dir.resolve("my-plan.csv")
    private val attached = mutableListOf<Pair<Logger, ListAppender<ILoggingEvent>>>()

    private fun read(path: String? = file.toString()): PlanFile = runBlocking { TrainingPlanFileSource(path).read() }

    private fun capture(name: Class<*>): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(name) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        attached += logger to appender
        return appender
    }

    @AfterTest
    fun detach() {
        attached.forEach { (logger, appender) -> logger.detachAppender(appender) }
    }

    @Test
    fun `004 R1 - no path is not configured`() {
        assertEquals(PlanFile.NotConfigured, read(null))
    }

    @Test
    fun `004 R2 - file is re-read on every call`() {
        Files.writeString(file, "first")
        assertEquals(PlanFile.Content("first"), read())
        Files.writeString(file, "second")
        assertEquals(PlanFile.Content("second"), read())
    }

    @Test
    fun `004 R2 - strips BOM`() {
        Files.write(file, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "WEEK 1;".toByteArray())
        assertEquals(PlanFile.Content("WEEK 1;"), read())
    }

    @Test
    fun `004 R2 - falls back to windows-1252`() {
        Files.write(file, byteArrayOf('a'.code.toByte(), 0xE7.toByte(), 'o'.code.toByte()))
        assertEquals(PlanFile.Content("aço"), read())
    }

    @Test
    fun `004 R2 - unreadable file logs class only`() {
        val logs = capture(TrainingPlanFileSource::class.java)
        assertEquals(PlanFile.Unreadable, read(dir.resolve("missing.csv").toString()))
        val message = logs.list.single().formattedMessage
        assertEquals("Training plan file could not be read (TRAINING_PLAN_CSV): NoSuchFileException", message)
        assertFalse("missing" in message)
        assertFalse(dir.toString() in message)
        assertEquals(null, logs.list.single().throwableProxy)
    }

    @Test
    fun `004 R2 - directory is unreadable`() {
        assertEquals(PlanFile.Unreadable, read(dir.toString()))
    }

    @Test
    fun `004 R2 - file over 1 MB is invalid`() {
        Files.write(file, ByteArray(MAX_PLAN_FILE_BYTES.toInt() + 1) { 'a'.code.toByte() })
        assertEquals(PlanFile.TooLarge, read())
        val service = TrainingPlanService(TrainingPlanFileSource(file.toString()), PLAN_START, fixedClock(), TEST_ZONE)
        assertEquals(TrainingPlanResult.Invalid("The plan file is larger than 1 MB."), runBlocking { service.get() })
    }

    @Test
    fun `004 R2 - parse failure log has no cell values`() {
        val logs = capture(TrainingPlanService::class.java)
        val broken = samplePlanText().replace("EASY;70,50;50;", "EASY;70,50;secret-cell;")
        val service = TrainingPlanService(FakePlanSource(PlanFile.Content(broken)), PLAN_START, fixedClock(), TEST_ZONE)
        assertIs<TrainingPlanResult.Invalid>(runBlocking { service.get() })
        val message = logs.list.single().formattedMessage
        assertTrue("WEEK 1, MONDAY: EASY value is not a number" in message)
        assertFalse("secret-cell" in message)
    }
}
