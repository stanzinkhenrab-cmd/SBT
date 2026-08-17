import SwiftUI

/// The numbered survey form. Everything typed here is written to the database
/// within a fraction of a second; the pill in the navigation bar reports the state.
struct SurveyFormView: View {

    let surveyRowId: Int64
    @Binding var path: [Route]

    @EnvironmentObject private var store: SurveyStore
    @State private var model: SurveyFormModel?

    var body: some View {
        Group {
            if let model {
                FormBody(model: model, surveyRowId: surveyRowId, path: $path)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .surveyBackground()
        .navigationTitle("Survey Form")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if model == nil, let survey = store.survey(id: surveyRowId) {
                model = SurveyFormModel(survey: survey, store: store)
            }
        }
        .onDisappear { model?.flush() }
    }

    private struct FormBody: View {

        @ObservedObject var model: SurveyFormModel
        let surveyRowId: Int64
        @Binding var path: [Route]

        @EnvironmentObject private var locationService: LocationService
        @EnvironmentObject private var store: SurveyStore
        @State private var showCamera = false
        @State private var showDatePicker = false
        @State private var cameraUnavailable = false

        var body: some View {
            ScrollView {
                VStack(spacing: 16) {
                    identitySection
                    locationSection
                    photoSection
                    gpsSection
                    shrubTypeSection
                    heightSection
                    maturitySection
                    berrySection
                    easeSection

                    if model.showErrors && !model.errors.isEmpty {
                        Text("\(model.errors.count) field(s) still need attention before saving.")
                            .font(.subheadline)
                            .foregroundStyle(SBTColor.danger)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    PrimaryButton(title: "Review & Save", systemImage: "checkmark.circle") {
                        if model.validateForReview() {
                            path.append(.review(surveyRowId))
                        }
                    }

                    Text("Your entries are stored on this device as you type. Nothing is sent anywhere.")
                        .font(.caption)
                        .foregroundStyle(SBTColor.inkSoft)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.bottom, 12)
                }
                .padding(16)
                .surveyContentWidth()
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    AutoSaveIndicator(state: model.autoSave)
                }
            }
            .onAppear { locationService.start() }
            .onDisappear { locationService.stop() }
            .onChange(of: locationService.status) { status in
                if let fix = status.fix { model.considerAutomatic(fix: fix) }
            }
            .fullScreenCover(isPresented: $showCamera) {
                CameraPicker(
                    onCapture: { image in
                        model.attachPhoto(image)
                        showCamera = false
                    },
                    onCancel: { showCamera = false }
                )
                .ignoresSafeArea()
            }
            .sheet(isPresented: $showDatePicker) {
                HarvestDateSheet(
                    date: model.survey.harvestDate ?? Date(),
                    onPick: { model.survey.harvestDate = Formats.startOfDay($0) },
                    onClear: { model.survey.harvestDate = nil }
                )
            }
            .alert("Camera unavailable", isPresented: $cameraUnavailable) {
                Button("OK", role: .cancel) {}
            } message: {
                Text("This device has no camera available. The survey can still be saved without a photo.")
            }
        }

        // MARK: - 1

        private var identitySection: some View {
            SectionCard(
                number: 1,
                title: "Survey ID and Date",
                subtitle: "Generated automatically – no typing needed"
            ) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Survey ID")
                        .font(.caption.weight(.medium))
                    Text(model.survey.surveyId)
                        .font(.title2.weight(.bold))
                }
                .foregroundStyle(SBTColor.onPrimaryContainer)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(
                    SBTColor.primaryContainer,
                    in: RoundedRectangle(cornerRadius: SBTMetrics.fieldCornerRadius)
                )
                DetailRow("Date", Formats.date(model.survey.createdAt))
                DetailRow("Time", Formats.time(model.survey.createdAt))
                DetailRow("Surveyor", model.survey.surveyorName)
            }
        }

        // MARK: - 2

        private var locationSection: some View {
            SectionCard(
                number: 2,
                title: "Location Information",
                subtitle: "District and block are remembered for the next survey"
            ) {
                SBTPickerField(
                    title: "District",
                    text: Binding(
                        get: { model.survey.district },
                        set: { newValue in
                            model.survey.district = newValue
                            model.districtChanged(to: newValue)
                        }
                    ),
                    options: SurveyOptions.districts,
                    required: true,
                    errorText: model.error(for: .district)
                )
                SBTPickerField(
                    title: "Block",
                    text: $model.survey.block,
                    options: SurveyOptions.blocks(for: model.survey.district)
                )
                SBTPickerField(
                    title: "Village",
                    text: $model.survey.village,
                    options: model.villageSuggestions,
                    required: true,
                    supportingText: "Type the name if it is not in the list",
                    errorText: model.error(for: .village)
                )
                SBTTextField(
                    title: "Site",
                    text: $model.survey.site,
                    supportingText: "Plantation, orchard or landmark, e.g. 'Riverbank plantation'"
                )
            }
        }

        // MARK: - 3

        private var photoSection: some View {
            SectionCard(
                number: 3,
                title: "Photo",
                subtitle: "Stored on this device as \(model.survey.surveyId).jpg"
            ) {
                let photoURL = store.photoURL(for: model.survey)
                if photoURL != nil {
                    SurveyPhotoView(url: photoURL)
                    HStack(spacing: 8) {
                        SecondaryButton(title: "Retake", systemImage: "arrow.triangle.2.circlepath") {
                            openCamera()
                        }
                        SecondaryButton(title: "Remove", systemImage: "trash") {
                            model.removePhoto()
                        }
                    }
                } else {
                    PrimaryButton(title: "Take Photo", systemImage: "camera") {
                        openCamera()
                    }
                    Text(
                        model.survey.photoFileName == nil
                            ? "No photo yet. A photo is recommended but not required to save."
                            : "The photo file for this survey is missing from the device."
                    )
                    .font(.caption)
                    .foregroundStyle(SBTColor.inkSoft)
                }
            }
        }

        private func openCamera() {
            if CameraPicker.isCameraAvailable {
                showCamera = true
            } else {
                cameraUnavailable = true
            }
        }

        // MARK: - 4

        private var gpsSection: some View {
            SectionCard(
                number: 4,
                title: "GPS Information",
                subtitle: "Captured automatically from this device – no internet needed",
                trailing: AnyView(GpsStatusPill(status: locationService.status))
            ) {
                DetailRow("Latitude", Formats.coordinate(model.survey.latitude), emphasise: true)
                DetailRow("Longitude", Formats.coordinate(model.survey.longitude), emphasise: true)
                DetailRow(
                    "Altitude",
                    model.survey.altitude.map { "\(Formats.metres($0)) m" } ?? "",
                    emphasise: true
                )
                if let accuracy = model.survey.accuracyM {
                    DetailRow("Accuracy", "± \(Formats.metres(accuracy)) m")
                }
                if let captured = model.survey.locationCapturedAt {
                    DetailRow("Fix taken", Formats.dateTime(captured))
                }

                Text(gpsHint)
                    .font(.caption)
                    .foregroundStyle(gpsHintIsError ? SBTColor.danger : SBTColor.inkSoft)
                    .frame(maxWidth: .infinity, alignment: .leading)

                SecondaryButton(
                    title: model.survey.hasLocation ? "Update Location" : "Record Location",
                    systemImage: "location.circle"
                ) {
                    if let fix = locationService.status.fix {
                        model.apply(fix: fix)
                    } else {
                        model.unlockLocation()
                        locationService.restart()
                    }
                }
            }
        }

        private var gpsHint: String {
            switch locationService.status {
            case .ready(let fix):
                return model.survey.hasLocation
                    ? ""
                    : "A fix is available (± \(Formats.metres(fix.accuracyM)) m). Tap below to record it."
            case .acquiring:
                return "Searching for satellites…"
            case .permissionRequired:
                return "Allow location access to record coordinates. The survey can still be saved and the position added later."
            case .servicesDisabled:
                return "Location services are switched off. Turn them on, then tap Update Location. The survey can be saved without coordinates."
            case .unavailable:
                return "No position yet. Move to open sky and tap Update Location. Coordinates can also be added later by editing the record."
            }
        }

        private var gpsHintIsError: Bool {
            switch locationService.status {
            case .permissionRequired, .servicesDisabled, .unavailable: return true
            default: return false
            }
        }

        // MARK: - 5 to 9

        private var shrubTypeSection: some View {
            SectionCard(number: 5, title: "Shrub Type") {
                SBTChoiceRows(
                    options: SurveyOptions.shrubTypes,
                    selection: $model.survey.shrubType,
                    errorText: model.error(for: .shrubType)
                )
            }
        }

        private var heightSection: some View {
            SectionCard(number: 6, title: "Plant Height") {
                SBTTextField(
                    title: "Plant Height",
                    text: $model.heightText,
                    required: true,
                    supportingText: "Height of the shrub in \(SurveyOptions.heightUnitLabel(model.survey.plantHeightUnit))",
                    errorText: model.error(for: .plantHeight),
                    keyboard: .decimalPad
                )
                Text("Unit")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(SBTColor.inkSoft)
                SBTSegmentedToggle(
                    options: SurveyOptions.heightUnits,
                    labels: ["metres (m)", "feet (ft)"],
                    selection: $model.survey.plantHeightUnit
                )
            }
        }

        private var maturitySection: some View {
            SectionCard(number: 7, title: "Dominant Fruit Maturity Stage") {
                SBTChoiceRows(
                    options: SurveyOptions.maturityStages,
                    selection: $model.survey.maturityStage,
                    errorText: model.error(for: .maturityStage)
                )
                Text("Harvest Date")
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(SBTColor.inkSoft)
                    .padding(.top, 4)
                SecondaryButton(
                    title: model.survey.harvestDate.map(Formats.date) ?? "Select harvest date",
                    systemImage: "calendar"
                ) {
                    showDatePicker = true
                }
            }
        }

        private var berrySection: some View {
            SectionCard(
                number: 8,
                title: "Berry Characteristics",
                subtitle: "Measured on a representative sample of berries"
            ) {
                SBTTextField(
                    title: "Berry Diameter (mm)",
                    text: $model.berryDiameterText,
                    supportingText: "Decimals allowed, for example 6.5",
                    errorText: model.error(for: .berryDiameter),
                    keyboard: .decimalPad
                )
                SBTTextField(
                    title: "TSS (°Brix)",
                    text: $model.tssText,
                    supportingText: "Refractometer reading, for example 11.2",
                    errorText: model.error(for: .tss),
                    keyboard: .decimalPad
                )
            }
        }

        private var easeSection: some View {
            SectionCard(number: 9, title: "Ease of Harvest") {
                SBTChoiceRows(
                    options: SurveyOptions.easeOfHarvest,
                    selection: $model.survey.easeOfHarvest
                )
            }
        }
    }
}

/// Date entry for the harvest date, with a way to clear it again.
struct HarvestDateSheet: View {

    @State var date: Date
    let onPick: (Date) -> Void
    let onClear: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack {
                DatePicker(
                    "Harvest date",
                    selection: $date,
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .padding()
                Spacer()
            }
            .navigationTitle("Harvest Date")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Clear") {
                        onClear()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Set date") {
                        onPick(date)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }
}
