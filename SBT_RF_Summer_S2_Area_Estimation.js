// =====================================================================
// SEABUCKTHORN (SBT) AREA ESTIMATION — LADAKH ROI
// Random Forest binary classification (SBT vs. Non-SBT)
// Latest available SUMMER Sentinel-2 Surface Reflectance (10 m grid)
// ---------------------------------------------------------------------
// Ground truth : projects/ee-stanzin-soil/assets/SBTV2 (SBT points only)
// Imagery      : COPERNICUS/S2_SR_HARMONIZED + COPERNICUS/S2_CLOUD_PROBABILITY
// Output grid  : 10 m, UTM zone 43N (EPSG:32643)
//
// HOW TO RUN: paste this whole script into the GEE Code Editor and
// press "Run". Console output, map layers and export tasks are created
// automatically. Start the export tasks from the "Tasks" tab.
//
// SPATIAL RESOLUTION — READ THIS:
//   * B2, B3, B4, B8 are NATIVE 10 m bands.
//   * B5, B6, B7, B8A, B11, B12 are NATIVE 20 m bands. They are
//     resampled (bilinear) onto the common 10 m processing grid so that
//     Random Forest can use every band at every 10 m pixel. Resampling
//     does NOT create 10 m detail: their true spatial resolution remains
//     20 m. Only Sentinel-2 is used — no Landsat/MODIS/Sentinel-3.
// =====================================================================


// =====================================================
// USER PARAMETERS
// =====================================================

var targetYear = 2026;

var summerStartMonth = 6;
var summerStartDay = 1;

var summerEndMonth = 9;
var summerEndDay = 30;

var maxCloudProbability = 40;        // s2cloudless probability (%) above which a pixel is cloud
var maxSceneCloudPercentage = 60;    // scene-level CLOUDY_PIXEL_PERCENTAGE filter

var numberOfTrees = 300;

var backgroundSamples = 3000;        // total pseudo-negative/background samples

var backgroundBuffer = 60;           // metres; no background sampled this close to an SBT GT point

var probabilityThreshold = 0.50;     // 0.50 | 0.60 | 0.70 | 0.80

var applyPostProcessing = true;

var minimumPatchPixels = 3;          // SBT patches smaller than this (10 m pixels, 8-connected) are removed

var randomSeed = 42;

var useFallbackYear = true;          // fall back to the previous summer if targetYear is inadequate

// Summer window of the target year (end date is inclusive)
var summerStart = ee.Date.fromYMD(targetYear, summerStartMonth, summerStartDay);
var summerEnd   = ee.Date.fromYMD(targetYear, summerEndMonth, summerEndDay);

// ---------- Additional (advanced) parameters ----------

// Fallback behaviour
var fallbackYear = targetYear - 1;   // most recent previous complete summer (2025)
var mixFallbackWithPrimary = false;  // true = merge targetYear + fallbackYear summers (NOT recommended)
var minImagesRequired = 10;          // min. scenes (after scene-cloud filter) for an adequate season
var minClearObservations = 3;        // min. cloud-free observations per pixel ...
var minClearCoverage = 0.90;         // ... over at least this fraction of the ROI

// Compositing: 'median' (default) or 'percentile'
var compositeMethod = 'median';
var compositePercentile = 40;        // used only when compositeMethod = 'percentile'

// Masking
var maskSnow = true;                 // mask SCL snow/ice (class 11)
var cloudBufferMeters = 50;          // dilation of cloud mask (0 = none)
var useProjectedShadowMask = false;  // geometric cloud-shadow projection (slower; SCL shadow is always used)
var darkNirThreshold = 0.15;         // for projected shadow mask
var shadowProjectionDistanceKm = 1;  // for projected shadow mask
var maxValidReflectance = 1.0;       // surface reflectance above this is treated as invalid

// Random Forest
var variablesPerSplit = null;        // null = default sqrt(number of predictors)
var minLeafPopulation = 1;
var bagFraction = 0.7;
var trainFraction = 0.70;            // 70 % training / 30 % validation

// Pseudo-negative sampling
var backgroundGridSize = 5000;           // metres; background spread evenly over grid cells of this size
var vegetatedBackgroundFraction = 0.25;  // share of background drawn from vegetated, non-GT pixels
var vegetatedBackgroundNdvi = 0.25;      // NDVI above which a pixel counts as "vegetated"

// Processing grid
var processingCrs = 'EPSG:32643';    // WGS 84 / UTM zone 43N (covers 72°E–78°E)
var processingScale = 10;            // metres — Sentinel-2 native 10 m grid

// Exports
var exportFolder = 'GEE_SBT_Ladakh';
var exportPrefix = 'SBT_Ladakh';
var gtExportFormat = 'CSV';          // 'CSV', 'SHP', 'GeoJSON' or 'KML'


// =====================================================================
// 0. PREDICTOR VARIABLES (edit this list to change the RF inputs)
// =====================================================================
// Available candidate bands (all computed below):
//   Reflectance : B2 B3 B4 B8 (10 m native) | B5 B6 B7 B8A B11 B12 (20 m native)
//   Indices     : NDVI EVI GNDVI SAVI NDRE NDMI NBR NDWI BSI
//   Ratios      : RE1_RED (B5/B4)  RE2_RE1 (B6/B5)  RE3_RED (B7/B4)
//                 NIRN_NIR (B8A/B8)  SWIR1_NIR (B11/B8)  SWIR2_NIR (B12/B8)
//   Summer phenology (same summer only): NDVI_P90, NDVI_RANGE (P90 − P10)
//   Terrain (Copernicus GLO-30 DEM, 30 m): ELEVATION, SLOPE
//
// Notes on the default selection:
//   * Red-edge (B5–B7, NDRE, RE ratios) separates SBT's chlorophyll/canopy
//     structure from crops, grass and riparian trees.
//   * SWIR (B11, B12, NDMI, NBR, BSI) separates leaf water content and
//     bare soil/rock/built-up surfaces; NDWI separates water.
//   * NDVI_P90 / NDVI_RANGE use only the selected summer's images and help
//     separate perennial SBT shrubs (green all summer) from crops that are
//     harvested in August–September.
//   * SWIR1_NIR and SWIR2_NIR are monotonic transforms of NDMI and NBR, so
//     they give a Random Forest identical splits — they are left out.
//   * ELEVATION/SLOPE are left out by default (30 m, non-spectral, and they
//     can learn the field-sampling pattern rather than SBT itself).
var predictorBands = [
  'B2', 'B3', 'B4', 'B8',                    // native 10 m
  'B5', 'B6', 'B7', 'B8A', 'B11', 'B12',     // native 20 m (resampled to 10 m grid)
  'NDVI', 'EVI', 'GNDVI', 'SAVI', 'NDRE', 'NDMI', 'NBR', 'NDWI', 'BSI',
  'RE1_RED', 'RE2_RE1', 'RE3_RED', 'NIRN_NIR',
  'NDVI_P90', 'NDVI_RANGE'
  // , 'SWIR1_NIR', 'SWIR2_NIR'
  // , 'ELEVATION', 'SLOPE'
];

