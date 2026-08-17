import SwiftUI

/// Seabuckthorn berry orange, Ladakh sage-green foliage and glacier blue.
/// The same palette as the Android build, so the two apps read as one product.
enum SBTColor {
    static let primary = Color(red: 0.66, green: 0.29, blue: 0.00)          // #A84B00
    static let primaryContainer = Color(red: 1.00, green: 0.86, blue: 0.75) // #FFDCC0
    static let onPrimaryContainer = Color(red: 0.21, green: 0.07, blue: 0.00)
    static let secondary = Color(red: 0.30, green: 0.42, blue: 0.24)        // #4C6A3E
    static let secondaryContainer = Color(red: 0.81, green: 0.94, blue: 0.72)
    static let onSecondaryContainer = Color(red: 0.05, green: 0.13, blue: 0.02)
    static let tertiary = Color(red: 0.22, green: 0.38, blue: 0.56)         // #37618E
    static let tertiaryContainer = Color(red: 0.82, green: 0.89, blue: 1.00)
    static let onTertiaryContainer = Color(red: 0.00, green: 0.11, blue: 0.21)
    static let danger = Color(red: 0.70, green: 0.15, blue: 0.12)
    static let background = Color(red: 1.00, green: 0.98, blue: 0.97)       // #FFFBF7
    static let surface = Color.white
    static let surfaceDim = Color(red: 0.97, green: 0.92, blue: 0.88)
    static let ink = Color(red: 0.13, green: 0.10, blue: 0.08)
    static let inkSoft = Color(red: 0.32, green: 0.27, blue: 0.23)
    static let outline = Color(red: 0.85, green: 0.77, blue: 0.71)
}

enum SBTMetrics {
    /// Minimum height for anything tappable. Larger than Apple's 44 pt guidance
    /// because the form is used outdoors, sometimes with gloves.
    static let touchTarget: CGFloat = 56
    static let cornerRadius: CGFloat = 16
    static let fieldCornerRadius: CGFloat = 12
    /// Keeps the single-column form readable on an iPad rather than stretching it.
    static let contentMaxWidth: CGFloat = 700
}

extension View {
    /// Centres the form column and caps its width on large screens.
    func surveyContentWidth() -> some View {
        frame(maxWidth: SBTMetrics.contentMaxWidth)
            .frame(maxWidth: .infinity)
    }

    /// The cream page background used behind every screen.
    func surveyBackground() -> some View {
        background(SBTColor.background.ignoresSafeArea())
    }
}
