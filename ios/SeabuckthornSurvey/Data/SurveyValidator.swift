import Foundation

/// Identifies a field so the form can highlight exactly what is missing.
enum SurveyField: String {
    case surveyorName
    case designation
    case organization
    case district
    case village
    case shrubType
    case plantHeight
    case maturityStage
    case berryDiameter
    case tss
}

/// Field-level rules for a survey record.
///
/// Required fields are the ones a record is meaningless without. Everything else -
/// photo, GPS, harvest date, berry measurements - is reported as a warning on the
/// review screen, because a plot can legitimately be recorded before those numbers
/// exist and the data must never be blocked from being saved.
enum SurveyValidator {

    static let maxHeightMetres = 25.0
    static let maxHeightFeet = 82.0
    static let maxBerryDiameterMm = 40.0
    static let maxTssBrix = 60.0

    struct Result {
        var errors: [SurveyField: String]
        var warnings: [String]

        var isValid: Bool { errors.isEmpty }
    }

    static func validate(
        _ survey: Survey,
        heightText: String = "",
        berryText: String = "",
        tssText: String = ""
    ) -> Result {
        var errors: [SurveyField: String] = [:]
        var warnings: [String] = []

        if survey.surveyorName.isBlank { errors[.surveyorName] = "Surveyor name is required" }
        if survey.designation.isBlank { errors[.designation] = "Designation is required" }
        if survey.organization.isBlank { errors[.organization] = "Organization is required" }
        if survey.district.isBlank { errors[.district] = "District is required" }
        if survey.village.isBlank { errors[.village] = "Village is required" }
        if (survey.shrubType ?? "").isBlank { errors[.shrubType] = "Select a shrub type" }
        if (survey.maturityStage ?? "").isBlank {
            errors[.maturityStage] = "Select the dominant fruit maturity stage"
        }

        let maxHeight = survey.plantHeightUnit == Survey.unitFeet ? maxHeightFeet : maxHeightMetres
        if heightText.isBlank && survey.plantHeight == nil {
            errors[.plantHeight] = "Plant height is required"
        } else if !heightText.isBlank && Formats.parseNumber(heightText) == nil {
            errors[.plantHeight] = "Enter a number, for example 1.8"
        } else if let height = survey.plantHeight, height <= 0 {
            errors[.plantHeight] = "Height must be greater than zero"
        } else if let height = survey.plantHeight, height > maxHeight {
            errors[.plantHeight] =
                "Height looks too large for \(survey.plantHeightUnit) (max \(Formats.number(maxHeight)))"
        }

        validateOptionalNumber(
            text: berryText,
            value: survey.berryDiameterMm,
            max: maxBerryDiameterMm,
            field: .berryDiameter,
            label: "Berry diameter",
            unit: "mm",
            errors: &errors
        )
        validateOptionalNumber(
            text: tssText,
            value: survey.tssBrix,
            max: maxTssBrix,
            field: .tss,
            label: "TSS",
            unit: "°Brix",
            errors: &errors
        )

        if !survey.hasLocation { warnings.append("GPS coordinates were not captured") }
        if (survey.photoFileName ?? "").isBlank { warnings.append("No photo was taken") }
        if survey.harvestDate == nil { warnings.append("Harvest date was not entered") }
        if survey.berryDiameterMm == nil { warnings.append("Berry diameter was not measured") }
        if survey.tssBrix == nil { warnings.append("TSS (°Brix) was not measured") }
        if (survey.easeOfHarvest ?? "").isBlank { warnings.append("Ease of harvest was not selected") }
        if survey.block.isBlank { warnings.append("Block was not entered") }
        if survey.site.isBlank { warnings.append("Site was not entered") }

        return Result(errors: errors, warnings: warnings)
    }

    private static func validateOptionalNumber(
        text: String,
        value: Double?,
        max: Double,
        field: SurveyField,
        label: String,
        unit: String,
        errors: inout [SurveyField: String]
    ) {
        if !text.isBlank && Formats.parseNumber(text) == nil {
            errors[field] = "\(label) must be a number"
            return
        }
        guard let value else { return }
        if value <= 0 {
            errors[field] = "\(label) must be greater than zero"
        } else if value > max {
            errors[field] = "\(label) above \(Formats.number(max)) \(unit) looks like a typing mistake"
        }
    }
}

extension String {
    /// Empty, or only whitespace.
    var isBlank: Bool {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
