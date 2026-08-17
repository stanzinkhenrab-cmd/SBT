import XCTest
@testable import SeabuckthornSurvey

/// The identifier rules are the part of this app that must never fail: a duplicate
/// or reused Survey ID would silently corrupt a season of field data.
final class SurveyDatabaseTests: XCTestCase {

    private var database: SurveyDatabase!

    override func setUpWithError() throws {
        database = try SurveyDatabase.inMemory()
    }

    override func tearDown() {
        database = nil
    }

    func testIdentifiersIncrementWithoutDuplicates() throws {
        var ids: [String] = []
        for _ in 1...25 {
            ids.append(try database.insertWithNewSurveyId(Survey()).surveyId)
        }
        XCTAssertEqual(Set(ids).count, ids.count)
        XCTAssertTrue(ids.first?.hasPrefix("SBT-") ?? false)
        XCTAssertEqual(ids.sorted(), ids)
    }

    func testConcurrentStartsNeverCollide() throws {
        let database = self.database!
        var created: [String] = []
        let lock = NSLock()
        let group = DispatchGroup()

        for _ in 0..<12 {
            group.enter()
            DispatchQueue.global().async {
                if let record = try? database.insertWithNewSurveyId(Survey()) {
                    lock.lock()
                    created.append(record.surveyId)
                    lock.unlock()
                }
                group.leave()
            }
        }
        XCTAssertEqual(group.wait(timeout: .now() + 20), .success)
        XCTAssertEqual(created.count, 12)
        XCTAssertEqual(Set(created).count, 12)
    }

    func testIdentifierAlreadyInTheTableIsSkipped() throws {
        let first = try database.insertWithNewSurveyId(Survey())
        // A restored database can hold identifiers beyond the counter; rewinding
        // the counter must not hand out one that already exists.
        let year = Int(first.surveyId.dropFirst(4).prefix(4)) ?? 2026
        try database.resetCounterForTesting(year: year, sequence: 0)

        let second = try database.insertWithNewSurveyId(Survey())
        XCTAssertNotEqual(second.surveyId, first.surveyId)
        XCTAssertNotNil(try database.survey(surveyId: first.surveyId))
    }

    func testDraftsAreSeparateFromSavedRecords() throws {
        let draft = try database.insertWithNewSurveyId(Survey())
        var saved = try database.insertWithNewSurveyId(Survey())
        saved.status = Survey.statusSaved
        try database.update(saved)

        XCTAssertEqual(try database.survey(id: draft.id)?.status, Survey.statusDraft)
        XCTAssertEqual(try database.latestDraft()?.id, draft.id)
        XCTAssertEqual(try database.savedSurveys().count, 1)
        XCTAssertEqual(try database.allSurveys().count, 2)
        XCTAssertEqual(try database.savedCount(), 1)
    }

    func testDeletingARecordLeavesTheRestIntact() throws {
        let first = try database.insertWithNewSurveyId(Survey())
        let second = try database.insertWithNewSurveyId(Survey())

        try database.delete(id: first.id)
        XCTAssertNil(try database.survey(id: first.id))
        XCTAssertNotNil(try database.survey(surveyId: second.surveyId))

        // The counter does not roll back, so a deleted number is never reissued.
        let third = try database.insertWithNewSurveyId(Survey())
        XCTAssertNotEqual(third.surveyId, first.surveyId)
    }

    func testEveryFieldSurvivesARoundTrip() throws {
        var record = try database.insertWithNewSurveyId(Survey())
        record.surveyorName = "Stanzin Khenrab"
        record.designation = "SMS Horticulture"
        record.organization = "KVK Leh"
        record.district = "Leh"
        record.block = "Kharu"
        record.village = "Sakti"
        record.site = "Riverbank plantation"
        record.photoFileName = "\(record.surveyId).jpg"
        record.latitude = 34.1526
        record.longitude = 77.5771
        record.altitude = 3524
        record.accuracyM = 4
        record.locationCapturedAt = Date(millisecondsSince1970: 1_767_225_000_000)
        record.shrubType = "Hardwood"
        record.plantHeight = 2.4
        record.plantHeightUnit = Survey.unitFeet
        record.maturityStage = "Ripe"
        record.harvestDate = Date(millisecondsSince1970: 1_767_225_000_000)
        record.berryDiameterMm = 6.4
        record.tssBrix = 11.2
        record.easeOfHarvest = "Medium"
        record.status = Survey.statusSaved
        try database.update(record)

        let stored = try XCTUnwrap(try database.survey(id: record.id))
        XCTAssertEqual(stored.surveyorName, "Stanzin Khenrab")
        XCTAssertEqual(stored.village, "Sakti")
        XCTAssertEqual(stored.site, "Riverbank plantation")
        XCTAssertEqual(stored.photoFileName, "\(record.surveyId).jpg")
        XCTAssertEqual(stored.latitude ?? 0, 34.1526, accuracy: 0.000001)
        XCTAssertEqual(stored.longitude ?? 0, 77.5771, accuracy: 0.000001)
        XCTAssertEqual(stored.altitude ?? 0, 3524, accuracy: 0.001)
        XCTAssertEqual(stored.accuracyM ?? 0, 4, accuracy: 0.001)
        XCTAssertEqual(stored.shrubType, "Hardwood")
        XCTAssertEqual(stored.plantHeight ?? 0, 2.4, accuracy: 0.000001)
        XCTAssertEqual(stored.plantHeightUnit, Survey.unitFeet)
        XCTAssertEqual(stored.maturityStage, "Ripe")
        XCTAssertEqual(stored.berryDiameterMm ?? 0, 6.4, accuracy: 0.000001)
        XCTAssertEqual(stored.tssBrix ?? 0, 11.2, accuracy: 0.000001)
        XCTAssertEqual(stored.easeOfHarvest, "Medium")
        XCTAssertEqual(stored.status, Survey.statusSaved)
        XCTAssertEqual(
            stored.harvestDate?.millisecondsSince1970,
            record.harvestDate?.millisecondsSince1970
        )
        XCTAssertNil(stored.savedAt)
    }

    func testOptionalFieldsComeBackAsNil() throws {
        let record = try database.insertWithNewSurveyId(Survey())
        let stored = try XCTUnwrap(try database.survey(id: record.id))
        XCTAssertNil(stored.photoFileName)
        XCTAssertNil(stored.latitude)
        XCTAssertNil(stored.longitude)
        XCTAssertNil(stored.altitude)
        XCTAssertNil(stored.shrubType)
        XCTAssertNil(stored.plantHeight)
        XCTAssertNil(stored.maturityStage)
        XCTAssertNil(stored.harvestDate)
        XCTAssertNil(stored.berryDiameterMm)
        XCTAssertNil(stored.tssBrix)
        XCTAssertNil(stored.easeOfHarvest)
        XCTAssertFalse(stored.hasLocation)
    }
}
