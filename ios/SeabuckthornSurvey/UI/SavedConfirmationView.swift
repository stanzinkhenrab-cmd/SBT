import SwiftUI

/// Confirmation that the record is on disk, and the fastest possible route into the
/// next one — a surveyor walking a plantation records many plants in a row.
struct SavedConfirmationView: View {

    let surveyRowId: Int64
    @Binding var path: [Route]

    @EnvironmentObject private var store: SurveyStore
    @State private var survey: Survey?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(SBTColor.secondary)
                    .frame(width: 96, height: 96)
                    .background(SBTColor.secondaryContainer, in: Circle())
                    .padding(.top, 32)

                Text("Survey Saved Successfully")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(SBTColor.ink)
                    .padding(.top, 24)

                VStack(spacing: 4) {
                    Text(survey?.surveyId ?? "")
                        .font(.title.weight(.bold))
                        .foregroundStyle(SBTColor.primary)
                    if let survey {
                        Text(surveySubtitle(survey))
                            .font(.subheadline)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(SBTColor.inkSoft)
                        if let photo = survey.photoFileName {
                            Text("Photo: \(photo)")
                                .font(.caption)
                                .foregroundStyle(SBTColor.inkSoft)
                        }
                        if survey.hasLocation {
                            Text("\(Formats.coordinate(survey.latitude)), \(Formats.coordinate(survey.longitude))")
                                .font(.caption)
                                .foregroundStyle(SBTColor.inkSoft)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(18)
                .background(
                    SBTColor.surfaceDim,
                    in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
                )
                .padding(.top, 12)

                Text("\(store.savedCount) survey(s) stored on this device")
                    .font(.caption)
                    .foregroundStyle(SBTColor.inkSoft)
                    .padding(.top, 10)

                PrimaryButton(title: "New Survey", systemImage: "plus") {
                    if let next = store.startNewDraft() {
                        path = [.surveyor(next.id)]
                    }
                }
                .padding(.top, 28)

                SecondaryButton(title: "View Saved Surveys", systemImage: "folder") {
                    path = [.list]
                }
                .padding(.top, 10)

                Button {
                    path.removeAll()
                } label: {
                    Label("Back to home", systemImage: "house")
                        .font(.body)
                        .foregroundStyle(SBTColor.primary)
                }
                .padding(.top, 14)
                .padding(.bottom, 28)
            }
            .padding(.horizontal, 24)
            .surveyContentWidth()
        }
        .surveyBackground()
        .navigationTitle("Saved")
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .onAppear { survey = store.survey(id: surveyRowId) }
    }
}
