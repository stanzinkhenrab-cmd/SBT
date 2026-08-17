import Foundation

/// Date, time and number formatting shared by the UI and the exporters, so a value
/// on screen and the same value in the CSV always read identically - and identically
/// to the Android build.
enum Formats {

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd-MM-yyyy"
        return formatter
    }()

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm:ss"
        return formatter
    }()

    private static let dateTimeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd-MM-yyyy HH:mm"
        return formatter
    }()

    private static let fileStampFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd-HHmm"
        return formatter
    }()

    static func date(_ value: Date?) -> String {
        guard let value else { return "" }
        return dateFormatter.string(from: value)
    }

    static func time(_ value: Date?) -> String {
        guard let value else { return "" }
        return timeFormatter.string(from: value)
    }

    static func dateTime(_ value: Date?) -> String {
        guard let value else { return "" }
        return dateTimeFormatter.string(from: value)
    }

    static func fileStamp(_ value: Date = Date()) -> String {
        fileStampFormatter.string(from: value)
    }

    /// Six decimals, roughly 0.1 m of resolution.
    static func coordinate(_ value: Double?) -> String {
        guard let value else { return "" }
        return String(format: "%.6f", value)
    }

    static func metres(_ value: Double?) -> String {
        guard let value else { return "" }
        return String(format: "%.1f", value)
    }

    /// Renders a number without a trailing `.0`, for round values such as `2`.
    static func number(_ value: Double?) -> String {
        guard let value, value.isFinite else { return "" }
        // %lld, not %d: on a 64-bit device %d would truncate the Int64 argument.
        if value == value.rounded(.down), abs(value) < 1e15 {
            return String(format: "%lld", Int64(value))
        }
        var text = String(format: "%.3f", value)
        while text.hasSuffix("0") { text.removeLast() }
        if text.hasSuffix(".") { text.removeLast() }
        return text
    }

    /// Parses a typed decimal, tolerating a comma decimal separator.
    static func parseNumber(_ text: String) -> Double? {
        let cleaned = text.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: ",", with: ".")
        guard !cleaned.isEmpty else { return nil }
        return Double(cleaned)
    }

    /// Local midnight for the given instant, used to store harvest dates.
    static func startOfDay(_ value: Date) -> Date {
        Calendar.current.startOfDay(for: value)
    }
}
