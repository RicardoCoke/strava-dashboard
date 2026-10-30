package app.stravadashboard.api

import app.stravadashboard.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {
    @Test
    fun `health endpoint returns ok`() =
        testApplication {
            application { module() }

            val response = client.get("/api/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"status":"ok"}""", response.bodyAsText())
        }
}
