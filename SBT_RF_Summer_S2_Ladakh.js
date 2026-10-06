/*******************************************************************************************************
 * SEABUCKTHORN (SBT; Hippophae spp.) AREA MAPPING — LADAKH (NUBRA–SHYOK VALLEY SYSTEM), TRANS-HIMALAYA
 * -----------------------------------------------------------------------------------------------------
 * Supervised six-class land-use/land-cover (LULC) classification with a Random Forest (RF) classifier
 * applied to a STRICTLY SUMMER-ONLY, cloud/shadow-masked Sentinel-2 L2A surface-reflectance composite,
 * spectral indices and terrain variables, trained on field ground-truth (GT) points.
 *
 * Class-code scheme (used identically in every section, table, raster and legend):
 *   1 = Seabuckthorn   2 = Agriculture   3 = Natural Vegetation
 *   4 = Water Bodies   5 = Barren Land   6 = Snow/Ice
 *
 * Sections:
 *    1 User parameters               10 Spectral indices               19 Feature importance
 *    2 ROI definition                11 DEM / terrain variables        20 Class area calculation
 *    3 Training asset import         12 Predictor stack                21 SBT area calculation
 *    4 Training data cleaning        13 Training neighbourhood         22 Ground-truth visualisation
 *    5 Sentinel-2 collection         14 Sample extraction              23 Sentinel-2 FCC
 *    6 Strict summer filtering       15 Training/validation split      24 Map visualisation / legend
 *    7 Cloud/shadow masking          16 Random Forest training         25 Console summary
 *    8 Summer composite              17 Classification                 26 Export tables (CSV/Excel)
 *    9 Spectral bands                18 Accuracy assessment            27 Export raster products
 *
 * HOW TO RUN
 *   1. Paste your GT asset path in the "USER INPUT: PASTE YOUR TRAINING ASSET HERE" block (Section 1).
 *   2. If your class column is not called 'class', set CLASS_FIELD (the script also auto-detects it).
 *   3. Click Run. Read the Console (results appear progressively), then start the export tasks
 *      in the Tasks tab. Every Console table can also be downloaded as CSV (pop-out icon ↗ → Download).
 *
 * Coding conventions: ES5 JavaScript (Code Editor compatible); server-side ee.* objects are used for all
 * heavy computation; the few synchronous getInfo() calls are restricted to input validation so that the
 * script stops with a clear message instead of silently producing wrong results.
 *******************************************************************************************************/


// =====================================================================================================
// 1. USER PARAMETERS
// =====================================================================================================

// ============================================================
// USER INPUT: PASTE YOUR TRAINING ASSET HERE
// ============================================================
// Replace the text between the quotes with the full Table ID of your GT point asset, for example
//   'projects/your-cloud-project/assets/SBT_GT_points'   or   'users/your_username/SBT_GT_points'
// (Assets tab → click the asset → copy the "Table ID").
// If you imported the table through the Code Editor "Imports" bar instead (e.g. it is named `table`),
// you may replace the whole statement below with:   var trainingPoints = table;
var trainingPoints = ee.FeatureCollection(
  'PASTE_YOUR_TRAINING_ASSET_PATH_HERE'
);

// Attribute (column) holding the class label. Values may be EITHER numeric codes (1–6, see CLASS_NAMES)
// OR class names ('Seabuckthorn', 'Agriculture', 'Natural Vegetation', 'Water Bodies', 'Barren Land',
// 'Snow/Ice'; case-insensitive, common synonyms accepted — see CLASS_NAME_SYNONYMS).
// If CLASS_FIELD is not found in the asset, the candidates below are searched (case-insensitive).
var CLASS_FIELD = 'class';
var CLASS_FIELD_CANDIDATES = ['class', 'class_id', 'classid', 'class_code', 'classcode', 'class_name',
  'classname', 'lulc', 'lc', 'landcover', 'land_cover', 'lc_class', 'code', 'label', 'type', 'name'];

// Numeric code mapping {code in your table: standard code}. Default assumes your table already uses 1–6.
// Example if your table uses 0–5 in the same order:  {'0': 1, '1': 2, '2': 3, '3': 4, '4': 5, '5': 6}
var NUMERIC_CODE_TO_CLASS = {'1': 1, '2': 2, '3': 3, '4': 4, '5': 5, '6': 6};

// ---------------- Class scheme (do not change the codes; they are used throughout) ----------------
var CLASS_CODES   = [1, 2, 3, 4, 5, 6];
var CLASS_NAMES   = ['Seabuckthorn', 'Agriculture', 'Natural Vegetation', 'Water Bodies', 'Barren Land', 'Snow/Ice'];
var CLASS_KEYS    = ['Seabuckthorn', 'Agriculture', 'Natural_Vegetation', 'Water_Bodies', 'Barren_Land', 'Snow_Ice'];
// Publication palette (ColorBrewer Set1-derived; distinct under common colour-vision deficiencies
// because hue AND lightness differ between neighbouring classes).
var CLASS_PALETTE = ['E41A1C', 'FFD700', '4DAF4A', '377EB8', 'C8B38A', 'FFFFFF'];
var GT_SYMBOLS    = ['star5', 'square', 'triangle', 'circle', 'diamond', 'hexagon'];  // GT point symbols
var SBT_CODE      = 1;
var CLASS_PROP    = 'lc_code';   // internal integer class property created by this script

// Text labels accepted for each class (matched after trimming and lower-casing).
var CLASS_NAME_SYNONYMS = {
  1: ['seabuckthorn', 'sea buckthorn', 'sea-buckthorn', 'sea_buckthorn', 'sbt', 'hippophae',
      'hippophae rhamnoides', 'hippophae tibetana'],
  2: ['agriculture', 'agricultural', 'agricultural field', 'agricultural fields', 'agricultural land',
      'agriculture land', 'agriculture field', 'agri', 'crop', 'crops', 'cropland', 'farmland'],
  3: ['natural vegetation', 'natural_vegetation', 'natural-vegetation', 'naturalvegetation', 'natural veg',
      'vegetation', 'other vegetation', 'shrub', 'shrubland', 'grassland', 'pasture'],
  4: ['water bodies', 'water body', 'water_bodies', 'water_body', 'waterbodies', 'waterbody', 'water',
      'river', 'lake', 'stream'],
  5: ['barren land', 'barren_land', 'barren-land', 'barrenland', 'barren', 'bare', 'bare land',
      'bare soil', 'bareland', 'rock', 'alluvium'],
  6: ['snow/ice', 'snow ice', 'snow_ice', 'snow-ice', 'snowice', 'snow', 'ice', 'snow and ice',
      'snow & ice', 'glacier']
};

// ---------------- Strict summer window (see Section 6 for the scientific rationale) ----------------
var SUMMER_START_MONTH = 7;  var SUMMER_START_DAY = 1;    // 1 July
var SUMMER_END_MONTH   = 8;  var SUMMER_END_DAY   = 31;   // 31 August (inclusive)
// null  → automatically use the most recent COMPLETE summer before the analysis (run) date.
// 2024  → force a specific summer (recommended when it matches the year of your GT field survey).
var SUMMER_YEAR = null;
var NUM_SUMMER_SEASONS = 1;     // 1 = single most recent summer. >1 merges the SAME Jul–Aug window of
                                // consecutive years (still summer-only) if one season is too cloudy.
var MAX_YEARS_BACK = 4;         // how many earlier summers may be tried if the latest one is insufficient
var MIN_SCENES = 5;             // minimum number of accepted scenes for a robust median composite
var PROCESSING_LAG_DAYS = 5;    // days after the window ends before a summer is treated as "complete"

// ---------------- Scene- and pixel-level quality screening ----------------
var MAX_SCENE_CLOUD_PCT = 40;   // scene metadata CLOUDY_PIXEL_PERCENTAGE upper limit
var MAX_SCENE_SNOW_PCT  = 50;   // scene metadata SNOW_ICE_PERCENTAGE upper limit (rejects fresh-snowfall
                                // scenes; perennial Karakoram snow/glacier cover stays below this)
var CS_CLEAR_THRESHOLD  = 0.60; // Cloud Score+ cs_cdf threshold (0.50–0.65 typical; higher = stricter)
var MASK_SCL_CLOUDS     = false;// also mask SCL cloud classes 8/9/10 (off: SCL over-flags bright
                                // alluvium/snow as cloud in Ladakh; Cloud Score+ is more reliable)

// ---------------- Terrain options ----------------
var DEM_SOURCE = 'GLO30';       // 'GLO30' (Copernicus DEM GLO-30, recommended) or 'SRTM' (SRTM 1 arc-second)
var USE_HAND   = false;         // add MERIT-Hydro Height Above Nearest Drainage (≈90 m; needs resampling)

// ---------------- Training neighbourhood (Section 13) ----------------
var trainingBufferMeters  = 10;   // user-adjustable; 0 = use only the pixel containing each GT point
var NEIGHBOUR_MAX_DNDVI   = 0.15; // neighbour pixel kept only if |NDVI − NDVI(centre pixel)| ≤ this
var NEIGHBOUR_MAX_DMNDWI  = 0.20; // ... and |MNDWI − MNDWI(centre pixel)| ≤ this

// ---------------- Training / validation partition (Section 15) ----------------
var TRAIN_FRACTION = 0.70;               // 70 % training / 30 % independent validation (per class)
var SPLIT_STRATEGY = 'stratified_random';// 'stratified_random' (per-class random split of GT points) or
                                         // 'spatial_block' (whole blocks go to one partition)
var SPATIAL_BLOCK_SIZE_M = 500;          // block size for 'spatial_block'
var MAX_TRAIN_PIXELS_PER_CLASS = 1000;   // cap on training pixels per class (limits SBT dominance); 0 = off
var RANDOM_SEED = 42;                    // single seed for every random operation (reproducibility)

// ---------------- Random Forest (Section 16) ----------------
var RF_NUM_TREES           = 500;   // stable OOB error/importance is normally reached by 300–500 trees
var RF_VARIABLES_PER_SPLIT = null;  // null → floor(sqrt(number of predictors)) (Breiman 2001 default)
var RF_MIN_LEAF_POPULATION = 1;     // fully grown trees (standard for classification RF)
var RF_BAG_FRACTION        = 0.632; // sub-sampling without replacement at 0.632 (Strobl et al. 2007)
var RF_SEED                = RANDOM_SEED;

// ---------------- Optional SBT habitat plausibility rule (OFF = pure RF output) ----------------
// When true, SBT pixels above SBT_MAX_ELEVATION_M or steeper than SBT_MAX_SLOPE_DEG or with
// NDVI < SBT_MIN_NDVI are re-assigned (NDVI ≥ SBT_MIN_NDVI → Natural Vegetation, else Barren Land).
// Accuracy is always assessed on the FINAL map, so the effect of the rule is reflected in the metrics.
var APPLY_SBT_HABITAT_RULE = false;
var SBT_MAX_ELEVATION_M = 4200;
var SBT_MAX_SLOPE_DEG   = 30;
var SBT_MIN_NDVI        = 0.10;

// ---------------- Outputs ----------------
var EXPORT_FOLDER = 'GEE_SBT_Ladakh';   // Google Drive folder
var EXPORT_PREFIX = 'SBT_Ladakh';       // file-name prefix
var EXPORT_SBT_PROBABILITY = true;      // also export the RF SBT class-probability raster
var COMPUTE_AREA_IN_CONSOLE = true;     // full-resolution area in the Console (may time out on large
                                        // ROIs; the exported CSV is always computed in batch mode)


// =====================================================================================================
// 2. ROI DEFINITION  (used unchanged for every step: filtering, compositing, indices, training-data
//    filtering, classification, accuracy, area and exports)
// =====================================================================================================
var minLon = 76.757353;
var minLat = 34.494454;
var maxLon = 77.731841;
var maxLat = 35.136103;

var roi = ee.Geometry.Rectangle([
  minLon,
  minLat,
  maxLon,
  maxLat
]);

// Analysis grid. The ROI (76.76–77.73°E) lies entirely in UTM zone 43N (72–78°E), the native CRS of the
// Sentinel-2 MGRS tiles covering it, and S2 tile origins are multiples of 10 m. Using EPSG:32643 with a
// 10 m transform anchored at (0, 0) therefore reproduces the native 10 m S2 pixel grid exactly: the 10 m
// bands are never resampled, and only the 20 m bands / DEM are interpolated (bilinear) to 10 m.
var ANALYSIS_CRS   = 'EPSG:32643';
var ANALYSIS_SCALE = 10;
var CRS_TRANSFORM  = [ANALYSIS_SCALE, 0, 0, 0, -ANALYSIS_SCALE, 0];
var ANALYSIS_PROJ  = ee.Projection(ANALYSIS_CRS, CRS_TRANSFORM);

// Client-side helpers used throughout (ES5 only, no external libraries)
function pad2(n) { return (n < 10 ? '0' : '') + n; }
function fmt(x, d) {
  if (x === null || x === undefined || isNaN(x)) return 'n/a';
  return Number(x).toFixed(d === undefined ? 2 : d);
}
function round(x, d) {
  if (x === null || x === undefined || isNaN(x)) return null;
  var p = Math.pow(10, d === undefined ? 4 : d);
  return Math.round(Number(x) * p) / p;
}

print('====================================================================');
print('SEABUCKTHORN (SBT) RF CLASSIFICATION — LADAKH — SUMMER SENTINEL-2');
print('====================================================================');
print('ROI (WGS84): minLon ' + minLon + ', minLat ' + minLat + ', maxLon ' + maxLon + ', maxLat ' + maxLat);
print('ROI geodesic area (km²):', roi.area(1).divide(1e6));


// =====================================================================================================
// 3. TRAINING ASSET IMPORT  (validated up-front so that a wrong path / field fails with a clear message)
// =====================================================================================================
var WORLD = ee.Geometry.Rectangle([-180, -89.9, 180, 89.9], null, false);