var classProperty = 'sbt_class';   // 1 = SBT, 0 = Non-SBT

var s2Bands10m = ['B2', 'B3', 'B4', 'B8'];
var s2Bands20m = ['B5', 'B6', 'B7', 'B8A', 'B11', 'B12'];
var s2Bands = ['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B8A', 'B11', 'B12'];

if (compositeMethod !== 'median' && compositeMethod !== 'percentile') {
  throw new Error("compositeMethod must be 'median' or 'percentile'.");
}


// =====================================================================
// 1. ROI
// =====================================================================
// North-West: Lat 35.136103, Lon 76.757353
// South-East: Lat 34.494454, Lon 77.731841
var roi = ee.Geometry.Rectangle([
  76.757353,
  34.494454,
  77.731841,
  35.136103
]);

// Client-side copy of the bounds (used only to size the background grid)
var roiWest = 76.757353, roiSouth = 34.494454, roiEast = 77.731841, roiNorth = 35.136103;

Map.centerObject(roi, 10);
Map.setOptions('HYBRID');


// =====================================================================
// 2. SEABUCKTHORN GROUND TRUTH (positive class only)
// =====================================================================
var sbtGT = ee.FeatureCollection(
  'projects/ee-stanzin-soil/assets/SBTV2'
);

// Only GT points inside the ROI are used anywhere in this script.
var sbtGTInRoi = sbtGT.filterBounds(roi);

print('=============== GROUND TRUTH ===============');
print('Total SBT GT points in asset:', sbtGT.size());
print('SBT GT points inside ROI:', sbtGTInRoi.size());
print('SBT GT points outside ROI (ignored):', sbtGT.size().subtract(sbtGTInRoi.size()));


// =====================================================================
// 3. SENTINEL-2 CLOUD / SHADOW / SNOW MASKING
// =====================================================================

// Cloud probability for one image: s2cloudless where available, otherwise
// a conservative SCL proxy (SCL 8/9 = cloud medium/high probability).
function getCloudProbability(img) {
  var scl = img.select('SCL');
  return ee.Image(ee.Algorithms.If(
    img.get('cloud_prob'),
    ee.Image(img.get('cloud_prob')).select('probability'),
    scl.eq(8).or(scl.eq(9)).multiply(100)
  )).rename('cloud_probability');
}

// Optional geometric cloud-shadow projection (s2cloudless tutorial approach).
function getProjectedShadow(img, cloudMask) {
  var notWater = img.select('SCL').neq(6);
  var darkPixels = img.select('B8').lt(darkNirThreshold * 1e4).and(notWater);
  var shadowAzimuth = ee.Number(90).subtract(ee.Number(img.get('MEAN_SOLAR_AZIMUTH_ANGLE')));
  var cloudProjection = cloudMask.directionalDistanceTransform(shadowAzimuth, shadowProjectionDistanceKm * 10)
    .reproject({crs: img.select('B2').projection(), scale: 100})
    .select('distance').mask();
  return cloudProjection.and(darkPixels);
}

// Full mask + scaling to surface reflectance (0–1).
function maskAndScaleS2(img) {
  var scl = img.select('SCL');

  // Clouds and cirrus
  var cloud = getCloudProbability(img).gt(maxCloudProbability).or(scl.eq(10));
  if (cloudBufferMeters > 0) {
    cloud = cloud.focalMax({radius: cloudBufferMeters, kernelType: 'circle', units: 'meters'});
  }

  // Cloud shadows (Sen2Cor SCL class 3, optionally + geometric projection)
  var shadow = scl.eq(3);
  if (useProjectedShadowMask) {
    shadow = shadow.or(getProjectedShadow(img, cloud));
  }

  // Snow / ice
  var snow = maskSnow ? scl.eq(11) : ee.Image(0);

  // No data / saturated or defective
  var invalid = scl.eq(0).or(scl.eq(1));

  var clear = cloud.or(shadow).or(snow).or(invalid).not();

  // Surface reflectance scaling. 10 m bands keep their native grid;
  // 20 m bands are bilinearly resampled when requested at 10 m.
  var sr10 = img.select(s2Bands10m).divide(10000);
  var sr20 = img.select(s2Bands20m).divide(10000).resample('bilinear');
  var sr = sr10.addBands(sr20).select(s2Bands).toFloat();

  // Valid surface reflectance only
  var validSR = sr.reduce(ee.Reducer.min()).gt(0)
    .and(sr.reduce(ee.Reducer.max()).lte(maxValidReflectance));

  return ee.Image(sr.updateMask(clear).updateMask(validSR)
    .copyProperties({source: img, exclude: ['cloud_prob']}))
    .set('system:time_start', img.get('system:time_start'));
}

// Lightweight cloud/shadow-free indicator, used ONLY for the season
// adequacy check (snow is not counted as "unclear" here, so that summer
// glaciers do not trigger an unnecessary fallback).
function clearObservationLite(img) {
  var scl = img.select('SCL');
  return getCloudProbability(img).lte(maxCloudProbability)
    .and(scl.neq(3)).and(scl.neq(10)).and(scl.neq(0)).and(scl.neq(1))
    .rename('clear').toUint8();
}


// =====================================================================
// 4. BUILD SUMMER SEASONS (primary = targetYear, fallback = previous year)
// =====================================================================

// Safe date-string summaries (return 'N/A' for empty collections)
function dateStrings(col) {
  return ee.List(col.aggregate_array('system:time_start')).sort()
    .map(function(t) { return ee.Date(t).format('YYYY-MM-dd'); });
}
function firstOrNA(list) { return ee.List(list).cat(['N/A']).get(0); }
function lastOrNA(list) { return ee.List(list).reverse().cat(['N/A']).get(0); }

