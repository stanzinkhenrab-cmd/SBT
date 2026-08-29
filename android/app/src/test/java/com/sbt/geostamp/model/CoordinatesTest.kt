package com.sbt.geostamp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {

    @Test
    fun `decimal degrees parse with and without a sign`() {
        assertEquals(34.9986, Coordinates.parseLatitude("34.9986")!!, TOLERANCE)
        assertEquals(-34.9986, Coordinates.parseLatitude("-34.9986")!!, TOLERANCE)
        assertEquals(77.383358, Coordinates.parseLongitude(" 77.383358 ")!!, TOLERANCE)
        assertEquals(-118.0, Coordinates.parseLongitude("-118")!!, TOLERANCE)
    }

    @Test
    fun `hemisphere letters set the sign from either side`() {
        assertEquals(-34.9986, Coordinates.parseLatitude("34.9986 S")!!, TOLERANCE)
        assertEquals(-34.9986, Coordinates.parseLatitude("S34.9986")!!, TOLERANCE)
        assertEquals(34.9986, Coordinates.parseLatitude("34.9986N")!!, TOLERANCE)
        assertEquals(-77.383358, Coordinates.parseLongitude("77.383358 w")!!, TOLERANCE)
    }

    @Test
    fun `degrees minutes seconds parse`() {
        assertEquals(34.998611, Coordinates.parseLatitude("""34°59'55.0"N""")!!, TOLERANCE)
        assertEquals(34.998611, Coordinates.parseLatitude("34 59 55.0 N")!!, TOLERANCE)
        assertEquals(-34.5, Coordinates.parseLatitude("""34°30'S""")!!, TOLERANCE)
    }

    @Test
    fun `out of range values are refused`() {
        assertNull(Coordinates.parseLatitude("90.1"))
        assertNull(Coordinates.parseLatitude("-91"))
        assertNull(Coordinates.parseLongitude("180.5"))
        assertEquals(90.0, Coordinates.parseLatitude("90")!!, TOLERANCE)
        assertEquals(-180.0, Coordinates.parseLongitude("-180")!!, TOLERANCE)
    }

    @Test
    fun `nonsense is refused rather than guessed`() {
        assertNull(Coordinates.parseLatitude(""))
        assertNull(Coordinates.parseLatitude("north"))
        assertNull(Coordinates.parseLatitude("-34 N"))          // sign contradicts the letter
        assertNull(Coordinates.parseLatitude("34 E"))           // wrong axis
        assertNull(Coordinates.parseLongitude("77 N"))          // wrong axis
        assertNull(Coordinates.parseLatitude("34 75 00"))       // 75 minutes
        assertNull(Coordinates.parseLatitude("34.5 30 00"))     // fractional degrees plus minutes
        assertNull(Coordinates.parseLatitude("34 30 00 12"))    // too many parts
    }

    @Test
    fun `a pasted pair splits into both halves`() {
        val comma = Coordinates.parsePair("34.998600, 77.383358")
        assertNotNull(comma)
        assertEquals(34.998600, comma!!.first, TOLERANCE)
        assertEquals(77.383358, comma.second, TOLERANCE)

        val spaced = Coordinates.parsePair("34.998600 77.383358")
        assertNotNull(spaced)
        assertEquals(77.383358, spaced!!.second, TOLERANCE)

        val signed = Coordinates.parsePair("-33.8688, 151.2093")
        assertEquals(-33.8688, signed!!.first, TOLERANCE)
    }

    @Test
    fun `a single value is never mistaken for a pair`() {
        assertNull(Coordinates.parsePair("34.998600"))
        // A decimal comma would otherwise read as "34 degrees, 9986 degrees".
        assertNull(Coordinates.parsePair("34,9986"))
        assertNull(Coordinates.parsePair(""))
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
