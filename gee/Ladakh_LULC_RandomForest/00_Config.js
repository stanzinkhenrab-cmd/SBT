// ============================================================================
// MODULE 00 — CONFIGURATION AND SHARED UTILITIES
// Project:  Land Use Land Cover (LULC) Classification of Ladakh, India
//           using Sentinel-2 Surface Reflectance and Random Forest
// Scope:    Single source of truth for every tunable parameter in the
//           project, plus small, generic helper functions that are reused
//           by every other module. No image processing or classification
//           logic lives here — see 01_Main_Script.js and the numbered
//           modules for that.
//
// Usage (Google Earth Engine Code Editor):
//   1. Save this file as a script inside a GEE script repository, e.g.
//      users/<your_username>/Ladakh_LULC_RandomForest, under the same
//      relative path/name it has here (00_Config.js).
//   2. In every other module, load it with:
//        var CONFIG = require('users/<your_username>/Ladakh_LULC_RandomForest:00_Config.js');
//      Replace <your_username> with your actual GEE account/repo owner.
// ============================================================================

// ----------------------------------------------------------------------------
// SECTION 1: PROJECT METADATA
// ----------------------------------------------------------------------------
var PROJECT = {
  title: 'Land Use Land Cover Classification of Ladakh, India using ' +
         'Sentinel-2 Surface Reflectance and Random Forest',
  version: '1.0.0',
  author: 'Configure with your name / institution before publication',
  repositoryPlaceholder: 'users/<your_username>/Ladakh_LULC_RandomForest'
};

// ----------------------------------------------------------------------------
// SECTION 2: STUDY AREA (ROI) PARAMETERS
// ----------------------------------------------------------------------------
// The ROI is derived automatically from the bounding box of the training
// points (see util.computeStudyArea below) so that the workflow never
// depends on a hand-digitized boundary. A fixed buffer is added around the
// bounding box so the classified raster extends beyond the outermost
// training/validation points.
var STUDY_AREA = {
  bufferMeters: 8000,          // Buffer added around the training-point bounding box
  fallbackRectangle: ee.Geometry.Rectangle(  // Used only if no training points are available
    [77.75, 32.75, 79.05, 33.40], null, false)
};

// ----------------------------------------------------------------------------
// SECTION 3: TEMPORAL PARAMETERS (AUTOMATICALLY CONFIGURABLE)
// ----------------------------------------------------------------------------
var TEMPORAL = {
  startDate: '2025-06-01',     // Growing-season composite start (edit freely)
  endDate:   '2025-09-30',     // Growing-season composite end (edit freely)
  compositeMethod: 'median'    // 'median' | 'mean' | 'medoid'
};

// ----------------------------------------------------------------------------
// SECTION 4: SENTINEL-2 SOURCE AND CLOUD-MASKING PARAMETERS
// ----------------------------------------------------------------------------
var SENTINEL2 = {
  srCollectionId: 'COPERNICUS/S2_SR_HARMONIZED',
  cloudProbCollectionId: 'COPERNICUS/S2_CLOUD_PROBABILITY',
  maxCloudCoverPercent: 10,    // Scene-level metadata filter (CLOUDY_PIXEL_PERCENTAGE)
  cloudProbThreshold: 40,      // Per-pixel s2cloudless probability threshold (%) above which pixels are masked
  cirrusBand: 'B10',           // Not present in L2A; kept for documentation only
  scale: 10,                   // Native Sentinel-2 10 m processing resolution
  crs: 'EPSG:4326'             // Output projection requested for this study
};

// Sentinel-2 band -> descriptive name mapping (surface reflectance, L2A)
var S2_BANDS = {
  blue:      'B2',
  green:     'B3',
  red:       'B4',
  redEdge1:  'B5',
  redEdge2:  'B6',
  redEdge3:  'B7',
  nir:       'B8',
  narrowNir: 'B8A',
  swir1:     'B11',
  swir2:     'B12'
};
var S2_BAND_LIST = ['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B8A', 'B11', 'B12'];
var S2_BAND_DESCRIPTIVE_NAMES = ['Blue', 'Green', 'Red', 'RedEdge1', 'RedEdge2',
  'RedEdge3', 'NIR', 'NarrowNIR', 'SWIR1', 'SWIR2'];

// ----------------------------------------------------------------------------
// SECTION 5: LULC CLASS SCHEMA
// ----------------------------------------------------------------------------
// The single letter that begins each training-point Name is mapped to a
// numeric class ID. This mapping is the ONLY place class codes are defined;
// every module reads from here so labels stay consistent end-to-end.
var CLASS_PROPERTY = 'class';      // Property name for the numeric class ID
var CLASS_PREFIX_MAP = {           // Name-prefix -> numeric class ID
  L: 0,  // Water Bodies
  S: 1,  // Snow Cover
  P: 2,  // Rangeland
  R: 3,  // Residential
  V: 4,  // Vegetation
  A: 5,  // Agriculture
  M: 6   // Wetlands
};
var CLASS_NAMES   = ['Water Bodies', 'Snow Cover', 'Rangeland', 'Residential',
  'Vegetation', 'Agriculture', 'Wetlands'];
