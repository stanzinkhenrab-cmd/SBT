package com.kvkleh.sbtsurvey.map

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web-Mercator helpers shared by the offline survey map.
 *
 * Coordinates are expressed in "tile units" at a given zoom level: the whole world spans
 * 2^zoom units on each axis, so a tile index is simply the integer part.
 */
object MapMath {

    const val TILE_SIZE_PX = 256f
    const val MIN_ZOOM = 3f
    const val MAX_ZOOM = 17f

    /** Latitude limit of the Web-Mercator projection. */
    const val MAX_LATITUDE = 85.05112878

    fun lonToTileX(longitude: Double, zoom: Int): Double =
        (longitude + 180.0) / 360.0 * (1 shl zoom)

    fun latToTileY(latitude: Double, zoom: Int): Double {
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val radians = clamped * PI / 180.0
        return (1.0 - asinh(tan(radians)) / PI) / 2.0 * (1 shl zoom)
    }

    fun tileXToLon(tileX: Double, zoom: Int): Double =
        tileX / (1 shl zoom) * 360.0 - 180.0

    fun tileYToLat(tileY: Double, zoom: Int): Double {
        val n = PI - 2.0 * PI * tileY / (1 shl zoom)
        return 180.0 / PI * atan(sinh(n))
    }

    fun tileCount(zoom: Int): Int = 1 shl zoom

    /** Wraps a tile X index around the antimeridian; returns null for out-of-range Y. */
    fun normaliseTileX(x: Int, zoom: Int): Int {
        val count = tileCount(zoom)
        return ((x % count) + count) % count
    }

    fun isValidTileY(y: Int, zoom: Int): Boolean = y in 0 until tileCount(zoom)

    fun zoomLevel(zoom: Float): Int = floor(zoom).toInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())

    /** On-screen size of one tile at a fractional [zoom]. */
    fun scaledTileSize(zoom: Float): Float {
        val level = zoomLevel(zoom)
        return TILE_SIZE_PX * Math.pow(2.0, (zoom - level).toDouble()).toFloat()
    }

    /** Approximate ground resolution in metres per screen pixel, for the scale bar. */
    fun metresPerPixel(latitude: Double, zoom: Float): Double {
        val equatorial = 156543.03392
        return equatorial * Math.cos(latitude * PI / 180.0) / Math.pow(2.0, zoom.toDouble())
    }

    /** Great-circle distance in metres between two coordinates. */
    fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * PI / 180.0) * Math.cos(lat2 * PI / 180.0) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
