import Foundation
import UIKit

/// Single entry point to survey storage for the UI.
///
/// Records are written continuously as drafts and only flipped to `SAVED` once the
/// surveyor confirms on the review screen; nothing is ever removed without an
/// explicit request.
@MainActor
final class SurveyStore: ObservableObject {

    @Published private(set) var savedSurveys: [Survey] = []
    @Published private(set) var draft: Survey?
    @Published private(set) var knownVillages: [String] = []
    @Published var errorMessage: String?

    let database: SurveyDatabase
    let photoStore: PhotoStore
    let preferences: SurveyPreferences

    var savedCount: Int { savedSurveys.count }

    init(database: SurveyDatabase, photoStore: PhotoStore, preferences: SurveyPreferences) {
        self.database = database
        self.photoStore = photoStore
        self.preferences = preferences
        refresh()
    }

    /// Re-reads the lists the UI observes. Cheap for field-sized datasets.
    func refresh() {
        do {
            savedSurveys = try database.savedSurveys()
            draft = try database.latestDraft()
            knownVillages = try database.knownVillages()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func survey(id: Int64) -> Survey? {
        do {
            return try database.survey(id: id)
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// Opens the next survey. An unfinished draft is resumed instead of starting a
    /// new record, so a form left open on a previous outing is never lost and no
    /// survey number is wasted.
    func startOrResumeDraft() -> Survey? {
        if let existing = draft { return existing }
        return startNewDraft()
    }

    /// Creates a brand new record even when a draft exists.
    func startNewDraft() -> Survey? {
        let profile = preferences.profile
        let location = preferences.lastLocation
        var template = Survey()
        template.surveyorName = profile.name
        template.designation = profile.designation
        template.organization = profile.organization
        template.district = location.district
        template.block = location.block
        template.village = location.village
        template.site = location.site
        do {
            let created = try database.insertWithNewSurveyId(template)
            refresh()
            return created
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// Persists the in-progress form. Called by the auto-save loop.
    @discardableResult
    func save(_ survey: Survey) -> Bool {
        var record = survey
        record.updatedAt = Date()
        do {
            try database.update(record)
            refresh()
            return true
        } catch {
            errorMessage = error.localizedDescription
            return false
        }
    }

    /// Promotes a draft to a saved record and refreshes the remembered surveyor and
    /// location so the next survey starts pre-filled.
    func commit(_ survey: Survey) -> Survey? {
        var record = survey
        record.status = Survey.statusSaved
        record.updatedAt = Date()
        record.savedAt = record.savedAt ?? Date()
        do {
            try database.update(record)
            preferences.profile = SurveyorProfile(
                name: record.surveyorName,
                designation: record.designation,
                organization: record.organization
            )
            preferences.lastLocation = LastLocation(
                district: record.district,
                block: record.block,
                village: record.village,
                site: record.site
            )
            refresh()
            return record
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// Stores a captured image and links it to the record.
    func attachPhoto(_ image: UIImage, to survey: Survey) -> Survey? {
        do {
            let fileName = try photoStore.save(image: image, surveyId: survey.surveyId)
            var record = survey
            record.photoFileName = fileName
            record.updatedAt = Date()
            try database.update(record)
            refresh()
            return record
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// Clears the photo from the record first, then removes the file, so a failure
    /// part-way leaves a record without a photo rather than one pointing at nothing.
    func removePhoto(from survey: Survey) -> Survey? {
        var record = survey
        record.photoFileName = nil
        record.updatedAt = Date()
        do {
            try database.update(record)
            photoStore.delete(surveyId: survey.surveyId)
            refresh()
            return record
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    /// Deletes a record and its photo. Only ever called behind a confirmation dialog.
    func delete(_ survey: Survey) {
        do {
            try database.delete(id: survey.id)
            photoStore.delete(surveyId: survey.surveyId)
            refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// Every record, for export. Drafts are included only when asked for.
    func exportRows(includeDrafts: Bool) -> [Survey] {
        do {
            let all = try database.allSurveys()
            return includeDrafts ? all : all.filter { !$0.isDraft }
        } catch {
            errorMessage = error.localizedDescription
            return []
        }
    }

    func photoURL(for survey: Survey) -> URL? {
        photoStore.url(forFileName: survey.photoFileName)
    }
}
