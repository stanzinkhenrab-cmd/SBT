import Foundation
import UIKit

/// Owns the on-device photo folder. One JPEG per survey, named after the survey
/// identifier (`SBT-2026-0001.jpg`), so an exported spreadsheet row and the image
/// on disk can always be matched by name alone.
struct PhotoStore {

    private let root: URL

    init(root: URL) {
        self.root = root
    }

    /// The photo folder, created on first use.
    var directory: URL {
        let folder = root.appendingPathComponent("Photos", isDirectory: true)
        if !FileManager.default.fileExists(atPath: folder.path) {
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        return folder
    }

    func fileName(for surveyId: String) -> String { "\(surveyId).jpg" }

    func url(for surveyId: String) -> URL {
        directory.appendingPathComponent(fileName(for: surveyId))
    }

    /// Resolves a stored file name against the current container.
    func url(forFileName fileName: String?) -> URL? {
        guard let fileName, !fileName.isBlank else { return nil }
        let candidate = directory.appendingPathComponent(fileName)
        return FileManager.default.fileExists(atPath: candidate.path) ? candidate : nil
    }

    func exists(fileName: String?) -> Bool {
        url(forFileName: fileName) != nil
    }

    /// Writes a captured image, replacing any earlier photo for the same record.
    ///
    /// The JPEG is written to a temporary file and then moved onto the final name,
    /// so an interrupted write cannot leave a truncated `SBT-….jpg` behind.
    @discardableResult
    func save(image: UIImage, surveyId: String, quality: CGFloat = 0.85) throws -> String {
        guard let data = image.jpegData(compressionQuality: quality) else {
            throw PhotoError.encodingFailed
        }
        let pending = directory.appendingPathComponent("\(surveyId).pending.jpg")
        let target = url(for: surveyId)
        try? FileManager.default.removeItem(at: pending)
        try data.write(to: pending, options: .atomic)
        try? FileManager.default.removeItem(at: target)
        try FileManager.default.moveItem(at: pending, to: target)
        excludeFromBackupIfNeeded(target)
        return fileName(for: surveyId)
    }

    func delete(surveyId: String) {
        try? FileManager.default.removeItem(at: url(for: surveyId))
        try? FileManager.default.removeItem(
            at: directory.appendingPathComponent("\(surveyId).pending.jpg")
        )
    }

    /// Photos are field data and should be backed up, so nothing is excluded here.
    /// The hook exists so the decision is visible rather than implicit.
    private func excludeFromBackupIfNeeded(_ url: URL) {}

    enum PhotoError: Error, LocalizedError {
        case encodingFailed

        var errorDescription: String? {
            switch self {
            case .encodingFailed: return "The photo could not be prepared for saving."
            }
        }
    }
}
