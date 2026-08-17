# Seabuckthorn Field Survey – Ladakh (iPhone & iPad)

The iOS build of the offline-first field survey app for seabuckthorn
(*Hippophae rhamnoides*) in Ladakh.

**Developed by Stanzin Khenrab**
Krishi Vigyan Kendra – Leh, Ladakh
MIDH-SBM

This is a native SwiftUI app, not a wrapper around the Android build. It follows
the same workflow, stores the same fields and exports the same columns, so records
collected on an iPhone, an iPad and an Android phone form one dataset.

---

## Before you start: what publishing an iOS app requires

Unlike Android, an iPhone app cannot simply be handed over as a file. To put this
on a device you need:

| | |
| --- | --- |
| A Mac | Xcode only runs on macOS. There is no supported way to build an iOS app from Windows or Linux. |
| Xcode 15 or newer | Free from the Mac App Store. |
| An Apple ID | Enough to install on **your own** device for 7 days at a time, for testing. |
| Apple Developer Program, ₹8,900/US$99 per year | Needed to install on other people's devices, to use TestFlight, and to publish on the App Store. |

For a handful of KVK field devices, TestFlight (part of the paid programme) is
usually the practical route: staff install from a link and get updates
automatically. For one or two devices you own, a free Apple ID and Xcode are
enough, with a re-install every 7 days.

## Opening the project

```bash
open ios/SeabuckthornSurvey.xcodeproj
```

Pick a simulator or a connected device and press Run. Set your team under
*Signing & Capabilities* before running on hardware.

`SeabuckthornSurvey.xcodeproj` is generated from the source tree by
`tools/generate_xcodeproj.py`. Adding a Swift file means adding the file and
re-running that script — CI fails if the project and the tree disagree. Nothing
else in the workflow depends on it: the project can be edited by hand in Xcode
too, as long as the script is re-run afterwards.

## What it does

A surveyor opens the app, taps **New Survey**, and records one shrub per record:
location, a photograph, GPS coordinates, shrub type, height, fruit maturity, berry
measurements and ease of harvest. Everything is written to the device as it is
typed. No account, no internet, no server.

```
Welcome → New Survey → Surveyor Information → Survey Form
        → Review & Save → Survey Saved Successfully → New Survey
```

The ⋯ menu on the welcome screen holds exactly three items: **Survey Map**,
**Export Data**, **About**.

## Data safety

| Risk | How it is handled |
| --- | --- |
| App closed or killed mid-form | The record is inserted as a draft the moment the form opens and rewritten 0.4 s after every keystroke. Leaving the screen flushes immediately. |
| Device restarts | Everything is in a SQLite database in the app's Documents folder. On restart the welcome screen offers to continue the unfinished draft. |
| Duplicate Survey IDs | Allocation and insertion happen inside one `BEGIN IMMEDIATE` transaction, guarded by a unique index. If a restored database already holds an identifier, the allocator skips past it. Covered by tests, including a concurrency test. |
| **App reinstalled or restored** | Photos are stored by **file name**, never by absolute path. An iOS app container is re-created with a new UUID on reinstall, so a stored absolute path would silently stop resolving — the folder is looked up at read time instead. |
| GPS unavailable | The record saves without coordinates and shows `Location unavailable`; the position can be added later by editing the record. |
| Camera unavailable | The screen says so and the survey continues. On a device with no camera the picker falls back to the photo library. |
| Interrupted photo write | The JPEG is written to a temporary file and moved onto `<Survey ID>.jpg` only once complete. |
| Accidental deletion | Deletion always goes through a confirmation dialog that names the Survey ID. Nothing is removed silently. |
| Database cannot be opened | The app still starts, says so, and refuses to pretend it is saving. |

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

Surveyor name, designation and organization (default **KVK Leh**, any other value
can be typed) are entered once and carried into every later survey, as are the last
district and block.

## Export

CSV, Excel `.xlsx` and JSON, through the system share sheet — which is also how a
file is saved into Files, iCloud Drive or a connected USB drive, or attached to an
email. The columns are identical to the Android build:

