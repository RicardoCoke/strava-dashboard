package app.stravadashboard.persistence

import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import org.jetbrains.exposed.sql.ResultRow

/** A stored per-km split (feature 016). */
internal fun ResultRow.toSplit(): Split =
    Split(
        index = this[RunSplitsTable.splitIndex],
        distance = Meters(this[RunSplitsTable.distanceM]),
        movingTime = Seconds(this[RunSplitsTable.movingTimeS]),
        elevationDifference = this[RunSplitsTable.elevationDifferenceM]?.let(::Meters),
        averageHeartRateBpm = this[RunSplitsTable.averageHeartrate],
        gradeAdjustedSpeed = this[RunSplitsTable.gradeAdjustedSpeedMps]?.let(::MetersPerSecond),
    )