var gtCheck;
try {
  gtCheck = ee.Dictionary({
    n: trainingPoints.size(),
    nWithGeometry: trainingPoints.filterBounds(WORLD).size()
  }).getInfo();
} catch (err) {
  throw new Error('TRAINING ASSET COULD NOT BE LOADED. Paste the full Table ID of your GT asset in the ' +
    '"USER INPUT: PASTE YOUR TRAINING ASSET HERE" block (Section 1) and make sure it is shared with / ' +
    'readable by your account. Earth Engine said: ' + err.message);
}
if (gtCheck.n === 0) {
  throw new Error('The training asset is EMPTY (0 features). Check the uploaded table.');
}
if (gtCheck.nWithGeometry === 0) {
  throw new Error('None of the ' + gtCheck.n + ' GT features has a geometry. When uploading a CSV, set the ' +
    'X (longitude) and Y (latitude) columns in the asset-upload dialog so that points are created.');
}

// Detect the class field (exact name first, then case-insensitive search of the candidates).
var gtPropertyNames = ee.Feature(trainingPoints.first()).propertyNames().getInfo();
var classField = null;
(function detectClassField() {
  if (gtPropertyNames.indexOf(CLASS_FIELD) >= 0) { classField = CLASS_FIELD; return; }
  var lowerToActual = {};
  gtPropertyNames.forEach(function(p) { lowerToActual[p.toLowerCase()] = p; });
  var search = [CLASS_FIELD.toLowerCase()].concat(CLASS_FIELD_CANDIDATES);
  for (var i = 0; i < search.length; i++) {
    if (lowerToActual[search[i].toLowerCase()] !== undefined) { classField = lowerToActual[search[i].toLowerCase()]; return; }
  }
})();
if (classField === null) {
  throw new Error('CLASS FIELD NOT FOUND. CLASS_FIELD = "' + CLASS_FIELD + '" does not exist in the asset. ' +
    'Available attributes: [' + gtPropertyNames.join(', ') + ']. Set CLASS_FIELD in Section 1 accordingly.');
}

print('--------------------------------------------------------------------');
print('3. TRAINING ASSET');
print('GT features in asset: ' + gtCheck.n + '  (with valid geometry: ' + gtCheck.nWithGeometry + ')');
print('Attributes found: ' + gtPropertyNames.filter(function(p) { return p.indexOf('system:') !== 0; }).join(', '));
print('Class field used: "' + classField + '"' + (classField !== CLASS_FIELD ? '  (auto-detected)' : ''));


// =====================================================================================================
// 4. TRAINING DATA CLEANING / FILTERING
//    a) drop features without geometry, b) drop missing/null labels, c) translate numeric codes or text
//    labels to the standard 1–6 codes, d) drop unrecognised labels, e) keep only points inside the ROI.
//    Each GT point keeps a unique gt_id (= its system:index) so that it can be traced through the
//    training/validation split and into the exported GT table.
// =====================================================================================================
var nameLookup = {};
CLASS_CODES.forEach(function(code) {
  CLASS_NAME_SYNONYMS[code].forEach(function(s) { nameLookup[s] = code; });
  nameLookup[CLASS_NAMES[code - 1].toLowerCase()] = code;
  nameLookup[String(code)] = code;   // harmless: numeric strings are routed through NUMERIC_CODE_TO_CLASS
});
var NAME_LOOKUP  = ee.Dictionary(nameLookup);
var NUMERIC_MAP  = ee.Dictionary(NUMERIC_CODE_TO_CLASS);
var CODE_TO_NAME = ee.Dictionary({'1': CLASS_NAMES[0], '2': CLASS_NAMES[1], '3': CLASS_NAMES[2],
                                  '4': CLASS_NAMES[3], '5': CLASS_NAMES[4], '6': CLASS_NAMES[5]});

function codeToName(code) {
  return CODE_TO_NAME.get(ee.Number(code).toInt().format('%d'), 'Unrecognised');
}

// Converts any label (number or text) to the standard integer code; unknown labels → -1.
// Both branches of every ee.Algorithms.If below are always valid, so no branch can raise an error.
function standardiseLabel(f) {
  var labelStr = ee.String(f.get(classField)).trim();     // numbers are converted to text, e.g. 1 → "1"
  var lower = labelStr.toLowerCase();
  var isNumeric = lower.match('^-?[0-9]+(\\.[0-9]+)?$').size().gt(0);
  var numericText = ee.String(ee.Algorithms.If(isNumeric, lower, '-1'));
  var numericKey = ee.Number.parse(numericText).round().toInt().format('%d');
  var code = ee.Number(ee.Algorithms.If(isNumeric,
    NUMERIC_MAP.get(numericKey, -1),
    NAME_LOOKUP.get(lower, -1))).toInt();
  var pt = f.geometry().centroid(1);                     // points stay points; any polygon → centroid
  var xy = pt.coordinates();
  return ee.Feature(pt, {
    gt_id: ee.String(f.get('system:index')),
    label_raw: labelStr,
    lc_code: code,
    lon: xy.get(0),
    lat: xy.get(1)
  });
}

var gtWithGeom   = trainingPoints.filterBounds(WORLD);
var gtNullLabel  = gtWithGeom.filter(ee.Filter.notNull([classField]).not());       // missing / null label
var gtLabelled   = gtWithGeom.filter(ee.Filter.notNull([classField])).map(standardiseLabel);
var gtUnrecognised = gtLabelled.filter(ee.Filter.rangeContains(CLASS_PROP, 1, 6).not());
var gtAll        = gtLabelled.filter(ee.Filter.rangeContains(CLASS_PROP, 1, 6));     // valid label, any location
var gtInRoi      = gtAll.filterBounds(roi);                                          // ← analysis set
var gtOutsideRoi = gtAll.filter(ee.Filter.bounds(roi).not());                        // excluded

print('--------------------------------------------------------------------');
print('4. GROUND-TRUTH CLEANING');
print('Raw label values found in "' + classField + '" (value: count):', trainingPoints.aggregate_histogram(classField));
print('GT with missing/null class label (excluded):', gtNullLabel.size());
print('GT with unrecognised class label (excluded):', gtUnrecognised.size());
print('Unrecognised labels (fix NUMERIC_CODE_TO_CLASS / CLASS_NAME_SYNONYMS if not empty):',
      gtUnrecognised.aggregate_histogram('label_raw'));
print('GT with valid class label (all locations):', gtAll.size());
print('GT outside the ROI (excluded):', gtOutsideRoi.size());
print('GT inside the ROI (used):', gtInRoi.size());
print('GT inside ROI per class code {code: n}:', gtInRoi.aggregate_histogram(CLASS_PROP));


// =====================================================================================================
// 5. SENTINEL-2 IMAGE COLLECTION
//    COPERNICUS/S2_SR_HARMONIZED = Level-2A bottom-of-atmosphere surface reflectance (Sen2Cor), with the
//    processing-baseline-04.00 offset harmonised so that all years share one radiometric scale
//    (reflectance = DN × 0.0001). Cloud Score+ (GOOGLE/CLOUD_SCORE_PLUS/V1/S2_HARMONIZED) supplies a
//    per-pixel clear-sky probability used for cloud AND cloud-shadow masking (Pasquarella et al. 2023).
// =====================================================================================================
var S2_BANDS  = ['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B8A', 'B11', 'B12'];
var BANDS_10M = ['B2', 'B3', 'B4', 'B8'];
var BANDS_20M = ['B5', 'B6', 'B7', 'B8A', 'B11', 'B12'];
var S2_SR     = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED');
var CS_PLUS   = ee.ImageCollection('GOOGLE/CLOUD_SCORE_PLUS/V1/S2_HARMONIZED');
var ANALYSIS_DATE = new Date();   // run date; no image acquired after it can enter the analysis


// =====================================================================================================
// 6. STRICT SUMMER FILTERING
//    Window: 1 July – 31 August (inclusive) of the most recent complete summer before the run date.
//    Rationale for the arid trans-Himalaya (Nubra–Shyok, valley floors ≈2,800–3,500 m):
//      • Seasonal snow has melted from valley floors and mid-slopes by early July, so only perennial
//        snow/glacier ice remains → Snow/Ice is mapped as a stable class, not transient snowfall.
//      • SBT (deciduous) is in full leaf from July; green-up in June is incomplete and leaf colouring /
//        fall starts mid–late September → July–August is the period of maximum, stable SBT canopy.
//      • Irrigated barley/wheat, peas and fodder (lucerne) are green in July–August (barley harvest begins
//        in late August), giving a distinct crop phenology/greenness relative to SBT.
//      • Ladakh is in the monsoon rain shadow: July–August still provides many clear Sentinel-2 views.
//      • June (residual snow, incomplete leaf-out) and September onward (harvest, senescence, first
//        snowfalls) are excluded to keep the composite phenologically homogeneous.
//    The window is applied server-side with explicit date filters and verified again in the Console.
// =====================================================================================================
function summerWindow(year) {
  return {
    year: year,
    start: year + '-' + pad2(SUMMER_START_MONTH) + '-' + pad2(SUMMER_START_DAY),
    end:   year + '-' + pad2(SUMMER_END_MONTH)   + '-' + pad2(SUMMER_END_DAY),       // inclusive
    endExclusive: ee.Date.fromYMD(year, SUMMER_END_MONTH, SUMMER_END_DAY).advance(1, 'day')
  };
}

function latestCompleteSummerYear() {
  var y = ANALYSIS_DATE.getUTCFullYear();
  var completeAfter = Date.UTC(y, SUMMER_END_MONTH - 1, SUMMER_END_DAY) + (1 + PROCESSING_LAG_DAYS) * 86400000;
  return (ANALYSIS_DATE.getTime() >= completeAfter) ? y : y - 1;
}

// Builds the screened (not yet pixel-masked) summer collection whose LAST season is `lastYear`.
function buildSummerCollection(lastYear) {
  var filters = [];
  for (var k = NUM_SUMMER_SEASONS - 1; k >= 0; k--) {
    var w = summerWindow(lastYear - k);
    filters.push(ee.Filter.date(w.start, w.endExclusive));
  }
  var dateFilter = (filters.length === 1) ? filters[0] : ee.Filter.or.apply(null, filters);
  return S2_SR
    .filterBounds(roi)
    .filter(dateFilter)                                                       // summer window(s) only
    .filter(ee.Filter.lte('system:time_start', ANALYSIS_DATE.getTime()))      // never "future" imagery
    .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', MAX_SCENE_CLOUD_PCT))     // scene-level cloud screen
    .filter(ee.Filter.lt('SNOW_ICE_PERCENTAGE', MAX_SCENE_SNOW_PCT));        // scene-level snow screen
}

// Select the most recent summer with enough accepted scenes (or the user-fixed SUMMER_YEAR).
var selectedYear = null;
var s2Summer = null;
var nSummerScenes = 0;
var candidateYears = [];
if (SUMMER_YEAR !== null) {
  candidateYears = [SUMMER_YEAR];
} else {
  for (var yb = 0; yb < MAX_YEARS_BACK; yb++) candidateYears.push(latestCompleteSummerYear() - yb);
}
for (var ci = 0; ci < candidateYears.length; ci++) {
  if (candidateYears[ci] < 2017) break;   // S2 L2A archive is complete from 2017 onwards
  var trial = buildSummerCollection(candidateYears[ci]);
  var nTrial;
  try { nTrial = trial.size().getInfo(); } catch (err) {
    throw new Error('Sentinel-2 collection query failed: ' + err.message);
  }
  print('Summer ' + candidateYears[ci] + ': ' + nTrial + ' accepted Sentinel-2 scenes');
  if (nTrial >= MIN_SCENES || (SUMMER_YEAR !== null && nTrial > 0)) {
    selectedYear = candidateYears[ci]; s2Summer = trial; nSummerScenes = nTrial; break;
  }
}
if (selectedYear === null) {
  throw new Error('NO SUITABLE SUMMER IMAGERY: fewer than MIN_SCENES = ' + MIN_SCENES + ' scenes passed the ' +
    'summer/cloud/snow screens for years ' + candidateYears.join(', ') + '. Increase MAX_SCENE_CLOUD_PCT, ' +
    'NUM_SUMMER_SEASONS or MAX_YEARS_BACK, or set SUMMER_YEAR.');
}

var firstWindow = summerWindow(selectedYear - NUM_SUMMER_SEASONS + 1);
var lastWindow  = summerWindow(selectedYear);
var summerStart = firstWindow.start;   // e.g. '2026-07-01'
var summerEnd   = lastWindow.end;      // e.g. '2026-08-31' (inclusive)
var SUMMER_TAG  = 'Summer' + (NUM_SUMMER_SEASONS > 1 ? (selectedYear - NUM_SUMMER_SEASONS + 1) + '_' : '') + selectedYear;
// Human-readable window label (with several seasons, only the summer window of EACH year is used).
var SUMMER_LABEL = (NUM_SUMMER_SEASONS > 1)
  ? pad2(SUMMER_START_DAY) + '/' + pad2(SUMMER_START_MONTH) + '–' + pad2(SUMMER_END_DAY) + '/' + pad2(SUMMER_END_MONTH) +
    ' of each year ' + (selectedYear - NUM_SUMMER_SEASONS + 1) + '–' + selectedYear
  : summerStart + ' to ' + summerEnd;

print('--------------------------------------------------------------------');
print('6. SENTINEL-2 SUMMER SELECTION');
print('Analysis (run) date: ' + ANALYSIS_DATE.toISOString().slice(0, 10));
print('Summer window(s): ' + pad2(SUMMER_START_DAY) + '/' + pad2(SUMMER_START_MONTH) + ' – ' +
      pad2(SUMMER_END_DAY) + '/' + pad2(SUMMER_END_MONTH) + ' of ' +
      (NUM_SUMMER_SEASONS > 1 ? (selectedYear - NUM_SUMMER_SEASONS + 1) + '–' : '') + selectedYear +
      '  →  summerStart = ' + summerStart + ', summerEnd = ' + summerEnd);
print('Scenes accepted (cloud < ' + MAX_SCENE_CLOUD_PCT + ' %, snow/ice < ' + MAX_SCENE_SNOW_PCT + ' %): ' + nSummerScenes);