var CLASS_PALETTE = ['#0066FF', '#FFFFFF', '#D2B48C', '#FF0000', '#008000',
  '#FFFF00', '#00FFFF'];
var CLASS_IDS = [0, 1, 2, 3, 4, 5, 6];
var NUM_CLASSES = CLASS_IDS.length;

// ----------------------------------------------------------------------------
// SECTION 6: TRAINING / VALIDATION DATA PARAMETERS
// ----------------------------------------------------------------------------
var TRAINING = {
  // Path to the GEE Table asset created by uploading
  // data/Ladakh_LULC_Training_Points.csv through the Assets tab.
  assetId: 'users/<your_username>/Ladakh_LULC_Training_Points',
  nameField: 'Name',
  latField: 'latitude',
  lonField: 'longitude',
  classProperty: CLASS_PROPERTY,
  splitFraction: 0.7,           // 70% training / 30% validation
  splitSeed: 42,                // Fixed seed -> reproducible split
  randomColumnName: 'random_split'
};

// ----------------------------------------------------------------------------
// SECTION 7: TERRAIN PARAMETERS (COPERNICUS DEM GLO-30)
// ----------------------------------------------------------------------------
var TERRAIN = {
  demCollectionId: 'COPERNICUS/DEM/GLO30',
  demBand: 'DEM',
  tpiRadiusPixels: 5,   // Neighbourhood radius (in pixels) for Topographic Position Index
  triRadiusPixels: 1    // Neighbourhood radius (in pixels) for Terrain Ruggedness Index
};

// ----------------------------------------------------------------------------
// SECTION 8: TEXTURE (GLCM) PARAMETERS
// ----------------------------------------------------------------------------
var TEXTURE = {
  sourceBands: ['nir', 'red', 'swir1'],  // Keys into S2_BANDS to run GLCM on
  glcmSize: 3,                           // GLCM window radius, in pixels
  quantizationLevels: 255                // Levels used when rescaling reflectance to an integer image before GLCM
};

// ----------------------------------------------------------------------------
// SECTION 9: OPTIONAL TRANSFORMS
// ----------------------------------------------------------------------------
var OPTIONAL_TRANSFORMS = {
  enablePCA: true,
  pcaNumComponents: 3,
  enableTasseledCap: true
};

// ----------------------------------------------------------------------------
// SECTION 10: RANDOM FOREST PARAMETERS
// ----------------------------------------------------------------------------
var RANDOM_FOREST = {
  numberOfTrees: 500,       // Default / final model tree count
  variablesPerSplit: null,  // null -> Earth Engine default (sqrt of number of predictors)
  minLeafPopulation: 1,     // Default minimum leaf population
  bagFraction: 0.7,         // Fraction of input resampled per tree
  seed: 42                  // Fixed seed -> reproducible trees
};

// ----------------------------------------------------------------------------
// SECTION 11: HYPERPARAMETER SEARCH (TREE-COUNT SENSITIVITY)
// ----------------------------------------------------------------------------
var HYPERPARAMETER_SEARCH = {
  enabled: true,
  treeOptions: [100, 300, 500, 700, 1000],
  autoSelectOptimalTrees: true,
  // A smaller model within this tolerance of the maximum observed accuracy
  // is preferred over a larger one with marginally higher accuracy, to
  // avoid recommending unnecessary model complexity.
  accuracyTolerance: 0.005
};

// ----------------------------------------------------------------------------
// SECTION 12: EXPORT PARAMETERS
// ----------------------------------------------------------------------------
var EXPORT = {
  driveFolder: 'Ladakh_LULC_RandomForest_Outputs',
  filePrefix: 'Ladakh_LULC_2025',
  maxPixels: 1e13,
  scale: SENTINEL2.scale,
  crs: SENTINEL2.crs
};

// ----------------------------------------------------------------------------
// SECTION 13: VISUALIZATION PARAMETERS
// ----------------------------------------------------------------------------
var VISUALIZATION = {
  mapCenterZoom: 9,
  legendTitle: 'LULC Class',
  confidenceVisPalette: ['#440154', '#3B528B', '#21908C', '#5DC963', '#FDE725'], // viridis-like
  rgbVisBands: [S2_BANDS.swir1, S2_BANDS.nir, S2_BANDS.red], // false-colour composite for context
  rgbVisMinMax: {min: 0, max: 3000}
};

