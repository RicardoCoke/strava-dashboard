package app.stravadashboard.config

/**
 * Typed application config, loaded once at startup from environment variables.
 * Add Strava and database settings here as features need them, and fail fast when required values are missing.
 */
data class AppConfig(
    val host: String,
    val port: Int,
) {
    companion object {
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8080

        fun fromEnvironment(env: Map<String, String> = System.getenv()): AppConfig =
            AppConfig(
                host = env["HOST"] ?: DEFAULT_HOST,
                port =
                    env["PORT"]?.let { requireNotNull(it.toIntOrNull()) { "PORT must be a number, was '$it'" } }
                        ?: DEFAULT_PORT,
            )
    }
}