var sceneDates = ee.List(s2Summer.aggregate_array('system:time_start'))
  .map(function(t) { return ee.Date(t).format('YYYY-MM-dd'); }).distinct().sort();
print('Acquisition dates used:', sceneDates);
print('MGRS tiles used:', ee.List(s2Summer.aggregate_array('MGRS_TILE')).distinct().sort());


// =====================================================================================================
// 7. CLOUD / CLOUD-SHADOW MASKING
//    • Cloud Score+ cs_cdf ≥ CS_CLEAR_THRESHOLD keeps clear pixels; cs_cdf is low for clouds, thin
//      cirrus, haze AND cloud shadows, so a single threshold handles both.
//    • SCL is used only to remove no-data (0), saturated/defective (1) and cloud-shadow (3) pixels.
//      SCL snow (11) and water (6) are deliberately NOT masked: they are target classes.
//    • Pixels missing in any band (S2 band-edge artefacts) are removed so all predictors are co-valid.
//    • 20 m bands are bilinearly resampled to the 10 m grid BEFORE compositing (avoids blocky 20 m
//      artefacts); 10 m bands keep their native grid (no resampling).
//    • DN × 0.0001 → surface reflectance (0–1), so every index uses physically scaled values.
// =====================================================================================================
function maskAndScaleS2(img) {
  var clear = img.select('cs_cdf').gte(CS_CLEAR_THRESHOLD);
  var scl = img.select('SCL');
  var sclOk = scl.neq(0).and(scl.neq(1)).and(scl.neq(3));
  if (MASK_SCL_CLOUDS) sclOk = sclOk.and(scl.neq(8)).and(scl.neq(9)).and(scl.neq(10));
  var refl = img.select(BANDS_10M)
    .addBands(img.select(BANDS_20M).resample('bilinear'))
    .select(S2_BANDS)
    .multiply(0.0001);
  var allBandsValid = refl.mask().reduce(ee.Reducer.min());
  return ee.Image(refl.updateMask(clear).updateMask(sclOk).updateMask(allBandsValid)
    .copyProperties(img, ['system:time_start', 'MGRS_TILE', 'SPACECRAFT_NAME']));
}

var s2Masked = s2Summer.linkCollection(CS_PLUS, ['cs_cdf']).map(maskAndScaleS2);


// =====================================================================================================
// 8. SUMMER COMPOSITE GENERATION
//    Per-pixel MEDIAN of all clear summer observations: robust to residual cloud/shadow/haze and to
//    occasional transient snow, and representative of the mid-summer canopy state.
// =====================================================================================================
var composite = s2Masked.median().select(S2_BANDS).clip(roi);
var clearObsCount = s2Masked.select('B4').count().rename('clear_obs').clip(roi);

// Fraction of the ROI with a valid composite (QC; evaluated at 100 m for speed).
var compositeCoverage = composite.select('B4').mask().reduceRegion({
  reducer: ee.Reducer.mean(), geometry: roi, crs: ANALYSIS_CRS, scale: 100, maxPixels: 1e10, tileScale: 4
}).get('B4');
print('Valid composite coverage of ROI (fraction):', compositeCoverage);


// =====================================================================================================
// 9. SPECTRAL BANDS (surface reflectance, 10 m grid)
//    B2  Blue      490 nm (10 m)  very high for snow, moderate for turbid glacial water, soil brightness;
//                                 used in EVI/BSI.
//    B3  Green     560 nm (10 m)  chlorophyll green peak; water penetration; snow/water indices.
//    B4  Red       665 nm (10 m)  chlorophyll absorption → vegetation vs. bare alluvium/soil.
//    B5  Red-edge1 705 nm (20 m)  onset of the red edge; most sensitive to leaf chlorophyll content —
//                                 SBT's silvery, scale-covered leaves have lower chlorophyll per area and
//                                 brighter visible reflectance than irrigated crops.
//    B6  Red-edge2 740 nm (20 m)  red-edge slope; canopy chlorophyll × LAI.
//    B7  Red-edge3 783 nm (20 m)  start of the NIR plateau; canopy structure (dense shrub thickets vs
//                                 open crops/steppe).
//    B8  NIR       842 nm (10 m)  leaf mesophyll scattering / biomass; strongly absorbed by water.
//    B8A NIR-n     865 nm (20 m)  narrow NIR, cleaner plateau; paired with B5 for NDRE.
//    B11 SWIR1    1610 nm (20 m)  leaf/soil water; snow & ice absorb strongly (snow vs cloud/bright soil).
//    B12 SWIR2    2190 nm (20 m)  soil minerals, dry vegetation, coarse alluvium/gravel vs moist soils.
//    B1, B9, B10 (60 m atmospheric bands) are omitted: they carry aerosol/water-vapour, not surface, signal.
// =====================================================================================================
print('Spectral bands:', S2_BANDS);


// =====================================================================================================
// 10. SPECTRAL INDICES (computed from scaled reflectance; each one targets a specific class contrast)
//    NDVI   (B8−B4)/(B8+B4)                      greenness: vegetation vs barren/water/snow
//    EVI    2.5(B8−B4)/(B8+6B4−7.5B2+1)          greenness without NDVI saturation in dense crops/thickets
//    SAVI   1.5(B8−B4)/(B8+B4+0.5)               soil-adjusted greenness for sparse cold-desert canopies
//    MSAVI  self-adjusting soil line version     sparse SBT/steppe on bright alluvium
//    GNDVI  (B8−B3)/(B8+B3)                      chlorophyll concentration (crop vs SBT vs steppe)
//    NDRE   (B8A−B5)/(B8A+B5)                    red-edge chlorophyll index (key SBT vs crop contrast)
//    CIre   B7/B5 − 1                            red-edge chlorophyll index, near-linear with canopy
//                                                chlorophyll (Gitelson et al. 2003); complements NDRE
//    NDWI   (B3−B8)/(B3+B8)                      open water (McFeeters 1996) / canopy water contrast
//    NDSI_MNDWI (B3−B11)/(B3+B11)                MNDWI (Xu 2006) and NDSI (Hall et al. 1995) are the SAME
//                                                formula for Sentinel-2 → included ONCE: high for water and
//                                                snow/ice, separated from each other by B8/B2 brightness
//    NDMI   (B8−B11)/(B8+B11)                    canopy/soil moisture: riparian SBT & irrigated crops vs
//                                                dry steppe and barren slopes
//    NBR    (B8−B12)/(B8+B12)                    dryness/vegetation vigour; separates dry gravel bars
//    BSI    ((B11+B4)−(B8+B2))/((B11+B4)+(B8+B2)) bare soil/alluvium vs vegetation
// =====================================================================================================
var INDEX_NAMES = ['NDVI', 'EVI', 'SAVI', 'MSAVI', 'GNDVI', 'NDRE', 'CIre', 'NDWI', 'NDSI_MNDWI', 'NDMI', 'NBR', 'BSI'];

function computeIndices(img) {
  var B = function(n) { return img.select(n); };
  var ndvi  = img.normalizedDifference(['B8', 'B4']).rename('NDVI');
  var evi   = img.expression('2.5 * (NIR - RED) / (NIR + 6 * RED - 7.5 * BLUE + 1)',
                {NIR: B('B8'), RED: B('B4'), BLUE: B('B2')}).clamp(-1, 1).rename('EVI');
  var savi  = img.expression('1.5 * (NIR - RED) / (NIR + RED + 0.5)',
                {NIR: B('B8'), RED: B('B4')}).rename('SAVI');
  var msavi = img.expression('(2 * NIR + 1 - sqrt((2 * NIR + 1) * (2 * NIR + 1) - 8 * (NIR - RED))) / 2',
                {NIR: B('B8'), RED: B('B4')}).rename('MSAVI');
  var gndvi = img.normalizedDifference(['B8', 'B3']).rename('GNDVI');
  var ndre  = img.normalizedDifference(['B8A', 'B5']).rename('NDRE');
  var cire  = img.expression('RE3 / RE1 - 1', {RE3: B('B7'), RE1: B('B5').max(0.001)})
                .clamp(-1, 10).rename('CIre');
  var ndwi  = img.normalizedDifference(['B3', 'B8']).rename('NDWI');
  var mndwi = img.normalizedDifference(['B3', 'B11']).rename('NDSI_MNDWI');
  var ndmi  = img.normalizedDifference(['B8', 'B11']).rename('NDMI');
  var nbr   = img.normalizedDifference(['B8', 'B12']).rename('NBR');
  var bsi   = img.expression('((SWIR1 + RED) - (NIR + BLUE)) / ((SWIR1 + RED) + (NIR + BLUE))',
                {SWIR1: B('B11'), RED: B('B4'), NIR: B('B8'), BLUE: B('B2')}).rename('BSI');
  return ee.Image.cat([ndvi, evi, savi, msavi, gndvi, ndre, cire, ndwi, mndwi, ndmi, nbr, bsi]);
}
var spectralIndices = computeIndices(composite);


// =====================================================================================================
// 11. DEM / TERRAIN VARIABLES
//    Copernicus DEM GLO-30 (30 m; best global DEM for steep Himalayan relief). Slope and aspect are
//    computed on the native 30 m DEM and only then bilinearly interpolated to the 10 m grid.
//    • Elevation: SBT occupies valley floors/floodplains (~2,800–3,800 m here); Snow/Ice is restricted
//      to high ridges; crops sit on irrigated fans/terraces near villages.
//    • Slope: SBT, water and fields are on near-flat floodplains/terraces; barren land and steppe
//      dominate steep valley sides — separates spectrally similar sparse vegetation on slopes.
//    • Northness = cos(aspect)·sin(slope), Eastness = sin(aspect)·sin(slope): aspect is circular (359° ≈ 1°)
//      so it is linearised; weighting by sin(slope) sets flat terrain (undefined aspect) to ≈0.
//      Captures radiation/moisture contrasts that control natural vegetation on slopes.
//    Only 4 terrain variables (5 with HAND) enter next to 22 spectral predictors, so terrain informs the
//    habitat context without dominating the spectral signal; their share of RF importance is reported.
// =====================================================================================================
var demNative;
if (DEM_SOURCE === 'SRTM') {
  demNative = ee.Image('USGS/SRTMGL1_003').select('elevation');
} else {
  var glo30 = ee.ImageCollection('COPERNICUS/DEM/GLO30').filterBounds(roi).select('DEM');
  demNative = glo30.mosaic().setDefaultProjection(glo30.first().projection());
}
demNative = demNative.rename('Elevation');
var slopeNative  = ee.Terrain.slope(demNative);
var aspectNative = ee.Terrain.aspect(demNative);
var slopeRad  = slopeNative.multiply(Math.PI / 180);
var aspectRad = aspectNative.multiply(Math.PI / 180);

var terrainStack = ee.Image.cat([
  demNative.resample('bilinear').rename('Elevation'),
  slopeNative.resample('bilinear').rename('Slope'),
  aspectRad.cos().multiply(slopeRad.sin()).resample('bilinear').rename('Northness'),
  aspectRad.sin().multiply(slopeRad.sin()).resample('bilinear').rename('Eastness')
]);
var TERRAIN_NAMES = ['Elevation', 'Slope', 'Northness', 'Eastness'];
if (USE_HAND) {
  // Height Above Nearest Drainage: low along rivers/floodplains → riparian SBT habitat indicator.
  terrainStack = terrainStack.addBands(
    ee.Image('MERIT/Hydro/v1_0_1').select('hnd').resample('bilinear').rename('HAND'));
  TERRAIN_NAMES.push('HAND');
}


// =====================================================================================================
// 12. PREDICTOR STACK
// =====================================================================================================
var PREDICTORS = S2_BANDS.concat(INDEX_NAMES).concat(TERRAIN_NAMES);
var FEATURE_GROUP = {};
S2_BANDS.forEach(function(b) { FEATURE_GROUP[b] = 'Spectral band'; });
INDEX_NAMES.forEach(function(b) { FEATURE_GROUP[b] = 'Spectral index'; });
TERRAIN_NAMES.forEach(function(b) { FEATURE_GROUP[b] = 'Terrain'; });

var predictorStack = composite.select(S2_BANDS)
  .addBands(spectralIndices)
  .addBands(terrainStack)
  .select(PREDICTORS)
  .clip(roi)
  .toFloat();

// Pixel coordinates in the analysis grid (NOT predictors): identify the exact 10 m pixel of every sample
// so that duplicated pixels and training/validation pixel overlap can be detected and removed.
var samplingImage = predictorStack.addBands(ee.Image.pixelCoordinates(ANALYSIS_PROJ).rename(['px', 'py']));

print('--------------------------------------------------------------------');
print('12. PREDICTOR STACK (' + PREDICTORS.length + ' variables): ' + PREDICTORS.join(', '));
print('Predictor stack band names (server check):', predictorStack.bandNames());


// =====================================================================================================
// 13. TRAINING-NEIGHBOURHOOD GENERATION
//    A GT point is a single GPS fix, but an SBT thicket, field or river reach occupies several 10 m pixels.
//    Each TRAINING point is buffered by `trainingBufferMeters` (default 10 m = one S2 pixel), so the
//    pixel containing the point plus its immediately adjacent pixels whose centres fall inside the circle
//    (typically 2–4 pixels in total) are sampled. Why 10 m:
//      • covers the positional error of hand-held GNSS (≈3–5 m) plus S2 geolocation error (<10 m);
//      • reduces single-pixel noise (mixed pixels, residual mis-registration);
//      • stays well inside typical SBT riparian thickets, irrigated fields and river channels (>20–30 m),
//        so the circle rarely reaches the next land-cover class (set 5 m for very narrow features).
//    Safeguards against crossing into another class:
//      (i)  a neighbour pixel is kept only if it is spectrally consistent with the centre pixel
//           (|ΔNDVI| ≤ NEIGHBOUR_MAX_DNDVI and |ΔMNDWI| ≤ NEIGHBOUR_MAX_DMNDWI);
//      (ii) a pixel claimed by GT points of DIFFERENT classes is discarded (label conflict);
//      (iii) duplicate pixels (shared by nearby GT points) are counted once.
//    Neighbourhoods are generated ONLY for training points (after the split, Section 15); validation
//    uses exactly one pixel per GT point, so validation observations stay independent.
// =====================================================================================================
function makeNeighbourhood(f) {
  return f.buffer(trainingBufferMeters, 0.1);
}


