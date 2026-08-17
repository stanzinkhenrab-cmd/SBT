import SwiftUI

struct AboutView: View {

    @EnvironmentObject private var environment: AppEnvironment

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Image("SBTEmblem")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)
                    .padding(12)
                    .background(SBTColor.surfaceDim, in: Circle())
                    .padding(.top, 32)

                Text("Seabuckthorn Field Survey – Ladakh")
                    .font(.title3.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(SBTColor.ink)
                    .padding(.top, 20)

                Divider()
                    .frame(width: 160)
                    .padding(.vertical, 18)

                Text("Developed by Stanzin Khenrab")
                    .font(.body)
                    .foregroundStyle(SBTColor.ink)
                Text("Krishi Vigyan Kendra – Leh, Ladakh")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(SBTColor.inkSoft)
                Text("MIDH-SBM")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(SBTColor.secondary)
                    .padding(.top, 6)

                Text("Version \(environment.appVersion)")
                    .font(.subheadline)
                    .foregroundStyle(SBTColor.inkSoft)
                    .padding(.top, 28)

                Text("All survey records and photographs stay on this device. The app needs no account, no internet connection and no cloud service.")
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(SBTColor.inkSoft)
                    .padding(.top, 24)
                    .padding(.bottom, 32)
            }
            .padding(.horizontal, 28)
            .surveyContentWidth()
        }
        .surveyBackground()
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}
