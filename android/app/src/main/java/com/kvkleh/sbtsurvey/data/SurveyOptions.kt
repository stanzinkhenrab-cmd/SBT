package com.kvkleh.sbtsurvey.data

import com.kvkleh.sbtsurvey.data.db.SurveyEntity

/**
 * Controlled vocabulary for the survey form. Every list here is also the exact set
 * of values written to the database and to the exported files, so that the dataset
 * stays analysable without any cleaning step.
 */
object SurveyOptions {

    val organizations = listOf(
        "KVK Leh",
        "KVK Kargil",
        "Department of Agriculture, Ladakh",
        "Department of Horticulture, Ladakh",
        "DIHAR-DRDO, Leh",
        "SKUAST-Kashmir",
        "LAHDC Leh",
        "LAHDC Kargil"
    )

    const val DEFAULT_ORGANIZATION = "KVK Leh"

    val districts = listOf("Leh", "Kargil")

    /** Development blocks, by district. Manual entry stays available everywhere. */
    val blocksByDistrict: Map<String, List<String>> = mapOf(
        "Leh" to listOf(
            "Leh", "Chuchot", "Kharu", "Nyoma", "Durbuk", "Diskit", "Panamik",
            "Khaltse", "Saspol", "Likir", "Lingshed", "Rong", "Kharnak",
            "Nubra", "Turtuk", "Zanskar Road"
        ),
        "Kargil" to listOf(
            "Kargil", "Drass", "Sankoo", "Taisuru", "Shargole", "Chiktan",
            "Shakar-Chiktan", "GM Pore", "Padum (Zanskar)", "Lungnak"
        )
    )

    /** A starting point for the village dropdown; surveyors may type any name. */
    val commonVillages: Map<String, List<String>> = mapOf(
        "Leh" to listOf(
            "Choglamsar", "Stok", "Chuchot Yokma", "Chuchot Gongma", "Shey",
            "Thiksey", "Saboo", "Phyang", "Spituk", "Nimmo", "Basgo", "Alchi",
            "Saspol", "Khaltse", "Domkhar", "Skurbuchan", "Achinathang",
            "Diskit", "Hunder", "Sumur", "Panamik", "Turtuk", "Tangtse",
            "Durbuk", "Chushul", "Nyoma", "Hanle", "Igoo", "Chemrey", "Sakti"
        ),
        "Kargil" to listOf(
            "Kargil Town", "Baroo", "Poyen", "Minji", "Trespone", "Sankoo",
            "Panikhar", "Parkachik", "Drass", "Shimsha Kharbu", "Mulbekh",
            "Shargole", "Chiktan", "Padum", "Karsha", "Sani", "Stongde"
        )
    )

    val shrubTypes = listOf("Hardwood", "Soft wood", "Mixed")

    val maturityStages = listOf("Unripe", "Intermediate", "Ripe", "Overripe")

    val easeOfHarvest = listOf("Easy", "Medium", "Hard")

    val heightUnits = listOf(SurveyEntity.UNIT_METRE, SurveyEntity.UNIT_FEET)

    fun heightUnitLabel(unit: String): String = when (unit) {
        SurveyEntity.UNIT_FEET -> "feet (ft)"
        else -> "metres (m)"
    }

    fun blocksFor(district: String): List<String> =
        blocksByDistrict[district] ?: emptyList()

    fun villagesFor(district: String): List<String> =
        commonVillages[district] ?: emptyList()
}
