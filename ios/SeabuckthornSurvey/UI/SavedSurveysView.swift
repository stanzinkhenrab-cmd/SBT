import SwiftUI

/// Every saved record, newest first, searchable by ID, village, district or surveyor.
struct SavedSurveysView: View {

    @Binding var path: [Route]
    @EnvironmentObject private var store: SurveyStore
    @State private var query = ""
    @State private var pendingDelete: Survey?

    private var visible: [Survey] {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !needle.isEmpty else { return store.savedSurveys }
        return store.savedSurveys.filter { survey in
            survey.surveyId.lowercased().contains(needle)
                || survey.village.lowercased().contains(needle)
                || survey.district.lowercased().contains(needle)
                || survey.block.lowercased().contains(needle)
                || survey.site.lowercased().contains(needle)
                || survey.surveyorName.lowercased().contains(needle)
        }
    }

    var body: some View {
        Group {
            if visible.isEmpty {
                emptyState
            } else {
                List {
                    ForEach(visible) { survey in
                        Button {
                            path.append(.detail(survey.id))
                        } label: {
                            row(survey)
                        }
                        .buttonStyle(.plain)
                        .listRowBackground(SBTColor.background)
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                pendingDelete = survey
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .surveyBackground()
        .searchable(text: $query, prompt: "Survey ID, village, district or surveyor")
        .navigationTitle("Saved Surveys")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Text("\(store.savedCount)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(SBTColor.inkSoft)
                    .accessibilityLabel("\(store.savedCount) records on this device")
            }
        }
        .onAppear { store.refresh() }
        .confirmationDialog(
            pendingDelete.map { "Delete \($0.surveyId)?" } ?? "Delete survey?",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let survey = pendingDelete { store.delete(survey) }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("This permanently removes the record and its photo from this device. Export your data first if it has not been backed up. This cannot be undone.")
        }
    }

    private func row(_ survey: Survey) -> some View {
        HStack(spacing: 14) {
            Text(String(survey.surveyId.suffix(4)))
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(SBTColor.onPrimaryContainer)
                .frame(width: 46, height: 46)
                .background(SBTColor.primaryContainer, in: Circle())

            VStack(alignment: .leading, spacing: 2) {
                Text(survey.surveyId)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(SBTColor.ink)
                Text(locationLine(survey))
                    .font(.subheadline)
                    .foregroundStyle(SBTColor.inkSoft)
                Text(Formats.dateTime(survey.createdAt))
                    .font(.caption)
                    .foregroundStyle(SBTColor.inkSoft)
                HStack(spacing: 8) {
                    if let stage = survey.maturityStage, !stage.isBlank {
                        Text(stage)
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(SBTColor.onSecondaryContainer)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(SBTColor.secondaryContainer, in: Capsule())
                    }
                    Image(systemName: survey.hasLocation ? "location.fill" : "location.slash")
                        .font(.caption)
                        .foregroundStyle(survey.hasLocation ? SBTColor.secondary : SBTColor.inkSoft)
                        .accessibilityLabel(survey.hasLocation ? "Coordinates recorded" : "No coordinates")
                    if survey.photoFileName != nil {
                        Image(systemName: "camera.fill")
                            .font(.caption)
                            .foregroundStyle(SBTColor.secondary)
                            .accessibilityLabel("Photo attached")
                    }
                }
                .padding(.top, 2)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(SBTColor.outline)
        }
        .padding(.vertical, 8)
    }

    private func locationLine(_ survey: Survey) -> String {
        let parts = [survey.village, survey.block, survey.district].filter { !$0.isBlank }
        return parts.isEmpty ? "Location not entered" : parts.joined(separator: ", ")
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Text(store.savedCount > 0 ? "No survey matches this search." : "No surveys saved yet.")
                .font(.headline)
                .foregroundStyle(SBTColor.inkSoft)
            if store.savedCount == 0 {
                Text("Records saved from the survey form appear here, newest first.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(SBTColor.inkSoft)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
