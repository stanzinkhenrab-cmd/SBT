import Foundation

/// The exported table definition. One place decides the column order and the value
/// for every cell, so CSV, Excel and JSON exports can never drift apart - and the
/// columns match the Android build exactly.
enum SurveyExportRow {

    /// Column headers, in export order.
    static let headers = [
        "Survey ID",
        "Surveyor Name",
        "Designation",
        "Organization",
        "Date",
        "Time",
        "District",
        "Block",
        "Village",
        "Site",
        "Photo Filename",
        "Latitude",
        "Longitude",
        "Altitude (m)",
        "GPS Accuracy (m)",
        "Shrub Type",
        "Plant Height",
        "Plant Height Unit",
        "Dominant Fruit Maturity Stage",
        "Harvest Date",
        "Berry Diameter (mm)",
        "TSS (°Brix)",
        "Ease of Harvest",
        "Record Status"
    ]

    /// JSON keys, in the same order as `headers`.
    static let jsonKeys = [
        "survey_id",
        "surveyor_name",
        "designation",
        "organization",
        "date",
        "time",
        "district",
        "block",
        "village",
        "site",
        "photo_filename",
        "latitude",
        "longitude",
        "altitude_m",
        "gps_accuracy_m",
        "shrub_type",
        "plant_height",
        "plant_height_unit",
        "dominant_fruit_maturity_stage",
        "harvest_date",
        "berry_diameter_mm",
        "tss_brix",
        "ease_of_harvest",
        "record_status"
    ]

    /// Column indices that hold numbers. Excel writes these as numeric cells so the
    /// dataset can be charted or averaged without retyping.
    static let numericColumns: Set<Int> = [11, 12, 13, 14, 16, 20, 21]

    static func values(for survey: Survey) -> [String] {
        [
            survey.surveyId,
            survey.surveyorName,
            survey.designation,
            survey.organization,
            Formats.date(survey.createdAt),
            Formats.time(survey.createdAt),
            survey.district,
            survey.block,
            survey.village,
            survey.site,
            survey.photoFileName ?? "",
            Formats.coordinate(survey.latitude),
            Formats.coordinate(survey.longitude),
            Formats.metres(survey.altitude),
            Formats.metres(survey.accuracyM),
            survey.shrubType ?? "",
            Formats.number(survey.plantHeight),
            survey.plantHeight == nil ? "" : survey.plantHeightUnit,
            survey.maturityStage ?? "",
            Formats.date(survey.harvestDate),
            Formats.number(survey.berryDiameterMm),
            Formats.number(survey.tssBrix),
            survey.easeOfHarvest ?? "",
            survey.isDraft ? "Draft" : "Saved"
        ]
    }
}
