# Seabuckthorn Field Survey – Ladakh

A professional, **100% offline-first** Android application for scientific field survey and data
collection of Seabuckthorn (*Hippophae rhamnoides* L.) in the cold-desert region of Ladakh.

Developed by **Stanzin Khenrab**, Krishi Vigyan Kendra – Leh, Ladakh (MIDH-SBM).

---

## 1. What this app does

Field staff use it to record standardized, per-shrub observations — site and land-use context,
GPS location, shrub morphology, fruit phenology and maturity staging, individual berry
measurements, fruit quality/juice parameters, environmental context, and categorized
photographs — entirely without an internet connection. GPS, camera, the local database,
calculations, search/filter, and every export/backup format work offline. The only place
internet could ever matter is an optional, explicitly user-initiated cloud-sync feature, which
is **not implemented** — the app requests no `INTERNET` permission at all.

## 2. Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite) for the local database
- Android `LocationManager` (GPS provider only — **no Google Play Services / Fused Location
  dependency**, so it works on rugged/budget field devices without Play Services)
- CameraX for in-app photo capture
- DataStore for app preferences / onboarding state
- A hand-written, dependency-free `.xlsx` (OOXML) writer and CSV/KML/GeoJSON exporters — no
  Apache POI or other heavy export library
- Manual dependency injection via a single `SeabuckthornApp` container (no Hilt/Koin — the app
  is small enough that a DI framework would be unnecessary weight)

## 3. Project layout

```
app/src/main/java/com/kvk/leh/seabuckthorn/
├── data/
│   ├── local/            Room entities, DAOs, AppDatabase
│   ├── repository/       SurveyRepository — the single point of DB access
│   ├── preferences/      AppPreferences (DataStore): onboarding + surveyor profile
│   ├── photo/            PhotoStorage — on-disk photo file layout
│   ├── export/           CsvExporter, XlsxWriter/XlsxExporter, KmlExporter, GeoJsonExporter
│   └── backup/           BackupManager — zip-based full backup/restore
├── domain/
│   ├── calculations/     CanopyCalculations, DescriptiveStatistics (berry stats)
│   ├── validation/       Validators, ScientificRanges
│   └── model/            Enums (controlled vocabularies), SurveyRecord, DashboardStats
├── location/              LocationTracker (GPS)
├── camera/                CameraController, WatermarkUtil
├── util/                  DateUtils, BitmapUtils, AppRestarter
└── ui/
    ├── theme/             Seabuckthorn Material 3 theme (light + dark)
    ├── navigation/        NavGraph, Destinations
    ├── onboarding/        First-launch profile + permissions flow
    ├── dashboard/         KPI tiles + charts + "New Survey"
    ├── survey/wizard/     The 8-step survey form (this is most of the app)
    ├── survey/list/       Search & filter
    ├── survey/detail/     Read-only record view, edit/duplicate/delete
    ├── photo/             Full-screen CameraX capture overlay
    ├── map/                Offline coordinate scatter-plot + point list
    ├── export/             CSV / Excel / KML / GeoJSON screen
    ├── backup/             Backup & Restore screen
    ├── about/              About screen
    └── components/         Shared form fields, cards, charts, dialogs
```

## 4. Database schema

One `surveys` row per field visit to a shrub, with five 1:1 child tables and two 1:N child
tables, all foreign-keyed to `surveys.id` with `ON DELETE CASCADE`:

```
surveys (1) ──┬── (1) shrub_characteristics
              ├── (1) phenology
              ├── (1) fruit_quality
              ├── (1) environment
              ├── (N) berry_measurements   (Berry 1..N per survey)
              └── (N) photos               (categorized, GPS/date-tagged)
```

- **surveys** — survey ID/code, date/time, surveyor, site/land-use, ownership, GPS
  (lat/long/altitude/accuracy, nullable — GPS can be marked unavailable rather than invented),
  `isDraft` flag, remarks.
- **shrub_characteristics** — type, growth form, size (height, canopy N-S/E-W plus the
  *computed* average diameter and canopy area), age, stand density, and an extended set of
  vegetative and health parameters (branches, leaf density, thorn density, pest/disease
  incidence, drought stress, etc.).
- **phenology** — the dominant maturity stage, the four maturity percentages
  (unripe/intermediate/ripe/overripe), and ten phenological milestone dates, each paired with
  an `*Estimated` boolean ("date observed" vs. "date estimated").
- **berry_measurements** — one row per measured berry (length/width/weight); mean, min, max and
  sample standard deviation are computed on the fly (`DescriptiveStatistics`), never stored
  redundantly.
- **fruit_quality** — colour, firmness, shape, TSS (°Brix, the primary required field), pH,
  juice yield, optional lab parameters, and fruit yield/quality parameters.
- **environment** — slope, aspect, soil, water regime, distance to water, grazing intensity,
  land-use history.