function coverageFraction(clearCount) {
  return ee.Number(clearCount.gte(minClearObservations).rename('cov').reduceRegion({
    reducer: ee.Reducer.mean(),
    geometry: roi,
    crs: processingCrs,
    scale: 300,             // coarse scale is sufficient for an availability check
    maxPixels: 1e10,
    tileScale: 4
  }).get('cov'));
}

function buildSummerSeason(year) {
  var start = ee.Date.fromYMD(year, summerStartMonth, summerStartDay);
  var endInclusive = ee.Date.fromYMD(year, summerEndMonth, summerEndDay);
  var endExclusive = endInclusive.advance(1, 'day');

  var raw = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
    .filterBounds(roi)
    .filterDate(start, endExclusive);

  var filtered = raw.filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', maxSceneCloudPercentage));

  var cloudProb = ee.ImageCollection('COPERNICUS/S2_CLOUD_PROBABILITY')
    .filterBounds(roi)
    .filterDate(start, endExclusive);

  // Join SR with s2cloudless on system:index (outer join keeps SR images
  // without a cloud-probability match; those use the SCL proxy).
  var joined = ee.ImageCollection(ee.Join.saveFirst({matchKey: 'cloud_prob', outer: true}).apply({
    primary: filtered,
    secondary: cloudProb,
    condition: ee.Filter.equals({leftField: 'system:index', rightField: 'system:index'})
  })).map(function(img) {
    return img.set('has_cloud_prob', ee.Algorithms.If(img.get('cloud_prob'), 1, 0));
  });

  var masked = joined.map(maskAndScaleS2);

  var clearCount = joined.map(clearObservationLite)
    .merge(ee.ImageCollection([ee.Image.constant(0).toUint8().rename('clear')]))
    .sum();

  var nFiltered = filtered.size();
  var coverage = coverageFraction(clearCount);
  var adequate = nFiltered.gte(minImagesRequired).and(coverage.gte(minClearCoverage));

  return {
    year: year,
    start: start,
    end: endInclusive,
    raw: raw,
    filtered: filtered,
    joined: joined,
    masked: masked,
    clearCount: clearCount,
    nFiltered: nFiltered,
    coverage: coverage,
    adequate: adequate
  };
}

function seasonDiagnostics(rawCol, filteredCol) {
  var dates = dateStrings(filteredCol);
  return ee.Dictionary({
    'Images before cloud filtering': rawCol.size(),
    'Images after cloud filtering': filteredCol.size(),
    'Distinct acquisition dates': dates.distinct().size(),
    'Earliest acquisition date': firstOrNA(dates),
    'Latest acquisition date': lastOrNA(dates),
    // (-1 = no images in the window)
    'Median scene cloud %': ee.List(filteredCol.aggregate_array('CLOUDY_PIXEL_PERCENTAGE'))
      .cat([-1]).slice(0, filteredCol.size().max(1)).reduce(ee.Reducer.median())
  });
}

var primarySeason = buildSummerSeason(targetYear);
var fallbackSeason = buildSummerSeason(fallbackYear);

// Latest Sentinel-2 SR image actually available in GEE over the ROI
var nowDate = ee.Date(Date.now());
var latestAvailableDate = lastOrNA(dateStrings(
  ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
    .filterBounds(roi)
    .filterDate(nowDate.advance(-365, 'day'), nowDate.advance(1, 'day'))
));

print('=============== SENTINEL-2 AVAILABILITY ===============');
print('Image collection:', 'COPERNICUS/S2_SR_HARMONIZED (+ COPERNICUS/S2_CLOUD_PROBABILITY)');
print('Latest Sentinel-2 SR image available in GEE over ROI (any season):', latestAvailableDate);
print('Summer window (' + targetYear + '):',
  summerStart.format('YYYY-MM-dd').cat(' to ').cat(summerEnd.format('YYYY-MM-dd')));
print('Summer ' + targetYear + ' diagnostics:', seasonDiagnostics(primarySeason.raw, primarySeason.filtered));
print('Summer ' + targetYear + ' ROI fraction with >= ' + minClearObservations + ' clear observations:',
  primarySeason.coverage);
print('Summer ' + targetYear + ' adequate (>= ' + minImagesRequired + ' images AND coverage >= ' +
  minClearCoverage + ')?', primarySeason.adequate);
if (useFallbackYear) {
  print('Summer ' + fallbackYear + ' (fallback candidate) diagnostics:',
    seasonDiagnostics(fallbackSeason.raw, fallbackSeason.filtered));
  print('Summer ' + fallbackYear + ' ROI fraction with >= ' + minClearObservations + ' clear observations:',
    fallbackSeason.coverage);
}


// =====================================================================
// 5. SEASON SELECTION (summer only — seasons are never mixed by default)
// =====================================================================
var switchToFallback = ee.Number(useFallbackYear ? 1 : 0).eq(1)
  .and(ee.Number(primarySeason.adequate).not());

function chooseSeason(primaryObj, fallbackObj, mixedObj) {
  return ee.Algorithms.If(switchToFallback,
    mixFallbackWithPrimary ? mixedObj : fallbackObj,
    primaryObj);
}

var seasonLabel = ee.String(chooseSeason(
  'PRIMARY SEASON: SUMMER ' + targetYear,
  'FALLBACK SEASON: SUMMER ' + fallbackYear,
  'MIXED SEASON (explicitly enabled): SUMMER ' + targetYear + ' + SUMMER ' + fallbackYear
));
var seasonYearUsed = ee.String(chooseSeason(
  String(targetYear), String(fallbackYear), targetYear + '+' + fallbackYear));

var selectedRaw = ee.ImageCollection(chooseSeason(
  primarySeason.raw, fallbackSeason.raw, primarySeason.raw.merge(fallbackSeason.raw)));
var selectedFiltered = ee.ImageCollection(chooseSeason(
  primarySeason.filtered, fallbackSeason.filtered, primarySeason.filtered.merge(fallbackSeason.filtered)));
var selectedMasked = ee.ImageCollection(chooseSeason(
  primarySeason.masked, fallbackSeason.masked, primarySeason.masked.merge(fallbackSeason.masked)));
var selectedCoverage = ee.Number(chooseSeason(
  primarySeason.coverage, fallbackSeason.coverage,
  coverageFraction(primarySeason.clearCount.add(fallbackSeason.clearCount))));
var selectedAdequate = ee.Number(chooseSeason(
  primarySeason.adequate, fallbackSeason.adequate,
  selectedFiltered.size().gte(minImagesRequired).and(selectedCoverage.gte(minClearCoverage))));

