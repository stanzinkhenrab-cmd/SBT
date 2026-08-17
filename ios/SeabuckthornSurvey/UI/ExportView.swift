import SwiftUI
import UniformTypeIdentifiers

/// Export screen. Files go through the system share sheet or the Files app, so the
/// dataset lands wherever the surveyor keeps their work — on the device, in iCloud
/// Drive, on a USB drive or straight into an email.
struct ExportView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @EnvironmentObject private var store: SurveyStore

    @State private var includeDrafts = false
    @State private var shareURL: URL?
    @State private var errorMessage: String?
    @State private var statusMessage: String?

    private var draftCount: Int { store.draft == nil ? 0 : 1 }

    private var exportCount: Int {
        store.savedCount + (includeDrafts ? draftCount : 0)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(exportCount)")
                        .font(.system(size: 34, weight: .bold))
                    Text("record(s) will be exported")
                        .font(.body)
                    Text("\(SurveyExportRow.headers.count) columns per record")
                        .font(.caption)
                }
                .foregroundStyle(SBTColor.onPrimaryContainer)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(
                    SBTColor.primaryContainer,
                    in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
                )

                if draftCount > 0 {
                    SectionCard(symbol: "doc.badge.clock", title: "Include unfinished draft") {
                        Toggle(isOn: $includeDrafts) {
                            Text("A survey that has not been saved yet is marked \"Draft\" in the Record Status column.")
                                .font(.caption)
                                .foregroundStyle(SBTColor.inkSoft)
                        }
                        .tint(SBTColor.primary)
                    }
                }

                ForEach(ExportFormat.allCases) { format in
                    formatCard(format)
                }

                if let statusMessage {
                    Text(statusMessage)
                        .font(.subheadline)
                        .foregroundStyle(SBTColor.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                if let errorMessage {
                    Text(errorMessage)
                        .font(.subheadline)
                        .foregroundStyle(SBTColor.danger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                SectionCard(symbol: "photo.on.rectangle", title: "Photographs") {
                    Text("Each photo is stored on this device as <Survey ID>.jpg, so a row in the exported table always matches its image by name.")
                        .font(.caption)
                        .foregroundStyle(SBTColor.inkSoft)
                    Text("Photos travel with the app: use Files, AirDrop or an iTunes/Finder backup to copy the survey folder off the device.")
                        .font(.caption)
                        .foregroundStyle(SBTColor.inkSoft)
                }
                .padding(.bottom, 16)
            }
            .padding(16)
            .surveyContentWidth()
        }
        .surveyBackground()
        .navigationTitle("Export Data")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { store.refresh() }
        .sheet(item: Binding(
            get: { shareURL.map(ShareItem.init) },
            set: { if $0 == nil { shareURL = nil } }
        )) { item in
            ShareSheet(url: item.url)
        }
    }

    private func formatCard(_ format: ExportFormat) -> some View {
        SectionCard(symbol: "doc.text", title: format.label, subtitle: ".\(format.fileExtension)") {
            Text(format.summary)
                .font(.caption)
                .foregroundStyle(SBTColor.inkSoft)
            HStack(spacing: 10) {
                SecondaryButton(title: "Share", systemImage: "square.and.arrow.up") {
                    export(format)
                }
            }
            .disabled(exportCount == 0)
            .opacity(exportCount == 0 ? 0.5 : 1)
        }
    }

    private func export(_ format: ExportFormat) {
        errorMessage = nil
        statusMessage = nil
        let rows = store.exportRows(includeDrafts: includeDrafts)
        do {
            let url = try environment.exportManager.writeToTemporaryFile(
                surveys: rows,
                format: format
            )
            statusMessage = "\(rows.count) record(s) written as \(format.label)."
            shareURL = url
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// `sheet(item:)` needs an Identifiable payload.
    private struct ShareItem: Identifiable {
        let url: URL
        var id: String { url.path }

        init(_ url: URL) { self.url = url }
    }
}

/// The system share sheet, which is also how a file is saved into the Files app.
struct ShareSheet: UIViewControllerRepresentable {

    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
