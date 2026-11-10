package app.stravadashboard.api

import app.stravadashboard.module
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val BIG_FILE_CHARS = 5_000

class FrontendRoutesTest {
    private fun ApplicationTestBuilder.appWithFrontend() {
        val dir = Files.createTempDirectory("frontend").toFile()
        dir.resolve("index.html").writeText("<app-root></app-root>")
        dir.resolve("main-ABC.js").writeText("bundle")
        // Big enough for the Compression plugin, which leaves tiny bodies alone.
        dir.resolve("chunk-BIG.js").writeText("x".repeat(BIG_FILE_CHARS))
        application {
            module(
                testConfig().copy(frontendDir = dir.path),
                garminEngine = MockEngine { respondError(HttpStatusCode.NotFound) },
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
    fun `hashed assets are cached for a year, index html and the api are not`() =
        testApplication {
            appWithFrontend()

            assertEquals("max-age=31536000", client.get("/main-ABC.js").headers[HttpHeaders.CacheControl])
            assertEquals("no-cache", client.get("/").headers[HttpHeaders.CacheControl])
            assertNull(client.get("/api/health").headers[HttpHeaders.CacheControl])
        }

    @Test
    fun `responses are gzipped when the client accepts it`() =
        testApplication {
            appWithFrontend()

            val response = client.get("/chunk-BIG.js") { header(HttpHeaders.AcceptEncoding, "gzip") }

            assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
        }

    @Test
    fun `api routes still win and unknown api paths are 404`() =
        testApplication {
            appWithFrontend()

            assertContains(client.get("/api/health").bodyAsText(), "ok")
            assertContains(client.get("/api/sync").bodyAsText(), "\"status\"")
            assertEquals(HttpStatusCode.NotFound, client.get("/api/nope").status)
        }
}