var selectedDates = dateStrings(selectedFiltered);
var nImagesBefore = selectedRaw.size();
var nImagesAfter = selectedFiltered.size();
var earliestImageDate = firstOrNA(selectedDates);
var latestImageDate = lastOrNA(selectedDates);
var medianCloudPct = seasonDiagnostics(selectedRaw, selectedFiltered).get('Median scene cloud %');
var nWithCloudProb = selectedMasked.aggregate_sum('has_cloud_prob');

print('=============== SEASON USED FOR CLASSIFICATION ===============');
print(seasonLabel);
if (!useFallbackYear) {
  print('Note: useFallbackYear = false -> SUMMER ' + targetYear + ' is used even if inadequate.');
}
print('Number of summer images before cloud filtering:', nImagesBefore);
print('Number of summer images after cloud filtering:', nImagesAfter);
print('Earliest image date used:', earliestImageDate);
print('Latest image date used:', latestImageDate);
print('Median scene cloud % of selected imagery:', medianCloudPct);
print('Images with s2cloudless probability (others use SCL proxy):', nWithCloudProb);
print('Selected season meets adequacy criteria?', selectedAdequate);


// =====================================================================
// 6. SUMMER COMPOSITE + SPECTRAL PREDICTORS
// =====================================================================
var composite;
if (compositeMethod === 'percentile') {
  composite = selectedMasked.select(s2Bands)
    .reduce(ee.Reducer.percentile([compositePercentile]))
    .rename(s2Bands);
} else {
  composite = selectedMasked.select(s2Bands).median();
}
print('Composite method:', compositeMethod === 'percentile'
  ? 'percentile (p' + compositePercentile + ')' : 'median');

function addSpectralPredictors(img) {
  var bands = {
    BLUE: img.select('B2'), GREEN: img.select('B3'), RED: img.select('B4'),
    RE1: img.select('B5'), RE2: img.select('B6'), RE3: img.select('B7'),
    NIR: img.select('B8'), NIRN: img.select('B8A'),
    SWIR1: img.select('B11'), SWIR2: img.select('B12')
  };

  var ndvi  = img.normalizedDifference(['B8', 'B4']).rename('NDVI');
  var evi   = img.expression(
    '2.5 * (NIR - RED) / (NIR + 6 * RED - 7.5 * BLUE + 1)', bands).clamp(-2, 2).rename('EVI');
  var gndvi = img.normalizedDifference(['B8', 'B3']).rename('GNDVI');
  var savi  = img.expression(
    '(1 + L) * (NIR - RED) / (NIR + RED + L)',
    {NIR: bands.NIR, RED: bands.RED, L: 0.5}).rename('SAVI');
  var ndre  = img.normalizedDifference(['B8', 'B5']).rename('NDRE');
  var ndmi  = img.normalizedDifference(['B8', 'B11']).rename('NDMI');
  var nbr   = img.normalizedDifference(['B8', 'B12']).rename('NBR');
  var ndwi  = img.normalizedDifference(['B3', 'B8']).rename('NDWI');   // water
  var bsi   = img.expression(
    '((SWIR1 + RED) - (NIR + BLUE)) / ((SWIR1 + RED) + (NIR + BLUE))', bands).rename('BSI');  // bare soil/rock/built-up

  var ratios = ee.Image.cat([
    bands.RE1.divide(bands.RED).rename('RE1_RED'),     // B5/B4
    bands.RE2.divide(bands.RE1).rename('RE2_RE1'),     // B6/B5
    bands.RE3.divide(bands.RED).rename('RE3_RED'),     // B7/B4
    bands.NIRN.divide(bands.NIR).rename('NIRN_NIR'),   // B8A/B8
    bands.SWIR1.divide(bands.NIR).rename('SWIR1_NIR'), // B11/B8
    bands.SWIR2.divide(bands.NIR).rename('SWIR2_NIR')  // B12/B8
  ]);

  return img.addBands([ndvi, evi, gndvi, savi, ndre, ndmi, nbr, ndwi, bsi, ratios]);
}

// Within-summer NDVI phenology (selected summer only)
var ndviPercentiles = selectedMasked
  .map(function(img) { return img.normalizedDifference(['B8', 'B4']).rename('NDVI'); })
  .reduce(ee.Reducer.percentile([10, 90]));
var ndviP90 = ndviPercentiles.select('NDVI_p90').rename('NDVI_P90');
var ndviRange = ndviPercentiles.select('NDVI_p90')
  .subtract(ndviPercentiles.select('NDVI_p10')).rename('NDVI_RANGE');

// Optional terrain predictors (only used if listed in predictorBands)
var demCollection = ee.ImageCollection('COPERNICUS/DEM/GLO30').filterBounds(roi).select('DEM');
var elevation = demCollection.mosaic()
  .setDefaultProjection(demCollection.first().projection())
  .rename('ELEVATION');
var slope = ee.Terrain.slope(elevation).rename('SLOPE');

var featureStack = ee.Image.cat([
  addSpectralPredictors(composite),
  ndviP90, ndviRange, elevation, slope
]).clip(roi);

var predictorImage = featureStack.select(predictorBands);

// 1 where every predictor is valid (cloud/snow-free summer observation), else 0
var validMask = predictorImage.mask().reduce(ee.Reducer.min()).gt(0).rename('valid');

print('Predictor bands used by Random Forest (' + predictorBands.length + '):', predictorBands);


// =====================================================================
// 7. TRAINING DATA
//    SBT = 1 (field GT inside ROI)
//    NON_SBT = 0 (PSEUDO-NEGATIVE / BACKGROUND samples — NOT field data)
// =====================================================================
var samplingProjection = ee.Projection(processingCrs);

// ---- 7a. Positive samples: SBT GT points inside ROI ----
var sbtSamples = predictorImage.sampleRegions({
  collection: sbtGTInRoi.map(function(f) {
    return ee.Feature(f.geometry(), {sbt_class: 1, sample_type: 'SBT_ground_truth'});
  }),
  properties: [classProperty, 'sample_type'],
  projection: samplingProjection,
  scale: processingScale,
  tileScale: 4,
  geometries: true
})
  .filter(ee.Filter.notNull(predictorBands))   // drop points on masked pixels
  .distinct(predictorBands);                    // drop points falling in the same 10 m pixel

