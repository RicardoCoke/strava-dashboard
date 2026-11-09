package app.stravadashboard.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/** Serves the built Angular app from [dir]; deep links like `/connect` get index.html for the router. */
fun Route.frontendRoutes(dir: String) {
    singlePageApplication { filesPath = dir }
    // An unknown API path stays a 404 rather than falling through to index.html.
    route("/api/{...}") {
        handle { call.respond(HttpStatusCode.NotFound) }
    }
}
