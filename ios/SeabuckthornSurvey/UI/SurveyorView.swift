import SwiftUI

/// Step one of the workflow. The details entered here are remembered and pre-filled
/// on every later survey, so they normally only need to be confirmed.
struct SurveyorView: View {

    let surveyRowId: Int64
    @Binding var path: [Route]

    @EnvironmentObject private var store: SurveyStore
    @State private var model: SurveyFormModel?

    var body: some View {
        Group {
            if let model {
                content(model: model)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .surveyBackground()
        .navigationTitle("Surveyor Information")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if model == nil, let survey = store.survey(id: surveyRowId) {
                model = SurveyFormModel(survey: survey, store: store)
            }
        }
        .onDisappear { model?.flush() }
    }

    private func content(model: SurveyFormModel) -> some View {
        FormBody(model: model, surveyRowId: surveyRowId, path: $path)
    }

    /// Split out so the view observes the model rather than an optional.
    private struct FormBody: View {

        @ObservedObject var model: SurveyFormModel
        let surveyRowId: Int64
        @Binding var path: [Route]

        var body: some View {
            ScrollView {
                VStack(spacing: 16) {
                    SectionCard(
                        symbol: "person.text.rectangle",
                        title: "Who is recording this survey",
                        subtitle: "Saved for the next survey so it is only entered once"
                    ) {
                        SBTTextField(
                            title: "Surveyor Name",
                            text: $model.survey.surveyorName,
                            required: true,
                            errorText: model.error(for: .surveyorName)
                        )
                        SBTTextField(
                            title: "Designation",
                            text: $model.survey.designation,
                            required: true,
                            supportingText: "For example: Subject Matter Specialist (Horticulture)",
                            errorText: model.error(for: .designation)
                        )
                        SBTPickerField(
                            title: "Organization",
                            text: $model.survey.organization,
                            options: SurveyOptions.organizations,
                            required: true,
                            supportingText: "Pick from the list or type another organization",
                            errorText: model.error(for: .organization)
                        )
                    }

                    Text("Survey \(model.survey.surveyId) has been created and is being saved automatically as you type.")
                        .font(.caption)
                        .foregroundStyle(SBTColor.inkSoft)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    PrimaryButton(title: "Continue to Survey Form", systemImage: "arrow.right") {
                        if model.validateSurveyor() {
                            path.append(.form(surveyRowId))
                        }
                    }
                    .padding(.top, 4)
                }
                .padding(16)
                .surveyContentWidth()
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    AutoSaveIndicator(state: model.autoSave)
                }
            }
        }
    }
}
