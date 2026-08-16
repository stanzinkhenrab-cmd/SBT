package com.kvk.leh.seabuckthorn.data.export

import com.kvk.leh.seabuckthorn.domain.calculations.DescriptiveStatistics
import com.kvk.leh.seabuckthorn.domain.model.SurveyRecord
import com.kvk.leh.seabuckthorn.util.DateUtils
import java.io.File
import java.io.FileWriter

/**
 * Writes every survey (one row per survey, all fields flattened) to a single CSV suitable for
 * Excel, R, Python, SPSS or GIS import. Berry measurements are summarized as mean/min/max/sd
 * here — the full per-berry table is available in the "Fruit Measurements" XLSX sheet.
 */
object CsvExporter {

    fun columns(): List<String> = listOf(
        "survey_id", "survey_code", "is_draft", "survey_date", "survey_time", "surveyor_name",
        "district", "block", "village", "site_name", "land_use_type", "ownership_status", "remarks",
        "latitude", "longitude", "altitude_m", "gps_accuracy_m", "gps_status",
        "shrub_type", "growth_form", "plant_height_m", "canopy_ns_m", "canopy_ew_m",
        "avg_canopy_diameter_m", "canopy_area_m2", "stem_circumference_cm", "num_major_stems",
        "estimated_age_years", "regeneration_status", "presence_of_suckers", "flowering_status", "fruiting_status",
        "overall_health", "pest_incidence", "disease_incidence", "browsing_damage", "mechanical_damage", "drought_stress",
        "dominant_maturity_stage", "pct_unripe", "pct_intermediate", "pct_ripe", "pct_overripe",
        "fifty_pct_maturity_date", "peak_maturity_date", "harvest_initiation_date",
        "berries_measured", "mean_berry_length_mm", "mean_berry_diameter_mm", "mean_berry_weight_g",
        "sd_berry_length_mm", "sd_berry_diameter_mm", "sd_berry_weight_g",
        "fruit_color", "fruit_firmness", "fruit_shape", "berries_per_cluster",
        "tss_brix", "ph", "juice_yield_pct", "titratable_acidity_pct", "vitamin_c_mg_100g", "total_carotenoids_mg_100g",
        "estimated_yield_kg_per_shrub", "fruit_damage_pct",
        "slope_pct", "aspect", "terrain_type", "soil_type", "water_regime", "distance_from_water_m",
        "photo_count"
    )

    fun rowFor(record: SurveyRecord): List<String> {
        val s = record.survey
        val sh = record.shrub
        val ph = record.phenology
        val fq = record.fruitQuality
        val env = record.environment
        val lengthStats = DescriptiveStatistics.summarize(record.berries.mapNotNull { it.lengthMm })
        val widthStats = DescriptiveStatistics.summarize(record.berries.mapNotNull { it.widthMm })
        val weightStats = DescriptiveStatistics.summarize(record.berries.mapNotNull { it.weightG })

        return listOf(
            s.id, s.surveyCode, s.isDraft.toString(), DateUtils.epochDayToDisplay(s.surveyDateEpochDay), s.surveyTime, s.surveyorName,
            s.district, s.block, s.village, s.siteName, s.landUseType, s.ownershipStatus, s.remarks.orEmpty(),
            s.latitude?.toString().orEmpty(), s.longitude?.toString().orEmpty(), s.altitude?.toString().orEmpty(),
            s.gpsAccuracyM?.toString().orEmpty(), s.gpsStatus,
            sh?.shrubType.orEmpty(), sh?.growthForm.orEmpty(), sh?.plantHeightM?.toString().orEmpty(),
            sh?.canopyNsM?.toString().orEmpty(), sh?.canopyEwM?.toString().orEmpty(),
            sh?.avgCanopyDiameterM?.toString().orEmpty(), sh?.canopyAreaM2?.toString().orEmpty(),
            sh?.stemCircumferenceCm?.toString().orEmpty(), sh?.numMajorStems?.toString().orEmpty(),
            sh?.estimatedAgeYears?.toString().orEmpty(), sh?.regenerationStatus.orEmpty(),
            sh?.presenceOfSuckers?.toString().orEmpty(), sh?.floweringStatus?.toString().orEmpty(), sh?.fruitingStatus?.toString().orEmpty(),
            sh?.overallHealth.orEmpty(), sh?.pestIncidence.orEmpty(), sh?.diseaseIncidence.orEmpty(),
            sh?.browsingDamage.orEmpty(), sh?.mechanicalDamage.orEmpty(), sh?.droughtStress.orEmpty(),
            ph?.dominantMaturityStage.orEmpty(), ph?.pctUnripe?.toString().orEmpty(), ph?.pctIntermediate?.toString().orEmpty(),
            ph?.pctRipe?.toString().orEmpty(), ph?.pctOverripe?.toString().orEmpty(),
            DateUtils.epochMillisToDisplayDate(ph?.fiftyPercentMaturityDate), DateUtils.epochMillisToDisplayDate(ph?.peakMaturityDate),
            DateUtils.epochMillisToDisplayDate(ph?.harvestInitiationDate),
            record.berries.size.toString(),
            lengthStats.mean?.toString().orEmpty(), widthStats.mean?.toString().orEmpty(), weightStats.mean?.toString().orEmpty(),
            lengthStats.standardDeviation?.toString().orEmpty(), widthStats.standardDeviation?.toString().orEmpty(), weightStats.standardDeviation?.toString().orEmpty(),
            fq?.fruitColor.orEmpty(), fq?.fruitFirmness.orEmpty(), fq?.fruitShape.orEmpty(), fq?.berriesPerCluster?.toString().orEmpty(),
            fq?.tssBrix?.toString().orEmpty(), fq?.ph?.toString().orEmpty(), fq?.juiceYieldPercent?.toString().orEmpty(),
            fq?.titratableAcidityPercent?.toString().orEmpty(), fq?.vitaminCMgPer100g?.toString().orEmpty(), fq?.totalCarotenoidsMgPer100g?.toString().orEmpty(),
            fq?.estimatedYieldKgPerShrub?.toString().orEmpty(), fq?.fruitDamagePercent?.toString().orEmpty(),
            env?.slopePercent?.toString().orEmpty(), env?.aspect.orEmpty(), env?.terrainType.orEmpty(), env?.soilType.orEmpty(),
            env?.waterRegime.orEmpty(), env?.distanceFromWaterM?.toString().orEmpty(),
            record.photos.size.toString()
        )
    }

    fun export(records: List<SurveyRecord>, outputFile: File) {
        FileWriter(outputFile).use { writer ->
            writer.append(columns().joinToString(",") { csvField(it) }).append("\n")
            records.forEach { record ->
                writer.append(rowFor(record).joinToString(",") { csvField(it) }).append("\n")
            }
        }
    }

    /** Quotes a field only when needed and doubles any embedded quotes, per RFC 4180. */
    fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
