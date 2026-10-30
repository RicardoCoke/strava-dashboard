package app.stravadashboard

import app.stravadashboard.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun `defaults to localhost 8080`() {
        assertEquals(AppConfig(host = "127.0.0.1", port = 8080), AppConfig.fromEnvironment(emptyMap()))
    }

    @Test
    fun `rejects non-numeric port`() {
        assertFailsWith<IllegalArgumentException> { AppConfig.fromEnvironment(mapOf("PORT" to "abc")) }
    }
}
