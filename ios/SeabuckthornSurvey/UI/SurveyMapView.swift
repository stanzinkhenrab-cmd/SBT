import SwiftUI
import MapKit

/// Saved survey positions.
///
/// Two views are available: an Apple Maps view, which caches the tiles it has
/// loaded and keeps working over an area already seen, and a plain coordinate plot
/// that needs no tiles at all. If map tiles cannot be fetched in the field the
/// surveyor switches view instead of losing the screen.
struct SurveyMapView: View {

    @Binding var path: [Route]
    @EnvironmentObject private var store: SurveyStore

    @State private var useTileMap = true
    @State private var selected: Survey?
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 34.1526, longitude: 77.5771),  // Leh
        span: MKCoordinateSpan(latitudeDelta: 1.2, longitudeDelta: 1.2)
    )

    private var points: [Survey] {
        store.savedSurveys.filter(\.hasLocation)
    }

    private var withoutLocation: Int {
        store.savedSurveys.count - points.count
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            if useTileMap {
                Map(coordinateRegion: $region, annotationItems: points) { survey in
                    MapAnnotation(
                        coordinate: CLLocationCoordinate2D(
                            latitude: survey.latitude ?? 0,
                            longitude: survey.longitude ?? 0
                        )
                    ) {
                        Button {
                            selected = survey
                        } label: {
                            Image(systemName: "mappin.circle.fill")
                                .font(.title)
                                .foregroundStyle(
                                    selected?.id == survey.id ? SBTColor.tertiary : SBTColor.primary
                                )
                                .background(Circle().fill(.white).padding(4))
                        }
                        .accessibilityLabel("Survey \(survey.surveyId)")
                    }
                }
                .ignoresSafeArea(edges: .bottom)
            } else {
                OfflinePlotView(surveys: points, selected: $selected)
            }

            VStack(spacing: 8) {
                if points.isEmpty {
                    Text("No survey has coordinates yet.\nRecord a GPS position on the survey form and the plot will appear here.")
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(SBTColor.inkSoft)
                        .padding(20)
                        .background(
                            SBTColor.surface,
                            in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius)
                        )
                }
                if let survey = selected {
                    callout(survey)
                }
            }
            .padding(12)
            .surveyContentWidth()
        }
        .navigationTitle("Survey Map")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    useTileMap.toggle()
                } label: {
                    Image(systemName: useTileMap ? "square.stack.3d.up" : "map")
                }
                .accessibilityLabel(
                    useTileMap ? "Switch to offline coordinate view" : "Switch to map view"
                )
            }
        }
        .onAppear {
            store.refresh()
            fitRegion()
        }
    }

    private func callout(_ survey: Survey) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(survey.surveyId)
                    .font(.headline)
                    .foregroundStyle(SBTColor.primary)
                Spacer()
                Button {
                    selected = nil
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(SBTColor.inkSoft)
                }
                .accessibilityLabel("Close")
            }
            DetailRow("Village", survey.village)
            DetailRow("Survey date", Formats.dateTime(survey.createdAt))
            DetailRow("Maturity stage", survey.maturityStage ?? "")
            DetailRow("Latitude", Formats.coordinate(survey.latitude))
            DetailRow("Longitude", Formats.coordinate(survey.longitude))
            if let altitude = survey.altitude {
                DetailRow("Altitude", "\(Formats.metres(altitude)) m")
            }
            Button("Open survey") {
                path.append(.detail(survey.id))
            }
            .font(.body.weight(.semibold))
            .foregroundStyle(SBTColor.primary)
            .padding(.top, 6)
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(16)
        .background(SBTColor.surface, in: RoundedRectangle(cornerRadius: SBTMetrics.cornerRadius))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
    }

    /// Frames every recorded point, with a little padding so none sits on the edge.
    private func fitRegion() {
        let latitudes = points.compactMap(\.latitude)
        let longitudes = points.compactMap(\.longitude)
        guard let minLat = latitudes.min(), let maxLat = latitudes.max(),
              let minLon = longitudes.min(), let maxLon = longitudes.max() else { return }
        region = MKCoordinateRegion(
            center: CLLocationCoordinate2D(
                latitude: (minLat + maxLat) / 2,
                longitude: (minLon + maxLon) / 2
            ),
            span: MKCoordinateSpan(
                latitudeDelta: max((maxLat - minLat) * 1.4, 0.01),
                longitudeDelta: max((maxLon - minLon) * 1.4, 0.01)
            )
        )
    }
}

/// A coordinate plot that needs no tiles, no network and no map framework.
///
/// This is the guaranteed view of the survey positions: it draws every recorded
/// point on a latitude/longitude grid scaled to the data. When map tiles cannot be
/// downloaded — the normal case in the field — the surveyor still sees where the
/// plots sit relative to one another.
struct OfflinePlotView: View {

    let surveys: [Survey]
    @Binding var selected: Survey?

