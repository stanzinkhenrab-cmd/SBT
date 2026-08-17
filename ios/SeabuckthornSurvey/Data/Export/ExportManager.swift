import Foundation
import UniformTypeIdentifiers

enum ExportFormat: String, CaseIterable, Identifiable {
    case csv
    case xlsx
    case json

    var id: String { rawValue }

    var label: String {
        switch self {
        case .csv: return "CSV"
        case .xlsx: return "Excel"
        case .json: return "JSON"
        }
    }

    var fileExtension: String {
        switch self {
        case .csv: return "csv"
        case .xlsx: return "xlsx"
        case .json: return "json"
        }
    }

    var summary: String {
        switch self {
        case .csv: return "Comma separated values. Opens in Excel, Numbers, R, QGIS and SPSS."
        case .xlsx: return "Formatted workbook with a frozen, filterable header row."
        case .json: return "Structured records for scripts and data pipelines."
        }
    }

    var contentType: UTType {
        switch self {
        case .csv: return .commaSeparatedText
        case .xlsx:
            return UTType("org.openxmlformats.spreadsheetml.sheet") ?? .data
        case .json: return .json
        }
    }
}

/// Builds export files in a temporary folder and hands them to the system share or
/// save sheet. Everything is written to disk first, so a cancelled share never
/// loses the file.
struct ExportManager {

    let appVersion: String

    private var exportDirectory: URL {
        let folder = FileManager.default.temporaryDirectory
            .appendingPathComponent("Exports", isDirectory: true)
        if !FileManager.default.fileExists(atPath: folder.path) {
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        return folder
    }

    func suggestedFileName(for format: ExportFormat, now: Date = Date()) -> String {
        "SBT-Survey-\(Formats.fileStamp(now)).\(format.fileExtension)"
    }

    func data(for surveys: [Survey], format: ExportFormat) throws -> Data {
        switch format {
        case .csv: return CSVWriter.data(for: surveys)
        case .xlsx: return XLSXWriter.data(for: surveys)
        case .json: return try JSONExportWriter.data(for: surveys, appVersion: appVersion)
        }
    }

    /// Writes the dataset into a temporary file and returns it, ready to be shared.
    func writeToTemporaryFile(surveys: [Survey], format: ExportFormat) throws -> URL {
        let url = exportDirectory.appendingPathComponent(suggestedFileName(for: format))
        try data(for: surveys, format: format).write(to: url, options: .atomic)
        return url
    }

    /// Removes previously generated export files; they are only temporary copies.
    func clearCache() {
        let contents = try? FileManager.default.contentsOfDirectory(
            at: exportDirectory,
            includingPropertiesForKeys: nil
        )
        contents?.forEach { try? FileManager.default.removeItem(at: $0) }
    }
}
