import Foundation

/// Writes a real `.xlsx` workbook.
///
/// An OOXML workbook is a ZIP of a handful of XML parts. Foundation has no archive
/// writer, so the ZIP is assembled here with stored (uncompressed) entries: the
/// export stays a few kilobytes of code with no third-party dependency, and it
/// produces the same workbook as the Android build. Numbers are written as numeric
/// cells and the header row is bold and frozen.
enum XLSXWriter {

    private static let sheetName = "Seabuckthorn Survey"

    static func data(for surveys: [Survey]) -> Data {
        var archive = ZipArchive()
        archive.add(name: "[Content_Types].xml", contents: contentTypes)
        archive.add(name: "_rels/.rels", contents: rootRelationships)
        archive.add(name: "xl/workbook.xml", contents: workbook())
        archive.add(name: "xl/_rels/workbook.xml.rels", contents: workbookRelationships)
        archive.add(name: "xl/styles.xml", contents: styles)
        archive.add(name: "xl/worksheets/sheet1.xml", contents: sheet(for: surveys))
        return archive.finish()
    }

    // MARK: - Parts

    private static func workbook() -> String {
        xmlDeclaration
            + "<workbook xmlns=\"\(mainNamespace)\" xmlns:r=\"\(relationshipNamespace)\">"
            + "<sheets><sheet name=\"\(escape(sheetName))\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
            + "</workbook>"
    }

    private static func sheet(for surveys: [Survey]) -> String {
        let columnCount = SurveyExportRow.headers.count
        let lastColumn = columnName(columnCount - 1)
        let rowCount = surveys.count + 1

        var xml = xmlDeclaration
        xml += "<worksheet xmlns=\"\(mainNamespace)\" xmlns:r=\"\(relationshipNamespace)\">"
        xml += "<dimension ref=\"A1:\(lastColumn)\(rowCount)\"/>"
        xml += "<sheetViews><sheetView workbookViewId=\"0\">"
        xml += "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
        xml += "</sheetView></sheetViews>"
        xml += "<sheetFormatPr defaultRowHeight=\"15\"/>"

        xml += "<cols>"
        for (index, header) in SurveyExportRow.headers.enumerated() {
            let width = min(max(header.count + 6, 12), 32)
            xml += "<col min=\"\(index + 1)\" max=\"\(index + 1)\" width=\"\(width)\" customWidth=\"1\"/>"
        }
        xml += "</cols>"

        xml += "<sheetData>"
        xml += "<row r=\"1\">"
        for (index, header) in SurveyExportRow.headers.enumerated() {
            xml += inlineCell(column: columnName(index), row: 1, value: header, style: 1)
        }
        xml += "</row>"

        for (rowIndex, survey) in surveys.enumerated() {
            let rowNumber = rowIndex + 2
            xml += "<row r=\"\(rowNumber)\">"
            for (columnIndex, value) in SurveyExportRow.values(for: survey).enumerated() {
                if value.isEmpty { continue }
                let reference = columnName(columnIndex)
                if SurveyExportRow.numericColumns.contains(columnIndex), Double(value) != nil {
                    xml += "<c r=\"\(reference)\(rowNumber)\"><v>\(value)</v></c>"
                } else {
                    xml += inlineCell(column: reference, row: rowNumber, value: value, style: 0)
                }
            }
            xml += "</row>"
        }
        xml += "</sheetData>"
        xml += "<autoFilter ref=\"A1:\(lastColumn)\(rowCount)\"/>"
        xml += "</worksheet>"
        return xml
    }

    private static func inlineCell(column: String, row: Int, value: String, style: Int) -> String {
        let styleAttribute = style == 0 ? "" : " s=\"\(style)\""
        return "<c r=\"\(column)\(row)\"\(styleAttribute) t=\"inlineStr\"><is>"
            + "<t xml:space=\"preserve\">\(escape(value))</t></is></c>"
    }

    /// 0 -> A, 25 -> Z, 26 -> AA.
    static func columnName(_ index: Int) -> String {
        var remaining = index
        var name = ""
        while remaining >= 0 {
            let scalar = UnicodeScalar(UInt8(65 + remaining % 26))
            name = String(Character(scalar)) + name
            remaining = remaining / 26 - 1
        }
        return name
    }

    private static func escape(_ value: String) -> String {
        var output = ""
        output.reserveCapacity(value.count + 16)
        for character in value {
            switch character {
            case "&": output += "&amp;"
            case "<": output += "&lt;"
            case ">": output += "&gt;"
            case "\"": output += "&quot;"
            case "'": output += "&apos;"
            default:
                // Strip control characters that OOXML does not allow.
                if let scalar = character.unicodeScalars.first,
                   scalar.value >= 0x20 || character == "\t" || character == "\n" {
                    output.append(character)
                }
            }
        }
        return output
    }

    // MARK: - Static parts

    private static let xmlDeclaration = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private static let mainNamespace = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private static let relationshipNamespace =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private static let packageRelationshipNamespace =
        "http://schemas.openxmlformats.org/package/2006/relationships"

    private static let contentTypes = xmlDeclaration
        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        + "</Types>"

    private static let rootRelationships = xmlDeclaration
        + "<Relationships xmlns=\"\(packageRelationshipNamespace)\">"
        + "<Relationship Id=\"rId1\" Type=\"\(relationshipNamespace)/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>"