// =====================================================================================================
// 14. SAMPLE EXTRACTION
//    Predictors are extracted on the 10 m analysis grid (EPSG:32643). Samples with ANY null predictor
//    (masked composite pixel, DEM gap) are removed. A GT point is a "valid sample" if its own pixel has a
//    complete predictor vector.
// =====================================================================================================
var POINT_PROPS = [CLASS_PROP, 'gt_id', 'lon', 'lat'];

function samplePixels(regions) {
  return samplingImage.sampleRegions({
    collection: regions,
    properties: POINT_PROPS,
    projection: ANALYSIS_PROJ,
    scale: ANALYSIS_SCALE,
    tileScale: 4,
    geometries: false
  })
  .filter(ee.Filter.notNull(PREDICTORS))
  .map(function(f) {
    // Unique numeric pixel identifier from the grid coordinates of the sampled pixel.
    var key = ee.Number(f.get('px')).round().multiply(1e8).add(ee.Number(f.get('py')).round());
    return f.set('pix_key', key);
  });
}

var centreSamples = samplePixels(gtInRoi);                       // one pixel per GT point (valid only)
var validIds = centreSamples.aggregate_array('gt_id');
var gtValid = gtInRoi.filter(ee.Filter.inList('gt_id', validIds)); // GT points with a complete predictor vector

print('--------------------------------------------------------------------');
print('14. SAMPLE EXTRACTION');
print('Training neighbourhood (buffer) radius: ' + trainingBufferMeters + ' m');
print('Valid GT samples (complete predictor vector at the GT pixel):', centreSamples.size());
print('Valid GT samples per class code {code: n}:', centreSamples.aggregate_histogram(CLASS_PROP));


// =====================================================================================================
// 15. TRAINING / VALIDATION SPLIT  (done on GT POINTS, before any neighbourhood sampling → no leakage)
//    'stratified_random': within every class, points are shuffled with RANDOM_SEED and exactly
//        round(TRAIN_FRACTION × n_class) go to training, the rest to validation → every class keeps the
//        same proportion in both sets.
//    'spatial_block': points are grouped in SPATIAL_BLOCK_SIZE_M blocks and whole blocks are assigned
//        to one set (reduces optimistic bias from spatial autocorrelation; class proportions approximate).
//    Leakage controls: neighbourhoods only for training points; training pixels that coincide with any
//    validation pixel are dropped; RF never sees the validation partition.
// =====================================================================================================
function stratifiedRandomSplit(points) {
  var out = null;
  CLASS_CODES.forEach(function(code) {
    var sub = points.filter(ee.Filter.eq(CLASS_PROP, code))
      .randomColumn('split_rand', RANDOM_SEED).sort('split_rand');
    var n = sub.size();
    var nTrain = n.multiply(TRAIN_FRACTION).round().toInt();
    var list = sub.toList(n.max(1));
    var tr = ee.FeatureCollection(list.slice(0, nTrain)).map(function(f) { return f.set('partition', 'training'); });
    var va = ee.FeatureCollection(list.slice(nTrain)).map(function(f) { return f.set('partition', 'validation'); });
    out = (out === null) ? tr.merge(va) : out.merge(tr).merge(va);
  });
  return out;
}

function spatialBlockSplit(points) {
  var withBlock = points.map(function(f) {
    var xy = f.geometry().transform(ANALYSIS_CRS, 0.1).coordinates();
    var bx = ee.Number(xy.get(0)).divide(SPATIAL_BLOCK_SIZE_M).floor().toInt().format('%d');
    var by = ee.Number(xy.get(1)).divide(SPATIAL_BLOCK_SIZE_M).floor().toInt().format('%d');
    return f.set('block_id', bx.cat('_').cat(by));
  });
  var blocks = withBlock.distinct('block_id').select(['block_id']).randomColumn('block_rand', RANDOM_SEED);
  var joined = ee.Join.saveFirst('blk').apply({
    primary: withBlock, secondary: blocks,
    condition: ee.Filter.equals({leftField: 'block_id', rightField: 'block_id'})
  });
  return ee.FeatureCollection(joined).map(function(f) {
    var r = ee.Number(ee.Feature(f.get('blk')).get('block_rand'));
    return f.set('partition', ee.Algorithms.If(r.lt(TRAIN_FRACTION), 'training', 'validation'));
  }).select(POINT_PROPS.concat(['label_raw', 'partition', 'block_id']));
}

var gtPartitioned = (SPLIT_STRATEGY === 'spatial_block') ? spatialBlockSplit(gtValid) : stratifiedRandomSplit(gtValid);
var gtTrainPts = gtPartitioned.filter(ee.Filter.eq('partition', 'training'));
var gtValPts   = gtPartitioned.filter(ee.Filter.eq('partition', 'validation'));
var trainIds   = gtTrainPts.aggregate_array('gt_id');
var valIds     = gtValPts.aggregate_array('gt_id');

var trainCentre = centreSamples.filter(ee.Filter.inList('gt_id', trainIds))
  .map(function(f) { return f.set('sample_type', 'centre'); });
var valSamples  = centreSamples.filter(ee.Filter.inList('gt_id', valIds));
var valPixKeys  = valSamples.aggregate_array('pix_key');

// --- neighbourhood pixels of TRAINING points, screened for spectral consistency with the centre pixel
var trainingPixelsAll = trainCentre;
if (trainingBufferMeters > 0) {
  var neighbourRaw = samplePixels(gtTrainPts.map(makeNeighbourhood));
  var neighbourJoined = ee.FeatureCollection(ee.Join.saveFirst('centre').apply({
    primary: neighbourRaw, secondary: trainCentre,
    condition: ee.Filter.equals({leftField: 'gt_id', rightField: 'gt_id'})
  }));
  var neighbourKept = neighbourJoined.map(function(f) {
    var c = ee.Feature(f.get('centre'));
    return f.set({
      dNDVI:  ee.Number(f.get('NDVI')).subtract(ee.Number(c.get('NDVI'))).abs(),
      dMNDWI: ee.Number(f.get('NDSI_MNDWI')).subtract(ee.Number(c.get('NDSI_MNDWI'))).abs(),
      sample_type: 'neighbour'
    });
  })
  .filter(ee.Filter.lte('dNDVI', NEIGHBOUR_MAX_DNDVI))
  .filter(ee.Filter.lte('dMNDWI', NEIGHBOUR_MAX_DMNDWI));
  trainingPixelsAll = trainCentre.merge(neighbourKept);
}
var TRAIN_PROPS = PREDICTORS.concat([CLASS_PROP, 'gt_id', 'pix_key', 'sample_type']);
trainingPixelsAll = trainingPixelsAll.select(TRAIN_PROPS);

// (a) remove any training pixel that is also a validation pixel (pixel-level leakage guard)
var trainingNoLeak = trainingPixelsAll.filter(ee.Filter.inList('pix_key', valPixKeys).not());
// (b) remove pixels claimed by GT points of different classes (label conflict). An equality-only
//     self-join collects all samples of the same pixel; the pixel is kept only if they share one class.
var samePixelJoined = ee.FeatureCollection(ee.Join.saveAll({matchesKey: 'same_pixel'}).apply({
  primary: trainingNoLeak, secondary: trainingNoLeak,
  condition: ee.Filter.equals({leftField: 'pix_key', rightField: 'pix_key'})
}));
var trainingNoConflict = samePixelJoined.map(function(f) {
  var nLabels = ee.FeatureCollection(ee.List(f.get('same_pixel'))).aggregate_count_distinct(CLASS_PROP);
  return f.set('n_labels', nLabels);
}).filter(ee.Filter.eq('n_labels', 1)).select(TRAIN_PROPS);
// (c) count every pixel once
var trainingUnique = trainingNoConflict.distinct('pix_key');

// (d) optional per-class cap (centre pixels are preferred over neighbour pixels) to limit the
//     dominance of the much larger SBT sample in the RF majority vote.
function capPerClass(fc) {
  if (!MAX_TRAIN_PIXELS_PER_CLASS || MAX_TRAIN_PIXELS_PER_CLASS <= 0) return fc;
  var out = null;
  CLASS_CODES.forEach(function(code) {
    var sub = fc.filter(ee.Filter.eq(CLASS_PROP, code))
      .randomColumn('cap_rand', RANDOM_SEED)
      .map(function(f) {
        var penalty = ee.Number(ee.Algorithms.If(ee.String(f.get('sample_type')).equals('centre'), 0, 1));
        return f.set('cap_key', ee.Number(f.get('cap_rand')).add(penalty));
      })
      .sort('cap_key')
      .limit(MAX_TRAIN_PIXELS_PER_CLASS);
    out = (out === null) ? sub : out.merge(sub);
  });
  return out;
}
var trainingPixels = capPerClass(trainingUnique).select(TRAIN_PROPS);

print('--------------------------------------------------------------------');
print('15. TRAINING / VALIDATION SPLIT (' + SPLIT_STRATEGY + ', ' + Math.round(TRAIN_FRACTION * 100) + ' / ' +
      Math.round((1 - TRAIN_FRACTION) * 100) + ', seed ' + RANDOM_SEED + ')');
print('Training GT points:', gtTrainPts.size());
print('Validation GT points (one pixel each):', gtValPts.size());
print('Training pixels per class after neighbourhood screening, de-duplication and cap {code: n}:',
      trainingPixels.aggregate_histogram(CLASS_PROP));


// =====================================================================================================
// 16. RANDOM FOREST TRAINING
//    numberOfTrees      RF_NUM_TREES (500) — error and importance stabilise well before 500 trees.
//    variablesPerSplit  floor(√p) — Breiman (2001) default for classification; decorrelates trees.
//    minLeafPopulation  1 — fully grown trees (low bias; variance controlled by averaging).
//    bagFraction        0.632 — subsampling without replacement; gives less biased variable importance
//                       than bootstrap with correlated predictors (Strobl et al. 2007) and leaves ≈37 %
//                       out-of-bag samples for the OOB error estimate.
//    seed               RANDOM_SEED — identical forest on every run.
// =====================================================================================================
var RF_MTRY = RF_VARIABLES_PER_SPLIT || Math.max(1, Math.floor(Math.sqrt(PREDICTORS.length)));

function makeRandomForest() {
  return ee.Classifier.smileRandomForest({
    numberOfTrees: RF_NUM_TREES,
    variablesPerSplit: RF_MTRY,
    minLeafPopulation: RF_MIN_LEAF_POPULATION,
    bagFraction: RF_BAG_FRACTION,
    seed: RF_SEED
  });
}
var rfTrainArgs = {features: trainingPixels, classProperty: CLASS_PROP, inputProperties: PREDICTORS};
var rfClassifier     = makeRandomForest().train(rfTrainArgs);
var rfProbClassifier = makeRandomForest().setOutputMode('MULTIPROBABILITY').train(rfTrainArgs);  // same forest
var rfExplain = ee.Dictionary(rfClassifier.explain());

print('--------------------------------------------------------------------');
print('16. RANDOM FOREST: trees = ' + RF_NUM_TREES + ', variablesPerSplit = ' + RF_MTRY +
      ', minLeafPopulation = ' + RF_MIN_LEAF_POPULATION + ', bagFraction = ' + RF_BAG_FRACTION +
      ', seed = ' + RF_SEED + ', predictors = ' + PREDICTORS.length);
print('RF out-of-bag error estimate:', rfExplain.get('outOfBagErrorEstimate'));


// =====================================================================================================
// 17. CLASSIFICATION
// =====================================================================================================
var lulcRF = predictorStack.classify(rfClassifier, 'LULC');
var lulc = lulcRF;
if (APPLY_SBT_HABITAT_RULE) {
  var ndviImg = predictorStack.select('NDVI');
  var implausibleSBT = lulcRF.eq(SBT_CODE).and(
    predictorStack.select('Elevation').gt(SBT_MAX_ELEVATION_M)
      .or(predictorStack.select('Slope').gt(SBT_MAX_SLOPE_DEG))
      .or(ndviImg.lt(SBT_MIN_NDVI)));
  var replacementClass = ee.Image(5).where(ndviImg.gte(SBT_MIN_NDVI), 3);   // Barren Land / Natural Vegetation
  lulc = lulcRF.where(implausibleSBT, replacementClass);
}
lulc = lulc.rename('LULC').toByte().clip(roi);                      // final 6-class map (codes 1–6)

// Binary SBT product used for SBT area estimation: SBT = 1, non-SBT = 0 (unclassified stays masked).
var sbtBinary = lulc.eq(SBT_CODE).rename('SBT_binary').toByte();

// RF class-membership probability of SBT (share of trees voting SBT), 0–100 %.
var sbtProbability = predictorStack.classify(rfProbClassifier)
  .arrayGet([CLASS_CODES.indexOf(SBT_CODE)]).multiply(100).rename('SBT_probability_pct').toFloat();


// =====================================================================================================
// 18. ACCURACY ASSESSMENT  (independent validation GT points, one pixel each, on the FINAL map)
//    Confusion matrix rows = reference (GT) class, columns = mapped class, order 1–6.
//    Producer's accuracy (PA) = recall = 1 − omission error.
//    User's accuracy (UA) = precision = 1 − commission error (GEE calls UA "consumer's accuracy").
//    F1 = 2·P·R/(P + R). One-vs-rest TP/FP/FN/TN are reported for every class (SBT = principal class).
// =====================================================================================================
var valClassified = lulc.sampleRegions({
  collection: gtValPts,
  properties: POINT_PROPS,
  projection: ANALYSIS_PROJ,
  scale: ANALYSIS_SCALE,
  tileScale: 4,
  geometries: false
});
var confusionMatrix = valClassified.errorMatrix(CLASS_PROP, 'LULC', CLASS_CODES);
var cmArray     = confusionMatrix.array();
var rowTotals   = cmArray.reduce(ee.Reducer.sum(), [1]);   // n × 1 : reference totals
var colTotals   = cmArray.reduce(ee.Reducer.sum(), [0]);   // 1 × n : mapped totals
var nValidation = ee.Number(cmArray.reduce(ee.Reducer.sum(), [0, 1]).get([0, 0]));
var overallAccuracy = ee.Number(confusionMatrix.accuracy());
var kappa = ee.Number(confusionMatrix.kappa());

