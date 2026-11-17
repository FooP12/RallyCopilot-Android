package com.rallycopilot.core

/**
 * Converts route geometry into short, spoken rally-style instructions.
 * Important: turn severity describes geometry only, never a safe driving speed.
 */
enum class TurnDirection { LEFT, RIGHT, STRAIGHT, U_TURN }

data class RouteManeuver(
    val direction: TurnDirection,
    val angleDegrees: Double,
    val distanceMeters: Int,
    val streetName: String? = null
)

data class RallyCallout(
    val paceNote: String,
    val distanceMeters: Int,
    val severity: Int?
)

object RallyCalloutEngine {
    fun severity(angleDegrees: Double): Int? {
        val angle = kotlin.math.abs(angleDegrees)
        return when {
            angle < 15 -> null
            angle < 35 -> 6
            angle < 55 -> 5
            angle < 80 -> 4
            angle < 110 -> 3
            angle < 145 -> 2
            else -> 1
        }
    }

    fun create(maneuver: RouteManeuver): RallyCallout {
        val note = when (maneuver.direction) {
            TurnDirection.LEFT -> "левый"
            TurnDirection.RIGHT -> "правый"
            TurnDirection.STRAIGHT -> "прямо"
            TurnDirection.U_TURN -> "разворот"
        }
        val grade = severity(maneuver.angleDegrees)
        val phrase = when {
            maneuver.direction == TurnDirection.STRAIGHT -> "Прямо"
            maneuver.direction == TurnDirection.U_TURN -> "Разворот"
            grade == null -> "$note плавно"
            else -> "$note $grade"
        }
        return RallyCallout(
            paceNote = phrase,
            distanceMeters = maneuver.distanceMeters.coerceAtLeast(0),
            severity = grade
        )
    }
}
