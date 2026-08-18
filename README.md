# Seabuckthorn Field Survey – Ladakh

Offline-first Android application for systematic Seabuckthorn (*Hippophae rhamnoides*)
field surveys in Ladakh.

**Developed by Stanzin Khenrab**
Krishi Vigyan Kendra – Leh, Ladakh · MIDH-SBM

---

## What it does

A field researcher opens the app, taps **New Survey**, and records one shrub observation:
surveyor identity, administrative location, GPS coordinates and elevation, a photograph,
and the plant/fruit parameters. Everything is written to a local SQLite database as it is
typed. There is no login, no cloud account and no network requirement.

| Area | Behaviour |
|---|---|
| Storage | Room/SQLite, WAL journal, app-private photo directory |
| Survey ID | `SBT-<year>-<0001>`, allocated inside a database transaction — never duplicated |
| Autosave | Debounced write on every edit, immediate write on section change, photo, GPS fix, backgrounding and finish |
| GPS | `FusedLocationProviderClient`, high accuracy, retains the *best* fix, retry/refresh, never blocks the form |
| Photos | Camera writes straight into permanent storage via FileProvider; gallery imports are copied in |
| Map | Offline canvas map with satellite, terrain, street or plain-grid basemaps; markers always from the local database |
| Map export | Print-ready PDF sheet, georeferenced GeoTIFF (EPSG:3857) for ArcGIS/QGIS, plus JPG and PNG |
| Data export | CSV, Excel (.xlsx), and a complete ZIP package with photographs and a README |
| Sharing | Save to any folder via the system picker, or send through the Android Sharesheet — WhatsApp, Gmail, Telegram, Drive, Bluetooth |

## Navigation

```
Welcome  →  Dashboard  →  Survey Form  →  Save & Finish  →  Thank You  →  Survey List
                  ⋮ (top-right)
                  ├── Survey Map      (basemaps, and PDF / GeoTIFF / JPG / PNG export)
                  ├── Export Data     (CSV / Excel / ZIP — save or share)
                  └── About
```

Export and share sit on one screen rather than two menu entries: both produce the same
file, and only the last step differs — **Save** writes it wherever you choose through the
system file picker, **Share** hands it to the Android Sharesheet.

## Survey form sections

1. Survey ID (auto-generated, with device date and time)
2. Location — district, block, village, site
3. Photo — take, choose, preview, retake, delete
4. GPS & Elevation — latitude, longitude, altitude, accuracy, acquisition time
5. Shrub Type — Hardwood / Soft wood / Mixed
6. Plant Height — value plus `m | ft` unit selector, live conversion
7. Dominant Fruit Maturity Stage — Unriped / Intermediate / Ripened / Overripened, plus Harvest Date
8. Berry Diameter (mm)
9. TSS (°Brix)
10. Ease of Harvest — Easy / Medium / Hard
11. Fruit Shape Type — Round / Oval / Oblong / Elliptical / Cylindrical / Other

Surveyor Information (name, designation, organization) sits above section 1 and is stored
on every record.

## Download the app

Every push builds an APK through GitHub Actions and attaches it to the rolling
**[Latest build](../../releases/tag/latest)** release, so field staff always have one
stable download link. Tagged versions (`v1.0.0`, …) get their own permanent release.

To install on a phone or tablet:

1. Open the release page on the device and download the `.apk`.
2. Open the downloaded file.
3. Allow installation from this source when Android asks.

No internet connection is needed once the app is installed.

The APK is also attached to each workflow run under **Actions → Build APK → Artifacts**,
which is useful for testing a specific commit.

### Signed release builds (optional)

Without signing secrets the workflow publishes the debug-signed APK, which Android
installs normally. To publish a properly signed release build instead, create a keystore
and add four repository secrets:

| Secret | Value |
|---|---|
| `SBT_KEYSTORE_BASE64` | `base64 -w0 release-keystore.jks` |
| `SBT_KEYSTORE_PASSWORD` | Keystore password |
| `SBT_KEY_ALIAS` | Key alias |
| `SBT_KEY_PASSWORD` | Key password |

For local release builds, put the same values in a `keystore.properties` file at the
repository root (it is git-ignored):

```properties
storeFile=release-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Keep the keystore safe — Android requires every future update to be signed with the same
key.

## Building

Requirements: Android Studio Ladybug or newer, JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug      # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # JVM unit tests
./gradlew assembleRelease    # minified release build (configure signing first)
```