// ---- 7b. Pseudo-negative / background samples ----
// Exclusion zone: buffer around every SBT GT point inside the ROI
var exclusionZone = ee.Image(0).byte().paint(
  sbtGTInRoi.map(function(f) { return f.buffer(backgroundBuffer); }), 1);
var backgroundAllowed = exclusionZone.eq(0);

// Grid cells (in UTM metres) used to spread background samples over the ROI
var pixelXY = ee.Image.pixelCoordinates(samplingProjection);
var gridCell = pixelXY.select('x').divide(backgroundGridSize).floor().multiply(100000)
  .add(pixelXY.select('y').divide(backgroundGridSize).floor())
  .toInt32().rename('grid_cell');

// Approximate number of grid cells covering the ROI (client-side sizing only)
var midLatRad = ((roiNorth + roiSouth) / 2) * Math.PI / 180;
var roiWidthM = (roiEast - roiWest) * 111320 * Math.cos(midLatRad);
var roiHeightM = (roiNorth - roiSouth) * 110574;
var nGridCells = (Math.ceil(roiWidthM / backgroundGridSize) + 1) *
                 (Math.ceil(roiHeightM / backgroundGridSize) + 1);

var nVegetatedBackground = Math.round(backgroundSamples * vegetatedBackgroundFraction);
var nRandomBackground = backgroundSamples - nVegetatedBackground;
// Over-sample per cell (cells can be partly masked/outside ROI), then trim
var pointsPerCell = Math.max(1, Math.ceil(nRandomBackground * 2 / nGridCells));

var sampleProps = predictorBands.concat([classProperty, 'sample_type']);

// (i) Spatially distributed random background: stratified by grid cell
var randomBackground = predictorImage.addBands(gridCell)
  .updateMask(backgroundAllowed)
  .stratifiedSample({
    numPoints: pointsPerCell,
    classBand: 'grid_cell',
    region: roi,
    projection: samplingProjection,
    scale: processingScale,
    seed: randomSeed,
    dropNulls: true,
    tileScale: 16,
    geometries: true
  })
  .randomColumn('shuffle', randomSeed)
  .sort('shuffle')
  .limit(nRandomBackground)
  .map(function(f) {
    return f.set(classProperty, 0, 'sample_type', 'pseudo_negative_random');
  })
  .select(sampleProps);

// (ii) Vegetated background ("hard negatives") outside the GT buffer, so the
//      classifier must learn SBT-specific signatures rather than "green = SBT".
//      CAUTION: unmapped SBT stands may exist among these pixels; this is the
//      inherent limitation of positive-only ground truth. Set
//      vegetatedBackgroundFraction = 0 to disable.
var vegetatedBackground = ee.FeatureCollection([]);
if (nVegetatedBackground > 0) {
  vegetatedBackground = predictorImage
    .addBands(ee.Image.constant(1).toInt().rename('veg_stratum'))
    .updateMask(backgroundAllowed)
    .updateMask(featureStack.select('NDVI').gt(vegetatedBackgroundNdvi))
    .stratifiedSample({
      numPoints: nVegetatedBackground,
      classBand: 'veg_stratum',
      region: roi,
      projection: samplingProjection,
      scale: processingScale,
      seed: randomSeed + 1,
      dropNulls: true,
      tileScale: 16,
      geometries: true
    })
    .map(function(f) {
      return f.set(classProperty, 0, 'sample_type', 'pseudo_negative_vegetated');
    })
    .select(sampleProps);
}

var backgroundSamplesFc = randomBackground.merge(vegetatedBackground)
  .distinct(predictorBands);   // remove duplicate pixels

print('=============== TRAINING DATA ===============');
print('SBT GT samples used (valid, one per 10 m pixel):', sbtSamples.size());
print('Pseudo-negative/background samples (NOT field observations):', backgroundSamplesFc.size());
print('  - spatially stratified random background:', randomBackground.size());
print('  - vegetated background (NDVI > ' + vegetatedBackgroundNdvi + '):', vegetatedBackground.size());
print('Background exclusion buffer around SBT GT (m):', backgroundBuffer);

// ---- 7c. Merge and split 70 / 30 ----
var allSamples = sbtSamples.select(sampleProps).merge(backgroundSamplesFc)
  .randomColumn('split', randomSeed);

var trainingSet = allSamples.filter(ee.Filter.lt('split', trainFraction));
var validationSet = allSamples.filter(ee.Filter.gte('split', trainFraction));

print('SBT training samples:', trainingSet.filter(ee.Filter.eq(classProperty, 1)).size());
print('Non-SBT training samples:', trainingSet.filter(ee.Filter.eq(classProperty, 0)).size());
print('SBT validation samples:', validationSet.filter(ee.Filter.eq(classProperty, 1)).size());
print('Non-SBT validation samples:', validationSet.filter(ee.Filter.eq(classProperty, 0)).size());


// =====================================================================
// 8. RANDOM FOREST
// =====================================================================
var rfParams = {
  numberOfTrees: numberOfTrees,
  minLeafPopulation: minLeafPopulation,
  bagFraction: bagFraction,
  seed: randomSeed
};
if (variablesPerSplit !== null) {
  rfParams.variablesPerSplit = variablesPerSplit;
}

// Class-label forest (majority vote) and the identical forest in
// PROBABILITY mode (fraction of trees voting SBT = probability of class 1).
var rfClassifier = ee.Classifier.smileRandomForest(rfParams).train({
  features: trainingSet,
  classProperty: classProperty,
  inputProperties: predictorBands
});
var rfProbabilityClassifier = ee.Classifier.smileRandomForest(rfParams)
  .setOutputMode('PROBABILITY')
  .train({
    features: trainingSet,
    classProperty: classProperty,
    inputProperties: predictorBands
  });

var importance = ee.Dictionary(ee.Dictionary(rfClassifier.explain()).get('importance'));
print('Random Forest variable importance:', importance);
print(ui.Chart.feature.byProperty(ee.Feature(null, importance))
  .setChartType('ColumnChart')
  .setOptions({
    title: 'Random Forest Variable Importance (SBT vs Non-SBT)',
    legend: {position: 'none'},
    hAxis: {title: 'Predictor'},
    vAxis: {title: 'Importance'}
  }));


// =====================================================================
// 9. CLASSIFICATION, PROBABILITY AND POST-PROCESSING
// =====================================================================
// Raw RF classification (majority vote): 0 = Non-SBT, 1 = SBT
var rawClassified = predictorImage.classify(rfClassifier).rename('raw_rf_class').toByte();

