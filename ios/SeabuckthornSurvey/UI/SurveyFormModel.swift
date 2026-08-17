import Foundation
import Combine
import UIKit

/// Drives the survey form.
///
/// Every edit lands in the database within `autoSaveDelay`, and the form is flushed
/// the moment the screen goes away, so closing the app, a battery pull or a crash
/// costs at most the keystroke in progress.
@MainActor
final class SurveyFormModel: ObservableObject {

    @Published var survey: Survey
    @Published var heightText: String
    @Published var berryDiameterText: String
    @Published var tssText: String

    @Published private(set) var autoSave: AutoSaveState = .idle
    @Published private(set) var errors: [SurveyField: String] = [:]
    @Published private(set) var showErrors = false

    /// True once a position has been recorded, so a later fix does not silently
    /// overwrite a reading the surveyor took at the plant.
    @Published private(set) var locationLocked: Bool

    private let store: SurveyStore
    private var cancellables = Set<AnyCancellable>()
    private var saveTask: Task<Void, Never>?

    private static let autoSaveDelay: UInt64 = 400_000_000  // 0.4 s

    init(survey: Survey, store: SurveyStore) {
        self.survey = survey
        self.store = store
        heightText = Formats.number(survey.plantHeight)
        berryDiameterText = Formats.number(survey.berryDiameterMm)
        tssText = Formats.number(survey.tssBrix)
        locationLocked = survey.hasLocation

        // Any edit to the record schedules a write.
        $survey
            .dropFirst()
            .sink { [weak self] _ in self?.scheduleSave() }
            .store(in: &cancellables)

        // The numeric fields keep their raw text so a half-typed "1." is not lost,
        // and parse into the record as they change.
        $heightText
            .dropFirst()
            .sink { [weak self] text in
                self?.survey.plantHeight = Formats.parseNumber(text)
                self?.revalidate()
            }
            .store(in: &cancellables)
        $berryDiameterText
            .dropFirst()
            .sink { [weak self] text in
                self?.survey.berryDiameterMm = Formats.parseNumber(text)
                self?.revalidate()
            }
            .store(in: &cancellables)
        $tssText
            .dropFirst()
            .sink { [weak self] text in
                self?.survey.tssBrix = Formats.parseNumber(text)
                self?.revalidate()
            }
            .store(in: &cancellables)
    }

    // MARK: - Editing helpers

    /// Changing district invalidates a block that belongs to the other district.
    func districtChanged(to district: String) {
        if !SurveyOptions.blocks(for: district).contains(survey.block) {
            survey.block = ""
        }
    }

    var villageSuggestions: [String] {
        Array(Set(SurveyOptions.villages(for: survey.district) + store.knownVillages)).sorted()
    }

    func error(for field: SurveyField) -> String? {
        showErrors ? errors[field] : nil
    }

    // MARK: - Photo

    func attachPhoto(_ image: UIImage) {
        flush()
        if let updated = store.attachPhoto(image, to: survey) {
            applyExternalUpdate(updated)
        }
    }

    func removePhoto() {
        flush()
        if let updated = store.removePhoto(from: survey) {
            applyExternalUpdate(updated)
        }
    }

    /// Adopts a record the store has just written. Assigning `survey` schedules a
    /// save through the observer above, which is cancelled here: the store has
    /// already written this exact record.
    private func applyExternalUpdate(_ updated: Survey) {
        survey = updated
        saveTask?.cancel()
        autoSave = .saved
    }

    // MARK: - GPS

    /// Records the fix when the record has no position yet.
    func considerAutomatic(fix: GpsFix) {
        guard !locationLocked else { return }
        apply(fix: fix)
    }

    /// Replaces the stored position with the current best fix.
    func apply(fix: GpsFix) {
        survey.latitude = fix.latitude
        survey.longitude = fix.longitude
        survey.altitude = fix.altitude
        survey.accuracyM = fix.accuracyM
        survey.locationCapturedAt = fix.capturedAt
        locationLocked = true
    }

    func unlockLocation() {
        locationLocked = false
    }

    // MARK: - Saving

    private func scheduleSave() {
        autoSave = .saving
        saveTask?.cancel()
        saveTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: SurveyFormModel.autoSaveDelay)
            guard !Task.isCancelled else { return }
            self?.persist()
        }
    }

    /// Writes the current form immediately; called when the screen goes away.
    func flush() {
        saveTask?.cancel()
        persist()
    }

    private func persist() {
        autoSave = store.save(survey) ? .saved : .failed
    }

    // MARK: - Validation

    private func revalidate() {
        errors = SurveyValidator.validate(
            survey,
            heightText: heightText,
            berryText: berryDiameterText,
            tssText: tssText
        ).errors
    }

    /// Checks only the surveyor block, so the first step can be left as soon as
    /// those three fields are filled in.
    func validateSurveyor() -> Bool {
        revalidate()
        showErrors = true
        let surveyorFields: Set<SurveyField> = [.surveyorName, .designation, .organization]
        let blocking = errors.keys.filter { surveyorFields.contains($0) }
        if blocking.isEmpty { flush() }
        return blocking.isEmpty
    }

    /// Returns true when the form may move on to the review screen.
    func validateForReview() -> Bool {
        revalidate()
        showErrors = true
        if errors.isEmpty { flush() }
        return errors.isEmpty
    }
}
