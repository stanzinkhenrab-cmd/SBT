import Foundation
import SwiftUI

/// Everything the app needs, created once at launch.
///
/// A manual container rather than a dependency-injection framework: there is one
/// database, one photo folder and one location service, and this keeps the wiring
/// visible in a single file.
@MainActor
final class AppEnvironment: ObservableObject {

    let store: SurveyStore
    let locationService: LocationService
    let exportManager: ExportManager
    let appVersion: String

    /// Set when the database itself could not be opened, which is the only failure
    /// the app cannot work around.
    @Published var fatalMessage: String?

    init() {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        appVersion = version ?? "1.0.0"

        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let photoStore = PhotoStore(root: documents)
        let preferences = SurveyPreferences()
        let databaseURL = documents.appendingPathComponent("sbt_survey.sqlite")

        var openedDatabase: SurveyDatabase
        var failure: String?
        do {
            openedDatabase = try SurveyDatabase(url: databaseURL)
        } catch {
            failure = error.localizedDescription
            // Fall back to a memory database so the app can still open and explain
            // itself rather than crashing on launch in the field.
            do {
                openedDatabase = try SurveyDatabase.inMemory()
            } catch {
                fatalError("SQLite is unavailable on this device: \(error)")
            }
        }

        store = SurveyStore(
            database: openedDatabase,
            photoStore: photoStore,
            preferences: preferences
        )
        locationService = LocationService()
        exportManager = ExportManager(appVersion: appVersion)
        fatalMessage = failure
    }
}