    var body: some View {
        GeometryReader { proxy in
            let bounds = PlotBounds(surveys: surveys)
            ZStack(alignment: .bottomLeading) {
                Canvas { context, size in
                    guard let bounds else { return }

                    // Reference grid, purely to give a sense of scale.
                    var grid = Path()
                    for step in 0...4 {
                        let x = size.width * CGFloat(step) / 4
                        let y = size.height * CGFloat(step) / 4
                        grid.move(to: CGPoint(x: x, y: 0))
                        grid.addLine(to: CGPoint(x: x, y: size.height))
                        grid.move(to: CGPoint(x: 0, y: y))
                        grid.addLine(to: CGPoint(x: size.width, y: y))
                    }
                    context.stroke(grid, with: .color(SBTColor.outline), lineWidth: 1)

                    for survey in surveys {
                        let point = bounds.point(for: survey, in: size)
                        let isSelected = survey.id == selected?.id
                        let radius: CGFloat = isSelected ? 9 : 6
                        let marker = Path(
                            ellipseIn: CGRect(
                                x: point.x - radius,
                                y: point.y - radius,
                                width: radius * 2,
                                height: radius * 2
                            )
                        )
                        context.fill(
                            marker,
                            with: .color(isSelected ? SBTColor.tertiary : SBTColor.primary)
                        )
                        let inner = Path(
                            ellipseIn: CGRect(
                                x: point.x - radius / 3,
                                y: point.y - radius / 3,
                                width: radius / 1.5,
                                height: radius / 1.5
                            )
                        )
                        context.fill(inner, with: .color(.white))
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture(coordinateSpace: .local) { location in
                    guard let bounds else { return }
                    let hit = surveys.min { first, second in
                        bounds.distance(from: location, to: first, in: proxy.size)
                            < bounds.distance(from: location, to: second, in: proxy.size)
                    }
                    if let hit, bounds.distance(from: location, to: hit, in: proxy.size) < 44 {
                        selected = hit
                    } else {
                        selected = nil
                    }
                }

                if let bounds {
                    Text("Lat \(Formats.coordinate(bounds.minLat)) – \(Formats.coordinate(bounds.maxLat))  ·  Lon \(Formats.coordinate(bounds.minLon)) – \(Formats.coordinate(bounds.maxLon))")
                        .font(.caption2)
                        .foregroundStyle(SBTColor.inkSoft)
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(SBTColor.surfaceDim.opacity(0.92))
                } else {
                    Text("No coordinates recorded yet.")
                        .font(.body)
                        .foregroundStyle(SBTColor.inkSoft)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .background(SBTColor.surfaceDim)
    }
}

/// Latitude/longitude extent of the plotted records, padded so points never touch
/// the edge of the canvas.
struct PlotBounds {

    let minLat: Double
    let maxLat: Double
    let minLon: Double
    let maxLon: Double

    /// Minimum span in degrees, so a single point or a tight cluster still renders.
    private static let minimumSpan = 0.004
    private static let inset: CGFloat = 0.08

    init?(surveys: [Survey]) {
        let latitudes = surveys.compactMap(\.latitude)
        let longitudes = surveys.compactMap(\.longitude)
        guard var lowLat = latitudes.min(), var highLat = latitudes.max(),
              var lowLon = longitudes.min(), var highLon = longitudes.max() else { return nil }

        if highLat - lowLat < PlotBounds.minimumSpan {
            let centre = (highLat + lowLat) / 2
            lowLat = centre - PlotBounds.minimumSpan / 2
            highLat = centre + PlotBounds.minimumSpan / 2
        }
        if highLon - lowLon < PlotBounds.minimumSpan {
            let centre = (highLon + lowLon) / 2
            lowLon = centre - PlotBounds.minimumSpan / 2
            highLon = centre + PlotBounds.minimumSpan / 2
        }
        minLat = lowLat
        maxLat = highLat
        minLon = lowLon
        maxLon = highLon
    }

    func point(for survey: Survey, in size: CGSize) -> CGPoint {
        guard let latitude = survey.latitude, let longitude = survey.longitude else {
            return CGPoint(x: size.width / 2, y: size.height / 2)
        }
        let usableWidth = size.width * (1 - 2 * PlotBounds.inset)
        let usableHeight = size.height * (1 - 2 * PlotBounds.inset)
        let x = size.width * PlotBounds.inset
            + usableWidth * CGFloat((longitude - minLon) / (maxLon - minLon))
        // Latitude grows northwards, screen y grows downwards.
        let y = size.height * PlotBounds.inset
            + usableHeight * CGFloat(1 - (latitude - minLat) / (maxLat - minLat))
        return CGPoint(x: x, y: y)
    }

    func distance(from location: CGPoint, to survey: Survey, in size: CGSize) -> CGFloat {
        let target = point(for: survey, in: size)
        return abs(target.x - location.x) + abs(target.y - location.y)
    }
}
