package app.stravadashboard.domain

/** The two people the dashboard can show (feature 019); [wire] is the value in `X-Person` and `?person=`. */
enum class Person(
    val wire: String,
) {
    RICARDO("ricardo"),
    MARINA("marina"),
    ;

    companion object {
        /** No value means Ricardo, an unknown value is null. */
        fun parse(text: String?): Person? = if (text == null) RICARDO else entries.firstOrNull { it.wire == text }
    }
}
