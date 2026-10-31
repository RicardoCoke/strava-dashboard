package app.stravadashboard.domain.trainingplan

import java.math.BigDecimal
import java.math.RoundingMode

private val NUMBER = Regex("""^-?\d+([.,]\d+)?$""")

/**
 * Thrown inside the parser for a malformed plan and turned into [PlanParseResult.Invalid] by
 * [TrainingPlanParser.parse], so it never leaves this package. [reason] must not contain cell values.
 */
internal class PlanFormatException(
    val reason: String,
) : RuntimeException(reason)

internal fun invalidPlan(reason: String): Nothing = throw PlanFormatException(reason)

/** The trimmed cell at [column], or "" when the row is shorter. */
internal fun List<String>.cell(column: Int): String = getOrNull(column)?.trim().orEmpty()

/** A number with `.` or `,` as the decimal mark, or null when the cell isn't one. */
internal fun parseNumber(cell: String): Double? =
    cell
        .trim()
        .takeIf { NUMBER.matches(it) }
        ?.replace(',', '.')
        ?.toDouble()

internal fun roundTwoDecimals(value: Double): Double =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

/** "5", "42.2": whole numbers without decimals, others as plain decimals. */
internal fun formatPlainNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
