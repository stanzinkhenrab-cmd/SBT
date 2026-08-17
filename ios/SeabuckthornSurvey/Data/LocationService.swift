import Foundation
import CoreLocation

/// A field position, as shown on the form and stored with the record.
struct GpsFix: Equatable {
    let latitude: Double
    let longitude: Double
    /// Metres above sea level; `nil` when the fix carries no usable altitude.
    let altitude: Double?
    let accuracyM: Double?
    let capturedAt: Date
}

/// What the GPS card shows the surveyor.
enum GpsStatus: Equatable {
    /// Location permission has not been granted yet.
    case permissionRequired
    /// Location services are switched off on the device.
    case servicesDisabled
    /// Listening for a fix.
    case acquiring
    /// A fix is available.
    case ready(GpsFix)
    /// Listening produced nothing usable; the record can still be saved.
    case unavailable

    var fix: GpsFix? {
        if case .ready(let fix) = self { return fix }
        return nil
    }
}

/// Wrapper over Core Location.
///
/// Everything here works without a network: the survey only ever needs the GNSS
/// receiver, which is exactly what field work in Ladakh depends on.
@MainActor
final class LocationService: NSObject, ObservableObject {

    @Published private(set) var status: GpsStatus = .acquiring

    private let manager = CLLocationManager()
    private var best: CLLocation?
    private var isRunning = false

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = kCLDistanceFilterNone
        manager.activityType = .other
    }

    /// Begins listening. Safe to call repeatedly; a second call is a no-op.
    func start() {
        guard !isRunning else { return }
        guard CLLocationManager.locationServicesEnabled() else {
            status = .servicesDisabled
            return
        }
        switch manager.authorizationStatus {
        case .notDetermined:
            status = .acquiring
            manager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            status = .permissionRequired
            return
        default:
            break
        }
        isRunning = true
        status = .acquiring
        best = nil
        manager.startUpdatingLocation()
        // Seed from the last known position so the card is never empty while the
        // receiver warms up; a live fix supersedes it as soon as one arrives.
        if let cached = manager.location, cached.timestamp.timeIntervalSinceNow > -600 {
            offer(cached)
        }
    }

    func stop() {
        guard isRunning else { return }
        isRunning = false
        manager.stopUpdatingLocation()
    }

    /// Drops the current best fix so a fresh reading can be taken at the plant.
    func restart() {
        stop()
        best = nil
        status = .acquiring
        start()
    }

    /// Accepts a reading only when it is at least as good as the best so far, so
    /// the value on screen settles instead of jittering.
    private func offer(_ location: CLLocation) {
        guard location.horizontalAccuracy >= 0 else { return }
        if let current = best {
            let isNewer = location.timestamp.timeIntervalSince(current.timestamp) > 20
            let isMoreAccurate = location.horizontalAccuracy <= current.horizontalAccuracy
            guard isNewer || isMoreAccurate else { return }
        }
        best = location
        status = .ready(
            GpsFix(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                altitude: location.verticalAccuracy >= 0 ? location.altitude : nil,
                accuracyM: location.horizontalAccuracy,
                capturedAt: location.timestamp
            )
        )
    }
}

extension LocationService: CLLocationManagerDelegate {

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let latest = locations.last else { return }
        Task { @MainActor in self.offer(latest) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            // A transient failure while a fix is already on screen is not worth
            // reporting; the record keeps the position it has.
            if self.best == nil { self.status = .unavailable }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let authorization = manager.authorizationStatus
        Task { @MainActor in
            switch authorization {
            case .authorizedWhenInUse, .authorizedAlways:
                self.isRunning = false
                self.start()
            case .denied, .restricted:
                self.status = .permissionRequired
            case .notDetermined:
                self.status = .acquiring
            @unknown default:
                self.status = .acquiring
            }
        }
    }
}
