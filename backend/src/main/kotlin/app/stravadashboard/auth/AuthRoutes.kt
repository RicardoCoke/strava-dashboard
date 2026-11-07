package app.stravadashboard.auth

import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.Person
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set

private const val AUTHORIZE_URL = "https://www.strava.com/oauth/authorize"
const val REQUIRED_SCOPE = "activity:read_all"

/** What connecting one person needs: their Strava app's client ID and their store. */
class PersonConnect(
    val clientId: String,
    val service: ConnectService,
)

private sealed interface CallbackOutcome {
    data class Connected(
        val athleteId: Long,
    ) : CallbackOutcome

    data class Failed(
        val code: String,
    ) : CallbackOutcome
}

fun Route.authRoutes(
    config: AppConfig,
    people: Map<Person, PersonConnect>,
) {
    get("/auth/strava/login") {
        val person = Person.parse(call.request.queryParameters["person"])
        val connect = person?.let(people::get)
        when {
            person == null -> call.respondRedirect("/connect?error=unknown_person")
            connect == null -> call.respondRedirect("/connect?error=person_not_configured")
            else -> {
                val state = OAuthState.random(person)
                call.sessions.set(state)
                call.respondRedirect(authorizeUrl(config, connect.clientId, state.value))
            }
        }
    }

    get("/auth/strava/callback") {
        val expected = call.sessions.get<OAuthState>()
        call.sessions.clear<OAuthState>()
        // The person comes from the signed cookie, never from the query.
        val connect = expected?.let { Person.parse(it.person) }?.let(people::get)
        when (val outcome = callbackOutcome(call.request.queryParameters, expected, connect)) {
            is CallbackOutcome.Connected -> {
                call.sessions.set(UserSession(outcome.athleteId))
                call.respondRedirect("/")
            }
            is CallbackOutcome.Failed -> call.respondRedirect("/connect?error=${outcome.code}")
        }
    }
}

private fun authorizeUrl(
    config: AppConfig,
    clientId: String,
    state: String,
): String =
    URLBuilder(AUTHORIZE_URL)
        .apply {
            parameters.append("client_id", clientId)
            parameters.append("redirect_uri", config.stravaRedirectUri)
            parameters.append("response_type", "code")
            parameters.append("approval_prompt", "auto")
            parameters.append("scope", REQUIRED_SCOPE)
            parameters.append("state", state)
        }.buildString()

private suspend fun callbackOutcome(
    params: Parameters,
    expected: OAuthState?,
    connect: PersonConnect?,
): CallbackOutcome {
    val code = params["code"]
    val scope = params["scope"].orEmpty()
    return when {
        expected == null || connect == null || !expected.matches(params["state"]) ->
            CallbackOutcome.Failed("state_mismatch")
        params["error"] != null -> CallbackOutcome.Failed("access_denied")
        REQUIRED_SCOPE !in scope.split(",").map { it.trim() } -> CallbackOutcome.Failed("scope_missing")
        code.isNullOrBlank() -> CallbackOutcome.Failed("token_exchange_failed")
        else ->
            when (val result = connect.service.connect(code, scope)) {
                is ConnectResult.Connected -> CallbackOutcome.Connected(result.athleteId)
                ConnectResult.TokenExchangeFailed -> CallbackOutcome.Failed("token_exchange_failed")
                ConnectResult.WrongAthlete -> CallbackOutcome.Failed("wrong_athlete")
            }
    }
}
