package app.stravadashboard.api

import app.stravadashboard.module
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FrontendRoutesTest {
    private fun ApplicationTestBuilder.appWithFrontend() {
        val dir = Files.createTempDirectory("frontend").toFile()
        dir.resolve("index.html").writeText("<app-root></app-root>")
        dir.resolve("main-ABC.js").writeText("bundle")
        application {
            module(
                testConfig().copy(frontendDir = dir.path),
                stravaEngine = MockEngine { respondError(HttpStatusCode.NotFound) },
            )
        }
    }

    @Test
    fun `root and deep links serve index html`() =
        testApplication {
            appWithFrontend()

            assertContains(client.get("/").bodyAsText(), "app-root")
            assertContains(client.get("/connect").bodyAsText(), "app-root")
        }

    @Test
    fun `built assets are served as files`() =
        testApplication {
            appWithFrontend()

            assertEquals("bundle", client.get("/main-ABC.js").bodyAsText())
        }

    @Test
    fun `api routes still win and unknown api paths are 404`() =
        testApplication {
            appWithFrontend()

            assertContains(client.get("/api/health").bodyAsText(), "ok")
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/sync").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/api/nope").status)
        }
}