function safeDiv(a, b) {
  a = ee.Number(a); b = ee.Number(b);
  return ee.Number(ee.Algorithms.If(b.eq(0), 0, a.divide(b.max(1e-12))));
}

var accuracyByClass = ee.FeatureCollection(CLASS_CODES.map(function(code, i) {
  var tp   = ee.Number(cmArray.get([i, i]));
  var refN = ee.Number(rowTotals.get([i, 0]));
  var mapN = ee.Number(colTotals.get([0, i]));
  var fn = refN.subtract(tp);
  var fp = mapN.subtract(tp);
  var tn = nValidation.subtract(tp).subtract(fn).subtract(fp);
  var recall    = safeDiv(tp, refN);
  var precision = safeDiv(tp, mapN);
  var f1 = safeDiv(precision.multiply(recall).multiply(2), precision.add(recall));
  return ee.Feature(null, {
    Class: CLASS_NAMES[i], Class_Code: code, Reference_n: refN, Mapped_n: mapN,
    TP: tp, FP: fp, FN: fn, TN: tn,
    Producers_Accuracy_pct: recall.multiply(100),
    Users_Accuracy_pct: precision.multiply(100),
    Precision: precision, Recall: recall, F1_score: f1,
    Omission_Error_pct: ee.Number(100).subtract(recall.multiply(100)),
    Commission_Error_pct: ee.Number(100).subtract(precision.multiply(100))
  });
}));
var macroF1 = ee.Number(accuracyByClass.aggregate_mean('F1_score'));
var sbtAccuracy = ee.Feature(accuracyByClass.filter(ee.Filter.eq('Class_Code', SBT_CODE)).first());

var accuracyOverall = ee.FeatureCollection([
  ee.Feature(null, {Metric: 'Overall_Accuracy_pct', Value: overallAccuracy.multiply(100)}),
  ee.Feature(null, {Metric: 'Kappa', Value: kappa}),
  ee.Feature(null, {Metric: 'Macro_F1', Value: macroF1}),
  ee.Feature(null, {Metric: 'RF_OOB_Error', Value: rfExplain.get('outOfBagErrorEstimate')}),
  ee.Feature(null, {Metric: 'Validation_points_n', Value: nValidation}),
  ee.Feature(null, {Metric: 'SBT_TP', Value: sbtAccuracy.get('TP')}),
  ee.Feature(null, {Metric: 'SBT_FP', Value: sbtAccuracy.get('FP')}),
  ee.Feature(null, {Metric: 'SBT_FN', Value: sbtAccuracy.get('FN')}),
  ee.Feature(null, {Metric: 'SBT_TN', Value: sbtAccuracy.get('TN')}),
  ee.Feature(null, {Metric: 'SBT_Precision', Value: sbtAccuracy.get('Precision')}),
  ee.Feature(null, {Metric: 'SBT_Recall', Value: sbtAccuracy.get('Recall')}),
  ee.Feature(null, {Metric: 'SBT_F1_score', Value: sbtAccuracy.get('F1_score')}),
  ee.Feature(null, {Metric: 'SBT_Producers_Accuracy_pct', Value: sbtAccuracy.get('Producers_Accuracy_pct')}),
  ee.Feature(null, {Metric: 'SBT_Users_Accuracy_pct', Value: sbtAccuracy.get('Users_Accuracy_pct')})
]);

// Confusion matrix in tabular (Excel) form: 6 reference rows + column totals + user's accuracy row.
var CM_COLUMNS = CLASS_KEYS.map(function(k) { return 'Mapped_' + k; });
var cmRows = CLASS_CODES.map(function(code, i) {
  var props = {Reference_Class: CLASS_NAMES[i], Class_Code: code};
  CLASS_CODES.forEach(function(c2, j) { props[CM_COLUMNS[j]] = cmArray.get([i, j]); });
  props.Row_Total = rowTotals.get([i, 0]);
  props.Producers_Accuracy_pct = safeDiv(cmArray.get([i, i]), rowTotals.get([i, 0])).multiply(100);
  return ee.Feature(null, props);
});
var cmTotalProps = {Reference_Class: 'Column_Total'};
var cmUaProps    = {Reference_Class: 'Users_Accuracy_pct'};
CLASS_CODES.forEach(function(c2, j) {
  cmTotalProps[CM_COLUMNS[j]] = colTotals.get([0, j]);
  cmUaProps[CM_COLUMNS[j]] = safeDiv(cmArray.get([j, j]), colTotals.get([0, j])).multiply(100);
});
cmTotalProps.Row_Total = nValidation;
var confusionTable = ee.FeatureCollection(cmRows.concat([ee.Feature(null, cmTotalProps), ee.Feature(null, cmUaProps)]));

// Point-level validation record (reference vs mapped class for every validation GT point).
var validationPointTable = valClassified.map(function(f) {
  var ref = ee.Number(f.get(CLASS_PROP)).toInt();
  var pred = ee.Number(f.get('LULC')).toInt();
  return ee.Feature(null, {
    GT_ID: f.get('gt_id'), Longitude: f.get('lon'), Latitude: f.get('lat'),
    Reference_Code: ref, Reference_Class: codeToName(ref),
    Mapped_Code: pred, Mapped_Class: codeToName(pred), Correct: ref.eq(pred)
  });
});

print('--------------------------------------------------------------------');
print('18. ACCURACY (validation GT points, final map)');
print('Confusion matrix (rows = reference 1–6, columns = mapped 1–6):', confusionMatrix);
print('Overall accuracy:', overallAccuracy);
print('Kappa:', kappa);


// =====================================================================================================
// 19. RANDOM FOREST FEATURE IMPORTANCE
//    GEE/Smile RF importance = total decrease in Gini impurity contributed by each variable over all
//    trees; also expressed as % of the total. Group totals show how much terrain contributes relative to
//    spectral information. A per-class spectral/terrain signature table (mean ± SD of every predictor
//    at the valid GT pixels) supports interpretation of class separability.
// =====================================================================================================
var importanceDict = ee.Dictionary(rfExplain.get('importance'));
var importanceSum  = ee.Number(importanceDict.values().reduce(ee.Reducer.sum()));
var FEATURE_GROUP_DICT = ee.Dictionary(FEATURE_GROUP);
var importanceSorted = ee.FeatureCollection(importanceDict.keys().map(function(k) {
  var v = ee.Number(importanceDict.get(k));
  return ee.Feature(null, {
    Feature: k,
    Feature_Group: FEATURE_GROUP_DICT.get(k, 'Other'),
    Importance: v,
    Relative_Importance_pct: v.divide(importanceSum).multiply(100)
  });
})).sort('Importance', false);
var importanceList = importanceSorted.toList(PREDICTORS.length + 5);
var importanceTable = ee.FeatureCollection(ee.List.sequence(0, importanceList.size().subtract(1)).map(function(i) {
  return ee.Feature(importanceList.get(i)).set('Rank', ee.Number(i).add(1).toInt());
}));
var importanceByGroup = ee.FeatureCollection(['Spectral band', 'Spectral index', 'Terrain'].map(function(g) {
  var sub = importanceTable.filter(ee.Filter.eq('Feature_Group', g));
  return ee.Feature(null, {Feature_Group: g, n_Features: sub.size(),
                           Relative_Importance_pct: sub.aggregate_sum('Relative_Importance_pct')});
}));

// Class signatures (mean and SD of every predictor per class, valid GT pixels).
var signatureStats = centreSamples.reduceColumns({
  reducer: ee.Reducer.mean().combine({reducer2: ee.Reducer.stdDev(), sharedInputs: true})
    .repeat(PREDICTORS.length).group({groupField: PREDICTORS.length, groupName: 'class_code'}),
  selectors: PREDICTORS.concat([CLASS_PROP])
});
var signatureGroups = ee.List(signatureStats.get('groups'));
var signatureByCode = ee.Dictionary.fromLists(
  signatureGroups.map(function(g) { return ee.Number(ee.Dictionary(g).get('class_code')).toInt().format('%d'); }),
  signatureGroups);
var emptySignature = ee.Dictionary({mean: ee.List.repeat(-9999, PREDICTORS.length),
                                    stdDev: ee.List.repeat(-9999, PREDICTORS.length)});
var signatureTable = ee.FeatureCollection(PREDICTORS.map(function(p, j) {
  var props = {Feature: p, Feature_Group: FEATURE_GROUP[p]};
  CLASS_CODES.forEach(function(code, i) {
    var g = ee.Dictionary(signatureByCode.get(String(code), emptySignature));
    props['Mean_' + CLASS_KEYS[i]] = ee.List(g.get('mean')).get(j);
    props['SD_' + CLASS_KEYS[i]]   = ee.List(g.get('stdDev')).get(j);
  });
  return ee.Feature(null, props);
}));


// =====================================================================================================
// 20. CLASS AREA CALCULATION
//    Area = Σ true pixel area (ee.Image.pixelArea, m², geodesic per pixel) on the exact 10 m analysis
//    grid within the exact ROI — not pixel count × nominal 100 m². Pixels without a valid summer
//    observation are reported separately as "Unclassified", so percentages sum to 100 % of the ROI.
// =====================================================================================================
var areaImage = ee.Image.pixelArea().addBands(lulc.unmask(0).rename('class_code'));
var areaStats = areaImage.reduceRegion({
  reducer: ee.Reducer.sum().group({groupField: 1, groupName: 'class_code'}),
  geometry: roi,
  crs: ANALYSIS_CRS,
  crsTransform: CRS_TRANSFORM,
  maxPixels: 1e13,
  tileScale: 8
});
var areaGroups = ee.List(areaStats.get('groups'));
var areaByCode = ee.Dictionary.fromLists(
  areaGroups.map(function(g) { return ee.Number(ee.Dictionary(g).get('class_code')).toInt().format('%d'); }),
  areaGroups.map(function(g) { return ee.Dictionary(g).get('sum'); }));
var roiPixelArea    = ee.Number(areaByCode.values().reduce(ee.Reducer.sum()));
var unclassifiedM2  = ee.Number(areaByCode.get('0', 0));
var classifiedArea  = roiPixelArea.subtract(unclassifiedM2);

function areaRow(name, code, m2, includeClassifiedPct) {
  m2 = ee.Number(m2);
  var props = {
    Class: name, Area_m2: m2, Area_ha: m2.divide(1e4), Area_km2: m2.divide(1e6),
    Percentage_of_ROI: m2.divide(roiPixelArea).multiply(100)
  };
  if (code !== null) props.Class_Code = code;
  if (includeClassifiedPct) props.Percentage_of_Classified = safeDiv(m2, classifiedArea).multiply(100);
  return ee.Feature(null, props);
}
var classAreaTable = ee.FeatureCollection(
  CLASS_CODES.map(function(code, i) { return areaRow(CLASS_NAMES[i], code, areaByCode.get(String(code), 0), true); })
  .concat([
    areaRow('Unclassified (no valid summer observation)', 0, unclassifiedM2, false),
    areaRow('TOTAL ROI', null, roiPixelArea, false)
  ]));


// =====================================================================================================
// 21. SBT AREA CALCULATION (from the binary SBT map)
//    SBT area = Σ pixelArea where SBT_binary = 1. The same grid, ROI and pixel-area model as Section 20
//    are used, so this value is identical to the SBT row of the class-area table (cross-check).
// =====================================================================================================
var sbtAreaM2 = ee.Number(areaByCode.get(String(SBT_CODE), 0));    // used for the Console summary
var sbtBinaryArea = sbtBinary.unmask(0).multiply(ee.Image.pixelArea()).rename('SBT_m2')
  .addBands(ee.Image.pixelArea().rename('ROI_m2'))
  .reduceRegion({
    reducer: ee.Reducer.sum(), geometry: roi, crs: ANALYSIS_CRS, crsTransform: CRS_TRANSFORM,
    maxPixels: 1e13, tileScale: 8
  });
var sbtBinaryM2 = ee.Number(sbtBinaryArea.get('SBT_m2'));
var sbtRoiM2    = ee.Number(sbtBinaryArea.get('ROI_m2'));
var sbtAreaTable = ee.FeatureCollection([ee.Feature(null, {
  Product: 'SBT binary map (SBT = 1, non-SBT = 0)',
  Summer_Window: SUMMER_LABEL,
  SBT_Area_m2: sbtBinaryM2,
  SBT_Area_ha: sbtBinaryM2.divide(1e4),
  SBT_Area_km2: sbtBinaryM2.divide(1e6),
  ROI_Area_km2: sbtRoiM2.divide(1e6),
  SBT_Percentage_of_ROI: sbtBinaryM2.divide(sbtRoiM2).multiply(100)
})]);


// =====================================================================================================
// 22. GROUND-TRUTH VISUALISATION
//    Every class has its own colour AND symbol (black outline so white Snow/Ice symbols stay visible).
// =====================================================================================================
var GT_SYMBOL_GLYPHS = ['★', '■', '▲', '●', '◆', '⬢'];   // legend glyphs matching GT_SYMBOLS
var gtStyleDict = {};
CLASS_CODES.forEach(function(code, i) {
  gtStyleDict[String(code)] = {color: '000000', fillColor: CLASS_PALETTE[i], pointShape: GT_SYMBOLS[i],
                               pointSize: (code === SBT_CODE ? 7 : 6), width: 1};
});
var GT_STYLE = ee.Dictionary(gtStyleDict);

