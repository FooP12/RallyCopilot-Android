package com.rallycopilot.core

/**
 * Turn-by-turn spoken guidance for road routes.
 * Distances are measured along the routed geometry, not as straight-line estimates.
 */
data class GuidanceStep(
    val distanceFromStartMeters: Double,
    val instruction: String,
    val isArrival: Boolean = false
)

class RallyGuidanceEngine {
    private var nextStepIndex = 0
    private var lastAnnouncedLevel = 5

    fun reset() {
        nextStepIndex = 0
        lastAnnouncedLevel = 5
    }

    fun update(progressMeters: Double, steps: List<GuidanceStep>): String? {
        while (nextStepIndex < steps.size) {
            val step = steps[nextStepIndex]
            val remaining = step.distanceFromStartMeters - progressMeters

            if (step.isArrival) {
                if (remaining <= 45.0) {
                    nextStepIndex++
                    return "Ты прибыл. Маршрут завершён."
                }
                return null
            }

            if (remaining <= 35.0) {
                nextStepIndex++
                lastAnnouncedLevel = 5
                return "Сейчас: " + step.instruction
            }

            val level = when {
                remaining <= 80.0 -> 1
                remaining <= 200.0 -> 2
                remaining <= 500.0 -> 3
                remaining <= 1000.0 -> 4
                else -> 5
            }
            if (level < lastAnnouncedLevel) {
                lastAnnouncedLevel = level
                val distanceText = when (level) {
                    4 -> "километр"
                    3 -> "пятьсот метров"
                    2 -> "двести метров"
                    else -> "восемьдесят метров"
                }
                return "Через " + distanceText + ": " + step.instruction
            }
            return null
        }
        return null
    }

    fun preview(progressMeters: Double, steps: List<GuidanceStep>): String {
        val step = steps.getOrNull(nextStepIndex) ?: return "Продолжай движение по маршруту"
        val remaining = (step.distanceFromStartMeters - progressMeters).coerceAtLeast(0.0)
        if (step.isArrival) return "До финиша " + distanceLabel(remaining)
        if (remaining <= 35.0) return "Сейчас: " + step.instruction
        return "Через " + distanceLabel(remaining) + " · " + step.instruction
    }

    private fun distanceLabel(meters: Double): String =
        if (meters >= 1000.0) "%.1f километра".format(meters / 1000.0) else meters.toInt().toString() + " метров"
}
