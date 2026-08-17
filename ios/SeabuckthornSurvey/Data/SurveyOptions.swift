import Foundation

/// Controlled vocabulary for the survey form. Identical to the Android build, so
/// the two datasets combine without any cleaning step.
enum SurveyOptions {

    static let organizations = [
        "KVK Leh",
        "KVK Kargil",
        "Department of Agriculture, Ladakh",
        "Department of Horticulture, Ladakh",
        "DIHAR-DRDO, Leh",
        "SKUAST-Kashmir",
        "LAHDC Leh",
        "LAHDC Kargil"
    ]

    static let defaultOrganization = "KVK Leh"

    static let districts = ["Leh", "Kargil"]

    /// Development blocks by district. Manual entry stays available everywhere.
    static let blocksByDistrict: [String: [String]] = [
        "Leh": [
            "Leh", "Chuchot", "Kharu", "Nyoma", "Durbuk", "Diskit", "Panamik",
            "Khaltse", "Saspol", "Likir", "Lingshed", "Rong", "Kharnak",
            "Nubra", "Turtuk", "Zanskar Road"
        ],
        "Kargil": [
            "Kargil", "Drass", "Sankoo", "Taisuru", "Shargole", "Chiktan",
            "Shakar-Chiktan", "GM Pore", "Padum (Zanskar)", "Lungnak"
        ]
    ]

    /// A starting point for the village field; surveyors may type any name.
    static let commonVillages: [String: [String]] = [
        "Leh": [
            "Choglamsar", "Stok", "Chuchot Yokma", "Chuchot Gongma", "Shey",
            "Thiksey", "Saboo", "Phyang", "Spituk", "Nimmo", "Basgo", "Alchi",
            "Saspol", "Khaltse", "Domkhar", "Skurbuchan", "Achinathang",
            "Diskit", "Hunder", "Sumur", "Panamik", "Turtuk", "Tangtse",
            "Durbuk", "Chushul", "Nyoma", "Hanle", "Igoo", "Chemrey", "Sakti"
        ],
        "Kargil": [
            "Kargil Town", "Baroo", "Poyen", "Minji", "Trespone", "Sankoo",
            "Panikhar", "Parkachik", "Drass", "Shimsha Kharbu", "Mulbekh",
            "Shargole", "Chiktan", "Padum", "Karsha", "Sani", "Stongde"
        ]
    ]

    static let shrubTypes = ["Hardwood", "Soft wood", "Mixed"]

    static let maturityStages = ["Unripe", "Intermediate", "Ripe", "Overripe"]

    static let easeOfHarvest = ["Easy", "Medium", "Hard"]

    static let heightUnits = [Survey.unitMetre, Survey.unitFeet]

    static func heightUnitLabel(_ unit: String) -> String {
        unit == Survey.unitFeet ? "feet (ft)" : "metres (m)"
    }

    static func blocks(for district: String) -> [String] {
        blocksByDistrict[district] ?? []
    }

    static func villages(for district: String) -> [String] {
        commonVillages[district] ?? []
    }
}