    private static let workbookRelationships = xmlDeclaration
        + "<Relationships xmlns=\"\(packageRelationshipNamespace)\">"
        + "<Relationship Id=\"rId1\" Type=\"\(relationshipNamespace)/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
        + "<Relationship Id=\"rId2\" Type=\"\(relationshipNamespace)/styles\" Target=\"styles.xml\"/>"
        + "</Relationships>"

    private static let styles = xmlDeclaration
        + "<styleSheet xmlns=\"\(mainNamespace)\">"
        + "<fonts count=\"2\">"
        + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
        + "</fonts>"
        + "<fills count=\"2\">"
        + "<fill><patternFill patternType=\"none\"/></fill>"
        + "<fill><patternFill patternType=\"gray125\"/></fill>"
        + "</fills>"
        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"2\">"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
        + "</cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>"
}

/// Minimal ZIP writer: stored entries only, which every spreadsheet application
/// accepts and which keeps the implementation small enough to audit.
struct ZipArchive {

    private struct Entry {
        let name: String
        let data: Data
        let crc32: UInt32
        let offset: UInt32
    }

    private var payload = Data()
    private var entries: [Entry] = []

    /// A fixed timestamp, so the same dataset always produces an identical file.
    private let dosTime: UInt16 = 0
    private let dosDate: UInt16 = (46 << 9) | (1 << 5) | 1  // 2026-01-01

    mutating func add(name: String, contents: String) {
        add(name: name, data: Data(contents.utf8))
    }

    mutating func add(name: String, data: Data) {
        let nameBytes = Array(name.utf8)
        let checksum = CRC32.checksum(data)
        let offset = UInt32(payload.count)

        payload.appendLittleEndian(UInt32(0x0403_4B50))     // local file header
        payload.appendLittleEndian(UInt16(20))              // version needed
        payload.appendLittleEndian(UInt16(0))               // flags
        payload.appendLittleEndian(UInt16(0))               // stored
        payload.appendLittleEndian(dosTime)
        payload.appendLittleEndian(dosDate)
        payload.appendLittleEndian(checksum)
        payload.appendLittleEndian(UInt32(data.count))      // compressed size
        payload.appendLittleEndian(UInt32(data.count))      // uncompressed size
        payload.appendLittleEndian(UInt16(nameBytes.count))
        payload.appendLittleEndian(UInt16(0))               // extra length
        payload.append(contentsOf: nameBytes)
        payload.append(data)

        entries.append(Entry(name: name, data: data, crc32: checksum, offset: offset))
    }

    mutating func finish() -> Data {
        var archive = payload
        let directoryOffset = UInt32(archive.count)

        for entry in entries {
            let nameBytes = Array(entry.name.utf8)
            archive.appendLittleEndian(UInt32(0x0201_4B50)) // central directory header
            archive.appendLittleEndian(UInt16(20))          // version made by
            archive.appendLittleEndian(UInt16(20))          // version needed
            archive.appendLittleEndian(UInt16(0))           // flags
            archive.appendLittleEndian(UInt16(0))           // stored
            archive.appendLittleEndian(dosTime)
            archive.appendLittleEndian(dosDate)
            archive.appendLittleEndian(entry.crc32)
            archive.appendLittleEndian(UInt32(entry.data.count))
            archive.appendLittleEndian(UInt32(entry.data.count))
            archive.appendLittleEndian(UInt16(nameBytes.count))
            archive.appendLittleEndian(UInt16(0))           // extra length
            archive.appendLittleEndian(UInt16(0))           // comment length
            archive.appendLittleEndian(UInt16(0))           // disk number
            archive.appendLittleEndian(UInt16(0))           // internal attributes
            archive.appendLittleEndian(UInt32(0))           // external attributes
            archive.appendLittleEndian(entry.offset)
            archive.append(contentsOf: nameBytes)
        }

        let directorySize = UInt32(archive.count) - directoryOffset
        archive.appendLittleEndian(UInt32(0x0605_4B50))     // end of central directory
        archive.appendLittleEndian(UInt16(0))               // disk number
        archive.appendLittleEndian(UInt16(0))               // disk with directory
        archive.appendLittleEndian(UInt16(entries.count))
        archive.appendLittleEndian(UInt16(entries.count))
        archive.appendLittleEndian(directorySize)
        archive.appendLittleEndian(directoryOffset)
        archive.appendLittleEndian(UInt16(0))               // comment length
        return archive
    }
}

private extension Data {
    mutating func appendLittleEndian(_ value: UInt16) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
    }

    mutating func appendLittleEndian(_ value: UInt32) {
        append(UInt8(value & 0xFF))
        append(UInt8((value >> 8) & 0xFF))
        append(UInt8((value >> 16) & 0xFF))
        append(UInt8((value >> 24) & 0xFF))
    }
}

/// Standard CRC-32, as required by the ZIP format.
enum CRC32 {

    private static let table: [UInt32] = {
        (0..<256).map { index -> UInt32 in
            var value = UInt32(index)
            for _ in 0..<8 {
                value = (value & 1) == 1 ? (value >> 1) ^ 0xEDB8_8320 : value >> 1
            }
            return value
        }
    }()

    static func checksum(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFF_FFFF
        for byte in data {
            crc = (crc >> 8) ^ table[Int((crc ^ UInt32(byte)) & 0xFF)]
        }
        return crc ^ 0xFFFF_FFFF
    }
}
