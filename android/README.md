# Seabuckthorn Field Survey – Ladakh

An offline-first Android application for systematic field survey of seabuckthorn
(*Hippophae rhamnoides*) plants in Ladakh.

**Developed by Stanzin Khenrab**
Krishi Vigyan Kendra – Leh, Ladakh
MIDH-SBM

---

## What it does

A surveyor opens the app, taps **New Survey**, and records one shrub per record:
location, a photograph, GPS coordinates, shrub type, height, fruit maturity,
berry measurements and ease of harvest. Everything is written to the device as it
is typed. No account, no internet, no server.

```
Welcome → New Survey → Surveyor Information → Survey Form
        → Review & Save → Survey Saved Successfully → New Survey
```

The three-dot menu on the welcome screen holds exactly three items: **Survey
Map**, **Export Data**, **About**.

## Data safety

This is the part of the app that matters most, so it is worth being explicit
about how records are protected:

| Risk | How it is handled |
| --- | --- |
| App closed or killed mid-form | The record is inserted as a draft the moment the form opens and rewritten within 400 ms of every keystroke. Leaving the screen flushes immediately, on an application-scoped coroutine that outlives the screen. |
| Phone restarts | Everything is in a Room/SQLite database in app storage. On restart the welcome screen offers to continue the unfinished draft. |
| Duplicate Survey IDs | Allocation and insertion happen in one database transaction, guarded by a unique index. If a restored database already holds an identifier, the allocator skips past it. Covered by tests, including a concurrency test. |
| GPS unavailable | The record saves without coordinates and shows `Location unavailable`; the position can be added later by editing the record. |
| Camera unavailable | The screen falls back to the device camera app. A survey is never blocked by a missing photo. |
| Interrupted photo capture | The camera writes to `<Survey ID>.pending.jpg` and only renames it to `<Survey ID>.jpg` once the capture completes. |
| Accidental deletion | Deletion always goes through a confirmation dialog that names the Survey ID. Nothing is ever removed silently. |
| Schema change | The database is built without `fallbackToDestructiveMigration`, so a future version must ship a migration rather than dropping field data. |

## Survey record

| # | Section | Fields |
| --- | --- | --- |
| 1 | Survey ID and Date | `SBT-YYYY-NNNN`, date and time — all automatic |
| 2 | Location Information | District, Block, Village, Site |
| 3 | Photo | Camera capture, stored as `<Survey ID>.jpg` |
| 4 | GPS Information | Latitude, Longitude, Altitude (m), accuracy, status |
| 5 | Shrub Type | Hardwood / Soft wood / Mixed |
| 6 | Plant Height | Value + unit (metres or feet) |
| 7 | Dominant Fruit Maturity Stage | Unripe / Intermediate / Ripe / Overripe, plus Harvest Date |
| 8 | Berry Characteristics | Berry Diameter (mm), TSS (°Brix) |
| 9 | Ease of Harvest | Easy / Medium / Hard |

Surveyor name, designation and organization (default **KVK Leh**, any other
value can be typed) are entered once and carried into every later survey, as are
the last district and block.

## Export

CSV (mandatory), Excel `.xlsx` and JSON, written through the Android Storage
Access Framework so the file lands wherever the surveyor chooses, or straight
into the share sheet. Columns:

```
Survey ID, Surveyor Name, Designation, Organization, Date, Time, District,
Block, Village, Site, Photo Filename, Latitude, Longitude, Altitude (m),
GPS Accuracy (m), Shrub Type, Plant Height, Plant Height Unit,
Dominant Fruit Maturity Stage, Harvest Date, Berry Diameter (mm),
TSS (°Brix), Ease of Harvest, Record Status
```

Photo file names always equal the Survey ID, so an exported table and the photo
folder can be matched by name alone.

The `.xlsx` writer produces the OOXML package directly (a small ZIP of XML
parts) instead of bundling a desktop spreadsheet library: the export is a few
kilobytes of code, needs no native libraries and cannot exhaust memory on an
entry-level phone. Numeric columns are written as real numbers, and the header
row is bold and frozen.

## Survey map

Two views, switchable from the app bar:

- **Tile map** — OpenStreetMap through osmdroid. Tiles are cached in app storage,
  so an area that has been viewed once keeps working without a network.
- **Coordinate view** — a plain latitude/longitude plot drawn on a canvas. It
  needs no tiles at all and is always available.

Tapping a point shows Survey ID, village, survey date, maturity stage, latitude
and longitude, with a link into the full record.

## Technology

Kotlin · Jetpack Compose · Material 3 · Room · CameraX · platform
`LocationManager` · Coroutines · MVVM · Storage Access Framework.

- `minSdk 24`, `targetSdk 35`, `compileSdk 35` — runs on the Android 7 handsets
  still common in the field as well as current phones.
- No Firebase, no Google Play services, no cloud dependency. `LocationManager`
  is used rather than the fused provider precisely so the app works on devices
  with old or missing Play services.
- Light theme only, by design: the form is read outdoors in strong sunlight.

### Layout

```
app/src/main/java/com/kvkleh/sbtsurvey/
├── data/
│   ├── db/          Room entities, DAO, database
│   ├── repo/        SurveyRepository — the only door to storage
│   ├── export/      CSV, XLSX and JSON writers, share/SAF plumbing
│   ├── photo/       PhotoStore — photo folder and pending-capture handling
│   ├── location/    LocationService — GPS status and fixes
│   ├── prefs/       Sticky surveyor profile and last location
│   ├── SurveyOptions.kt    Controlled vocabulary and Ladakh location lists
│   ├── SurveyValidator.kt  Field rules (pure, unit tested)
│   └── Formats.kt          Shared date/number formatting
└── ui/
    ├── home/ form/ review/ list/ detail/ camera/ map/ export/ about/
    ├── components/  Shared form controls and status pills
    ├── theme/       Colour, type and shape
    └── nav/         Routes and NavHost
```

## Building

Open the `android/` folder in Android Studio (Ladybug or newer) and run, or from
the command line:

```bash
cd android
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
```

A release APK (`./gradlew assembleRelease`) is unsigned; sign it with your own
keystore before distributing it to field staff.

CI (`.github/workflows/android-build.yml`) runs the unit tests and builds both
APKs on every push, and uploads them as downloadable build artifacts.

## Tests

- `SurveyDaoTest` — identifiers increment, never duplicate, survive concurrent
  starts, skip identifiers already present, and are never reissued after a
  deletion.
- `SurveyValidatorTest` — required fields, unit-dependent height limits, and the
  rule that a missing photo or GPS fix warns but never blocks a save.
- `ExportFormatTest` — column set and order, CSV quoting, the XLSX package parts
  Excel requires, numeric cells, and photo-to-Survey-ID correspondence.

## Permissions

| Permission | Why |
| --- | --- |
| `CAMERA` | Photograph the shrub |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Record plot coordinates |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Map tiles only; the survey workflow never uses them |

All three are optional in practice — the app refuses none of its work when they
are declined.
