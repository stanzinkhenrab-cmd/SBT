# GeoStamp Camera

An Android app that burns a location card into your photos — place name, address,
coordinates, altitude, date/time and a note — then saves the result to your gallery.
It is the same idea as the "GPS Map Camera" stamp, rebuilt as a small, self-contained
app with four layout templates.

## What it does

- **Capture or import.** Take a picture with the in-app camera, pick one from the gallery,
  or share an image into the app from anywhere else.
- **Find the place.** Coordinates come from the phone's GPS. When you import a photo the
  app first reads the coordinates and capture time out of the picture's own EXIF data, so
  old photos get stamped with where they were actually taken. The place name and address
  come from the platform geocoder — no API key, no account.
- **Choose a template.** Four layouts, each with its own use:

  | Template | Look |
  | --- | --- |
  | Classic | Map thumbnail, place name, address, coordinates, date and a QR code on a dark card. |
  | Compact | One translucent bar across the bottom — place, coordinates and time. |
  | Ribbon | Accent-coloured band with a large place name and a QR code. |
  | Polaroid | Adds a printed footer *below* the photo instead of covering any of it. |

- **Tune it.** Edit the place, address and note; toggle the map, QR, coordinates, date and
  note lines; switch between decimal and DMS coordinates; pick an accent colour; scale the
  card; change the map zoom.
- **Download.** "Download" writes a full-resolution JPEG to `Pictures/GeoStamp`, which is
  where your gallery app will pick it up. "Share" hands the same image to any other app.

Everything is rendered on the device. The only network traffic is the OpenStreetMap tiles
behind the map thumbnail, and the app falls back to a drawn placeholder when it is offline.

## Building

You need JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
cd android
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

The release build is minified and — for convenience — signed with the debug key, so it
installs straight onto a phone. Swap in a real `signingConfig` in `app/build.gradle.kts`
before publishing anywhere.

Install with `adb install -r app/build/outputs/apk/release/app-release.apk`, or copy the
APK to the phone and open it (Android will ask you to allow installs from that source).

## Tests

```bash
./gradlew testDebugUnitTest
```

The renderer tests run under Robolectric with native (Skia) graphics, so they draw the real
thing and write one PNG per template to `app/build/reports/stamp-samples/`. Two opt-in flags
help when you want to eyeball a layout:

```bash
./gradlew testDebugUnitTest --rerun-tasks \
  -Dgeostamp.sampleImage=/path/to/photo.jpg \   # stamp a real photo instead of a gradient
  -Dgeostamp.liveMap=true                        # fetch real OSM tiles for the thumbnail
```

## Layout of the code

```
app/src/main/java/com/sbt/geostamp/
  MainActivity.kt          screen switching, permissions, share-intent entry point
  model/Stamp.kt           templates, stamp content, display options, formatting
  location/                GPS fix (platform LocationManager) and reverse geocoding
  map/StaticMap.kt         OSM tile fetch + stitch + map pin, with an offline fallback
  qr/QrCodes.kt            QR for the "open in maps" link
  render/StampRenderer.kt  the actual drawing — one function per template
  io/                      EXIF-aware photo loading, gallery writing, sharing
  ui/                      Compose screens and the editor view model
```

`StampRenderer` sizes everything from a single `sizeBase` derived from the photo, so a stamp
looks identical on the small on-screen preview and on the full-resolution save.

## Map tiles

The thumbnail uses `tile.openstreetmap.org`, which is a volunteer-funded service with a
[tile usage policy](https://operations.osmfoundation.org/policies/tiles/): fine for personal
and low-volume use, not for a widely distributed app. If you ship this, point
`StaticMap.tileUrlTemplate` at your own tile server or a commercial provider, and keep the
identifying `User-Agent`.

## Permissions

| Permission | Why |
| --- | --- |
| `CAMERA` | the in-app viewfinder |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | the coordinates on the stamp |
| `INTERNET` | map tiles only |
| `WRITE_EXTERNAL_STORAGE` (API ≤ 28) | saving to the gallery on older Android |

On Android 10 and newer the app writes through MediaStore, so no storage permission is asked
for. Picking a photo uses the system photo picker, which needs no permission either.
