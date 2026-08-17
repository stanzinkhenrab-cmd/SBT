import XCTest
@testable import SeabuckthornSurvey

final class SurveyValidatorTests: XCTestCase {

    private func complete() -> Survey {
        var survey = Survey()
        survey.surveyId = "SBT-2026-0001"
        survey.surveyorName = "Stanzin Khenrab"
        survey.designation = "SMS Horticulture"
        survey.organization = "KVK Leh"
        survey.district = "Leh"
        survey.village = "Sakti"
        survey.shrubType = "Hardwood"
        survey.plantHeight = 2.4
        survey.maturityStage = "Ripe"
        return survey
    }

    func testACompleteRecordPasses() {
        XCTAssertTrue(SurveyValidator.validate(complete(), heightText: "2.4").isValid)
    }

    func testRequiredFieldsAreReportedIndividually() {
        let result = SurveyValidator.validate(Survey())
        XCTAssertFalse(result.isValid)
        for field: SurveyField in [
            .surveyorName, .designation, .organization, .district,
            .village, .shrubType, .maturityStage, .plantHeight
        ] {
            XCTAssertNotNil(result.errors[field], "expected an error for \(field)")
        }
    }

    func testAMissingPhotoOrFixIsAWarningNeverABlocker() {
        let result = SurveyValidator.validate(complete(), heightText: "2.4")
        XCTAssertTrue(result.isValid)
        XCTAssertTrue(result.warnings.contains { $0.contains("GPS") })
        XCTAssertTrue(result.warnings.contains { $0.contains("photo") })
    }

    func testHeightIsRejectedWhenNotANumberOrOutOfRange() {
        var noNumber = complete()
        noNumber.plantHeight = nil
        XCTAssertNotNil(
            SurveyValidator.validate(noNumber, heightText: "two").errors[.plantHeight]
        )

        var tooTall = complete()
        tooTall.plantHeight = 400
        XCTAssertNotNil(
            SurveyValidator.validate(tooTall, heightText: "400").errors[.plantHeight]
        )

        var negative = complete()
        negative.plantHeight = -1
        XCTAssertNotNil(
            SurveyValidator.validate(negative, heightText: "-1").errors[.plantHeight]
        )
    }

    func testTheHeightLimitFollowsTheSelectedUnit() {
        var metres = complete()
        metres.plantHeight = 40
        metres.plantHeightUnit = Survey.unitMetre
        XCTAssertNotNil(SurveyValidator.validate(metres, heightText: "40").errors[.plantHeight])

        var feet = complete()
        feet.plantHeight = 40
        feet.plantHeightUnit = Survey.unitFeet
        XCTAssertNil(SurveyValidator.validate(feet, heightText: "40").errors[.plantHeight])
    }

    func testBerryMeasurementsAreOptionalButMustBeSensibleWhenGiven() {
        let blank = SurveyValidator.validate(complete(), heightText: "2.4")
        XCTAssertNil(blank.errors[.berryDiameter])
        XCTAssertNil(blank.errors[.tss])

        var absurd = complete()
        absurd.berryDiameterMm = 900
        absurd.tssBrix = 400
        let result = SurveyValidator.validate(
            absurd,
            heightText: "2.4",
            berryText: "900",
            tssText: "400"
        )
        XCTAssertEqual(result.errors.count, 2)
        XCTAssertNotNil(result.errors[.berryDiameter])
        XCTAssertNotNil(result.errors[.tss])
    }

    func testTheControlledVocabularyMatchesTheAndroidBuild() {
        XCTAssertEqual(SurveyOptions.shrubTypes, ["Hardwood", "Soft wood", "Mixed"])
        XCTAssertEqual(
            SurveyOptions.maturityStages,
            ["Unripe", "Intermediate", "Ripe", "Overripe"]
        )
        XCTAssertEqual(SurveyOptions.easeOfHarvest, ["Easy", "Medium", "Hard"])
        XCTAssertEqual(SurveyOptions.districts, ["Leh", "Kargil"])
        XCTAssertEqual(SurveyOptions.defaultOrganization, "KVK Leh")
        XCTAssertEqual(SurveyOptions.heightUnits, ["m", "ft"])
    }
}
