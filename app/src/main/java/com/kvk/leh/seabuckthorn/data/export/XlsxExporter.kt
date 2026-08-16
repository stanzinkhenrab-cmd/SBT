package com.kvk.leh.seabuckthorn.data.export

import com.kvk.leh.seabuckthorn.BuildConfig
import com.kvk.leh.seabuckthorn.domain.calculations.DescriptiveStatistics
import com.kvk.leh.seabuckthorn.domain.model.SurveyRecord
import com.kvk.leh.seabuckthorn.util.DateUtils
import java.io.File

/**
 * Builds the full multi-sheet .xlsx workbook: Survey Information, GPS, Shrub Characteristics,
 * Phenology, Fruit Measurements (per-berry), Fruit Quality, Environmental Parameters, Photos,
 * and Metadata — matching the structure field researchers expect for downstream analysis.
 */
object XlsxExporter {

    fun export(records: List<SurveyRecord>, outputFile: File) {
        val writer = XlsxWriter()

        writer.addSheet(
            "Survey Information",
            listOf("Survey ID", "Survey Code", "Draft", "Date", "Time", "Surveyor", "District", "Block", "Village", "Site", "Land Use", "Ownership", "Remarks"),
            records.map { r ->
                val s = r.survey
                listOf(s.id, s.surveyCode, s.isDraft.toString(), DateUtils.epochDayToDisplay(s.surveyDateEpochDay), s.surveyTime, s.surveyorName, s.district, s.block, s.village, s.siteName, s.landUseType, s.ownershipStatus, s.remarks.orEmpty())
            }
        )

        writer.addSheet(
            "GPS",
            listOf("Survey Code", "Latitude", "Longitude", "Altitude (m)", "Accuracy (m)", "Status", "Acquired At"),
            records.map { r ->
                val s = r.survey
                listOf(s.surveyCode, s.latitude, s.longitude, s.altitude, s.gpsAccuracyM?.toDouble(), s.gpsStatus, s.gpsAcquiredAt?.let { DateUtils.epochMillisToDisplayDateTime(it) }.orEmpty())
            }
        )

        writer.addSheet(
            "Shrub Characteristics",
            listOf(
                "Survey Code", "Shrub Type", "Growth Form", "Height (m)", "Canopy N-S (m)", "Canopy E-W (m)",
                "Avg Diameter (m)", "Canopy Area (m2)", "Stem Circumference (cm)", "Major Stems", "Estimated Age (yr)",
                "Density (per ha)", "Regeneration", "Suckers", "Flowering", "Fruiting",
                "Overall Health", "Pest Incidence", "Disease Incidence", "Browsing Damage", "Mechanical Damage", "Drought Stress"
            ),
            records.map { r ->
                val sh = r.shrub
                listOf(
                    r.survey.surveyCode, sh?.shrubType, sh?.growthForm, sh?.plantHeightM, sh?.canopyNsM, sh?.canopyEwM,
                    sh?.avgCanopyDiameterM, sh?.canopyAreaM2, sh?.stemCircumferenceCm, sh?.numMajorStems, sh?.estimatedAgeYears,
                    sh?.plantDensityPerHa, sh?.regenerationStatus, sh?.presenceOfSuckers?.toString(), sh?.floweringStatus?.toString(), sh?.fruitingStatus?.toString(),
                    sh?.overallHealth, sh?.pestIncidence, sh?.diseaseIncidence, sh?.browsingDamage, sh?.mechanicalDamage, sh?.droughtStress
                )
            }
        )

        writer.addSheet(
            "Phenology",
            listOf(
                "Survey Code", "Dominant Stage", "% Unripe", "% Intermediate", "% Ripe", "% Overripe",
                "Flowering Initiation", "Flowering Peak", "Fruit Set", "First Colour Change",
                "First Maturity", "50% Maturity", "Peak Maturity", "Harvest Initiation", "Est. Full Maturity"
            ),
            records.map { r ->
                val p = r.phenology
                listOf(
                    r.survey.surveyCode, p?.dominantMaturityStage, p?.pctUnripe, p?.pctIntermediate, p?.pctRipe, p?.pctOverripe,
                    DateUtils.epochMillisToDisplayDate(p?.floweringInitiationDate), DateUtils.epochMillisToDisplayDate(p?.floweringPeakDate),
                    DateUtils.epochMillisToDisplayDate(p?.fruitSetDate), DateUtils.epochMillisToDisplayDate(p?.firstColorChangeDate),
                    DateUtils.epochMillisToDisplayDate(p?.firstMaturityDate), DateUtils.epochMillisToDisplayDate(p?.fiftyPercentMaturityDate),
                    DateUtils.epochMillisToDisplayDate(p?.peakMaturityDate), DateUtils.epochMillisToDisplayDate(p?.harvestInitiationDate),
                    DateUtils.epochMillisToDisplayDate(p?.estimatedFullMaturityDate)
                )
            }
        )

        val berryRows = records.flatMap { r ->
            r.berries.map { b ->
                listOf(r.survey.surveyCode, b.berryIndex, b.lengthMm, b.widthMm, b.weightG)
            }
        }
        writer.addSheet("Fruit Measurements", listOf("Survey Code", "Berry #", "Length (mm)", "Diameter (mm)", "Weight (g)"), berryRows)

        writer.addSheet(
            "Fruit Quality",
            listOf(
                "Survey Code", "Colour", "Firmness", "Shape", "Berries/Cluster",
                "Mean Length (mm)", "Mean Diameter (mm)", "Mean Weight (g)", "SD Length", "SD Diameter", "SD Weight",
                "TSS (°Brix)", "pH", "Juice Yield (%)", "Titratable Acidity (%)", "Vitamin C (mg/100g)", "Carotenoids (mg/100g)",
                "Fruits/Branch", "Fruits/Cluster", "Est. Yield (kg/shrub)", "Fruit-bearing Branch (%)", "Colour Intensity", "Detachment Ease", "Fruit Damage (%)"
            ),
            records.map { r ->
                val fq = r.fruitQuality
                val lengthStats = DescriptiveStatistics.summarize(r.berries.mapNotNull { it.lengthMm })
                val widthStats = DescriptiveStatistics.summarize(r.berries.mapNotNull { it.widthMm })
                val weightStats = DescriptiveStatistics.summarize(r.berries.mapNotNull { it.weightG })
                listOf(
                    r.survey.surveyCode, fq?.fruitColor, fq?.fruitFirmness, fq?.fruitShape, fq?.berriesPerCluster,
                    lengthStats.mean, widthStats.mean, weightStats.mean, lengthStats.standardDeviation, widthStats.standardDeviation, weightStats.standardDeviation,
                    fq?.tssBrix, fq?.ph, fq?.juiceYieldPercent, fq?.titratableAcidityPercent, fq?.vitaminCMgPer100g, fq?.totalCarotenoidsMgPer100g,
                    fq?.fruitsPerBranch, fq?.fruitsPerCluster, fq?.estimatedYieldKgPerShrub, fq?.fruitBearingBranchPercent, fq?.berryColorIntensity, fq?.berryDetachmentEase, fq?.fruitDamagePercent
                )
            }
        )

        writer.addSheet(
            "Environmental Parameters",
            listOf("Survey Code", "Slope (%)", "Aspect", "Terrain", "Soil Type", "Soil Texture", "Soil Moisture", "Water Regime", "River", "Distance to Water (m)", "Grazing Intensity", "Land Use History"),
            records.map { r ->
                val e = r.environment
                listOf(r.survey.surveyCode, e?.slopePercent, e?.aspect, e?.terrainType, e?.soilType, e?.soilSurfaceTexture, e?.soilMoistureClass, e?.waterRegime, e?.riverName, e?.distanceFromWaterM, e?.grazingIntensity, e?.landUseHistory)
            }
        )

        val photoRows = records.flatMap { r ->
            r.photos.map { p ->
                listOf(r.survey.surveyCode, p.photoNumber, p.category, DateUtils.epochMillisToDisplayDateTime(p.capturedAt), p.latitude, p.longitude, p.altitude, p.isWatermarked.toString(), p.filePath)
            }
        }
        writer.addSheet("Photos", listOf("Survey Code", "Photo #", "Category", "Captured At", "Latitude", "Longitude", "Altitude", "Watermarked", "File Path"), photoRows)

        writer.addSheet(
            "Metadata",
            listOf("Field", "Value"),
            listOf(
                listOf("Application", "Seabuckthorn Field Survey - Ladakh"),
                listOf("App Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
                listOf("Developer", "Stanzin Khenrab, Krishi Vigyan Kendra - Leh, Ladakh (MIDH-SBM)"),
                listOf("Export Generated At", DateUtils.epochMillisToDisplayDateTime(System.currentTimeMillis())),
                listOf("Total Survey Records", records.size),
                listOf("Total Photographs", records.sumOf { it.photos.size }),
                listOf("Data Collection Mode", "100% offline field data collection")
            )
        )

        writer.writeTo(outputFile)
    }
}
