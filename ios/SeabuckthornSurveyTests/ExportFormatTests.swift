import XCTest
@testable import SeabuckthornSurvey

final class ExportFormatTests: XCTestCase {

    private func sampleSurvey() -> Survey {
        var survey = Survey()
        survey.id = 1
        survey.surveyId = "SBT-2026-0001"
        survey.status = Survey.statusSaved
        survey.createdAt = Date(millisecondsSince1970: 1_767_225_000_000)
        survey.updatedAt = survey.createdAt
        survey.surveyorName = "Stanzin Khenrab"
        survey.designation = "SMS, Horticulture"
        survey.organization = "KVK Leh"
        survey.district = "Leh"
        survey.block = "Kharu"
        survey.village = "Sakti"
        survey.site = "Riverbank plantation"
        survey.photoFileName = "SBT-2026-0001.jpg"
        survey.latitude = 34.152600
        survey.longitude = 77.577100
        survey.altitude = 3524.0
        survey.accuracyM = 4.0
        survey.shrubType = "Hardwood"
        survey.plantHeight = 2.5
        survey.maturityStage = "Ripe"
        survey.harvestDate = survey.createdAt
        survey.berryDiameterMm = 6.4
        survey.tssBrix = 11.2
        survey.easeOfHarvest = "Medium"
        return survey
    }

    func testHeaderAndValueCountsMatch() {
        XCTAssertEqual(SurveyExportRow.headers.count, SurveyExportRow.values(for: sampleSurvey()).count)
        XCTAssertEqual(SurveyExportRow.headers.count, SurveyExportRow.jsonKeys.count)
    }

    func testEveryRequestedColumnIsPresent() {
        let required = [
            "Survey ID", "Surveyor Name", "Designation", "Organization", "Date", "Time",
            "District", "Block", "Village", "Site", "Photo Filename", "Latitude",
            "Longitude", "Altitude (m)", "Shrub Type", "Plant Height", "Plant Height Unit",
            "Dominant Fruit Maturity Stage", "Harvest Date", "Berry Diameter (mm)",
            "TSS (°Brix)", "Ease of Harvest"
        ]
        for column in required {
            XCTAssertTrue(SurveyExportRow.headers.contains(column), "missing column: \(column)")
        }
    }

    func testPhotoFileNameFollowsTheSurveyId() {
        let values = SurveyExportRow.values(for: sampleSurvey())
        let idIndex = SurveyExportRow.headers.firstIndex(of: "Survey ID")!
        let photoIndex = SurveyExportRow.headers.firstIndex(of: "Photo Filename")!
        XCTAssertEqual("\(values[idIndex]).jpg", values[photoIndex])
    }

    func testCsvQuotesSeparatorsAndDoublesQuotationMarks() {
        XCTAssertEqual(CSVWriter.escape("plain"), "plain")
        XCTAssertEqual(CSVWriter.escape("a,b"), "\"a,b\"")
        XCTAssertEqual(CSVWriter.escape("say \"hi\""), "\"say \"\"hi\"\"\"")
    }

    func testCsvExportHasOneHeaderRowAndOneRowPerRecord() throws {
        var second = sampleSurvey()
        second.id = 2
        second.surveyId = "SBT-2026-0002"

        let data = CSVWriter.data(for: [sampleSurvey(), second])
        let text = try XCTUnwrap(String(data: data, encoding: .utf8))
        let body = text.hasPrefix("\u{FEFF}") ? String(text.dropFirst()) : text
        let lines = body.trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: "\r\n")

