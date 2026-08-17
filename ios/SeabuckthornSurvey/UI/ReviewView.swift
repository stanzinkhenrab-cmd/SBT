import SwiftUI

/// Last stop before a record becomes a saved survey: everything entered, with the
/// Survey ID shown prominently, plus a way back into the form.
struct ReviewView: View {

    let surveyRowId: Int64
    @Binding var path: [Route]

    @EnvironmentObject private var store: SurveyStore
    @State private var survey: Survey?
    @State private var warnings: [String] = []
    @State private var isSaving = false
    @State private var saveError: String?

    var body: some View {
        ScrollView {
            if let survey {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Saving as")
                            .font(.subheadline.weight(.semibold))
                        Text(survey.surveyId)
                            .font(.system(size: 34, weight: .bold))
                        Text("Check the details below before saving.")
                            .font(.subheadline)
                    }
                    .foregroundStyle(SBTColor.onPrimaryContainer)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(18)
                    .background(
                        SBTColor.primaryContainer,
                        in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
                    )

                    MissingDataNotice(warnings: warnings)

                    SurveySummaryView(survey: survey, photoURL: store.photoURL(for: survey))

                    if let saveError {
                        Text(saveError)
                            .font(.subheadline)
                            .foregroundStyle(SBTColor.danger)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    PrimaryButton(title: "Save Survey", systemImage: "tray.and.arrow.down", isBusy: isSaving) {
                        save(survey)
                    }

                    SecondaryButton(title: "Edit Survey", systemImage: "pencil") {
                        path.removeLast()
                    }
                    .padding(.bottom, 16)
                }
                .padding(16)
                .surveyContentWidth()
            } else {
                ProgressView().padding(40)
            }
        }
        .surveyBackground()
        .navigationTitle("Review & Save")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear(perform: reload)
    }

    /// Re-reads the record on every appearance: the form's final auto-save may
    /// still have been in flight when this screen was pushed.
    private func reload() {
        guard let record = store.survey(id: surveyRowId) else { return }
        survey = record
        warnings = SurveyValidator.validate(record).warnings
    }

    private func save(_ record: Survey) {
        guard !isSaving else { return }
        isSaving = true
        saveError = nil
        if let saved = store.commit(record) {
            isSaving = false
            path.append(.saved(saved.id))
        } else {
            isSaving = false
            saveError = "The survey could not be saved. It is still stored as a draft."
        }
    }
}