// ----------------------------------------------------------------------------
// SECTION 14: SHARED UTILITY FUNCTIONS
// ----------------------------------------------------------------------------

/**
 * Rebuilds a Point geometry for a feature from its latitude/longitude
 * properties, regardless of how the source table asset was ingested.
 * This guarantees reproducible geometry independent of upload settings.
 */
function buildPointFeature(feature) {
  var lon = feature.getNumber(TRAINING.lonField);
  var lat = feature.getNumber(TRAINING.latField);
  return ee.Feature(ee.Geometry.Point([lon, lat]), feature.toDictionary());
}

/**
 * Extracts the first character of the point Name and assigns the matching
 * numeric class ID from CLASS_PREFIX_MAP. Features whose prefix is not in
 * the map receive a null class property and are dropped by
 * filterValidClasses(). No manual editing of individual points is required.
 */
function assignClassFromName(feature) {
  var name = ee.String(feature.get(TRAINING.nameField));
  var prefix = name.slice(0, 1).toUpperCase();
  var classId = ee.Dictionary(CLASS_PREFIX_MAP).get(prefix, null);
  return feature.set(CLASS_PROPERTY, classId);
}

/** Removes any feature whose class could not be resolved from its prefix. */
function filterValidClasses(featureCollection) {
  return featureCollection.filter(ee.Filter.notNull([CLASS_PROPERTY]));
}

/**
 * Full ingestion pipeline for the raw training-point table: rebuilds
 * geometry, assigns numeric class IDs from the Name prefix, and drops any
 * unrecognized rows.
 */
function prepareTrainingPoints(rawFeatureCollection) {
  var withGeometry = rawFeatureCollection.map(buildPointFeature);
  var withClass = withGeometry.map(assignClassFromName);
  return filterValidClasses(withClass);
}

/**
 * Adds a fixed-seed random column and splits a FeatureCollection into a
 * training and a validation subset using TRAINING.splitFraction. Reruns of
 * this function on the same input always produce the same split.
 */
function splitTrainingValidation(featureCollection) {
  var withRandom = featureCollection.randomColumn(TRAINING.randomColumnName, TRAINING.splitSeed);
  var trainingSet = withRandom.filter(ee.Filter.lt(TRAINING.randomColumnName, TRAINING.splitFraction));
  var validationSet = withRandom.filter(ee.Filter.gte(TRAINING.randomColumnName, TRAINING.splitFraction));
  return {training: trainingSet, validation: validationSet};
}

/**
 * Computes the study-area geometry automatically from the bounding box of
 * the (already geometry-corrected) training points, expanded by
 * STUDY_AREA.bufferMeters. Falls back to a fixed rectangle if the input
 * collection is empty.
 */
function computeStudyArea(pointFeatureCollection) {
  var bounds = pointFeatureCollection.geometry().bounds();
  return bounds.buffer(STUDY_AREA.bufferMeters).bounds();
}

// ----------------------------------------------------------------------------
// MODULE EXPORTS
// ----------------------------------------------------------------------------
exports.PROJECT = PROJECT;
exports.STUDY_AREA = STUDY_AREA;
exports.TEMPORAL = TEMPORAL;
exports.SENTINEL2 = SENTINEL2;
exports.S2_BANDS = S2_BANDS;
exports.S2_BAND_LIST = S2_BAND_LIST;
exports.S2_BAND_DESCRIPTIVE_NAMES = S2_BAND_DESCRIPTIVE_NAMES;
exports.CLASS_PROPERTY = CLASS_PROPERTY;
exports.CLASS_PREFIX_MAP = CLASS_PREFIX_MAP;
exports.CLASS_NAMES = CLASS_NAMES;
exports.CLASS_PALETTE = CLASS_PALETTE;
exports.CLASS_IDS = CLASS_IDS;
exports.NUM_CLASSES = NUM_CLASSES;
exports.TRAINING = TRAINING;
exports.TERRAIN = TERRAIN;
exports.TEXTURE = TEXTURE;
exports.OPTIONAL_TRANSFORMS = OPTIONAL_TRANSFORMS;
exports.RANDOM_FOREST = RANDOM_FOREST;
exports.HYPERPARAMETER_SEARCH = HYPERPARAMETER_SEARCH;
exports.EXPORT = EXPORT;
exports.VISUALIZATION = VISUALIZATION;

exports.util = {
  buildPointFeature: buildPointFeature,
  assignClassFromName: assignClassFromName,
  filterValidClasses: filterValidClasses,
  prepareTrainingPoints: prepareTrainingPoints,
  splitTrainingValidation: splitTrainingValidation,
  computeStudyArea: computeStudyArea
};