function styleByClass(fc) {
  return fc.map(function(f) {
    return f.set('style', GT_STYLE.get(ee.Number(f.get(CLASS_PROP)).toInt().format('%d')));
  }).style({styleProperty: 'style'});
}
var gtAllLayerImg   = styleByClass(gtInRoi);
var gtTrainLayerImg = gtTrainPts.style({color: '000000', fillColor: 'FFFFFF', pointShape: 'circle', pointSize: 4, width: 1});
var gtValLayerImg   = gtValPts.style({color: '000000', fillColor: 'FF00FF', pointShape: 'diamond', pointSize: 5, width: 1});

// Validation points whose mapped class differs from the GT class (visual error analysis).
var valMisclassified = lulc.sampleRegions({
  collection: gtValPts, properties: [CLASS_PROP], projection: ANALYSIS_PROJ, scale: ANALYSIS_SCALE,
  tileScale: 4, geometries: true
}).map(function(f) {
  return f.set('correct', ee.Number(f.get(CLASS_PROP)).eq(ee.Number(f.get('LULC'))));
}).filter(ee.Filter.eq('correct', 0));
var valMisLayerImg = valMisclassified.style({color: '000000', fillColor: '00FFFF', pointShape: 'cross', pointSize: 7, width: 1});


// =====================================================================================================
// 23. SENTINEL-2 FALSE COLOUR COMPOSITE (FCC)
//    Standard vegetation FCC: R = NIR (B8), G = Red (B4), B = Green (B3).
//      • Vegetation reflects strongly in NIR → red tones. Irrigated crops: bright red/pink (high NIR, dense
//        green canopy); SBT thickets: darker crimson–maroon, coarse texture along rivers (silvery foliage,
//        woody structure, shadowing); sparse natural vegetation: faint pink–brown on slopes.
//      • Water absorbs NIR → dark blue/black (turbid glacial rivers appear cyan–blue).
//      • Bare soil / alluvial deposits → grey, cyan-grey or beige; Snow/Ice → white.
//    A SWIR FCC (R = B11, G = B8, B = B4) is also provided: snow/ice appears cyan-blue (low SWIR),
//    moist vegetation green, dry alluvium pink-brown.
// =====================================================================================================
var TRUE_COLOUR_VIS = {bands: ['B4', 'B3', 'B2'], min: 0.0, max: 0.35, gamma: 1.3};
var FCC_VIS         = {bands: ['B8', 'B4', 'B3'], min: 0.0, max: [0.50, 0.35, 0.35], gamma: 1.2};
var SWIR_FCC_VIS    = {bands: ['B11', 'B8', 'B4'], min: 0.0, max: [0.50, 0.50, 0.35], gamma: 1.2};
var fccImage = composite.visualize(FCC_VIS).rename(['FCC_R_B8', 'FCC_G_B4', 'FCC_B_B3']);


// =====================================================================================================
// 24. MAP VISUALISATION AND LEGEND
//    Layers are added bottom → top. Toggle them in the Layers menu (top-right of the map).
// =====================================================================================================
Map.setOptions('SATELLITE');
Map.centerObject(roi, 10);

var roiOutline = ee.Image().byte().paint({featureCollection: ee.FeatureCollection([ee.Feature(roi)]), color: 1, width: 2});
var LULC_VIS = {min: 1, max: 6, palette: CLASS_PALETTE};

Map.addLayer(composite, TRUE_COLOUR_VIS, 'S2 summer composite – true colour (B4-B3-B2)', false);
Map.addLayer(fccImage, {}, 'S2 FCC – NIR-Red-Green (B8-B4-B3)', true);
Map.addLayer(composite, SWIR_FCC_VIS, 'S2 FCC – SWIR1-NIR-Red (B11-B8-B4)', false);
Map.addLayer(clearObsCount, {min: 0, max: 20, palette: ['D73027', 'FEE08B', '1A9850']}, 'QC – clear summer observations per pixel', false);
Map.addLayer(spectralIndices.select('NDVI'), {min: -0.1, max: 0.7, palette: ['8C510A', 'F6E8C3', 'C7EAE5', '35978F', '01665E']}, 'NDVI (summer composite)', false);
Map.addLayer(lulc, LULC_VIS, 'RF LULC classification (1–6)', true);
Map.addLayer(sbtBinary, {min: 0, max: 1, palette: ['EDEDED', CLASS_PALETTE[0]]}, 'SBT binary (1 = SBT, 0 = non-SBT)', false);
Map.addLayer(sbtBinary.selfMask(), {palette: [CLASS_PALETTE[0]]}, 'SBT extent only (over FCC)', false);
Map.addLayer(sbtProbability.updateMask(sbtProbability.gte(10)), {min: 10, max: 100, palette: ['FFFFB2', 'FECC5C', 'FD8D3C', 'F03B20', 'BD0026']}, 'SBT RF probability (%)', false);
Map.addLayer(gtAllLayerImg, {}, 'Ground truth – all GT points in ROI (by class)', true);
Map.addLayer(gtTrainLayerImg, {}, 'Ground truth – training points', false);
Map.addLayer(gtValLayerImg, {}, 'Ground truth – validation points', false);
Map.addLayer(valMisLayerImg, {}, 'QC – misclassified validation points', false);
Map.addLayer(roiOutline, {palette: ['FFFF00']}, 'ROI', true);

var legend = ui.Panel({style: {position: 'bottom-left', padding: '8px 12px', backgroundColor: 'rgba(255,255,255,0.92)'}});
legend.add(ui.Label('Seabuckthorn LULC (Random Forest)', {fontWeight: 'bold', fontSize: '14px', margin: '0 0 2px 0'}));
legend.add(ui.Label('Sentinel-2 summer ' + SUMMER_LABEL + ' | 10 m',
                    {fontSize: '11px', color: '#444', margin: '0 0 6px 0'}));
CLASS_CODES.forEach(function(code, i) {
  legend.add(ui.Panel([
    ui.Label('', {backgroundColor: '#' + CLASS_PALETTE[i], padding: '8px', margin: '0 6px 4px 0', border: '1px solid #555'}),
    ui.Label(code + '  ' + CLASS_NAMES[i], {margin: '0 8px 4px 0', fontSize: '12px'}),
    ui.Label('GT ' + GT_SYMBOL_GLYPHS[i], {margin: '0 0 4px 0', fontSize: '12px', color: '#' + (code === 6 ? '777777' : CLASS_PALETTE[i])})
  ], ui.Panel.Layout.Flow('horizontal')));
});
legend.add(ui.Label('SBT binary: red = SBT (1), grey = non-SBT (0)', {fontSize: '11px', color: '#444', margin: '4px 0 0 0'}));
legend.add(ui.Label('Magenta ◆ validation GT | cyan ✚ misclassified validation GT', {fontSize: '11px', color: '#444'}));
Map.add(legend);


// =====================================================================================================
// 25. CONSOLE SUMMARY
//    Results are computed server-side and arrive asynchronously; each block below is reserved in a fixed
//    position so the Console reads top-to-bottom like a report. Every table is a Google Table chart:
//    click its pop-out icon (↗) to enlarge and "Download CSV". The final block is tab-separated text that
//    pastes directly into Excel (columns split automatically) or Word.
// =====================================================================================================

// ---- Table 2 (training samples) is assembled here because it summarises Sections 4–15 ----
function histCount(hist, code) { return ee.Number(ee.Dictionary(hist).get(String(code), 0)); }
var hInitial  = gtAll.aggregate_histogram(CLASS_PROP);
var hInRoi    = gtInRoi.aggregate_histogram(CLASS_PROP);
var hValid    = centreSamples.aggregate_histogram(CLASS_PROP);
var hTrain    = gtTrainPts.aggregate_histogram(CLASS_PROP);
var hVal      = gtValPts.aggregate_histogram(CLASS_PROP);
var hTrainPix = trainingPixels.aggregate_histogram(CLASS_PROP);
var trainingSampleTable = ee.FeatureCollection(CLASS_CODES.map(function(code, i) {
  return ee.Feature(null, {
    Class: CLASS_NAMES[i], Class_Code: code,
    Initial_GT: histCount(hInitial, code), Within_ROI: histCount(hInRoi, code),
    Valid_Samples: histCount(hValid, code), Training: histCount(hTrain, code),
    Validation: histCount(hVal, code), Training_Pixels: histCount(hTrainPix, code)
  });
}).concat([ee.Feature(null, {
  Class: 'TOTAL', Initial_GT: gtAll.size(), Within_ROI: gtInRoi.size(), Valid_Samples: centreSamples.size(),
  Training: gtTrainPts.size(), Validation: gtValPts.size(), Training_Pixels: trainingPixels.size()
})]));

// ---- Column definitions (shared by the Console tables and the CSV exports) ----
var T1_COLUMNS = ['Class', 'Class_Code', 'Area_m2', 'Area_ha', 'Area_km2', 'Percentage_of_ROI', 'Percentage_of_Classified'];
var T1B_COLUMNS = ['Product', 'Summer_Window', 'SBT_Area_m2', 'SBT_Area_ha', 'SBT_Area_km2', 'ROI_Area_km2', 'SBT_Percentage_of_ROI'];
var T2_COLUMNS = ['Class', 'Class_Code', 'Initial_GT', 'Within_ROI', 'Valid_Samples', 'Training', 'Validation', 'Training_Pixels'];
var T3_COLUMNS = ['Rank', 'Feature', 'Feature_Group', 'Importance', 'Relative_Importance_pct'];
var T3B_COLUMNS = ['Feature_Group', 'n_Features', 'Relative_Importance_pct'];
var T4_COLUMNS = ['Reference_Class', 'Class_Code'].concat(CM_COLUMNS).concat(['Row_Total', 'Producers_Accuracy_pct']);
var T5_COLUMNS = ['Class', 'Class_Code', 'Reference_n', 'Mapped_n', 'TP', 'FP', 'FN', 'TN', 'Producers_Accuracy_pct',
                  'Users_Accuracy_pct', 'Precision', 'Recall', 'F1_score', 'Omission_Error_pct', 'Commission_Error_pct'];
var T5B_COLUMNS = ['Metric', 'Value'];
var T7_COLUMNS = ['Feature', 'Feature_Group'];
CLASS_KEYS.forEach(function(k) { T7_COLUMNS.push('Mean_' + k); T7_COLUMNS.push('SD_' + k); });

var DECIMALS = {Area_m2: 1, Area_ha: 3, Area_km2: 4, Percentage_of_ROI: 3, Percentage_of_Classified: 3,
                SBT_Area_m2: 1, SBT_Area_ha: 3, SBT_Area_km2: 4, ROI_Area_km2: 3, SBT_Percentage_of_ROI: 3,
                Importance: 3, Relative_Importance_pct: 2, Producers_Accuracy_pct: 2, Users_Accuracy_pct: 2,
                Omission_Error_pct: 2, Commission_Error_pct: 2, Precision: 4, Recall: 4, F1_score: 4, Value: 4};

// ---- Client-side rendering helpers ----
var STYLE_H = {fontWeight: 'bold', fontSize: '13px', margin: '12px 0 2px 0', color: '#1B4F72'};
var STYLE_TEXT = {whiteSpace: 'pre', fontFamily: 'monospace', fontSize: '11px', margin: '2px 0'};
function consoleSlot(title) {
  var panel = ui.Panel([ui.Label(title, STYLE_H), ui.Label('computing…', {color: '#888', fontSize: '11px'})]);
  print(panel);
  return panel;
}
function fillSlot(panel, widgets) {
  var title = panel.widgets().get(0);
  panel.clear();
  panel.add(title);
  widgets.forEach(function(w) { panel.add(w); });
}
function textLabel(text) { return ui.Label(text, STYLE_TEXT); }
function errorLabel(prefix, err) {
  return ui.Label(prefix + ': ' + err, {color: '#B03A2E', fontSize: '11px', whiteSpace: 'pre-wrap'});
}
function rowsFrom(fcJson, columns) {
  return fcJson.features.map(function(f) {
    return columns.map(function(c) {
      var v = f.properties[c];
      if (v === undefined || v === null) return null;
      if (typeof v === 'number') return (DECIMALS[c] !== undefined) ? round(v, DECIMALS[c]) : round(v, 4);
      return v;
    });
  });
}
function chartTable(columns, rows) {
  var cols = columns.map(function(h, j) {
    var type = 'string';
    for (var r = 0; r < rows.length; r++) {
      var v = rows[r][j];
      if (v !== null && v !== '') { type = (typeof v === 'number') ? 'number' : 'string'; break; }
    }
    return {id: 'c' + j, label: h, type: type};
  });
  var dtRows = rows.map(function(r) {
    return {c: r.map(function(v, j) {
      if (v === null || v === '') return {v: null};
      if (cols[j].type === 'number') return {v: (typeof v === 'number') ? v : null};
      return {v: String(v)};
    })};
  });
  return ui.Chart({cols: cols, rows: dtRows}, 'Table', {allowHtml: false, pageSize: 60});
}
function tsv(columns, rows) {
  return [columns.join('\t')].concat(rows.map(function(r) {
    return r.map(function(v) { return (v === null) ? '' : String(v); }).join('\t');
  })).join('\n');
}

// ---- Reserve the Console blocks in report order ----
var slotInputs     = consoleSlot('25.1  INPUTS & QUALITY CONTROL');
var slotSamples    = consoleSlot('Table 2 – Training samples per class (GT points; Training_Pixels = after neighbourhood)');
var slotImportance = consoleSlot('Table 3 – Random Forest feature importance');
var slotAccuracy   = consoleSlot('Tables 4–5 – Confusion matrix and accuracy (independent validation GT)');
var slotArea       = consoleSlot('Table 1 – Class area within the ROI (10 m, pixel-area based)');
var slotSignature  = consoleSlot('Table 7 – Class signatures: mean / SD of each predictor at valid GT pixels');
var slotSummary    = consoleSlot('MANUSCRIPT-READY SUMMARY (tab-separated; paste into Excel / Word)');

