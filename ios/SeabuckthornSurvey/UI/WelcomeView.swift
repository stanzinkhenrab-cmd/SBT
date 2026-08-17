import SwiftUI

/// The screen the app opens on: identity, one clear action, and quick access to the
/// records already collected.
struct WelcomeView: View {

    @EnvironmentObject private var store: SurveyStore
    @Binding var path: [Route]
    @State private var confirmDiscard = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Image("SBTEmblem")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 92, height: 92)
                    .padding(12)
                    .background(SBTColor.surfaceDim, in: Circle())
                    .padding(.top, 24)

                Text("Seabuckthorn Field Survey")
                    .font(.system(.title, design: .default).weight(.bold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(SBTColor.ink)
                    .padding(.top, 20)
                Text("Ladakh")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(SBTColor.primary)

                Divider()
                    .frame(width: 180)
                    .padding(.vertical, 18)

                Text("Developed by Stanzin Khenrab")
                    .font(.body)
                    .foregroundStyle(SBTColor.ink)
                Text("Krishi Vigyan Kendra – Leh, Ladakh")
                    .font(.subheadline)
                    .foregroundStyle(SBTColor.inkSoft)
                    .multilineTextAlignment(.center)
                Text("MIDH-SBM")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(SBTColor.secondary)
                    .padding(.top, 6)

                PrimaryButton(title: "New Survey", systemImage: "plus") {
                    if let survey = store.startOrResumeDraft() {
                        path.append(.surveyor(survey.id))
                    }
                }
                .padding(.top, 32)

                SecondaryButton(
                    title: "Saved Surveys (\(store.savedCount))",
                    systemImage: "folder"
                ) {
                    path.append(.list)
                }
                .padding(.top, 12)

                if let draft = store.draft {
                    draftCard(draft)
                        .padding(.top, 20)
                }

                if !store.preferences.profile.name.isBlank {
                    Text("Signed in for field work as \(store.preferences.profile.name)")
                        .font(.caption)
                        .foregroundStyle(SBTColor.inkSoft)
                        .padding(.top, 20)
                }

                Text("Works fully offline · No account needed")
                    .font(.caption)
                    .foregroundStyle(SBTColor.inkSoft)
                    .padding(.top, 24)
                    .padding(.bottom, 28)
            }
            .padding(.horizontal, 24)
            .surveyContentWidth()
        }
        .surveyBackground()
        .navigationTitle("Seabuckthorn Field Survey")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button {
                        path.append(.map)
                    } label: {
                        Label("Survey Map", systemImage: "map")
                    }
                    Button {
                        path.append(.export)
                    } label: {
                        Label("Export Data", systemImage: "square.and.arrow.up")
                    }
                    Button {
                        path.append(.about)
                    } label: {
                        Label("About", systemImage: "info.circle")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .accessibilityLabel("More options")
                }
            }
        }
        .onAppear { store.refresh() }
        .confirmationDialog(
            "Discard unfinished survey?",
            isPresented: $confirmDiscard,
            titleVisibility: .visible
        ) {
            Button("Discard", role: .destructive) {
                if let draft = store.draft { store.delete(draft) }
            }
            Button("Keep", role: .cancel) {}
        } message: {
            Text("The draft \(store.draft?.surveyId ?? "") and any photo taken for it will be permanently removed. Saved surveys are not affected.")
        }
    }

    private func draftCard(_ draft: Survey) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Unfinished survey")
                .font(.subheadline.weight(.semibold))
            Text("\(draft.surveyId) · last auto-saved \(Formats.dateTime(draft.updatedAt))")
                .font(.caption)
            HStack(spacing: 8) {
                Button {
                    path.append(.surveyor(draft.id))
                } label: {
                    Label("Continue", systemImage: "arrow.right")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 46)
                        .background(SBTColor.secondary, in: RoundedRectangle(cornerRadius: 12))
                        .foregroundStyle(.white)
                }
                .buttonStyle(.plain)

                Button {
                    confirmDiscard = true
                } label: {
                    Image(systemName: "trash")
                        .frame(width: 52, height: 46)
                        .background(SBTColor.surface, in: RoundedRectangle(cornerRadius: 12))
                        .foregroundStyle(SBTColor.danger)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Discard draft")
            }
        }
        .foregroundStyle(SBTColor.onSecondaryContainer)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(
            SBTColor.secondaryContainer,
            in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
        )
    }
}
