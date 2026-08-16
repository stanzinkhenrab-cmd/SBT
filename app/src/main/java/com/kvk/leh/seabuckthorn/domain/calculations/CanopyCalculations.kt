package com.kvk.leh.seabuckthorn.domain.calculations

/** Shrub canopy geometry derived from two perpendicular diameter measurements. */
object CanopyCalculations {

    /** Average canopy diameter = (North-South + East-West) / 2, in the same unit as inputs (m). */
    fun averageCanopyDiameter(canopyNs: Double?, canopyEw: Double?): Double? {
        if (canopyNs == null || canopyEw == null) return null
        if (canopyNs < 0 || canopyEw < 0) return null
        return (canopyNs + canopyEw) / 2.0
    }

    /** Estimated canopy area = pi * (average diameter / 2)^2, treating the canopy as circular, in m^2. */
    fun canopyArea(averageDiameter: Double?): Double? {
        if (averageDiameter == null || averageDiameter < 0) return null
        val radius = averageDiameter / 2.0
        return Math.PI * radius * radius
    }
}
