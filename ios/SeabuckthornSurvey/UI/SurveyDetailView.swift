import SwiftUI

/// A saved record in full, with the option to edit it or delete it.
struct SurveyDetailView: View {

    let surveyRowId: Int64
    @Binding var path: [Route]

    @EnvironmentObject private var store: SurveyStore
    @State private var survey: Survey?
    @State private var confirmDelete = false

    var body: some View {
        ScrollView {
            if let survey {
                VStack(spacing: 16) {
                    SurveySummaryView(survey: survey, photoURL: store.photoURL(for: survey))

                    PrimaryButton(title: "Edit Survey", systemImage: "pencil") {
                        path.append(.form(survey.id))
                    }

                    SecondaryButton(title: "Delete Survey", systemImage: "trash") {
                        confirmDelete = true
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
        .navigationTitle(survey?.surveyId ?? "Survey")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { survey = store.survey(id: surveyRowId) }
        .confirmationDialog(
            survey.map { "Delete \($0.surveyId)?" } ?? "Delete survey?",
            isPresented: $confirmDelete,
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let survey {
                    store.delete(survey)
                    path.removeLast()
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently removes the record and its photo from this device. Export your data first if it has not been backed up. This cannot be undone.")
        }
    }
}