Instrumented database tests (need a device or emulator):

```bash
./gradlew connectedDebugAndroidTest
```

No API keys are required. The map uses OpenStreetMap raster tiles when a network happens
to be present and falls back to a coordinate grid when offline, so there is nothing to
configure.

## Project layout

```
app/src/main/java/com/kvkleh/sbtsurvey/
├── SbtApplication.kt          Application + hand-wired dependency container
├── MainActivity.kt            Single activity, Compose entry point
├── data/
│   ├── local/                 Room entities, DAO, database
│   └── repo/                  SurveyRepository — the only write path
├── domain/                    Option enums, unit conversion, Ladakh reference data
├── location/                  LocationController — best-fix GPS acquisition
├── photo/                     PhotoStore — permanent photo storage
├── export/                    CSV / XLSX / ZIP writers, Sharesheet launcher
├── map/                       Web-Mercator maths, tile cache
└── ui/                        Compose screens, components, theme, navigation
```

### Why some choices were made

* **Hand-written XLSX writer.** Apache POI adds tens of megabytes to an APK that has to
  be side-loaded over a slow connection in Leh. The writer emits a minimal but valid
  SpreadsheetML package that Excel, LibreOffice and Google Sheets all open, with numeric
  cells kept numeric.
* **Custom offline map instead of Google Maps.** The Maps SDK needs an API key and shows
  a blank grid with no connectivity. The canvas map draws survey markers from the local
  database, so coordinates are always visible in the field, and it can layer Esri
  satellite or terrain imagery underneath when a connection happens to be available.
* **One renderer for screen and export.** `MapComposer` paints the interactive map, the
  PDF page, the JPG/PNG and the GeoTIFF, so what a surveyor sees is exactly what gets
  printed. Drawing to a plain canvas keeps PDF text and symbols vector while only the
  imagery is raster.
* **Hand-written GeoTIFF.** A full geospatial stack would add tens of megabytes for one
  export format. The writer emits a GeoTIFF 1.8.2 raster whose coordinate system travels
  inside the file, verified against GDAL.
* **No DI framework.** The object graph is small and entirely local; keeping it explicit
  in `AppContainer` makes the data path obvious.
* **Intent-based camera capture.** `ActivityResultContracts.TakePicture` with a
  FileProvider destination writes the JPEG directly into permanent storage, which is what
  guarantees the photo stays linked to its Survey ID.

## Field acceptance checklist

Run these on both a phone and a tablet before handing the app to surveyors.

- [ ] Create a survey with the device in aeroplane mode
- [ ] Close the app halfway through data entry, reopen, confirm the draft is intact
- [ ] Force-stop the app from Android Settings, reopen, confirm no data loss
- [ ] Capture a photo, restart the device, confirm the thumbnail still resolves
- [ ] Acquire GPS on a tablet (expect a longer cold fix; the elapsed counter should run)
- [ ] Press Refresh GPS and confirm a new reading replaces the old one
- [ ] Confirm latitude, longitude and altitude appear on the detail screen
- [ ] Create several surveys and confirm every Survey ID is unique and sequential
- [ ] Export CSV, Excel and the ZIP package; open each on a computer
- [ ] Confirm all columns are populated and photographs are present in the ZIP
- [ ] Share an export through the Sharesheet (WhatsApp / email / Telegram / Drive)
- [ ] Rotate the device and check portrait, landscape and tablet layouts
- [ ] Edit a saved survey and confirm changes persist
- [ ] Delete a survey and confirm the confirmation dialog appears
- [ ] Deny camera and location permission and confirm the app stays usable

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Stamp each record with GPS coordinates |
| `CAMERA` | Photograph the surveyed shrub |
| `READ_EXTERNAL_STORAGE` (API ≤ 32 only) | Import an existing photo from the gallery |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Optional map tiles only; the app never uploads data |

Every permission is requested in context with an explanation, and denial degrades
gracefully — a survey can always be completed and saved.

## Data integrity

* Survey IDs are allocated and the record inserted in one transaction, with a uniqueness
  check that also survives a restore from backup.
* The database is copied to `files/backups/` on every cold start; the five most recent
  copies are kept.
* Photographs live in `files/survey_photos/` and are deleted only with their record.
* Exports are verified to be non-empty before being offered for sharing.
* Deletion always asks for confirmation.
