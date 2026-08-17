import Foundation

/// A single seabuckthorn field observation.
///
/// The fields, their names and their stored values mirror the Android app exactly,
/// so a dataset collected on either platform is the same dataset.
struct Survey: Identifiable, Equatable {

    static let statusDraft = "DRAFT"
    static let statusSaved = "SAVED"

    static let unitMetre = "m"
    static let unitFeet = "ft"

    /// Local row id. Zero until the record has been inserted.
    var id: Int64 = 0

    /// Human readable identifier, e.g. `SBT-2026-0001`. Unique for the life of the install.
    var surveyId: String = ""

    var status: String = Survey.statusDraft

    /// Creation instant, captured automatically. Doubles as the survey date and time.
    var createdAt: Date = Date()
    var updatedAt: Date = Date()
    /// Instant the record was promoted from draft to saved; `nil` while still a draft.
    var savedAt: Date?

    // 0. Surveyor ---------------------------------------------------------
    var surveyorName: String = ""
    var designation: String = ""
    var organization: String = ""

    // 2. Location ---------------------------------------------------------
    var district: String = ""
    var block: String = ""
    var village: String = ""
    var site: String = ""

    // 3. Photo ------------------------------------------------------------
    /// File name only, always `<surveyId>.jpg`.
    ///
    /// Deliberately not an absolute path: an iOS app container is re-created with a
    /// new UUID on reinstall and on some restores, so a stored absolute path would
    /// silently stop resolving. The folder is looked up at read time instead.
    var photoFileName: String?

    // 4. GPS --------------------------------------------------------------
    var latitude: Double?
    var longitude: Double?
    /// Altitude in metres.
    var altitude: Double?
    var accuracyM: Double?
    var locationCapturedAt: Date?

    // 5. Shrub type -------------------------------------------------------
    var shrubType: String?

    // 6. Plant height -----------------------------------------------------
    var plantHeight: Double?
    var plantHeightUnit: String = Survey.unitMetre

    // 7. Maturity ---------------------------------------------------------
    var maturityStage: String?
    /// Harvest date at local midnight.
    var harvestDate: Date?

    // 8. Berry characteristics -------------------------------------------
    var berryDiameterMm: Double?
    var tssBrix: Double?

    // 9. Ease of harvest --------------------------------------------------
    var easeOfHarvest: String?

    var isDraft: Bool { status == Survey.statusDraft }

    var hasLocation: Bool { latitude != nil && longitude != nil }

    /// Formats a per-year sequence number as `SBT-YYYY-NNNN`.
    static func formatSurveyId(year: Int, sequence: Int) -> String {
        String(format: "SBT-%04d-%04d", year, sequence)
    }
}