- **photos** — file path, category (10 predefined categories), photo number, capture time, and
  the GPS position at capture time; watermarking is opt-in per photo and never touches the
  original file unless the user turns it on for that shot.

All calculated fields (`avgCanopyDiameterM`, `canopyAreaM2`) are computed in
`domain/calculations/CanopyCalculations.kt` and written once at save time; berry statistics are
always computed live from the raw per-berry rows so there is never a stale cached aggregate.

## 5. Export formats

From **Dashboard → ⋮ → Export Data**:

- **CSV** — one row per survey, every field flattened (survey info, GPS, shrub, phenology,
  berry summary stats, fruit quality, environment). Opens cleanly in Excel, R (`read.csv`),
  Python (`pandas.read_csv`), SPSS, or as a GIS attribute table.
- **Excel (.xlsx)** — a 9-sheet workbook: *Survey Information, GPS, Shrub Characteristics,
  Phenology, Fruit Measurements* (one row per individual berry — for statistical work), *Fruit
  Quality, Environmental Parameters, Photos, Metadata* (app version, export timestamp, record
  counts, developer credit).
- **KML** — one placemark per surveyed GPS point, for Google Earth.
- **GeoJSON** — a `FeatureCollection` of points with survey properties, for QGIS/ArcGIS.

All exports are written to app-private storage and only leave the device when the user taps
**Share** — nothing uploads automatically.

### Backup & Restore

**Backup Data** flushes the SQLite write-ahead log and zips the database file together with
every photo into a single `.sbtbackup` file. **Restore Data** replaces the current database and
photo set with the contents of a chosen backup (with a confirmation dialog, since it's
destructive) and then requires an app restart — the Restore screen prompts for this
automatically. Backups never leave the device except via the same Share/copy action the user
explicitly initiates.

## 6. Building the project

> **A note on this repository's origin:** this project was generated in a sandboxed CI-style
> environment whose network policy blocks `dl.google.com` — the host that serves the Android
> Gradle Plugin and every AndroidX/Compose/Room/CameraX artifact. That means **the build could
> not be compiled or APK-verified inside that sandbox**; the source was written and manually
> reviewed (package/import consistency, brace/paren balancing, API-shape checks against the
> pinned library versions) but has not been machine-compiled. Building it on a normal machine
> or in standard CI, both of which can reach Google's Maven repository, is expected to work
> using the steps below — but budget time for the first real build to surface anything that
> manual review missed, and treat this as source ready for that first build/test pass, not as a
> pre-verified binary.

### Prerequisites

- Android Studio (Koala/2024.1 or newer) or a JDK 17 + Android SDK command-line setup
- Android SDK Platform 34, Build-Tools 34.x
- A device or emulator running Android 7.0 (API 24) or newer, ideally with a real GPS chip and
  camera for full feature testing

### Steps

```bash
git clone <this repo>
cd SBT
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # unsigned release build; sign before distributing
```

Or open the project root in Android Studio and let it sync — the Gradle wrapper
(`gradle/wrapper/gradle-wrapper.properties`) is pinned to Gradle 8.9, compatible with Android
Gradle Plugin 8.5.2.

No `local.properties` `sdk.dir` is committed; Android Studio will create it automatically, or
set the `ANDROID_HOME` environment variable before running `./gradlew` from the command line.

### Signing a release build

Add your keystore config to `app/build.gradle.kts` under a `signingConfigs` block (not included
here since keystores are secrets that must never be committed) before running
`./gradlew assembleRelease`, or use Android Studio's Build → Generate Signed Bundle/APK flow.

## 7. Sample dataset

`sample_data/sample_surveys.csv` contains five illustrative survey records in the same column
layout the app's CSV export produces — useful for testing downstream analysis scripts or
verifying an R/Python/SPSS import pipeline before running it against real field exports.

## 8. Permissions

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — GPS capture only, via the platform
  `LocationManager`, never Google Play Services.
- `CAMERA` — in-app photo capture only.
- `VIBRATE` — local UI feedback.
- **No `INTERNET` permission is requested.**

Permissions are requested once during first-launch onboarding, with an explanation of why each
is needed, and are never re-requested once granted or declined.

## 9. What has and hasn't been device-tested

Implemented and reviewed: navigation, Room schema/DAOs/transactions, GPS capture via
`LocationManager`, CameraX capture + optional watermarking, the 8-step survey wizard with live
calculations (canopy area, berry statistics, maturity-percentage validation), search/filter,
dashboard aggregation, all four export formats, backup/restore (including the
close-database → restore → restart-process flow), dark mode, and the offline-only permission
model.

**Not yet run on a physical device or emulator** (see the build note above) — GPS hardware
behavior, CameraX device-specific quirks, exact multi-screen-size layout, and real APK
installation should be verified on the first build/test pass before field deployment.
