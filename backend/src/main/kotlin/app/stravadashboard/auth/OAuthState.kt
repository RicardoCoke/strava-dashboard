package app.stravadashboard.auth

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The OAuth `state` value, kept in the signed `sd_oauth_state` cookie between login and callback. */
@Serializable
data class OAuthState(
    val value: String,
) {
    fun matches(received: String?): Boolean =
        received != null && MessageDigest.isEqual(value.toByteArray(), received.toByteArray())

    companion object {
        private const val STATE_BYTES = 32
        private val random = SecureRandom()

        fun random(): OAuthState {
            val bytes = ByteArray(STATE_BYTES).also(random::nextBytes)
            return OAuthState(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        }
    }
}