```
Survey ID, Surveyor Name, Designation, Organization, Date, Time, District,
Block, Village, Site, Photo Filename, Latitude, Longitude, Altitude (m),
GPS Accuracy (m), Shrub Type, Plant Height, Plant Height Unit,
Dominant Fruit Maturity Stage, Harvest Date, Berry Diameter (mm),
TSS (°Brix), Ease of Harvest, Record Status
```

Photo file names always equal the Survey ID, so an exported table and the photo
folder match by name alone.

The `.xlsx` writer builds the OOXML package directly — Foundation has no archive
writer, so the ZIP container (stored entries, CRC-32, central directory) is
assembled in `XLSXWriter.swift`. That avoids a third-party dependency for a file
format the app only ever writes, and produces the same workbook as the Android
build: numeric columns as real numbers, header row bold and frozen.

## Survey map

Two views, switchable from the navigation bar:

- **Map** — Apple Maps via MapKit, which caches the tiles it has loaded.
- **Coordinate view** — a latitude/longitude plot drawn with SwiftUI `Canvas`. It
  needs no tiles at all and is always available, which is the normal case in the
  field.

Tapping a point shows Survey ID, village, survey date, maturity stage, latitude and
longitude, with a link into the full record.

## Technology

Swift 5 · SwiftUI · MapKit · Core Location · SQLite · Combine · MVVM.

- **iOS 16.0 or newer**, iPhone and iPad, portrait on iPhone and any orientation on
  iPad. That covers the iPhone 8 and later.
- No third-party packages at all: nothing to resolve, nothing to break a build in a
  year's time.
- SQLite is used directly rather than Core Data or SwiftData, so the schema matches
  the Android build column for column and the Survey ID transaction is explicit.
- Light appearance on every device, by design: the form is read outdoors in strong
  sunlight.

### Layout

```
ios/
├── SeabuckthornSurvey.xcodeproj      generated by tools/generate_xcodeproj.py
├── tools/generate_xcodeproj.py
├── SeabuckthornSurvey/
│   ├── App/          app entry point and the dependency container
│   ├── Data/
│   │   ├── Survey.swift              the record
│   │   ├── SurveyDatabase.swift      SQLite schema, queries, ID transaction
│   │   ├── SurveyStore.swift         the only door to storage for the UI
│   │   ├── Export/                   CSV, XLSX (with its own ZIP writer), JSON
│   │   ├── PhotoStore.swift          photo folder, named by Survey ID
│   │   ├── LocationService.swift     Core Location status and fixes
│   │   ├── SurveyOptions.swift       controlled vocabulary and Ladakh lists
│   │   ├── SurveyValidator.swift     field rules (pure, unit tested)
│   │   └── Formats.swift             shared date and number formatting
│   ├── UI/                           screens and shared controls
│   └── Resources/Assets.xcassets     app icon, emblem, accent colour
└── SeabuckthornSurveyTests/
```

## Building from the command line

```bash
cd ios
xcodebuild test -project SeabuckthornSurvey.xcodeproj -scheme SeabuckthornSurvey \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

CI (`.github/workflows/ios-build.yml`) runs the unit tests on a simulator, builds
for a device, and checks that the generated project file still matches the source
tree.

## Tests

- `SurveyWorkflowTests` — the whole field workflow through the storage layer: new
  survey, surveyor details, location, photo stored under the Survey ID, GPS, plant
  data, auto-save, review, save, list, CSV export and delete, with the exported
  cells checked against what was entered. Separate cases cover the record with no
  GPS and no photo, and removing a photo.
- `SurveyDatabaseTests` — identifiers increment, never duplicate, survive twelve
  concurrent starts, skip identifiers already present and are never reissued after
  a deletion; plus a full-field round trip through SQLite.
- `SurveyValidatorTests` — required fields, unit-dependent height limits, and the
  rule that a missing photo or GPS fix warns but never blocks a save.
- `ExportFormatTests` — column set and order, CSV quoting and byte-order mark, the
  XLSX package parts Excel requires, numeric cells, JSON typing, and
  photo-to-Survey-ID correspondence.

## Permissions

| Permission | Why |
| --- | --- |
| Camera | Photograph the shrub |
| Location (when in use) | Record plot coordinates |

Both are optional in practice — the app refuses none of its work when they are
declined. There is no network permission because the survey workflow never uses
the network; map tiles are the only thing that benefits from one.
