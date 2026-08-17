import SwiftUI

/// The read-only view of a record, shared by the review step and the saved-survey
/// detail screen so that both always show exactly the same fields.
struct SurveySummaryView: View {

    let survey: Survey
    let photoURL: URL?

    var body: some View {
        VStack(spacing: 16) {
            SectionCard(symbol: "person.crop.circle", title: "Surveyor") {
                DetailRow("Survey ID", survey.surveyId, emphasise: true)
                DetailRow("Date", Formats.date(survey.createdAt))
                DetailRow("Time", Formats.time(survey.createdAt))
                DetailRow("Surveyor Name", survey.surveyorName)
                DetailRow("Designation", survey.designation)
                DetailRow("Organization", survey.organization)
            }

            SectionCard(symbol: "mappin.and.ellipse", title: "Location") {
                DetailRow("District", survey.district)
                DetailRow("Block", survey.block)
                DetailRow("Village", survey.village)
                DetailRow("Site", survey.site)
                DetailRow("Latitude", Formats.coordinate(survey.latitude))
                DetailRow("Longitude", Formats.coordinate(survey.longitude))
                DetailRow("Altitude", survey.altitude.map { "\(Formats.metres($0)) m" } ?? "")
                if let accuracy = survey.accuracyM {
                    DetailRow("GPS accuracy", "± \(Formats.metres(accuracy)) m")
                }
            }

            SectionCard(symbol: "camera", title: "Photo") {
                if photoURL != nil {
                    SurveyPhotoView(url: photoURL)
                }
                DetailRow("Photo file", survey.photoFileName ?? "")
            }

            SectionCard(symbol: "leaf", title: "Plant") {
                DetailRow("Shrub Type", survey.shrubType ?? "")
                DetailRow(
                    "Plant Height",
                    survey.plantHeight.map { "\(Formats.number($0)) \(survey.plantHeightUnit)" } ?? ""
                )
                DetailRow("Dominant Fruit Maturity Stage", survey.maturityStage ?? "")
                DetailRow("Harvest Date", Formats.date(survey.harvestDate))
            }

            SectionCard(symbol: "drop", title: "Berries and harvest") {
                DetailRow(
                    "Berry Diameter",
                    survey.berryDiameterMm.map { "\(Formats.number($0)) mm" } ?? ""
                )
                DetailRow("TSS", survey.tssBrix.map { "\(Formats.number($0)) °Brix" } ?? "")
                DetailRow("Ease of Harvest", survey.easeOfHarvest ?? "")
            }
        }
    }
}

/// Compact one-line description used in lists and map callouts.
func surveySubtitle(_ survey: Survey) -> String {
    var parts = [Formats.dateTime(survey.createdAt)]
    if !survey.village.isBlank { parts.append(survey.village) }
    if let stage = survey.maturityStage, !stage.isBlank { parts.append(stage) }
    return parts.joined(separator: " · ")
}
