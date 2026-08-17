import SwiftUI

/// A numbered section of the survey form. Each section is its own card so the form
/// reads as a checklist while scrolling.
struct SectionCard<Content: View>: View {

    var number: Int?
    var symbol: String?
    var title: String
    var subtitle: String?
    var trailing: AnyView?
    @ViewBuilder var content: () -> Content

    init(
        number: Int? = nil,
        symbol: String? = nil,
        title: String,
        subtitle: String? = nil,
        trailing: AnyView? = nil,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.number = number
        self.symbol = symbol
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .center, spacing: 12) {
                if let number {
                    Text("\(number)")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(SBTColor.onPrimaryContainer)
                        .frame(width: 30, height: 30)
                        .background(SBTColor.primaryContainer, in: Circle())
                } else if let symbol {
                    Image(systemName: symbol)
                        .font(.title3)
                        .foregroundStyle(SBTColor.primary)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(SBTColor.ink)
                    if let subtitle {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(SBTColor.inkSoft)
                    }
                }
                Spacer(minLength: 8)
                if let trailing { trailing }
            }
            VStack(alignment: .leading, spacing: 12) {
                content()
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(SBTColor.surface, in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
                .stroke(SBTColor.outline.opacity(0.6), lineWidth: 1)
        )
    }
}

/// Text field with a required marker, helper text and a large touch target.
struct SBTTextField: View {

    let title: String
    @Binding var text: String
    var required: Bool = false
    var supportingText: String?
    var errorText: String?
    var keyboard: UIKeyboardType = .default
    var autocapitalization: TextInputAutocapitalization = .words

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(required ? "\(title) *" : title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(SBTColor.inkSoft)
            TextField("", text: $text)
                .font(.body)
                .keyboardType(keyboard)
                .textInputAutocapitalization(autocapitalization)
                .autocorrectionDisabled()
                .padding(.horizontal, 14)
                .frame(minHeight: SBTMetrics.touchTarget)
                .background(
                    SBTColor.surface,
                    in: RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                        .stroke(errorText == nil ? SBTColor.outline : SBTColor.danger, lineWidth: 1.5)
                )
            if let helper = errorText ?? supportingText {
                Text(helper)
                    .font(.caption)
                    .foregroundStyle(errorText == nil ? SBTColor.inkSoft : SBTColor.danger)
            }
        }
    }
}

/// A text field with a suggestion menu. Values can be picked from the list or typed
/// by hand, which is what field work needs: a village that is on no list must still
/// be recordable.
struct SBTPickerField: View {

    let title: String
    @Binding var text: String
    let options: [String]
    var required: Bool = false
    var supportingText: String?
    var errorText: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(required ? "\(title) *" : title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(SBTColor.inkSoft)
            HStack(spacing: 0) {
                TextField("", text: $text)
                    .font(.body)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                    .padding(.leading, 14)
                Menu {
                    if options.isEmpty {
                        Text("No suggestions – type the name")
                    }
                    ForEach(options, id: \.self) { option in
                        Button {
                            text = option
                        } label: {
                            if option == text {
                                Label(option, systemImage: "checkmark")
                            } else {
                                Text(option)
                            }
                        }
                    }
                } label: {
                    Image(systemName: "chevron.down")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(SBTColor.primary)
                        .frame(width: 52, height: SBTMetrics.touchTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Show \(title) options")
            }
            .frame(minHeight: SBTMetrics.touchTarget)
            .background(
                SBTColor.surface,
                in: RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
            )
            .overlay(
                RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                    .stroke(errorText == nil ? SBTColor.outline : SBTColor.danger, lineWidth: 1.5)
            )
            if let helper = errorText ?? supportingText {
                Text(helper)
                    .font(.caption)
                    .foregroundStyle(errorText == nil ? SBTColor.inkSoft : SBTColor.danger)
            }
        }
    }
}

/// Single-choice list rendered as full-width rows. Rows beat a compact picker
/// outdoors: the whole row is a large target that can be hit with gloves.
struct SBTChoiceRows: View {

    let options: [String]
    @Binding var selection: String?
    var errorText: String?

    var body: some View {
        VStack(spacing: 6) {
            ForEach(options, id: \.self) { option in
                let isSelected = option == selection
                Button {
                    selection = option
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                            .font(.title3)
                            .foregroundStyle(isSelected ? SBTColor.primary : SBTColor.inkSoft)
                        Text(option)
                            .font(.body.weight(isSelected ? .semibold : .regular))
                            .foregroundStyle(isSelected ? SBTColor.onPrimaryContainer : SBTColor.ink)
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .frame(minHeight: SBTMetrics.touchTarget)
                    .background(
                        isSelected ? SBTColor.primaryContainer : SBTColor.surface,
                        in: RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                            .stroke(
                                isSelected ? SBTColor.primary : SBTColor.outline,
                                lineWidth: isSelected ? 1.5 : 1
                            )
                    )
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
            }
            if let errorText {
                Text(errorText)
                    .font(.caption)
                    .foregroundStyle(SBTColor.danger)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

/// Compact segmented control, used for the plant height unit.
struct SBTSegmentedToggle: View {

    let options: [String]
    let labels: [String]
    @Binding var selection: String

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(options.enumerated()), id: \.element) { index, option in
                let isSelected = option == selection
                Button {
                    selection = option
                } label: {
                    Text(labels.indices.contains(index) ? labels[index] : option)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(isSelected ? Color.white : SBTColor.inkSoft)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(
                            isSelected ? SBTColor.primary : SBTColor.surface,
                            in: RoundedRectangle(cornerRadius: 9)
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .overlay(
            RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                .stroke(SBTColor.outline, lineWidth: 1)
        )
    }
}

/// A labelled key/value line, used on the review and detail screens.
struct DetailRow: View {

    let label: String
    let value: String
    var emphasise: Bool = false

    init(_ label: String, _ value: String, emphasise: Bool = false) {
        self.label = label
        self.value = value
        self.emphasise = emphasise
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(SBTColor.inkSoft)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(value.isEmpty ? "—" : value)
                .font(.body.weight(emphasise ? .semibold : .regular))
                .foregroundStyle(value.isEmpty ? SBTColor.inkSoft : SBTColor.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 3)
    }
}

/// The primary action button used at the bottom of each step.
struct PrimaryButton: View {

    let title: String
    var systemImage: String?
    var isBusy: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if isBusy {
                    ProgressView().tint(.white)
                } else if let systemImage {
                    Image(systemName: systemImage)
                }
                Text(title).font(.headline)
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: 58)
            .background(SBTColor.primary, in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius))
            .foregroundStyle(.white)
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
    }
}

/// The secondary action button, outlined rather than filled.
struct SecondaryButton: View {

    let title: String
    var systemImage: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title).font(.body.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .frame(minHeight: SBTMetrics.touchTarget)
            .background(SBTColor.surface, in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
                    .stroke(SBTColor.outline, lineWidth: 1.5)
            )
            .foregroundStyle(SBTColor.ink)
        }
        .buttonStyle(.plain)
    }
}
