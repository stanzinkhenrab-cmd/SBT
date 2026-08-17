import SwiftUI

@main
struct SeabuckthornSurveyApp: App {

    @StateObject private var environment = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(environment)
                .environmentObject(environment.store)
                .environmentObject(environment.locationService)
                // The form is read outdoors in strong Ladakh sunlight, where a dark
                // surface is much harder to see, so the app stays light on every
                // device rather than following the system appearance.
                .preferredColorScheme(.light)
                .tint(SBTColor.primary)
        }
    }
}

/// Holds the navigation stack for the whole workflow.
struct RootView: View {

    @EnvironmentObject private var environment: AppEnvironment
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            WelcomeView(path: $path)
                .navigationDestination(for: Route.self) { route in
                    destination(for: route)
                }
        }
        .alert(
            "Storage problem",
            isPresented: Binding(
                get: { environment.fatalMessage != nil },
                set: { if !$0 { environment.fatalMessage = nil } }
            ),
            actions: { Button("OK", role: .cancel) {} },
            message: {
                Text(environment.fatalMessage ?? "")
                    + Text("\n\nSurveys recorded now may not survive closing the app. Please report this before collecting data.")
            }
        )
    }

    @ViewBuilder
    private func destination(for route: Route) -> some View {
        switch route {
        case .surveyor(let id):
            SurveyorView(surveyRowId: id, path: $path)
        case .form(let id):
            SurveyFormView(surveyRowId: id, path: $path)
        case .review(let id):
            ReviewView(surveyRowId: id, path: $path)
        case .saved(let id):
            SavedConfirmationView(surveyRowId: id, path: $path)
        case .list:
            SavedSurveysView(path: $path)
        case .detail(let id):
            SurveyDetailView(surveyRowId: id, path: $path)
        case .map:
            SurveyMapView(path: $path)
        case .export:
            ExportView()
        case .about:
            AboutView()
        }
    }
}

/// Welcome → Surveyor → Form → Review → Saved, plus the three menu destinations.
/// Deliberately shallow: a surveyor recording many plants should never have to walk
/// back through a stack of screens.
enum Route: Hashable {
    case surveyor(Int64)
    case form(Int64)
    case review(Int64)
    case saved(Int64)
    case list
    case detail(Int64)
    case map
    case export
    case about
}
