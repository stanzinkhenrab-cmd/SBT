package com.kvkleh.sbtsurvey

import com.kvkleh.sbtsurvey.map.MapMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMathTest {

    @Test
    fun `projection round trips for a Ladakh coordinate`() {
        val lat = 34.152588
        val lon = 77.577049
        val zoom = 12

        val tileX = MapMath.lonToTileX(lon, zoom)
        val tileY = MapMath.latToTileY(lat, zoom)

        assertEquals(lon, MapMath.tileXToLon(tileX, zoom), 1e-6)
        assertEquals(lat, MapMath.tileYToLat(tileY, zoom), 1e-6)
    }

    @Test
    fun `tile x wraps around the antimeridian`() {
        assertEquals(0, MapMath.normaliseTileX(4, 2))
        assertEquals(3, MapMath.normaliseTileX(-1, 2))
    }

    @Test
    fun `distance between Leh and Kargil is roughly 220 km`() {
        val metres = MapMath.distanceMetres(34.152588, 77.577049, 34.5539, 76.1349)
        assertTrue("was $metres", metres in 120_000.0..250_000.0)
    }
}
