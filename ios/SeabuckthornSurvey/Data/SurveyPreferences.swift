import Foundation

/// The surveyor details carried over from one survey to the next.
struct SurveyorProfile: Equatable {
    var name: String = ""
    var designation: String = ""
    var organization: String = SurveyOptions.defaultOrganization

    var isComplete: Bool {
        !name.isBlank && !designation.isBlank && !organization.isBlank
    }
}

/// The location a surveyor last worked in, pre-filled on the next record.
struct LastLocation: Equatable {
    var district: String = ""
    var block: String = ""
    var village: String = ""
    var site: String = ""
}

/// Small preference store. Surveyor identity and the last district or block are
/// re-used across records so a surveyor walking a plantation types only what changes.
final class SurveyPreferences {

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var profile: SurveyorProfile {
        get {
            SurveyorProfile(
                name: defaults.string(forKey: Key.name) ?? "",
                designation: defaults.string(forKey: Key.designation) ?? "",
                organization: {
                    let stored = defaults.string(forKey: Key.organization) ?? ""
                    return stored.isBlank ? SurveyOptions.defaultOrganization : stored
                }()
            )
        }
        set {
            defaults.set(newValue.name, forKey: Key.name)
            defaults.set(newValue.designation, forKey: Key.designation)
            defaults.set(newValue.organization, forKey: Key.organization)
        }
    }

    var lastLocation: LastLocation {
        get {
            LastLocation(
                district: defaults.string(forKey: Key.district) ?? "",
                block: defaults.string(forKey: Key.block) ?? "",
                village: defaults.string(forKey: Key.village) ?? "",
                site: defaults.string(forKey: Key.site) ?? ""
            )
        }
        set {
            defaults.set(newValue.district, forKey: Key.district)
            defaults.set(newValue.block, forKey: Key.block)
            defaults.set(newValue.village, forKey: Key.village)
            defaults.set(newValue.site, forKey: Key.site)
        }
    }

    private enum Key {
        static let name = "surveyor_name"
        static let designation = "surveyor_designation"
        static let organization = "surveyor_organization"
        static let district = "last_district"
        static let block = "last_block"
        static let village = "last_village"
        static let site = "last_site"
    }
}