var R = {done: 0, errors: {}};
var SUMMARY_TASKS = 4;   // inputs, samples, accuracy, area
function taskDone() { R.done += 1; if (R.done === SUMMARY_TASKS) renderSummary(); }

// ---- 25.1 Inputs & QC ----
ee.Dictionary({
  sceneCount: s2Summer.size(),
  sceneDates: sceneDates,
  tiles: ee.List(s2Summer.aggregate_array('MGRS_TILE')).distinct().sort(),
  sensors: ee.List(s2Summer.aggregate_array('SPACECRAFT_NAME')).distinct().sort(),
  lastAcquisition: ee.Date(s2Summer.aggregate_max('system:time_start')).format('YYYY-MM-dd'),
  coverage: compositeCoverage,
  nRaw: trainingPoints.size(), nNullLabel: gtNullLabel.size(), nUnrecognised: gtUnrecognised.size(),
  nLabelled: gtAll.size(), nInRoi: gtInRoi.size(), nOutside: gtOutsideRoi.size(), nValid: centreSamples.size(),
  nTrainPts: gtTrainPts.size(), nValPts: gtValPts.size(),
  nPixAll: trainingPixelsAll.size(), nPixNoLeak: trainingNoLeak.size(), nPixNoConflict: trainingNoConflict.size(),
  nPixUnique: trainingUnique.size(), nPixFinal: trainingPixels.size(),
  sampleTypes: trainingPixels.aggregate_histogram('sample_type'),
  trainPixHist: hTrainPix, valHist: hVal,
  idOverlap: ee.List(trainIds).filter(ee.Filter.inList('item', valIds)).size(),
  pixOverlap: trainingPixels.filter(ee.Filter.inList('pix_key', valPixKeys)).size(),
  meanB4: centreSamples.aggregate_mean('B4'), meanB8: centreSamples.aggregate_mean('B8'),
  minNDVI: centreSamples.aggregate_min('NDVI'), maxNDVI: centreSamples.aggregate_max('NDVI')
}).evaluate(function(info, err) {
  if (err) { R.errors.inputs = err; fillSlot(slotInputs, [errorLabel('Input summary failed', err)]); taskDone(); return; }
  R.inputs = info;
  var startMD = pad2(SUMMER_START_MONTH) + '-' + pad2(SUMMER_START_DAY);
  var endMD = pad2(SUMMER_END_MONTH) + '-' + pad2(SUMMER_END_DAY);
  var outside = info.sceneDates.filter(function(d) { var md = d.slice(5); return md < startMD || md > endMD; });
  var afterRun = info.lastAcquisition > ANALYSIS_DATE.toISOString().slice(0, 10);
  var missingTrain = [], lowVal = [];
  CLASS_CODES.forEach(function(code, i) {
    if (!info.trainPixHist[String(code)]) missingTrain.push(CLASS_NAMES[i]);
    var nv = info.valHist[String(code)] || 0;
    if (nv < 20) lowVal.push(CLASS_NAMES[i] + ' (' + nv + ')');
  });
  var ok = function(b) { return b ? '[OK]  ' : '[!!]  '; };
  var lines = [
    'Analysis (run) date ........ ' + ANALYSIS_DATE.toISOString().slice(0, 10),
    'ROI (WGS84) ................ ' + minLon + '–' + maxLon + ' °E, ' + minLat + '–' + maxLat + ' °N',
    'Sentinel-2 product ......... COPERNICUS/S2_SR_HARMONIZED (Level-2A surface reflectance)',
    'Summer window .............. ' + SUMMER_LABEL,
    'Scenes used ................ ' + info.sceneCount + '  (' + info.sceneDates.length + ' acquisition dates; tiles ' +
        info.tiles.join(', ') + '; ' + info.sensors.join(', ') + ')',
    'Acquisition dates .......... ' + info.sceneDates.join(', '),
    'Cloud/shadow masking ....... Cloud Score+ cs_cdf ≥ ' + CS_CLEAR_THRESHOLD + '; SCL 0/1/3' + (MASK_SCL_CLOUDS ? '/8/9/10' : '') +
        ' masked; scene cloud < ' + MAX_SCENE_CLOUD_PCT + ' %, snow/ice < ' + MAX_SCENE_SNOW_PCT + ' %',
    'Composite .................. per-pixel median, 10 m, ' + ANALYSIS_CRS + '; valid coverage ' + fmt(info.coverage * 100, 2) + ' % of ROI',
    'Training buffer ............ ' + trainingBufferMeters + ' m (neighbour screen |ΔNDVI| ≤ ' + NEIGHBOUR_MAX_DNDVI +
        ', |ΔMNDWI| ≤ ' + NEIGHBOUR_MAX_DMNDWI + ')',
    'Predictors (' + PREDICTORS.length + ') ............ ' + PREDICTORS.join(', '),
    '',
    'GT features in asset ....... ' + info.nRaw,
    '  missing/null label ....... ' + info.nNullLabel + ' (excluded)',
    '  unrecognised label ....... ' + info.nUnrecognised + ' (excluded)',
    '  valid label (any place) .. ' + info.nLabelled,
    '  outside ROI .............. ' + info.nOutside + ' (excluded)',
    '  inside ROI ............... ' + info.nInRoi,
    '  valid samples ............ ' + info.nValid + ' (complete predictor vector)',
    'Split (' + SPLIT_STRATEGY + ') .. training ' + info.nTrainPts + ' points, validation ' + info.nValPts + ' points',
    'Training pixels ............ ' + info.nPixAll + ' extracted → ' + info.nPixNoLeak + ' after leakage guard → ' +
        info.nPixNoConflict + ' after label-conflict removal → ' + info.nPixUnique + ' unique → ' + info.nPixFinal +
        ' after per-class cap (' + (MAX_TRAIN_PIXELS_PER_CLASS || 'none') + ')',
    '  of which centre / neighbour pixels: ' + (info.sampleTypes.centre || 0) + ' / ' + (info.sampleTypes.neighbour || 0),
    '',
    'QUALITY-CONTROL CHECKS',
    ok(outside.length === 0) + 'all ' + info.sceneDates.length + ' acquisition dates fall inside the summer window' +
        (outside.length ? ' — OUTSIDE: ' + outside.join(', ') : ''),
    ok(!afterRun) + 'no imagery acquired after the analysis date (last acquisition ' + info.lastAcquisition + ')',
    ok(info.idOverlap === 0) + 'training and validation GT points are disjoint (shared points: ' + info.idOverlap + ')',
    ok(info.pixOverlap === 0) + 'training and validation pixels are disjoint (shared pixels: ' + info.pixOverlap + ')',
    ok(missingTrain.length === 0) + 'all 6 classes present in training' + (missingTrain.length ? ' — MISSING: ' + missingTrain.join(', ') : ''),
    ok(lowVal.length === 0) + 'every class has ≥ 20 validation points' + (lowVal.length ? ' — LOW: ' + lowVal.join(', ') : ''),
    ok(info.meanB4 > 0 && info.meanB4 < 1 && info.meanB8 > 0 && info.meanB8 < 1.2) + 'reflectance correctly scaled (mean B4 = ' +
        fmt(info.meanB4, 4) + ', mean B8 = ' + fmt(info.meanB8, 4) + ')',
    ok(info.minNDVI >= -1 && info.maxNDVI <= 1) + 'NDVI within [−1, 1] at GT pixels (' + fmt(info.minNDVI, 3) + ' to ' + fmt(info.maxNDVI, 3) + ')',
    ok(info.coverage >= 0.98) + 'composite covers ≥ 98 % of the ROI (' + fmt(info.coverage * 100, 2) + ' %)' +
        (info.coverage < 0.98 ? ' — consider NUM_SUMMER_SEASONS = 2 or a lower CS_CLEAR_THRESHOLD' : '')
  ];
  if (info.nUnrecognised > 0) lines.push('[!!]  ' + info.nUnrecognised + ' GT labels were not recognised — see the Section 4 print-out.');
  if (missingTrain.length > 0) lines.push('[!!]  A class without training data cannot be mapped: check your class labels/codes.');
  fillSlot(slotInputs, [textLabel(lines.join('\n'))]);
  taskDone();
});

// ---- Table 2 ----
trainingSampleTable.evaluate(function(fc, err) {
  if (err) { R.errors.samples = err; fillSlot(slotSamples, [errorLabel('Training-sample table failed', err)]); taskDone(); return; }
  R.samples = rowsFrom(fc, T2_COLUMNS);
  fillSlot(slotSamples, [chartTable(T2_COLUMNS, R.samples)]);
  taskDone();
});

// ---- Tables 3, 4, 5 (one request: all depend on the trained forest) ----
ee.Dictionary({
  importance: importanceTable, groups: importanceByGroup, confusion: confusionTable,
  byClass: accuracyByClass, overall: accuracyOverall,
  oa: overallAccuracy, kappa: kappa, macroF1: macroF1, nVal: nValidation,
  oob: rfExplain.get('outOfBagErrorEstimate')
}).evaluate(function(acc, err) {
  if (err) {
    R.errors.acc = err;
    fillSlot(slotImportance, [errorLabel('Random Forest / importance failed', err)]);
    fillSlot(slotAccuracy, [errorLabel('Accuracy assessment failed', err)]);
    taskDone(); return;
  }
  R.acc = {
    importance: rowsFrom(acc.importance, T3_COLUMNS), groups: rowsFrom(acc.groups, T3B_COLUMNS),
    confusion: rowsFrom(acc.confusion, T4_COLUMNS), byClass: rowsFrom(acc.byClass, T5_COLUMNS),
    byClassRaw: acc.byClass.features.map(function(f) { return f.properties; }),
    oa: acc.oa, kappa: acc.kappa, macroF1: acc.macroF1, nVal: acc.nVal, oob: acc.oob
  };
  var impChartRows = R.acc.importance.map(function(r) { return [r[1], r[4]]; });
  var impChart = ui.Chart([['Feature', 'Relative importance (%)']].concat(impChartRows), 'BarChart', {
    title: 'RF feature importance (Gini; % of total)', legend: {position: 'none'}, colors: ['#1B4F72'],
    hAxis: {title: 'Relative importance (%)'}, vAxis: {textStyle: {fontSize: 10}},
    chartArea: {left: 90, width: '70%', height: '90%'}, height: Math.max(320, 18 * impChartRows.length)
  });
  fillSlot(slotImportance, [
    textLabel('RF: ' + RF_NUM_TREES + ' trees | variablesPerSplit ' + RF_MTRY + ' | minLeafPopulation ' +
              RF_MIN_LEAF_POPULATION + ' | bagFraction ' + RF_BAG_FRACTION + ' | seed ' + RF_SEED +
              ' | OOB error ' + fmt(acc.oob, 4)),
    chartTable(T3_COLUMNS, R.acc.importance), impChart,
    ui.Label('Importance by predictor group', {fontSize: '12px', fontWeight: 'bold'}),
    chartTable(T3B_COLUMNS, R.acc.groups)
  ]);
  var sbt = R.acc.byClassRaw[CLASS_CODES.indexOf(SBT_CODE)];
  fillSlot(slotAccuracy, [
    ui.Label('Table 4 – Confusion matrix (rows = reference GT, columns = mapped)', {fontSize: '12px', fontWeight: 'bold'}),
    chartTable(T4_COLUMNS, R.acc.confusion),
    ui.Label('Table 5 – Accuracy by class (PA = recall, UA = consumer\'s accuracy = precision)', {fontSize: '12px', fontWeight: 'bold'}),
    chartTable(T5_COLUMNS, R.acc.byClass),
    textLabel([
      'Overall accuracy ....... ' + fmt(acc.oa * 100, 2) + ' %   (n = ' + acc.nVal + ' validation GT points)',
      'Kappa coefficient ...... ' + fmt(acc.kappa, 4),
      'Macro-averaged F1 ...... ' + fmt(acc.macroF1, 4),
      'RF out-of-bag error .... ' + fmt(acc.oob, 4),
      '',
      'SEABUCKTHORN (class ' + SBT_CODE + ', one-vs-rest)',
      '  TP = ' + sbt.TP + '   FP = ' + sbt.FP + '   FN = ' + sbt.FN + '   TN = ' + sbt.TN,
      '  Precision (UA) ....... ' + fmt(sbt.Precision, 4) + '  (' + fmt(sbt.Users_Accuracy_pct, 2) + ' %)',
      '  Recall (PA) .......... ' + fmt(sbt.Recall, 4) + '  (' + fmt(sbt.Producers_Accuracy_pct, 2) + ' %)',
      '  F1-score ............. ' + fmt(sbt.F1_score, 4)
    ].join('\n'))
  ]);
  taskDone();
});

// ---- Table 1 (full-resolution area; heavy) ----
if (COMPUTE_AREA_IN_CONSOLE) {
  classAreaTable.evaluate(function(fc, err) {
    if (err) {
      R.errors.area = err;
      fillSlot(slotArea, [errorLabel('Interactive area computation did not finish', err + '\nThis is normal for ' +
        'large ROIs at 10 m. Run the export task "' + EXPORT_PREFIX + '_T1_ClassArea_' + SUMMER_TAG + '" (Tasks tab): ' +
        'it computes the identical table in batch mode without the interactive time limit.')]);
      taskDone(); return;
    }
    R.area = rowsFrom(fc, T1_COLUMNS);
    var areaChart = ui.Chart([['Metric'].concat(CLASS_NAMES)].concat([['Area (km²)'].concat(
      R.area.slice(0, CLASS_CODES.length).map(function(r) { return r[4]; }))]), 'ColumnChart', {
      title: 'Class area (km²)', colors: CLASS_PALETTE.map(function(c) { return c === 'FFFFFF' ? '#BBBBBB' : '#' + c; }),
      vAxis: {title: 'km²'}, legend: {position: 'right'}, height: 300
    });
    var sbtRow = R.area[CLASS_CODES.indexOf(SBT_CODE)];
    fillSlot(slotArea, [
      chartTable(T1_COLUMNS, R.area), areaChart,
      textLabel([
        'SEABUCKTHORN AREA (from the 10 m RF map, summer ' + SUMMER_LABEL + ')',
        '  SBT area ............. ' + fmt(sbtRow[4], 4) + ' km²',
        '  SBT area ............. ' + fmt(sbtRow[3], 2) + ' ha',
        '  SBT share of ROI ..... ' + fmt(sbtRow[5], 3) + ' %'
      ].join('\n'))
    ]);
    taskDone();
  });
} else {
  R.areaSkipped = true;
  fillSlot(slotArea, [textLabel('Skipped (COMPUTE_AREA_IN_CONSOLE = false). Use the exported Table 1 CSV.')]);
  taskDone();
}

