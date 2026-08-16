package com.kvk.leh.seabuckthorn.domain.calculations

import kotlin.math.sqrt

/** Mean/min/max/sample-standard-deviation summary of a numeric measurement series (e.g. berry length). */
data class StatSummary(
    val count: Int,
    val mean: Double?,
    val min: Double?,
    val max: Double?,
    val standardDeviation: Double?
)

object DescriptiveStatistics {

    fun summarize(values: List<Double>): StatSummary {
        if (values.isEmpty()) {
            return StatSummary(count = 0, mean = null, min = null, max = null, standardDeviation = null)
        }
        val mean = values.average()
        val min = values.min()
        val max = values.max()
        // Sample standard deviation (n-1 denominator); undefined for a single observation.
        val stdDev = if (values.size > 1) {
            val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
            sqrt(variance)
        } else {
            null
        }
        return StatSummary(count = values.size, mean = mean, min = min, max = max, standardDeviation = stdDev)
    }
}