// SBT probability (0–1)
var sbtProbability = predictorImage.classify(rfProbabilityClassifier).rename('sbt_probability');

// Classification at the selected probability threshold: 0 = Non-SBT, 1 = SBT
var classified = sbtProbability.gte(probabilityThreshold).rename('classification').toByte();

var sbtMask = classified.eq(1);

// Minimum mapping unit: remove SBT patches smaller than minimumPatchPixels
// (8-connected, evaluated on the 10 m grid when exported / reduced at 10 m).
var patchSize = sbtMask.selfMask().connectedPixelCount({
  maxSize: Math.max(minimumPatchPixels + 1, 2),
  eightConnected: true
});
var sbtMaskPostProcessed = sbtMask.and(patchSize.unmask(0).gte(minimumPatchPixels));

// FINAL SBT MASK (1 = SBT, 0 = Non-SBT; masked where no valid summer data)
var finalSbtMask = (applyPostProcessing ? sbtMaskPostProcessed : sbtMask)
  .rename('sbt').toByte();
var finalClassification = finalSbtMask.rename('classification');

print('=============== CLASSIFICATION ===============');
print('Probability threshold used for final SBT area:', probabilityThreshold);
print('Post-processing applied:', applyPostProcessing
  ? 'YES (patches < ' + minimumPatchPixels + ' pixels removed, 8-connected)' : 'NO');


// =====================================================================
// 10. VALIDATION (independent 30 % split)
// =====================================================================
var validated = validationSet.classify(rfProbabilityClassifier, 'sbt_probability')
  .map(function(f) {
    return f.set('predicted', ee.Number(f.get('sbt_probability')).gte(probabilityThreshold));
  });

var confusionMatrix = validated.errorMatrix(classProperty, 'predicted', [0, 1]);
var overallAccuracy = confusionMatrix.accuracy();
var kappa = confusionMatrix.kappa();
var producersAccuracy = confusionMatrix.producersAccuracy();   // [[PA_nonSBT], [PA_SBT]]
var consumersAccuracy = confusionMatrix.consumersAccuracy();   // [[UA_nonSBT, UA_SBT]]
var sbtProducers = ee.Number(producersAccuracy.get([1, 0]));
var sbtUsers = ee.Number(consumersAccuracy.get([0, 1]));
var sbtF1 = sbtProducers.multiply(sbtUsers).multiply(2)
  .divide(sbtProducers.add(sbtUsers).max(1e-12));

print('=============== VALIDATION (30 % hold-out) ===============');
print('Confusion Matrix (rows = reference [0 Non-SBT, 1 SBT], cols = predicted):', confusionMatrix);
print('Overall Accuracy:', overallAccuracy);
print('Kappa:', kappa);
print("Producer's Accuracy [Non-SBT, SBT]:", producersAccuracy);
print("User's/Consumer's Accuracy [Non-SBT, SBT]:", consumersAccuracy);
print("SBT Producer's Accuracy (recall):", sbtProducers);
print("SBT User's Accuracy (precision):", sbtUsers);
print('SBT F1-score:', sbtF1);
print('IMPORTANT: Validation accuracy represents agreement against the constructed ' +
  'validation sample (field SBT points + automatically generated pseudo-negative/background ' +
  'points) and is NOT equivalent to an independent field-based accuracy assessment using ' +
  'independently collected non-SBT ground-truth observations.');


// =====================================================================
// 11. SBT AREA (pixelArea() on the 10 m grid, inside ROI only)
// =====================================================================
var pixelArea = ee.Image.pixelArea();
var areaImage = ee.Image.cat([
  pixelArea.rename('roi_m2'),
  pixelArea.updateMask(validMask).rename('valid_m2'),
  pixelArea.updateMask(finalSbtMask).rename('sbt_m2'),
  pixelArea.updateMask(sbtMask).rename('sbt_unfiltered_m2')
]).clip(roi);

var areaStats = areaImage.reduceRegion({
  reducer: ee.Reducer.sum(),
  geometry: roi,
  crs: processingCrs,
  scale: processingScale,
  maxPixels: 1e13,
  tileScale: 16
});

var roiAreaM2 = ee.Number(areaStats.get('roi_m2'));
var validAreaM2 = ee.Number(areaStats.get('valid_m2'));
var sbtAreaM2 = ee.Number(areaStats.get('sbt_m2'));
var sbtUnfilteredM2 = ee.Number(areaStats.get('sbt_unfiltered_m2'));
var sbtAreaHa = sbtAreaM2.divide(10000);       // 1 ha  = 10,000 m²
var sbtAreaKm2 = sbtAreaM2.divide(1000000);    // 1 km² = 1,000,000 m²
var roiAreaHa = roiAreaM2.divide(10000);
var roiAreaKm2 = roiAreaM2.divide(1000000);
var sbtPercent = sbtAreaM2.divide(roiAreaM2).multiply(100);

print('=============== SBT AREA (inside ROI, 10 m pixelArea) ===============');
print('ROI area from geometry (km²):', roi.area({maxError: 1}).divide(1e6));
print('Area results:', ee.Dictionary({
  'Total ROI Area (m²)': roiAreaM2,
  'Total ROI Area (ha)': roiAreaHa,
  'Valid (classified) area (ha)': validAreaM2.divide(10000),
  'SBT Area (m²)': sbtAreaM2,
  'SBT Area (hectares)': sbtAreaHa,
  'SBT Area (km²)': sbtAreaKm2,
  'SBT % of ROI': sbtPercent,
  'SBT Area before post-processing (ha)': sbtUnfilteredM2.divide(10000)
}));
print('If the area print times out, run the export task "' + exportPrefix +
  '_E_Area_Summary" (batch tasks have no interactive time limit).');


// =====================================================================
// 12. MAP LAYERS
// =====================================================================
var trueColourVis = {bands: ['B4', 'B3', 'B2'], min: 0.0, max: 0.30, gamma: 1.2};
var falseColourVis = {bands: ['B8', 'B4', 'B3'], min: 0.0, max: 0.45, gamma: 1.2};
var ndviVis = {min: -0.1, max: 0.8,
  palette: ['8c510a', 'd8b365', 'f6e8c3', 'c7eae5', '5ab4ac', '01665e', '003c30']};
var classVis = {min: 0, max: 1, palette: ['d9d9d9', 'e31a1c']};
var probVis = {min: 0, max: 1,
  palette: ['ffffcc', 'ffeda0', 'fed976', 'feb24c', 'fd8d3c', 'f03b20', 'bd0026']};

