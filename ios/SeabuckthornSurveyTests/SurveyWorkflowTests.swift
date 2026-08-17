import XCTest
import UIKit
@testable import SeabuckthornSurvey

/// Walks the whole field workflow through the storage layer:
/// new survey → surveyor → location → photo → GPS → plant data → auto-save →
/// save → view → export → delete.
@MainActor
final class SurveyWorkflowTests: XCTestCase {

    private var database: SurveyDatabase!
    private var photoStore: PhotoStore!
    private var preferences: SurveyPreferences!
    private var store: SurveyStore!
    private var root: URL!

    private let profile = SurveyorProfile(
        name: "Stanzin Khenrab",
        designation: "SMS Horticulture",
        organization: "KVK Leh"
    )

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SBTTests-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)

        database = try SurveyDatabase.inMemory()
        photoStore = PhotoStore(root: root)
        // A private suite keeps the test off the simulator's shared defaults.
        let suiteName = "SBTTests-\(UUID().uuidString)"
        preferences = SurveyPreferences(defaults: UserDefaults(suiteName: suiteName)!)
        preferences.profile = profile
        store = SurveyStore(database: database, photoStore: photoStore, preferences: preferences)
    }

    override func tearDownWithError() throws {
        store = nil
        database = nil
        try? FileManager.default.removeItem(at: root)
    }

    func testCompleteFieldWorkflow() throws {
        // New Survey — the record exists on disk before a single field is typed.
        var working = try XCTUnwrap(store.startOrResumeDraft())
        XCTAssertTrue(working.isDraft)
        XCTAssertTrue(working.surveyId.hasPrefix("SBT-"))
        XCTAssertEqual(working.organization, "KVK Leh")
        XCTAssertEqual(store.savedCount, 0)
        XCTAssertEqual(store.draft?.id, working.id)

        // Location, plant data and GPS, written the way auto-save writes them.
        working.district = "Leh"
        working.block = "Kharu"
        working.village = "Sakti"
        working.site = "Riverbank plantation"
        working.latitude = 34.1526
        working.longitude = 77.5771
        working.altitude = 3524
        working.accuracyM = 4
        working.locationCapturedAt = Date()
        working.shrubType = "Hardwood"
        working.plantHeight = 2.4
        working.maturityStage = "Ripe"
        working.harvestDate = Formats.startOfDay(Date())
        working.berryDiameterMm = 6.4
        working.tssBrix = 11.2
        working.easeOfHarvest = "Medium"
        XCTAssertTrue(store.save(working))
        XCTAssertEqual(store.survey(id: working.id)?.village, "Sakti")

        // Photo capture, stored under the Survey ID.
        let image = try makeImage()
        working = try XCTUnwrap(store.attachPhoto(image, to: working))
        XCTAssertEqual(working.photoFileName, "\(working.surveyId).jpg")
        XCTAssertNotNil(store.photoURL(for: working))

        // Review: valid, and nothing missing since every field was filled in.
        let validation = SurveyValidator.validate(working, heightText: "2.4")
        XCTAssertTrue(validation.isValid, "\(validation.errors)")
        XCTAssertTrue(validation.warnings.isEmpty, "\(validation.warnings)")

        // Save.
        let saved = try XCTUnwrap(store.commit(working))
        XCTAssertEqual(saved.status, Survey.statusSaved)
        XCTAssertEqual(store.savedCount, 1)
        XCTAssertNil(store.draft)

        // The surveyor and the location are remembered for the next survey.
        XCTAssertEqual(preferences.profile.name, "Stanzin Khenrab")
        XCTAssertEqual(preferences.lastLocation.district, "Leh")
        let next = try XCTUnwrap(store.startNewDraft())
        XCTAssertEqual(next.surveyorName, "Stanzin Khenrab")
        XCTAssertEqual(next.block, "Kharu")
        XCTAssertNotEqual(next.surveyId, saved.surveyId)

        // View: saved records only, newest first.
        XCTAssertEqual(store.savedSurveys.count, 1)
        XCTAssertEqual(store.savedSurveys.first?.surveyId, saved.surveyId)

        // Export: the saved row carries every value through to the file.
        let rows = store.exportRows(includeDrafts: false)
        XCTAssertEqual(rows.count, 1)
        let csv = try XCTUnwrap(String(data: CSVWriter.data(for: rows), encoding: .utf8))
        let body = csv.hasPrefix("\u{FEFF}") ? String(csv.dropFirst()) : csv
        let lines = body.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: "\r\n")
        XCTAssertEqual(lines.count, 2)

        let header = lines[0].components(separatedBy: ",")
        let values = lines[1].components(separatedBy: ",")
        func cell(_ column: String) throws -> String {
            let index = try XCTUnwrap(header.firstIndex(of: column))
            return values[index]
        }
        XCTAssertEqual(try cell("Survey ID"), saved.surveyId)
        XCTAssertEqual(try cell("Village"), "Sakti")
        XCTAssertEqual(try cell("Latitude"), "34.152600")
        XCTAssertEqual(try cell("Altitude (m)"), "3524.0")
        XCTAssertEqual(try cell("Shrub Type"), "Hardwood")
        XCTAssertEqual(try cell("Plant Height"), "2.4")
        XCTAssertEqual(try cell("Plant Height Unit"), "m")
        XCTAssertEqual(try cell("Dominant Fruit Maturity Stage"), "Ripe")
        XCTAssertEqual(try cell("Berry Diameter (mm)"), "6.4")
        XCTAssertEqual(try cell("TSS (°Brix)"), "11.2")
        XCTAssertEqual(try cell("Ease of Harvest"), "Medium")
        XCTAssertEqual(try cell("Photo Filename"), "\(saved.surveyId).jpg")
        XCTAssertEqual(values.count, SurveyExportRow.headers.count)

        XCTAssertGreaterThan(XLSXWriter.data(for: rows).count, 0)

        // Delete: the record and its image go together, the other survey stays.
        store.delete(saved)
        XCTAssertNil(store.survey(id: saved.id))
        XCTAssertFalse(photoStore.exists(fileName: saved.photoFileName))
        XCTAssertEqual(store.savedCount, 0)
        XCTAssertEqual(store.draft?.surveyId, next.surveyId)
    }

    func testARecordWithoutGpsOrPhotoStillSaves() throws {
        var working = try XCTUnwrap(store.startOrResumeDraft())
        working.district = "Kargil"
        working.village = "Sankoo"
        working.shrubType = "Mixed"
        working.plantHeight = 1.6
        working.maturityStage = "Unripe"
        XCTAssertTrue(store.save(working))

        let validation = SurveyValidator.validate(working, heightText: "1.6")
        XCTAssertTrue(validation.isValid)
        XCTAssertTrue(validation.warnings.contains { $0.contains("GPS") })
        XCTAssertTrue(validation.warnings.contains { $0.contains("photo") })

        var saved = try XCTUnwrap(store.commit(working))
        XCTAssertEqual(saved.status, Survey.statusSaved)
        XCTAssertFalse(saved.hasLocation)

        // Coordinates can be added later without touching anything else.
        saved.latitude = 34.5
        saved.longitude = 76.1
        saved.altitude = 2700
        XCTAssertTrue(store.save(saved))

        let updated = try XCTUnwrap(store.survey(id: saved.id))
        XCTAssertTrue(updated.hasLocation)
        XCTAssertEqual(updated.status, Survey.statusSaved)
        XCTAssertEqual(updated.village, "Sankoo")
    }

    func testRemovingAPhotoClearsBothTheRecordAndTheFile() throws {
        var working = try XCTUnwrap(store.startOrResumeDraft())
        working = try XCTUnwrap(store.attachPhoto(try makeImage(), to: working))
        XCTAssertTrue(photoStore.exists(fileName: working.photoFileName))

        working = try XCTUnwrap(store.removePhoto(from: working))
        XCTAssertNil(working.photoFileName)
        XCTAssertFalse(FileManager.default.fileExists(
            atPath: photoStore.url(for: working.surveyId).path
        ))
        XCTAssertNil(store.survey(id: working.id)?.photoFileName)
    }

    private func makeImage() throws -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 24, height: 18))
        return renderer.image { context in
            UIColor.orange.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 24, height: 18))
        }
    }
}