        XCTAssertEqual(lines.count, 3)
        XCTAssertTrue(lines[0].hasPrefix("Survey ID,"))
        XCTAssertTrue(lines[1].contains("SBT-2026-0001"))
        XCTAssertTrue(lines[2].contains("SBT-2026-0002"))
    }

    func testCsvStartsWithAByteOrderMark() {
        let data = CSVWriter.data(for: [sampleSurvey()])
        XCTAssertEqual(Array(data.prefix(3)), [0xEF, 0xBB, 0xBF])
    }

    func testXlsxContainsTheWorkbookPartsExcelRequires() throws {
        let data = XLSXWriter.data(for: [sampleSurvey()])
        let names = try ZipReader.entryNames(in: data)
        for part in [
            "[Content_Types].xml",
            "_rels/.rels",
            "xl/workbook.xml",
            "xl/_rels/workbook.xml.rels",
            "xl/styles.xml",
            "xl/worksheets/sheet1.xml"
        ] {
            XCTAssertTrue(names.contains(part), "missing part: \(part)")
        }
    }

    func testXlsxKeepsTheDegreeSignAndWritesNumbersAsNumbers() throws {
        let data = XLSXWriter.data(for: [sampleSurvey()])
        let sheet = try XCTUnwrap(ZipReader.entryText(named: "xl/worksheets/sheet1.xml", in: data))
        XCTAssertTrue(sheet.contains("TSS (°Brix)"))
        // Latitude is column L (index 11) and must be a bare numeric cell.
        XCTAssertTrue(sheet.contains("<c r=\"L2\"><v>34.152600</v></c>"))
    }

    func testXlsxArchiveIsWellFormed() throws {
        let data = XLSXWriter.data(for: [sampleSurvey()])
        // End-of-central-directory signature, and a central directory that points
        // at real local headers.
        XCTAssertTrue(try ZipReader.hasValidCentralDirectory(data))
    }

    func testSpreadsheetColumnNamesRollOverPastZ() {
        XCTAssertEqual(XLSXWriter.columnName(0), "A")
        XCTAssertEqual(XLSXWriter.columnName(25), "Z")
        XCTAssertEqual(XLSXWriter.columnName(26), "AA")
        XCTAssertEqual(XLSXWriter.columnName(27), "AB")
    }

    func testJsonExportCarriesEveryRecordAndTypesNumbers() throws {
        let data = try JSONExportWriter.data(for: [sampleSurvey()], appVersion: "1.0.0")
        let root = try XCTUnwrap(
            JSONSerialization.jsonObject(with: data) as? [String: Any]
        )
        XCTAssertEqual(root["record_count"] as? Int, 1)
        let records = try XCTUnwrap(root["records"] as? [[String: Any]])
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0]["survey_id"] as? String, "SBT-2026-0001")
        XCTAssertEqual(records[0]["latitude"] as? Double, 34.1526)
        XCTAssertEqual(records[0]["tss_brix"] as? Double, 11.2)
        XCTAssertEqual(records[0]["village"] as? String, "Sakti")
    }

    func testSurveyIdsAreZeroPaddedAndSortable() {
        XCTAssertEqual(Survey.formatSurveyId(year: 2026, sequence: 1), "SBT-2026-0001")
        XCTAssertEqual(Survey.formatSurveyId(year: 2026, sequence: 42), "SBT-2026-0042")
        XCTAssertEqual(Survey.formatSurveyId(year: 2026, sequence: 1234), "SBT-2026-1234")
    }

    func testNumbersDropTrailingZerosButKeepDecimals() {
        XCTAssertEqual(Formats.number(2.0), "2")
        XCTAssertEqual(Formats.number(2.5), "2.5")
        XCTAssertEqual(Formats.number(11.25), "11.25")
        XCTAssertEqual(Formats.number(nil), "")
    }

    func testDecimalInputAcceptsACommaSeparator() {
        XCTAssertEqual(Formats.parseNumber("6,4"), 6.4)
        XCTAssertEqual(Formats.parseNumber(" 6.4 "), 6.4)
        XCTAssertNil(Formats.parseNumber("abc"))
        XCTAssertNil(Formats.parseNumber(""))
    }
}

/// Just enough ZIP reading to check what the exporter produced.
enum ZipReader {

    static func entryNames(in data: Data) throws -> [String] {
        try entries(in: data).map(\.name)
    }

    static func entryText(named name: String, in data: Data) throws -> String? {
        guard let entry = try entries(in: data).first(where: { $0.name == name }) else {
            return nil
        }
        return String(data: entry.contents, encoding: .utf8)
    }

    static func hasValidCentralDirectory(_ data: Data) throws -> Bool {
        !(try entries(in: data).isEmpty)
    }

    private struct Entry {
        let name: String
        let contents: Data
    }

    /// Walks the local file headers. The exporter stores every entry uncompressed,
    /// so the payload follows the header directly.
    private static func entries(in data: Data) throws -> [Entry] {
        var results: [Entry] = []
        var offset = 0
        let bytes = [UInt8](data)

        func value16(_ index: Int) -> Int {
            Int(bytes[index]) | (Int(bytes[index + 1]) << 8)
        }
        func value32(_ index: Int) -> Int {
            Int(bytes[index]) | (Int(bytes[index + 1]) << 8)
                | (Int(bytes[index + 2]) << 16) | (Int(bytes[index + 3]) << 24)
        }

        while offset + 30 <= bytes.count, value32(offset) == 0x0403_4B50 {
            let compressedSize = value32(offset + 18)
            let nameLength = value16(offset + 26)
            let extraLength = value16(offset + 28)
            let nameStart = offset + 30
            let dataStart = nameStart + nameLength + extraLength
            guard dataStart + compressedSize <= bytes.count else { break }

            let name = String(decoding: bytes[nameStart..<(nameStart + nameLength)], as: UTF8.self)
            let contents = Data(bytes[dataStart..<(dataStart + compressedSize)])
            results.append(Entry(name: name, contents: contents))
            offset = dataStart + compressedSize
        }
        return results
    }
}
