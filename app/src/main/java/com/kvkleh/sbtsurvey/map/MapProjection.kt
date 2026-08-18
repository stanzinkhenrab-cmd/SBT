package com.kvkleh.sbtsurvey.map

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Geographic extent in WGS-84 degrees. */
data class GeoBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    val centerLat: Double get() = (minLat + maxLat) / 2
    val centerLon: Double get() = (minLon + maxLon) / 2

    /** Grows the extent by [fraction] on each side, so markers are not on the edge. */
    fun padded(fraction: Double): GeoBounds {
        val latPad = max((maxLat - minLat) * fraction, MIN_SPAN_DEGREES)
        val lonPad = max((maxLon - minLon) * fraction, MIN_SPAN_DEGREES)
        return GeoBounds(
            minLat = (minLat - latPad).coerceAtLeast(-MapMath.MAX_LATITUDE),
            maxLat = (maxLat + latPad).coerceAtMost(MapMath.MAX_LATITUDE),
            minLon = minLon - lonPad,
            maxLon = maxLon + lonPad
        )
    }

    companion object {
        private const val MIN_SPAN_DEGREES = 0.0015

        /** Extent of a view centred on a point at a given fractional zoom. */
        fun around(
            centerLat: Double,
            centerLon: Double,
            zoom: Float,
            widthPx: Float,
            heightPx: Float
        ): GeoBounds {
            val level = MapMath.zoomLevel(zoom)
            val tileSize = MapMath.scaledTileSize(zoom)
            val cx = MapMath.lonToTileX(centerLon, level)
            val cy = MapMath.latToTileY(centerLat, level)
            val halfX = (widthPx / 2f) / tileSize
            val halfY = (heightPx / 2f) / tileSize
            return GeoBounds(
                minLat = MapMath.tileYToLat(cy + halfY, level),
                maxLat = MapMath.tileYToLat(cy - halfY, level),
                minLon = MapMath.tileXToLon(cx - halfX, level),
                maxLon = MapMath.tileXToLon(cx + halfX, level)
            )
        }
    }
}

/**
 * Maps geographic coordinates onto a rectangle of canvas pixels.
 *
 * The tile level is chosen so that source tiles are always reduced rather than
 * enlarged, which is what keeps printed and exported maps sharp. Because the mapping is
 * a plain Web-Mercator scale-and-offset, it also yields the exact pixel size and corner
 * coordinate a GeoTIFF needs.
 */
class MapProjection(
    val level: Int,
    /** Canvas pixels per tile unit at [level]. */
    val tileSize: Double,
    /** Tile coordinate of the left edge of [left]. */
    val originTileX: Double,
    /** Tile coordinate of the top edge of [top]. */
    val originTileY: Double,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun xOf(longitude: Double): Float =
        left + ((MapMath.lonToTileX(longitude, level) - originTileX) * tileSize).toFloat()

    fun yOf(latitude: Double): Float =
        top + ((MapMath.latToTileY(latitude, level) - originTileY) * tileSize).toFloat()

    fun longitudeAt(x: Float): Double =
        MapMath.tileXToLon(originTileX + (x - left) / tileSize, level)

    fun latitudeAt(y: Float): Double =
        MapMath.tileYToLat(originTileY + (y - top) / tileSize, level)

    /** Ground size of one canvas pixel, in metres on the Web-Mercator plane. */
    val metresPerPixel: Double
        get() = (WORLD_METRES / (1 shl level)) / tileSize

    /** Easting of the outer left edge, EPSG:3857 metres. */
    val webMercatorLeft: Double
        get() = -HALF_WORLD_METRES + originTileX * (WORLD_METRES / (1 shl level))

    /** Northing of the outer top edge, EPSG:3857 metres. */
    val webMercatorTop: Double
        get() = HALF_WORLD_METRES - originTileY * (WORLD_METRES / (1 shl level))

    companion object {
        /** Equatorial radius of the WGS-84 sphere used by Web Mercator. */
        const val EARTH_RADIUS_M = 6378137.0
        const val WORLD_METRES = 2 * PI * EARTH_RADIUS_M
        const val HALF_WORLD_METRES = PI * EARTH_RADIUS_M

        /**
         * Fits [bounds] inside a rectangle, preserving aspect ratio.
         *
         * @param detailBoost extra tile levels for print output, where the canvas has
         *        many more pixels than a screen and would otherwise show soft imagery.
         */
        fun fit(
            bounds: GeoBounds,
            left: Float,
            top: Float,
            width: Float,
            height: Float,
            maxLevel: Int,
            detailBoost: Int = 0
        ): MapProjection {
            // World width in canvas pixels needed to make the extent fill the rectangle.
            val lonFraction = ((bounds.maxLon - bounds.minLon) / 360.0).coerceAtLeast(1e-9)
            val worldPxForWidth = width / lonFraction

            val yTop = mercatorY(bounds.maxLat)
            val yBottom = mercatorY(bounds.minLat)
            val latFraction = ((yBottom - yTop) / 1.0).coerceAtLeast(1e-9)
            val worldPxForHeight = height / latFraction

            // The smaller scale is the one that lets the whole extent fit.
            val worldPx = min(worldPxForWidth.toDouble(), worldPxForHeight.toDouble())

            // Smallest level whose native 256 px tiles are at least as detailed as needed.
            val neededLevel = ceil(log2(worldPx / MapMath.TILE_SIZE_PX)).toInt() + detailBoost
            val level = neededLevel.coerceIn(MapMath.MIN_ZOOM.toInt(), maxLevel)

            val tileSize = worldPx / (1 shl level)
            val centerTileX = MapMath.lonToTileX(bounds.centerLon, level)
            val centerTileY = MapMath.latToTileY(bounds.centerLat, level)

            return MapProjection(
                level = level,
                tileSize = tileSize,
                originTileX = centerTileX - (width / 2f) / tileSize,
                originTileY = centerTileY - (height / 2f) / tileSize,
                left = left,
                top = top,
                width = width,
                height = height
            )
        }

        /** Mercator Y as a 0..1 fraction of the world, matching [MapMath.latToTileY]. */
        private fun mercatorY(latitude: Double): Double {
            val clamped = latitude.coerceIn(-MapMath.MAX_LATITUDE, MapMath.MAX_LATITUDE)
            val radians = clamped * PI / 180.0
            return (1.0 - kotlin.math.asinh(kotlin.math.tan(radians)) / PI) / 2.0
        }

        private fun log2(value: Double): Double = ln(value) / ln(2.0)
    }
}