Map.addLayer(featureStack, trueColourVis, '2. Summer Sentinel-2 true colour (B4/B3/B2, 10 m)');
Map.addLayer(featureStack, falseColourVis, '3. Summer Sentinel-2 false colour (B8/B4/B3, 10 m)', false);
Map.addLayer(featureStack.select('NDVI'), ndviVis, '4. NDVI (summer composite)', false);
Map.addLayer(rawClassified, classVis, '7. Raw Random Forest classification (0 Non-SBT, 1 SBT)', false);
Map.addLayer(sbtProbability, probVis, '9. SBT Probability (RF confidence)', false);
Map.addLayer(classified, classVis, 'Classification at threshold ' + probabilityThreshold, false);
Map.addLayer(finalSbtMask.selfMask(), {palette: ['FF00FF']},
  '8. FINAL SBT MASK' + (applyPostProcessing ? ' (post-processed)' : ''));

Map.addLayer(sbtGT, {color: 'FFFF00'}, '5a. All SBT GT points (asset)', false);
Map.addLayer(sbtGTInRoi, {color: '00FFFF'}, '5b. SBT GT points inside ROI');
Map.addLayer(backgroundSamplesFc, {color: 'FFA500'}, '6. Pseudo-negative/background points', false);

Map.addLayer(ee.Image().byte().paint(ee.FeatureCollection([ee.Feature(roi)]), 1, 3),
  {palette: ['FFFFFF']}, '1. ROI');

// Legend
var legend = ui.Panel({style: {position: 'bottom-left', padding: '8px 12px'}});
legend.add(ui.Label('Seabuckthorn (SBT) mapping', {fontWeight: 'bold', fontSize: '14px'}));
function legendRow(colour, text) {
  return ui.Panel([
    ui.Label('', {backgroundColor: '#' + colour, padding: '8px', margin: '0 6px 4px 0'}),
    ui.Label(text, {margin: '0 0 4px 0'})
  ], ui.Panel.Layout.Flow('horizontal'));
}
legend.add(legendRow('FF00FF', 'Final SBT (prob ≥ ' + probabilityThreshold + ')'));
legend.add(legendRow('00FFFF', 'SBT GT points inside ROI'));
legend.add(legendRow('FFFF00', 'All SBT GT points (asset)'));
legend.add(legendRow('FFA500', 'Pseudo-negative/background points'));
legend.add(ui.Label('Zoom to ≥ 13 to view the 10 m post-processed mask accurately.',
  {fontSize: '11px', color: '555555'}));
Map.add(legend);


// =====================================================================
// 13. EXPORTS (Google Drive) — start them from the Tasks tab
// =====================================================================
// A. Final SBT classification: 0 = Non-SBT, 1 = SBT, 255 = no valid summer data / outside ROI
Export.image.toDrive({
  image: finalClassification.unmask(255).clip(roi).toByte(),
  description: exportPrefix + '_A_Final_Classification',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_A_Final_Classification_10m',
  region: roi,
  crs: processingCrs,
  scale: processingScale,
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF',
  formatOptions: {cloudOptimized: true, noData: 255}
});

// B. Final SBT binary mask: 1 = SBT, 0 = Non-SBT (no-data inside ROI written as 0)
Export.image.toDrive({
  image: finalSbtMask.unmask(0).clip(roi).toByte(),
  description: exportPrefix + '_B_Final_SBT_Binary_Mask',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_B_SBT_Binary_Mask_10m',
  region: roi,
  crs: processingCrs,
  scale: processingScale,
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF',
  formatOptions: {cloudOptimized: true, noData: 255}
});

// C. Summer predictor composite used for classification (float32).
//    NOTE: ~25 bands × ~64 million pixels — several GB; GEE splits it into tiles.
Export.image.toDrive({
  image: predictorImage.toFloat(),
  description: exportPrefix + '_C_Summer_S2_Predictor_Composite',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_C_Summer_S2_Predictors_10m',
  region: roi,
  crs: processingCrs,
  scale: processingScale,
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF',
  formatOptions: {cloudOptimized: true, noData: -9999}
});

// D. SBT GT points inside ROI (with longitude/latitude columns)
Export.table.toDrive({
  collection: sbtGTInRoi.map(function(f) {
    var xy = f.geometry().centroid(1).coordinates();
    return f.set({longitude: xy.get(0), latitude: xy.get(1)});
  }),
  description: exportPrefix + '_D_SBT_GT_Points_Inside_ROI',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_D_SBT_GT_Points_Inside_ROI',
  fileFormat: gtExportFormat
});

// E. SBT area summary (CSV)
var summaryFeature = ee.Feature(null, {
  Season_used: seasonLabel,
  Season_year: seasonYearUsed,
  Earliest_image_date: earliestImageDate,
  Latest_image_date: latestImageDate,
  Images_before_cloud_filtering: nImagesBefore,
  Number_of_images: nImagesAfter,
  Median_scene_cloud_pct: medianCloudPct,
  Composite_method: compositeMethod,
  ROI_area_m2: roiAreaM2,
  ROI_area_ha: roiAreaHa,
  ROI_area_km2: roiAreaKm2,
  Valid_area_ha: validAreaM2.divide(10000),
  SBT_area_m2: sbtAreaM2,
  SBT_area_ha: sbtAreaHa,
  SBT_area_km2: sbtAreaKm2,
  SBT_percentage: sbtPercent,
  SBT_area_before_postprocessing_ha: sbtUnfilteredM2.divide(10000),
  GT_points_in_asset: sbtGT.size(),
  GT_points_in_ROI: sbtGTInRoi.size(),
  GT_points_used: sbtSamples.size(),
  Pseudo_negative_samples: backgroundSamplesFc.size(),
  RF_trees: numberOfTrees,
  Probability_threshold: probabilityThreshold,
  Post_processing: applyPostProcessing ? 'min patch ' + minimumPatchPixels + ' px' : 'none',
  Overall_accuracy: overallAccuracy,
  Kappa: kappa,
  SBT_producers_accuracy: sbtProducers,
  SBT_users_accuracy: sbtUsers,
  Validation_note: 'Accuracy vs constructed sample (field SBT + pseudo-negative background); ' +
    'not an independent field accuracy assessment'
});

