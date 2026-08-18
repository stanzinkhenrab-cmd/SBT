package com.kvkleh.sbtsurvey.domain

/**
 * Administrative reference data for Ladakh.
 *
 * Districts and blocks are kept here rather than in the database so that the list can be
 * extended in a future release without a migration. Both fields also accept free text in
 * the form, so a surveyor is never blocked by a missing entry.
 */
object LadakhAdmin {

    val districts: List<String> = listOf(
        "Leh",
        "Kargil",
        "Nubra",
        "Changthang",
        "Zanskar",
        "Sham",
        "Drass",
        "Sankoo",
        "Other"
    )

    private val blocksByDistrict: Map<String, List<String>> = mapOf(
        "Leh" to listOf("Leh", "Kharu", "Saspol", "Khaltse", "Chuchot", "Nyoma", "Durbuk", "Likir"),
        "Kargil" to listOf("Kargil", "Drass", "Sankoo", "Shakar-Chiktan", "Taisuru", "GM Pore", "Shargole"),
        "Nubra" to listOf("Diskit", "Panamik", "Turtuk", "Sumur", "Bogdang"),
        "Changthang" to listOf("Nyoma", "Durbuk", "Korzok", "Chushul", "Hanle"),
        "Zanskar" to listOf("Padum", "Zanskar", "Sani", "Karsha"),
        "Sham" to listOf("Khaltse", "Saspol", "Nurla", "Domkhar", "Skurbuchan"),
        "Drass" to listOf("Drass", "Mushkoo", "Kaksar"),
        "Sankoo" to listOf("Sankoo", "Suru", "Panikhar")
    )

    /** Suggested blocks for a district; empty when the district is unknown or free-typed. */
    fun blocksFor(district: String?): List<String> =
        blocksByDistrict[district?.trim()].orEmpty()
}
