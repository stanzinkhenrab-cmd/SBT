import Foundation

/// Writes the survey table as RFC 4180 CSV.
enum CSVWriter {

    /// Byte-order mark so Excel opens `°Brix` correctly on a Windows machine.
    private static let byteOrderMark = Data([0xEF, 0xBB, 0xBF])

    static func data(for surveys: [Survey]) -> Data {
        var text = headerLine()
        for survey in surveys {
            text += SurveyExportRow.values(for: survey).map(escape).joined(separator: ",") + "\r\n"
        }
        var output = byteOrderMark
        output.append(text.data(using: .utf8) ?? Data())
        return output
    }

    private static func headerLine() -> String {
        SurveyExportRow.headers.map(escape).joined(separator: ",") + "\r\n"
    }

    static func escape(_ value: String) -> String {
        let needsQuotes = value.contains(",") || value.contains("\"")
            || value.contains("\n") || value.contains("\r")
        let escaped = value.replacingOccurrences(of: "\"", with: "\"\"")
        return needsQuotes ? "\"\(escaped)\"" : escaped
    }
}

/// Writes the survey table as a JSON document with a small metadata header.
enum JSONExportWriter {

    static func data(for surveys: [Survey], appVersion: String) throws -> Data {
        var records: [[String: Any]] = []
        for survey in surveys {
            let values = SurveyExportRow.values(for: survey)
            var record: [String: Any] = [:]
            for (index, key) in SurveyExportRow.jsonKeys.enumerated() {
                let raw = values[index]
                if SurveyExportRow.numericColumns.contains(index) {
                    record[key] = Double(raw) ?? NSNull()
                } else {
                    record[key] = raw
                }
            }
            records.append(record)
        }

        let root: [String: Any] = [
            "dataset": "Seabuckthorn Field Survey – Ladakh",
            "app_version": appVersion,
            "platform": "iOS",
            "exported_at": Formats.dateTime(Date()),
            "record_count": surveys.count,
            "records": records
        ]
        return try JSONSerialization.data(
            withJSONObject: root,
            options: [.prettyPrinted, .sortedKeys]
        )
    }
}
