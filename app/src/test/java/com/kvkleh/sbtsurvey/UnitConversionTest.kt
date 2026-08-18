package com.kvkleh.sbtsurvey

import com.kvkleh.sbtsurvey.domain.HeightUnit
import com.kvkleh.sbtsurvey.domain.toFeet
import com.kvkleh.sbtsurvey.domain.toMetres
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConversionTest {

    @Test
    fun `metres are unchanged when already in metres`() {
        assertEquals(2.4, 2.4.toMetres(HeightUnit.METRE), 1e-9)
    }

    @Test
    fun `feet convert to metres`() {
        assertEquals(1.0, 3.280839895013123.toMetres(HeightUnit.FOOT), 1e-9)
    }

    @Test
    fun `metres convert to feet`() {
        assertEquals(3.280839895013123, 1.0.toFeet(HeightUnit.METRE), 1e-9)
    }

    @Test
    fun `round trip keeps the value`() {
        val original = 2.4
        val asFeet = original.toFeet(HeightUnit.METRE)
        assertEquals(original, asFeet.toMetres(HeightUnit.FOOT), 1e-9)
    }
}