Export.table.toDrive({
  collection: ee.FeatureCollection([summaryFeature]),
  description: exportPrefix + '_E_Area_Summary',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_E_Area_Summary',
  fileFormat: 'CSV',
  selectors: [
    'Season_used', 'Season_year', 'Earliest_image_date', 'Latest_image_date',
    'Images_before_cloud_filtering', 'Number_of_images', 'Median_scene_cloud_pct',
    'Composite_method', 'ROI_area_m2', 'ROI_area_ha', 'ROI_area_km2', 'Valid_area_ha',
    'SBT_area_m2', 'SBT_area_ha', 'SBT_area_km2', 'SBT_percentage',
    'SBT_area_before_postprocessing_ha', 'GT_points_in_asset', 'GT_points_in_ROI',
    'GT_points_used', 'Pseudo_negative_samples', 'RF_trees', 'Probability_threshold',
    'Post_processing', 'Overall_accuracy', 'Kappa', 'SBT_producers_accuracy',
    'SBT_users_accuracy', 'Validation_note'
  ]
});

// F. (extra) SBT probability ×100 (0–100, 255 = no data) for threshold sensitivity
Export.image.toDrive({
  image: sbtProbability.multiply(100).round().unmask(255).clip(roi).toByte(),
  description: exportPrefix + '_F_SBT_Probability',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_F_SBT_Probability_x100_10m',
  region: roi,
  crs: processingCrs,
  scale: processingScale,
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF',
  formatOptions: {cloudOptimized: true, noData: 255}
});

// G. (extra) Pseudo-negative samples, for reproducibility
Export.table.toDrive({
  collection: backgroundSamplesFc,
  description: exportPrefix + '_G_Pseudo_Negative_Samples',
  folder: exportFolder,
  fileNamePrefix: exportPrefix + '_G_Pseudo_Negative_Samples',
  fileFormat: 'CSV'
});


// =====================================================================
// 14. FINAL CONSOLE SUMMARY
// =====================================================================
function fmt(value, decimals) {
  if (value === null || value === undefined || value === 'N/A' || isNaN(Number(value))) {
    return 'N/A';
  }
  var parts = Number(value).toFixed(decimals).split('.');
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return parts.join('.');
}

var finalSummary = ee.Dictionary({
  season: seasonLabel,
  earliest: earliestImageDate,
  latest: latestImageDate,
  latestAvailable: latestAvailableDate,
  nBefore: nImagesBefore,
  nAfter: nImagesAfter,
  medianCloud: medianCloudPct,
  gtAsset: sbtGT.size(),
  gtRoi: sbtGTInRoi.size(),
  gtUsed: sbtSamples.size(),
  roiM2: roiAreaM2,
  sbtM2: sbtAreaM2,
  sbtHa: sbtAreaHa,
  sbtKm2: sbtAreaKm2,
  sbtPct: sbtPercent,
  oa: overallAccuracy,
  kappa: kappa,
  sbtPA: sbtProducers,
  sbtUA: sbtUsers
});

print('Computing final summary (may take a few minutes for the 10 m area calculation)...');

finalSummary.evaluate(function(s, error) {
  if (error) {
    print('Final summary could not be computed interactively: ' + error);
    print('Run the export task "' + exportPrefix + '_E_Area_Summary" from the Tasks tab ' +
      'to obtain the same results as a CSV (batch tasks have no interactive time limit).');
    return;
  }

  var text = [
    '========================================',
    'SEABUCKTHORN AREA ESTIMATION',
    '========================================',
    '',
    'Season used:                ' + s.season,
    'Image collection:           COPERNICUS/S2_SR_HARMONIZED (cloud mask: S2_CLOUD_PROBABILITY)',
    'Earliest image:             ' + s.earliest,
    'Latest image:               ' + s.latest,
    'Latest S2 image in GEE:     ' + s.latestAvailable,
    '',
    'Number of summer images:                 ' + s.nBefore,
    'Number of images after cloud filtering:  ' + s.nAfter,
    'Median scene cloud %:                    ' + (s.medianCloud < 0 ? 'N/A' : fmt(s.medianCloud, 1)),
    '',
    'SBT GT points in asset:     ' + s.gtAsset,
    'SBT GT points inside ROI:   ' + s.gtRoi,
    'SBT GT samples used:        ' + s.gtUsed,
    '',
    'ROI area:                   ' + fmt(s.roiM2 / 1e4, 2) + ' ha  (' + fmt(s.roiM2 / 1e6, 2) + ' km²)',
    'SBT area (m²):              ' + fmt(s.sbtM2, 0),
    'SBT area (hectares):        ' + fmt(s.sbtHa, 2),
    'SBT area (km²):             ' + fmt(s.sbtKm2, 4),
    '',
    'SBT percentage of ROI:      ' + fmt(s.sbtPct, 4) + ' %',
    '',
    'Random Forest trees:        ' + numberOfTrees,
    'Probability threshold:      ' + probabilityThreshold,
    'Post-processing:            ' + (applyPostProcessing
      ? 'patches < ' + minimumPatchPixels + ' px removed' : 'none'),
    '',
    'Overall Accuracy:           ' + fmt(s.oa, 4),
    'Kappa:                      ' + fmt(s.kappa, 4),
    "SBT Producer's Accuracy:    " + fmt(s.sbtPA, 4),
    "SBT User's Accuracy:        " + fmt(s.sbtUA, 4),
    '(accuracy vs constructed validation sample incl. pseudo-negatives;',
    ' NOT an independent field-based accuracy assessment)',
    '',
    '========================================'
  ].join('\n');

  print(ui.Label(text, {whiteSpace: 'pre', fontFamily: 'monospace', fontSize: '12px'}));
  print(ui.Label('>>> TOTAL SEABUCKTHORN AREA: ' + fmt(s.sbtHa, 2) + ' ha <<<', {
    fontSize: '22px', fontWeight: 'bold', color: 'white',
    backgroundColor: '#8B008B', padding: '10px'
  }));

  Map.add(ui.Panel([
    ui.Label('Total SBT area', {fontWeight: 'bold', fontSize: '13px', margin: '0'}),
    ui.Label(fmt(s.sbtHa, 2) + ' ha', {fontWeight: 'bold', fontSize: '22px', color: '#8B008B', margin: '2px 0'}),
    ui.Label(fmt(s.sbtKm2, 3) + ' km²  |  ' + fmt(s.sbtPct, 3) + ' % of ROI', {fontSize: '12px', margin: '0'}),
    ui.Label(s.season, {fontSize: '11px', color: '555555', margin: '2px 0 0 0'})
  ], null, {position: 'top-right', padding: '8px 12px'}));
});
