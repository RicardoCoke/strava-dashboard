package app.stravadashboard.api

import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

const val XSRF_COOKIE = "XSRF-TOKEN"
const val XSRF_HEADER = "X-XSRF-TOKEN"
private const val XSRF_TOKEN_BYTES = 32

private val random = SecureRandom()

class XsrfConfig {
    var secureCookies: Boolean = true
}

/**
 * Double-submit XSRF protection (feature 009): every `/api` response without the cookie sets a random
 * [XSRF_COOKIE], which Angular echoes in [XSRF_HEADER] on state-changing requests. Not HttpOnly, so that
 * Angular can read it.
 */
val XsrfProtection =
    createApplicationPlugin("XsrfProtection", ::XsrfConfig) {
        val secure = pluginConfig.secureCookies
        onCall { call ->
            if (call.request.path().startsWith("/api/") && call.request.cookies[XSRF_COOKIE] == null) {
                call.response.cookies.append(
                    Cookie(
                        name = XSRF_COOKIE,
                        value = newToken(),
                        encoding = CookieEncoding.RAW,
                        path = "/",
                        secure = secure,
                        httpOnly = false,
                        extensions = mapOf("SameSite" to "Strict"),
                    ),
                )
            }
        }
    }

/** True when the request's [XSRF_HEADER] matches its [XSRF_COOKIE], compared in constant time. */
fun ApplicationCall.hasValidXsrfToken(): Boolean {
    val cookie = request.cookies[XSRF_COOKIE, CookieEncoding.RAW]
    val header = request.headers[XSRF_HEADER]
    if (cookie.isNullOrBlank() || header == null) return false
    return MessageDigest.isEqual(cookie.toByteArray(), header.toByteArray())
}

private fun newToken(): String {
    val bytes = ByteArray(XSRF_TOKEN_BYTES).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
