package com.sbt.geostamp.model

/**
 * Parses coordinates the way people actually type them.
 *
 * Accepts plain decimal degrees (`34.9986`, `-77.3834`), a hemisphere letter in either
 * position (`34.9986 N`, `S 34.9986`, `34.9986N`), and degrees/minutes/seconds written with
 * the usual marks or with plain spaces (`34°59'55.0"N`, `34 59 55 N`).
 */
object Coordinates {

    private val NUMBER = Regex("""-?\d+(?:\.\d+)?""")

    /** Digits, whitespace and the marks people put between them — everything else is a letter. */
    private val NOISE = Regex("""[\d\s.,+\-°'\u2032\u2033\u00BA"]""")

    const val MAX_LATITUDE = 90.0
    const val MAX_LONGITUDE = 180.0

    fun parseLatitude(text: String): Double? = parseComponent(text, MAX_LATITUDE)

    fun parseLongitude(text: String): Double? = parseComponent(text, MAX_LONGITUDE)

    /**
     * Splits a pasted "lat, lon" string into its two halves. Returns null unless both sides
     * parse and land inside their valid ranges — so a single value is never mistaken for a pair.
     */
    fun parsePair(text: String): Pair<Double, Double>? {
        val halves = splitPair(text) ?: return null
        val latitude = parseLatitude(halves.first) ?: return null
        val longitude = parseLongitude(halves.second) ?: return null
        return latitude to longitude
    }

    /** True when [text] looks like it carries both halves, so the UI can offer to split it. */
    fun looksLikePair(text: String): Boolean = splitPair(text) != null

    private fun splitPair(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // A comma only separates the pair when it is not being used as a decimal point,
        // which we can tell by counting the numbers on each side.
        val parts = trimmed.split(',', ';', '/', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size == 2 && parts.all { NUMBER.containsMatchIn(it) }) {
            return parts[0] to parts[1]
        }
        if (parts.size != 1) return null

        // No separator punctuation: fall back to splitting between the two halves' numbers,
        // which only works when each half is plain decimal degrees.
        val numbers = NUMBER.findAll(trimmed).map { it.range }.toList()
        if (numbers.size != 2) return null
        val boundary = numbers[1].first
        return trimmed.substring(0, boundary).trim() to trimmed.substring(boundary).trim()
    }

    private fun parseComponent(text: String, max: Double): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val numbers = NUMBER.findAll(trimmed).map { it.value }.toList()
        if (numbers.isEmpty() || numbers.size > 3) return null

        val degrees = numbers[0].toDoubleOrNull() ?: return null
        val minutes = numbers.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val seconds = numbers.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        if (minutes < 0 || minutes >= 60 || seconds < 0 || seconds >= 60) return null
        // Minutes and seconds only make sense against whole degrees.
        if (numbers.size > 1 && degrees != Math.floor(degrees)) return null

        val magnitude = Math.abs(degrees) + minutes / 60.0 + seconds / 3600.0
        if (magnitude > max) return null

        // Whatever is left once the numbers and their marks are gone must be a hemisphere.
        val letters = NOISE.replace(trimmed, "").uppercase()
        if (letters.length > 1) return null
        val hemisphere = letters.firstOrNull()
        val axisLetters = if (max == MAX_LATITUDE) "NS" else "EW"
        if (hemisphere != null && hemisphere !in axisLetters) return null

        val signedNegative = degrees < 0
        val letterNegative = hemisphere == 'S' || hemisphere == 'W'
        // "-34 N" contradicts itself; refuse rather than guess.
        if (signedNegative && hemisphere != null && !letterNegative) return null

        return if (signedNegative || letterNegative) -magnitude else magnitude
    }
}
