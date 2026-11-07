package app.stravadashboard.api

import app.stravadashboard.domain.Person
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

private const val PERSON_HEADER = "X-Person"

/** Matches when `X-Person` names [person]; no header means Ricardo, an unknown value matches nobody (404). */
private class PersonRouteSelector(
    private val person: Person,
) : RouteSelector() {
    override suspend fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int,
    ): RouteSelectorEvaluation =
        if (Person.parse(context.call.request.headers[PERSON_HEADER]) == person) {
            RouteSelectorEvaluation.Constant
        } else {
            RouteSelectorEvaluation.Failure(RouteSelectorEvaluation.qualityFailedParameter, HttpStatusCode.NotFound)
        }

    override fun toString(): String = "(person ${person.wire})"
}

fun Route.forPerson(
    person: Person,
    build: Route.() -> Unit,
): Route = createChild(PersonRouteSelector(person)).apply(build)
