import SwiftUI

/// What the auto-save pill is currently reporting.
enum AutoSaveState {
    case idle
    case saving
    case saved
    case failed
}

/// The pill in the navigation bar that tells the surveyor their work is on disk.
/// Auto-save is the app's central promise, so it stays visible while a form is open
/// rather than hiding behind a toast.
struct AutoSaveIndicator: View {

    let state: AutoSaveState

    var body: some View {
        HStack(spacing: 6) {
            switch state {
            case .saving:
                ProgressView().controlSize(.mini).tint(SBTColor.inkSoft)
            case .failed:
                Image(systemName: "exclamationmark.triangle.fill")
            default:
                Image(systemName: "checkmark.circle.fill")
            }
            Text(label)
                .font(.caption.weight(.medium))
        }
        .foregroundStyle(state == .failed ? Color.white : SBTColor.inkSoft)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(
            state == .failed ? SBTColor.danger : SBTColor.surfaceDim,
            in: Capsule()
        )
        .accessibilityLabel(label)
    }

    private var label: String {
        switch state {
        case .idle: return "Auto-save on"
        case .saving: return "Saving…"
        case .saved: return "Auto-saved"
        case .failed: return "Not saved"
        }
    }
}

/// Status of the GPS receiver, shown on the location card.
struct GpsStatusPill: View {

    let status: GpsStatus

    var body: some View {
        HStack(spacing: 6) {
            if status == .acquiring {
                ProgressView().controlSize(.mini).tint(SBTColor.onTertiaryContainer)
            } else {
                Image(systemName: symbol)
            }
            Text(label).font(.caption.weight(.medium))
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(background, in: Capsule())
        .accessibilityLabel(label)
    }

    private var label: String {
        switch status {
        case .ready: return "GPS Ready"
        case .acquiring: return "Acquiring GPS…"
        case .permissionRequired: return "Location permission needed"
        case .servicesDisabled: return "Location services off"
        case .unavailable: return "Location unavailable"
        }
    }

    private var symbol: String {
        switch status {
        case .ready: return "location.fill"
        case .acquiring: return "location"
        case .permissionRequired, .servicesDisabled: return "location.slash"
        case .unavailable: return "exclamationmark.icloud"
        }
    }

    private var foreground: Color {
        switch status {
        case .ready: return SBTColor.onSecondaryContainer
        case .acquiring: return SBTColor.onTertiaryContainer
        default: return Color.white
        }
    }

    private var background: Color {
        switch status {
        case .ready: return SBTColor.secondaryContainer
        case .acquiring: return SBTColor.tertiaryContainer
        default: return SBTColor.danger
        }
    }
}

/// The block of optional fields a record is missing, shown before saving.
struct MissingDataNotice: View {

    let warnings: [String]

    var body: some View {
        if warnings.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Text("Optional information not recorded")
                    .font(.subheadline.weight(.semibold))
                ForEach(warnings, id: \.self) { warning in
                    Text("• \(warning)").font(.subheadline)
                }
                Text("The survey can still be saved. These fields can be filled in later by editing the record.")
                    .font(.caption)
                    .padding(.top, 4)
            }
            .foregroundStyle(SBTColor.onTertiaryContainer)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(
                SBTColor.tertiaryContainer,
                in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
            )
        }
    }
}