// ---- Table 7 (independent of the summary) ----
signatureTable.evaluate(function(fc, err) {
  if (err) { fillSlot(slotSignature, [errorLabel('Signature table failed', err)]); return; }
  fillSlot(slotSignature, [chartTable(T7_COLUMNS, rowsFrom(fc, T7_COLUMNS))]);
});

// ---- Manuscript-ready summary ----
function renderSummary() {
  var L = [];
  var I = R.inputs, A = R.acc;
  L.push('SEABUCKTHORN (SBT) MAPPING — RANDOM FOREST LULC CLASSIFICATION, LADAKH');
  L.push('Study area (ROI)\t' + minLon + '–' + maxLon + ' °E, ' + minLat + '–' + maxLat + ' °N');
  L.push('Imagery\tSentinel-2 L2A surface reflectance, summer ' + SUMMER_LABEL +
         (I ? ' (' + I.sceneCount + ' scenes, ' + I.sceneDates.length + ' dates, tiles ' + I.tiles.join('/') + ')' : ''));
  L.push('Pre-processing\tCloud Score+ (cs_cdf ≥ ' + CS_CLEAR_THRESHOLD + ') + SCL shadow mask; median composite; 10 m, ' + ANALYSIS_CRS);
  L.push('Predictors (' + PREDICTORS.length + ')\t' + PREDICTORS.join(', '));
  L.push('Classifier\tRandom Forest: ' + RF_NUM_TREES + ' trees, mtry ' + RF_MTRY + ', min leaf ' + RF_MIN_LEAF_POPULATION +
         ', bag fraction ' + RF_BAG_FRACTION + ', seed ' + RF_SEED + (A ? ', OOB error ' + fmt(A.oob, 4) : ''));
  if (I) {
    L.push('Ground truth\t' + I.nRaw + ' GT points; ' + I.nInRoi + ' in ROI; ' + I.nValid + ' valid; ' + I.nTrainPts +
           ' training (' + I.nPixFinal + ' pixels, ' + trainingBufferMeters + ' m neighbourhood); ' + I.nValPts + ' validation');
  }
  if (A) {
    var sbt = A.byClassRaw[CLASS_CODES.indexOf(SBT_CODE)];
    L.push('Overall accuracy\t' + fmt(A.oa * 100, 2) + ' %');
    L.push('Kappa\t' + fmt(A.kappa, 4));
    L.push('Macro F1\t' + fmt(A.macroF1, 4));
    L.push('SBT precision / recall / F1\t' + fmt(sbt.Precision, 4) + ' / ' + fmt(sbt.Recall, 4) + ' / ' + fmt(sbt.F1_score, 4));
    L.push('SBT TP / FP / FN / TN\t' + sbt.TP + ' / ' + sbt.FP + ' / ' + sbt.FN + ' / ' + sbt.TN);
  }
  if (R.area) {
    var s = R.area[CLASS_CODES.indexOf(SBT_CODE)];
    L.push('SBT area\t' + fmt(s[4], 4) + ' km²\t' + fmt(s[3], 2) + ' ha\t' + fmt(s[5], 3) + ' % of ROI');
  } else {
    L.push('SBT area\tsee exported Table 1 / Table 1b (batch computation)');
  }
  L.push('');
  if (R.area)    { L.push('Table 1 – Class area'); L.push(tsv(T1_COLUMNS, R.area)); L.push(''); }
  if (R.samples) { L.push('Table 2 – Training samples'); L.push(tsv(T2_COLUMNS, R.samples)); L.push(''); }
  if (A) {
    L.push('Table 3 – RF feature importance'); L.push(tsv(T3_COLUMNS, A.importance)); L.push('');
    L.push('Table 4 – Confusion matrix (rows = reference, columns = mapped)'); L.push(tsv(T4_COLUMNS, A.confusion)); L.push('');
    L.push('Table 5 – Accuracy by class'); L.push(tsv(T5_COLUMNS, A.byClass)); L.push('');
  }
  var errs = Object.keys(R.errors);
  if (errs.length) L.push('Not available in the Console: ' + errs.join(', ') + ' (use the exported CSV files).');
  fillSlot(slotSummary, [textLabel(L.join('\n'))]);
}


// =====================================================================================================
// 26. EXPORT TABLES (CSV → opens directly in Microsoft Excel)
//    Start each task in the Tasks tab. Column order is fixed with `selectors`; no geometry column.
// =====================================================================================================
// Table 6 – every GT record with its class, coordinates and fate in the analysis.
function tagGT(fc, withinRoi, validPixel, partition) {
  return fc.map(function(f) {
    var props = {Within_ROI: withinRoi, Valid_Pixel: validPixel};
    if (partition !== null) props.partition = partition;
    return f.set(props);
  });
}
var gtNullStandardised = gtNullLabel.map(function(f) {
  var pt = f.geometry().centroid(1);
  var xy = pt.coordinates();
  return ee.Feature(pt, {
    gt_id: ee.String(f.get('system:index')), label_raw: '', lc_code: -1, lon: xy.get(0), lat: xy.get(1),
    Within_ROI: ee.Algorithms.If(roi.contains(pt, 1), 1, 0), Valid_Pixel: 0, partition: 'excluded_missing_label'
  });
});
var gtUnrecognisedTagged = gtUnrecognised.map(function(f) {
  return f.set({Within_ROI: ee.Algorithms.If(roi.contains(f.geometry(), 1), 1, 0), Valid_Pixel: 0,
                partition: 'excluded_unrecognised_label'});
});
var groundTruthTable = tagGT(gtPartitioned, 1, 1, null)
  .merge(tagGT(gtInRoi.filter(ee.Filter.inList('gt_id', validIds).not()), 1, 0, 'excluded_no_valid_pixel'))
  .merge(tagGT(gtOutsideRoi, 0, 0, 'excluded_outside_ROI'))
  .merge(gtUnrecognisedTagged)
  .merge(gtNullStandardised)
  .map(function(f) {
    var code = ee.Number(f.get(CLASS_PROP)).toInt();
    return ee.Feature(null, {
      GT_ID: f.get('gt_id'), Class: codeToName(code), Class_Code: code, Raw_Label: f.get('label_raw'),
      Longitude: f.get('lon'), Latitude: f.get('lat'), Within_ROI: f.get('Within_ROI'),
      Valid_Pixel: f.get('Valid_Pixel'), Partition: f.get('partition')
    });
  });

// Table 8 – Sentinel-2 scenes used (traceability of the summer composite).
var sceneTable = ee.FeatureCollection(s2Summer.map(function(img) {
  return ee.Feature(null, {
    Image_ID: img.get('system:index'),
    Date: ee.Date(img.get('system:time_start')).format('YYYY-MM-dd'),
    Tile: img.get('MGRS_TILE'), Spacecraft: img.get('SPACECRAFT_NAME'),
    Cloudy_Pixel_Pct: img.get('CLOUDY_PIXEL_PERCENTAGE'), Snow_Ice_Pct: img.get('SNOW_ICE_PERCENTAGE')
  });
}));

// Table 9 – run parameters (reproducibility record).
var runParameters = [
  ['ROI', minLon + ',' + minLat + ',' + maxLon + ',' + maxLat],
  ['Analysis_date', ANALYSIS_DATE.toISOString().slice(0, 10)],
  ['Summer_start', summerStart], ['Summer_end', summerEnd], ['Summer_seasons', NUM_SUMMER_SEASONS],
  ['S2_collection', 'COPERNICUS/S2_SR_HARMONIZED'], ['Cloud_mask', 'Cloud Score+ cs_cdf >= ' + CS_CLEAR_THRESHOLD + '; SCL 0/1/3' + (MASK_SCL_CLOUDS ? '/8/9/10' : '')],
  ['Max_scene_cloud_pct', MAX_SCENE_CLOUD_PCT], ['Max_scene_snow_pct', MAX_SCENE_SNOW_PCT],
  ['Composite', 'median'], ['CRS', ANALYSIS_CRS], ['Scale_m', ANALYSIS_SCALE],
  ['DEM', DEM_SOURCE === 'SRTM' ? 'USGS/SRTMGL1_003' : 'COPERNICUS/DEM/GLO30'], ['HAND', USE_HAND],
  ['Predictors', PREDICTORS.join(' ')], ['Class_field', classField],
  ['Training_buffer_m', trainingBufferMeters], ['Neighbour_max_dNDVI', NEIGHBOUR_MAX_DNDVI], ['Neighbour_max_dMNDWI', NEIGHBOUR_MAX_DMNDWI],
  ['Split_strategy', SPLIT_STRATEGY], ['Train_fraction', TRAIN_FRACTION], ['Spatial_block_m', SPATIAL_BLOCK_SIZE_M],
  ['Max_train_pixels_per_class', MAX_TRAIN_PIXELS_PER_CLASS], ['Random_seed', RANDOM_SEED],
  ['RF_trees', RF_NUM_TREES], ['RF_variables_per_split', RF_MTRY], ['RF_min_leaf_population', RF_MIN_LEAF_POPULATION],
  ['RF_bag_fraction', RF_BAG_FRACTION], ['RF_seed', RF_SEED], ['SBT_habitat_rule', APPLY_SBT_HABITAT_RULE]
];
var runParameterTable = ee.FeatureCollection(runParameters.map(function(p) {
  return ee.Feature(null, {Parameter: p[0], Value: String(p[1])});
}));

var T6_COLUMNS  = ['GT_ID', 'Class', 'Class_Code', 'Raw_Label', 'Longitude', 'Latitude', 'Within_ROI', 'Valid_Pixel', 'Partition'];
var T6B_COLUMNS = ['GT_ID', 'Longitude', 'Latitude', 'Reference_Code', 'Reference_Class', 'Mapped_Code', 'Mapped_Class', 'Correct'];
var T8_COLUMNS  = ['Image_ID', 'Date', 'Tile', 'Spacecraft', 'Cloudy_Pixel_Pct', 'Snow_Ice_Pct'];
var T9_COLUMNS  = ['Parameter', 'Value'];

function exportTable(fc, tableName, columns) {
  var name = EXPORT_PREFIX + '_' + tableName + '_' + SUMMER_TAG;
  Export.table.toDrive({
    collection: fc, description: name, folder: EXPORT_FOLDER, fileNamePrefix: name,
    fileFormat: 'CSV', selectors: columns
  });
}
exportTable(classAreaTable,       'T1_ClassArea',            T1_COLUMNS);   // area + percentage composition
exportTable(sbtAreaTable,         'T1b_SBT_Area_Binary',     T1B_COLUMNS);
exportTable(trainingSampleTable,  'T2_TrainingSamples',      T2_COLUMNS);
exportTable(importanceTable,      'T3_RF_FeatureImportance', T3_COLUMNS);
exportTable(importanceByGroup,    'T3b_Importance_ByGroup',  T3B_COLUMNS);
exportTable(confusionTable,       'T4_ConfusionMatrix',      T4_COLUMNS);
exportTable(accuracyByClass,      'T5_Accuracy_ByClass',     T5_COLUMNS);
exportTable(accuracyOverall,      'T5b_Accuracy_Overall',    T5B_COLUMNS);
exportTable(groundTruthTable,     'T6_GroundTruth',          T6_COLUMNS);
exportTable(validationPointTable, 'T6b_ValidationPoints',    T6B_COLUMNS);
exportTable(signatureTable,       'T7_ClassSignatures',      T7_COLUMNS);
exportTable(sceneTable,           'T8_Sentinel2_Scenes',     T8_COLUMNS);
exportTable(runParameterTable,    'T9_RunParameters',        T9_COLUMNS);


// =====================================================================================================
// 27. EXPORT RASTER PRODUCTS (GeoTIFF, Cloud-Optimised, EPSG:32643, exact 10 m S2 grid, exact ROI)
//    No-data conventions: LULC 0 = unclassified; SBT binary 255 = no data;
//    composite −9999 = no data (reflectance × 10000, Int16); probability 255 = no data; FCC 0 = no data.
// =====================================================================================================
function exportImage(img, productName) {
  var name = EXPORT_PREFIX + '_' + productName + '_' + SUMMER_TAG;
  Export.image.toDrive({
    image: img, description: name, folder: EXPORT_FOLDER, fileNamePrefix: name,
    region: roi, crs: ANALYSIS_CRS, crsTransform: CRS_TRANSFORM, maxPixels: 1e13,
    fileFormat: 'GeoTIFF', formatOptions: {cloudOptimized: true}
  });
}
exportImage(lulc.unmask(0).toByte(),                                         'LULC_RF_10m');
exportImage(sbtBinary.unmask(255).toByte(),                                  'SBT_Binary_10m');
exportImage(composite.multiply(10000).round().toInt16().unmask(-9999),       'S2_SummerComposite_SRx10000_10m');
exportImage(fccImage.unmask(0).toByte(),                                     'S2_FCC_B8B4B3_RGB_10m');
if (EXPORT_SBT_PROBABILITY) {
  exportImage(sbtProbability.round().toByte().unmask(255),                   'SBT_Probability_pct_10m');
}

print('--------------------------------------------------------------------');
print('26–27. EXPORTS: 13 CSV tables + ' + (EXPORT_SBT_PROBABILITY ? 5 : 4) + ' GeoTIFFs queued → open the Tasks tab ' +
      'and click RUN on each (Drive folder "' + EXPORT_FOLDER + '").');
