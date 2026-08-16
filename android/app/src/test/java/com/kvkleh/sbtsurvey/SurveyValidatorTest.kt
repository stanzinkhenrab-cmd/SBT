package com.kvkleh.sbtsurvey

import com.kvkleh.sbtsurvey.data.SurveyField
import com.kvkleh.sbtsurvey.data.SurveyValidator
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyValidatorTest {

    private fun complete() = SurveyEntity(
        surveyId = "SBT-2026-0001",
        surveyorName = "Stanzin Khenrab",
        designation = "SMS Horticulture",
        organization = "KVK Leh",
        district = "Leh",
        village = "Sakti",
        shrubType = "Hardwood",
        plantHeight = 2.4,
        maturityStage = "Ripe"
    )

    @Test
    fun `a complete record passes`() {
        assertTrue(SurveyValidator.validate(complete(), heightText = "2.4").isValid)
    }

    @Test
    fun `required fields are reported individually`() {
        val result = SurveyValidator.validate(SurveyEntity())
        assertFalse(result.isValid)
        listOf(
            SurveyField.SURVEYOR_NAME,
            SurveyField.DESIGNATION,
            SurveyField.ORGANIZATION,
            SurveyField.DISTRICT,
            SurveyField.VILLAGE,
            SurveyField.SHRUB_TYPE,
            SurveyField.MATURITY_STAGE,
            SurveyField.PLANT_HEIGHT
        ).forEach { field ->
            assertTrue("expected an error for $field", result.errors.containsKey(field))
        }
    }

    @Test
    fun `a missing photo or fix is a warning, never a blocker`() {
        val result = SurveyValidator.validate(complete(), heightText = "2.4")
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("GPS") })
        assertTrue(result.warnings.any { it.contains("photo") })
    }

    @Test
    fun `height is rejected when it is not a number or out of range`() {
        val text = SurveyValidator.validate(complete().copy(plantHeight = null), heightText = "two")
        assertTrue(text.errors.containsKey(SurveyField.PLANT_HEIGHT))

        val tooTall = SurveyValidator.validate(complete().copy(plantHeight = 400.0), heightText = "400")
        assertTrue(tooTall.errors.containsKey(SurveyField.PLANT_HEIGHT))

        val negative = SurveyValidator.validate(complete().copy(plantHeight = -1.0), heightText = "-1")
        assertTrue(negative.errors.containsKey(SurveyField.PLANT_HEIGHT))
    }

    @Test
    fun `the height limit follows the selected unit`() {
        val metres = complete().copy(plantHeight = 40.0, plantHeightUnit = SurveyEntity.UNIT_METRE)
        assertTrue(SurveyValidator.validate(metres, heightText = "40").errors.containsKey(SurveyField.PLANT_HEIGHT))

        val feet = complete().copy(plantHeight = 40.0, plantHeightUnit = SurveyEntity.UNIT_FEET)
        assertFalse(SurveyValidator.validate(feet, heightText = "40").errors.containsKey(SurveyField.PLANT_HEIGHT))
    }

    @Test
    fun `berry measurements are optional but must be sensible when given`() {
        val blank = SurveyValidator.validate(complete(), heightText = "2.4")
        assertFalse(blank.errors.containsKey(SurveyField.BERRY_DIAMETER))
        assertFalse(blank.errors.containsKey(SurveyField.TSS))

        val absurd = SurveyValidator.validate(
            complete().copy(berryDiameterMm = 900.0, tssBrix = 400.0),
            heightText = "2.4",
            berryText = "900",
            tssText = "400"
        )
        assertEquals(2, absurd.errors.size)
        assertTrue(absurd.errors.containsKey(SurveyField.BERRY_DIAMETER))
        assertTrue(absurd.errors.containsKey(SurveyField.TSS))
    }
}
