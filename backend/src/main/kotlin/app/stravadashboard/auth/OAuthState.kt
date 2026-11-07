package app.stravadashboard.auth

import app.stravadashboard.domain.Person
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The OAuth `state` value, kept in the signed `sd_oauth_state` cookie between login and callback. */
@Serializable
data class OAuthState(
    val value: String,
    /** Who is connecting; a cookie from before feature 019 has no person and means Ricardo. */
    val person: String = Person.RICARDO.wire,
) {
    fun matches(received: String?): Boolean =
        received != null && MessageDigest.isEqual(value.toByteArray(), received.toByteArray())

    companion object {
        private const val STATE_BYTES = 32
        private val random = SecureRandom()

        fun random(person: Person): OAuthState {
            val bytes = ByteArray(STATE_BYTES).also(random::nextBytes)
            return OAuthState(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), person.wire)
        }
    }
}
