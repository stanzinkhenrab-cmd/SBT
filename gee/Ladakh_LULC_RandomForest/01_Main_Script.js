// ============================================================================
// ============================================================================
//  LAND USE LAND COVER (LULC) CLASSIFICATION OF LADAKH, INDIA
//  RANDOM FOREST CLASSIFICATION USING SENTINEL-2 SURFACE REFLECTANCE
// ============================================================================
// ============================================================================
//
// TITLE
//   Machine-Learning-Based Land Use Land Cover Mapping of the Cold-Desert
//   Region of Ladakh, India, using Sentinel-2 Level-2A Surface Reflectance,
//   Multi-Source Spectral-Textural-Terrain Predictors, and a Random Forest
//   Classifier in Google Earth Engine.
//
// OBJECTIVES
//   1. Produce a reproducible, seven-class LULC map of Ladakh (Water Bodies,
//      Snow Cover, Rangeland, Residential, Vegetation, Agriculture,
//      Wetlands) at 10 m spatial resolution.
//   2. Build a comprehensive predictor stack (spectral bands, vegetation /
//      water / snow / urban / bare-soil indices, terrain derivatives, GLCM
//      texture, and optional PCA / Tasseled Cap components) suitable for
//      publication-grade classification in a topographically extreme,
//      semi-arid, high-altitude environment.
//   3. Train and tune a Random Forest classifier, quantify per-class and
//      overall accuracy with a complete statistical suite, and report
//      variable importance.
//   4. Generate publication-ready maps, figures, and tabular outputs
//      (GeoTIFFs, CSVs) with a fully reproducible, seeded workflow.
//
// DATASETS
//   - Sentinel-2 Level-2A Surface Reflectance: COPERNICUS/S2_SR_HARMONIZED
//     (European Space Agency / Copernicus Programme; see
//     https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_S2_SR_HARMONIZED)
//   - Sentinel-2 Cloud Probability: COPERNICUS/S2_CLOUD_PROBABILITY (s2cloudless;
//     https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_S2_CLOUD_PROBABILITY)
//   - Copernicus DEM GLO-30: COPERNICUS/DEM/GLO30 (30 m global DEM; see
//     https://developers.google.com/earth-engine/datasets/catalog/COPERNICUS_DEM_GLO30)
//   - Field / photo-interpreted training points supplied by the project
//     (data/Ladakh_LULC_Training_Points.csv), uploaded as a GEE Table asset.
//
// PROJECTION
//   EPSG:4326 (geographic, WGS 84), output at the Sentinel-2 native 10 m grid.
//
// SPATIAL RESOLUTION
//   10 m (Sentinel-2 bands resampled/aggregated to a common 10 m grid;
//   Copernicus DEM GLO-30 resampled to 10 m during composite building).
//
// TEMPORAL RESOLUTION
//   Single growing-season composite (default June-September 2025, fully
//   configurable in 00_Config.js), built from all Sentinel-2 scenes over the
//   study area meeting the cloud-cover criteria.
//
// CLASSIFICATION METHOD
//   Random Forest (Breiman, 2001), implemented as ee.Classifier.smileRandomForest.
//
// MACHINE LEARNING PARAMETERS
//   Trees: 500 (default; 100/300/500/700/1000 compared automatically)
//   Variables per split: Automatic (Earth Engine default, ~sqrt(numPredictors))
//   Minimum leaf population: 1 (Earth Engine default)
//   Bag fraction: 0.7
//   Seed: 42 (fixed for full reproducibility)
//
// TRAINING SAMPLE INFORMATION
//   700 field/photo-interpreted points across 7 classes (see
//   data/Ladakh_LULC_Training_Points.csv), split 70% training / 30%
//   validation with a fixed random seed (42).
//
// EXPECTED OUTPUTS
//   - Classified LULC raster (GeoTIFF)
//   - Per-class probability raster (GeoTIFF, one band per class)
//   - Confidence and uncertainty rasters
//   - Confusion matrix, overall/producer/user accuracy, kappa, precision,
//     recall, F1 (macro/weighted), balanced accuracy, specificity, MCC,
//     per-class IoU (CSV)
//   - Variable importance table and chart (CSV + in-app chart)
//   - Per-class area statistics in pixels, hectares, km2, percentage (CSV)
//   - Training/validation sample exports and validation maps
//   - Publication-quality map with legend, class colours, north arrow and
//     scale-bar indicators
//
// REFERENCES
//   Breiman, L. (2001). Random forests. Machine Learning, 45(1), 5-32.
//     https://doi.org/10.1023/A:1010933404324
//   Belgiu, M., & Dragut, L. (2016). Random forest in remote sensing: A
//     review of applications and future directions. ISPRS Journal of
//     Photogrammetry and Remote Sensing, 114, 24-31.
//     https://doi.org/10.1016/j.isprsjprs.2016.01.011
//   Olofsson, P., Foody, G. M., Herold, M., Stehman, S. V., Woodcock, C. E.,
//     & Wulder, M. A. (2014). Good practices for estimating area and
//     assessing accuracy of land change. Remote Sensing of Environment,
//     148, 42-57. https://doi.org/10.1016/j.rse.2014.02.015
//   Stehman, S. V. (1997). Selecting and interpreting measures of thematic
//     classification accuracy. Remote Sensing of Environment, 62(1), 77-89.
//     https://doi.org/10.1016/S0034-4257(97)00083-7
//   Congalton, R. G. (1991). A review of assessing the accuracy of
//     classifications of remotely sensed data. Remote Sensing of
//     Environment, 37(1), 35-46. https://doi.org/10.1016/0034-4257(91)90048-B
//   Google Earth Engine Developer Documentation: https://developers.google.com/earth-engine
//   Copernicus Sentinel-2 User Guide: https://sentinels.copernicus.eu/web/sentinel/user-guides/sentinel-2-msi
//   Copernicus DEM Product Handbook: https://spacedata.copernicus.eu/collections/copernicus-digital-elevation-model
//
// REPRODUCIBILITY
//   Every stochastic step (train/validation split, Random Forest training)
//   is seeded (seed = 42). Re-running this script against the same input
//   asset and date range reproduces identical results.
//
// FILE MAP (this project)
//   00_Config.js               Parameters and shared utility functions
//   01_Main_Script.js          THIS FILE - orchestrates the full workflow
//   02_Accuracy_Assessment.js  Confusion matrix and full accuracy-metric suite
//   03_Variable_Importance.js  Variable importance table and chart
//   04_Area_Statistics.js      Per-class pixel/area/percentage statistics
//   05_Export_Module.js        All Export.image / Export.table calls
//   06_Visualization_Module.js Map layers, legend, validation maps
// ============================================================================


// ----------------------------------------------------------------------------
// SECTION 0: MODULE IMPORTS
// ----------------------------------------------------------------------------
// Replace <your_username> with the GEE account/repo that hosts these files.
var REPO = 'users/<your_username>/Ladakh_LULC_RandomForest';

var CONFIG = require(REPO + ':00_Config.js');
var AccuracyAssessment = require(REPO + ':02_Accuracy_Assessment.js');
var VariableImportance = require(REPO + ':03_Variable_Importance.js');
var AreaStatistics = require(REPO + ':04_Area_Statistics.js');
var ExportModule = require(REPO + ':05_Export_Module.js');
var VisualizationModule = require(REPO + ':06_Visualization_Module.js');


// ----------------------------------------------------------------------------
// SECTION 1: TRAINING DATA INGESTION AND STUDY AREA
// ----------------------------------------------------------------------------
// Load the raw uploaded table asset (Name, latitude, longitude columns) and
// run it through the shared ingestion pipeline: rebuild point geometry,
// assign numeric class IDs from the Name prefix, drop unrecognized rows.
var rawTrainingPoints = ee.FeatureCollection(CONFIG.TRAINING.assetId);
var trainingPoints = CONFIG.util.prepareTrainingPoints(rawTrainingPoints);

// The study area is derived automatically from the training-point bounding
// box (plus a fixed buffer) so no manual digitizing is ever required.
var studyArea = CONFIG.util.computeStudyArea(trainingPoints);

Map.centerObject(studyArea, CONFIG.VISUALIZATION.mapCenterZoom);


// ----------------------------------------------------------------------------
// SECTION 2: SENTINEL-2 CLOUD MASKING AND COMPOSITE BUILDING
// ----------------------------------------------------------------------------

/**
 * Joins each Sentinel-2 SR image with its matching s2cloudless cloud
 * probability image (shared system:index) and masks pixels whose cloud
 * probability exceeds the configured threshold.
 */
function maskS2Clouds(image) {
  var cloudProbability = ee.Image(image.get('s2cloudless')).select('probability');
  var isCloudFree = cloudProbability.lt(CONFIG.SENTINEL2.cloudProbThreshold);
  return image.updateMask(isCloudFree)
      .divide(10000)                     // Scale surface reflectance to [0, 1]
      .copyProperties(image, image.propertyNames());
}

/**
 * Builds a cloud-masked, cloud-filtered Sentinel-2 surface-reflectance
 * composite over the study area and date range defined in 00_Config.js.
 */
function buildSentinel2Composite(region, startDate, endDate) {
  var s2Sr = ee.ImageCollection(CONFIG.SENTINEL2.srCollectionId)
      .filterBounds(region)
      .filterDate(startDate, endDate)
      .filter(ee.Filter.lte('CLOUDY_PIXEL_PERCENTAGE', CONFIG.SENTINEL2.maxCloudCoverPercent));

  var s2CloudProb = ee.ImageCollection(CONFIG.SENTINEL2.cloudProbCollectionId)
      .filterBounds(region)
      .filterDate(startDate, endDate);

  var joined = ee.Join.saveFirst('s2cloudless').apply({
    primary: s2Sr,
    secondary: s2CloudProb,
    condition: ee.Filter.equals({leftField: 'system:index', rightField: 'system:index'})
  });

  var masked = ee.ImageCollection(joined).map(maskS2Clouds);

  var composite = CONFIG.TEMPORAL.compositeMethod === 'mean'
      ? masked.mean()
      : (CONFIG.TEMPORAL.compositeMethod === 'medoid' ? medoidComposite(masked) : masked.median());

  return composite.select(CONFIG.S2_BAND_LIST, CONFIG.S2_BAND_DESCRIPTIVE_NAMES).clip(region);
}

/** Medoid compositing: for each pixel, keeps the observation closest (in Euclidean band-space) to the median. */
function medoidComposite(collection) {
  var median = collection.select(CONFIG.S2_BAND_LIST).median();
  var withDistance = collection.map(function(image) {
    var distance = image.select(CONFIG.S2_BAND_LIST).subtract(median).pow(2).reduce(ee.Reducer.sum()).sqrt();
    return image.addBands(distance.rename('medoid_distance'));
  });
  return withDistance.qualityMosaic('medoid_distance');
}

var sentinel2Composite = buildSentinel2Composite(studyArea, CONFIG.TEMPORAL.startDate, CONFIG.TEMPORAL.endDate);
var bandName = {
  blue: 'Blue', green: 'Green', red: 'Red', redEdge1: 'RedEdge1', redEdge2: 'RedEdge2',
  redEdge3: 'RedEdge3', nir: 'NIR', narrowNir: 'NarrowNIR', swir1: 'SWIR1', swir2: 'SWIR2'
};


// ----------------------------------------------------------------------------
// SECTION 3: SPECTRAL INDEX FEATURE ENGINEERING
// ----------------------------------------------------------------------------
// Every index is computed once from the descriptively-renamed composite
// bands and returned as a single-band, descriptively-named image. All
// indices are combined into one multi-band image by computeSpectralIndices().

/** Vegetation indices. */
function computeVegetationIndices(image) {
  var nir = image.select(bandName.nir);
  var red = image.select(bandName.red);
  var blue = image.select(bandName.blue);
  var green = image.select(bandName.green);

  var ndvi = image.normalizedDifference([bandName.nir, bandName.red]).rename('NDVI');

  var evi = image.expression(
    '2.5 * (NIR - RED) / (NIR + 6 * RED - 7.5 * BLUE + 1)',
    {NIR: nir, RED: red, BLUE: blue}).rename('EVI');

  var savi = image.expression(
    '((NIR - RED) / (NIR + RED + 0.5)) * 1.5',
    {NIR: nir, RED: red}).rename('SAVI');

  var msavi = image.expression(
    '(2 * NIR + 1 - sqrt(pow(2 * NIR + 1, 2) - 8 * (NIR - RED))) / 2',
    {NIR: nir, RED: red}).rename('MSAVI');

  var gndvi = image.normalizedDifference([bandName.nir, bandName.green]).rename('GNDVI');

  var osavi = image.expression(
    '(NIR - RED) / (NIR + RED + 0.16)',
    {NIR: nir, RED: red}).rename('OSAVI');

  var rvi = nir.divide(red).rename('RVI');

  var dvi = nir.subtract(red).rename('DVI');

  var ipvi = nir.divide(nir.add(red)).rename('IPVI');

  return ee.Image.cat([ndvi, evi, savi, msavi, gndvi, osavi, rvi, dvi, ipvi]);
}

/** Water indices. */
function computeWaterIndices(image) {
  var nir = image.select(bandName.nir);
  var green = image.select(bandName.green);
  var blue = image.select(bandName.blue);
  var swir1 = image.select(bandName.swir1);
  var swir2 = image.select(bandName.swir2);

  var ndwi = image.normalizedDifference([bandName.green, bandName.nir]).rename('NDWI');       // McFeeters (1996)
  var mndwi = image.normalizedDifference([bandName.green, bandName.swir1]).rename('MNDWI');   // Xu (2006)

  var awei = image.expression(
    '4 * (GREEN - SWIR1) - (0.25 * NIR + 2.75 * SWIR2)',
    {GREEN: green, SWIR1: swir1, NIR: nir, SWIR2: swir2}).rename('AWEI');                     // Feyisa et al. (2014)

  var ndmi = image.normalizedDifference([bandName.nir, bandName.swir1]).rename('NDMI');
  var lswi = image.normalizedDifference([bandName.nir, bandName.swir1]).rename('LSWI');       // Land Surface Water Index

  return ee.Image.cat([ndwi, mndwi, awei, ndmi, lswi]);
}

/** Snow indices. */
function computeSnowIndices(image) {
  var green = image.select(bandName.green);
  var swir1 = image.select(bandName.swir1);

  var ndsi = image.normalizedDifference([bandName.green, bandName.swir1]).rename('NDSI');     // Hall et al. (1995)
  var snowRatio = green.divide(swir1).rename('SnowRatio');

  return ee.Image.cat([ndsi, snowRatio]);
}

/** Urban / built-up indices. */
function computeUrbanIndices(image, savi, mndwi) {
  var nir = image.select(bandName.nir);
  var swir1 = image.select(bandName.swir1);
  var swir2 = image.select(bandName.swir2);

  var ndbi = image.normalizedDifference([bandName.swir1, bandName.nir]).rename('NDBI');       // Zha et al. (2003)

  var ibi = image.expression(
    '(NDBI - (SAVI + MNDWI) / 2) / (NDBI + (SAVI + MNDWI) / 2)',
    {NDBI: ndbi, SAVI: savi, MNDWI: mndwi}).rename('IBI');                                    // Xu (2008)

  var ui = image.expression(
    '(SWIR2 - NIR) / (SWIR2 + NIR)',
    {SWIR2: swir2, NIR: nir}).rename('UI');                                                   // Kawamura et al. (1996)

  return ee.Image.cat([ndbi, ibi, ui]);
}

/** Bare-soil indices. */
function computeBareSoilIndices(image) {
  var nir = image.select(bandName.nir);
  var red = image.select(bandName.red);
  var green = image.select(bandName.green);
  var blue = image.select(bandName.blue);
  var swir1 = image.select(bandName.swir1);
  var swir2 = image.select(bandName.swir2);

  var bsi = image.expression(
    '((SWIR1 + RED) - (NIR + BLUE)) / ((SWIR1 + RED) + (NIR + BLUE))',
    {SWIR1: swir1, RED: red, NIR: nir, BLUE: blue}).rename('BSI');                            // Rikimaru et al. (2002)

  var mbsi = image.expression(
    '((SWIR1 - SWIR2 - NIR) / (SWIR1 + SWIR2 + NIR)) + 0.5',
    {SWIR1: swir1, SWIR2: swir2, NIR: nir}).rename('MBSI');                                   // Modified Bare Soil Index (Nguyen & Henebry, 2019)

  var bi = image.expression(
    'sqrt((RED * RED + GREEN * GREEN) / 2)',
    {RED: red, GREEN: green}).rename('BI');                                                   // Brightness Index

  return ee.Image.cat([bsi, mbsi, bi]);
}

/** Combines all spectral indices into a single multi-band image. */
function computeSpectralIndices(image) {
  var vegetation = computeVegetationIndices(image);
  var water = computeWaterIndices(image);
  var snow = computeSnowIndices(image);
  var soil = computeBareSoilIndices(image);
  var urban = computeUrbanIndices(image, vegetation.select('SAVI'), water.select('MNDWI'));
  return ee.Image.cat([vegetation, water, snow, urban, soil]);
}

var spectralIndices = computeSpectralIndices(sentinel2Composite);


// ----------------------------------------------------------------------------
// SECTION 4: TERRAIN FEATURE ENGINEERING (COPERNICUS DEM GLO-30)
// ----------------------------------------------------------------------------

/** Builds the full terrain-derivative stack from the Copernicus GLO-30 DEM. */
function computeTerrainFeatures(region) {
  var elevation = ee.ImageCollection(CONFIG.TERRAIN.demCollectionId)
      .select(CONFIG.TERRAIN.demBand)
      .mosaic()
      .clip(region)
      .rename('Elevation');

  var slope = ee.Terrain.slope(elevation).rename('Slope');
  var aspect = ee.Terrain.aspect(elevation).rename('Aspect');
  var hillshade = ee.Terrain.hillshade(elevation).rename('Hillshade');

  // Curvature: second-derivative approximation via a discrete Laplacian kernel.
  var curvature = elevation.convolve(ee.Kernel.laplacian8({normalize: false})).rename('Curvature');

  // Topographic Position Index: elevation minus the local neighbourhood mean.
  var tpiMean = elevation.focalMean({
    radius: CONFIG.TERRAIN.tpiRadiusPixels, kernelType: 'circle', units: 'pixels'
  });
  var tpi = elevation.subtract(tpiMean).rename('TPI');

  // Terrain Ruggedness Index: mean absolute elevation difference to neighbours (Riley et al., 1999).
  var triMean = elevation.focalMean({
    radius: CONFIG.TERRAIN.triRadiusPixels, kernelType: 'square', units: 'pixels'
  });
  var tri = elevation.subtract(triMean).abs().rename('TRI');

  return ee.Image.cat([elevation, slope, aspect, hillshade, curvature, tpi, tri]);
}

var terrainFeatures = computeTerrainFeatures(studyArea);


// ----------------------------------------------------------------------------
// SECTION 5: GLCM TEXTURE FEATURE ENGINEERING
// ----------------------------------------------------------------------------

/**
 * Computes GLCM (contrast, entropy, variance, homogeneity, ASM, correlation,
 * dissimilarity) plus a first-order local mean for a single band, after
 * rescaling reflectance to an unsigned integer image (required by
 * ee.Image.glcmTexture).
 */
function computeTextureForBand(image, bandKey) {
  var descriptiveName = bandName[bandKey];
  var reflectanceBand = image.select(descriptiveName);

  var integerBand = reflectanceBand
      .unitScale(0, 1)
      .clamp(0, 1)                      // guard against reflectance artefacts slightly outside [0, 1]
      .multiply(CONFIG.TEXTURE.quantizationLevels)
      .toInt32()
      .rename(descriptiveName);

  var glcm = integerBand.glcmTexture({size: CONFIG.TEXTURE.glcmSize});

  var contrast = glcm.select(descriptiveName + '_contrast').rename(descriptiveName + '_Contrast');
  var entropy = glcm.select(descriptiveName + '_ent').rename(descriptiveName + '_Entropy');
  var variance = glcm.select(descriptiveName + '_var').rename(descriptiveName + '_Variance');
  var homogeneity = glcm.select(descriptiveName + '_idm').rename(descriptiveName + '_Homogeneity');
  var asm = glcm.select(descriptiveName + '_asm').rename(descriptiveName + '_ASM');
  var correlation = glcm.select(descriptiveName + '_corr').rename(descriptiveName + '_Correlation');
  var dissimilarity = glcm.select(descriptiveName + '_diss').rename(descriptiveName + '_Dissimilarity');

  var localMean = reflectanceBand.focalMean({
    radius: CONFIG.TEXTURE.glcmSize, kernelType: 'square', units: 'pixels'
  }).rename(descriptiveName + '_Mean');

  return ee.Image.cat([contrast, entropy, variance, homogeneity, asm, correlation, dissimilarity, localMean]);
}

/** Combines GLCM texture across all configured source bands. */
function computeTextureFeatures(image) {
  var textureImages = CONFIG.TEXTURE.sourceBands.map(function(bandKey) {
    return computeTextureForBand(image, bandKey);
  });
  return ee.Image.cat(textureImages);
}

var textureFeatures = computeTextureFeatures(sentinel2Composite);


// ----------------------------------------------------------------------------
// SECTION 6: OPTIONAL TRANSFORMS — PCA AND TASSELED CAP
// ----------------------------------------------------------------------------

/**
 * Principal Component Analysis over the original Sentinel-2 bands, following
 * the standard Earth Engine covariance/eigen-decomposition pattern. Returns
 * the top N components as a multi-band image (PC1, PC2, ...).
 */
function computePCA(image, region, numComponents) {
  var bandNames = bandName_S2List();
  var meanCentered = image.select(bandNames).subtract(
      image.select(bandNames).reduceRegion({
        reducer: ee.Reducer.mean(), geometry: region, scale: CONFIG.SENTINEL2.scale,
        maxPixels: CONFIG.EXPORT.maxPixels, bestEffort: true
      }).toImage(bandNames));

  var arrayImage = meanCentered.toArray();
  var covariance = arrayImage.reduceRegion({
    reducer: ee.Reducer.centeredCovariance(), geometry: region, scale: CONFIG.SENTINEL2.scale,
    maxPixels: CONFIG.EXPORT.maxPixels, bestEffort: true
  }).get('array');

  var eigens = ee.Array(covariance).eigen();
  var eigenVectors = eigens.slice(1, 1);

  // The eigenvector matrix yields one component per input band (ordered by
  // descending eigenvalue); flatten using the FULL band count, then select
  // only the leading numComponents (the components that explain the most
  // variance) requested by the caller.
  var allComponentNames = pcBandNames(bandNames.length);
  var principalComponents = ee.Image(eigenVectors)
      .matrixMultiply(arrayImage.toArray(1))
      .arrayProject([0])
      .arrayFlatten([allComponentNames]);

  return principalComponents.select(pcBandNames(numComponents));
}

function bandName_S2List() { return CONFIG.S2_BAND_DESCRIPTIVE_NAMES; }

function pcBandNames(numComponents) {
  var names = [];
  for (var i = 1; i <= numComponents; i++) { names.push('PC' + i); }
  return names;
}

/**
 * Sentinel-2 Tasseled Cap transformation (Brightness, Greenness, Wetness)
 * using the Nedkov (2017) coefficients derived for Sentinel-2 surface
 * reflectance.
 */
function computeTasseledCap(image) {
  var coefficients = ee.Array([
    [0.3510, 0.3813, 0.3437, 0.7196, 0.2396, 0.1949],   // Brightness
    [-0.3599, -0.3533, -0.4734, 0.6633, 0.0087, -0.2856], // Greenness
    [0.2578, 0.2305, 0.0883, 0.1071, -0.7611, -0.5308]  // Wetness
  ]);

  var bands = [bandName.blue, bandName.green, bandName.red, bandName.nir, bandName.swir1, bandName.swir2];
  var arrayImage = image.select(bands).toArray().toArray(1);
  var tasseledCapArray = ee.Image(coefficients).matrixMultiply(arrayImage);

  return tasseledCapArray.arrayProject([0]).arrayFlatten([['Brightness', 'Greenness', 'Wetness']]);
}

var optionalFeatures = ee.Image([]);
if (CONFIG.OPTIONAL_TRANSFORMS.enablePCA) {
  optionalFeatures = optionalFeatures.addBands(
      computePCA(sentinel2Composite, studyArea, CONFIG.OPTIONAL_TRANSFORMS.pcaNumComponents));
}
if (CONFIG.OPTIONAL_TRANSFORMS.enableTasseledCap) {
  optionalFeatures = optionalFeatures.addBands(computeTasseledCap(sentinel2Composite));
}


// ----------------------------------------------------------------------------
// SECTION 7: FINAL PREDICTOR STACK
// ----------------------------------------------------------------------------
var predictorStack = sentinel2Composite
    .addBands(spectralIndices)
    .addBands(terrainFeatures)
    .addBands(textureFeatures)
    .addBands(optionalFeatures)
    .clip(studyArea);

var predictorBandNames = predictorStack.bandNames();


// ----------------------------------------------------------------------------
// SECTION 8: SAMPLE EXTRACTION AND TRAIN/VALIDATION SPLIT
// ----------------------------------------------------------------------------
var sampledPoints = predictorStack.sampleRegions({
  collection: trainingPoints,
  properties: [CONFIG.CLASS_PROPERTY],
  scale: CONFIG.SENTINEL2.scale,
  projection: CONFIG.SENTINEL2.crs,
  geometries: true
});

var split = CONFIG.util.splitTrainingValidation(sampledPoints);
var trainingSamples = split.training;
var validationSamples = split.validation;


// ----------------------------------------------------------------------------
// SECTION 9: HYPERPARAMETER SEARCH — TREE-COUNT SENSITIVITY
// ----------------------------------------------------------------------------

/** Trains an RF classifier with a given tree count and returns its validation overall accuracy as an ee.Feature. */
function evaluateTreeCount(numberOfTrees) {
  var classifier = ee.Classifier.smileRandomForest({
    numberOfTrees: numberOfTrees,
    bagFraction: CONFIG.RANDOM_FOREST.bagFraction,
    seed: CONFIG.RANDOM_FOREST.seed
  }).train({
    features: trainingSamples,
    classProperty: CONFIG.CLASS_PROPERTY,
    inputProperties: predictorBandNames
  });

  var validated = validationSamples.classify(classifier);
  var confusionMatrix = validated.errorMatrix(CONFIG.CLASS_PROPERTY, 'classification');
  return ee.Feature(null, {trees: numberOfTrees, accuracy: confusionMatrix.accuracy()});
}

var recommendedTreeCount = CONFIG.RANDOM_FOREST.numberOfTrees;

if (CONFIG.HYPERPARAMETER_SEARCH.enabled) {
  var treeCountResults = CONFIG.HYPERPARAMETER_SEARCH.treeOptions.map(evaluateTreeCount);
  var treeCountFC = ee.FeatureCollection(treeCountResults);

  var treeCountChart = ui.Chart.feature.byFeature(treeCountFC, 'trees', 'accuracy')
      .setChartType('LineChart')
      .setOptions({
        title: 'Overall Accuracy vs. Number of Trees',
        hAxis: {title: 'Number of Trees'},
        vAxis: {title: 'Overall Accuracy', minValue: 0, maxValue: 1},
        pointSize: 6,
        lineWidth: 2
      });
  print(treeCountChart);

  // A single, small synchronous fetch of five (trees, accuracy) pairs to
  // drive the automatic recommendation and print a human-readable summary.
  var treeCountResultsClient = treeCountFC.getInfo().features.map(function(f) { return f.properties; });
  print('Hyperparameter search results (trees, accuracy):', treeCountResultsClient);

  if (CONFIG.HYPERPARAMETER_SEARCH.autoSelectOptimalTrees) {
    var maxAccuracy = Math.max.apply(null, treeCountResultsClient.map(function(r) { return r.accuracy; }));
    var withinTolerance = treeCountResultsClient.filter(function(r) {
      return (maxAccuracy - r.accuracy) <= CONFIG.HYPERPARAMETER_SEARCH.accuracyTolerance;
    });
    var smallestAdequateModel = withinTolerance.reduce(function(best, r) {
      return (r.trees < best.trees) ? r : best;
    }, withinTolerance[0]);

    recommendedTreeCount = smallestAdequateModel.trees;
    print('Recommended number of trees (highest accuracy within ' +
        CONFIG.HYPERPARAMETER_SEARCH.accuracyTolerance + ' tolerance, smallest model preferred): ' +
        recommendedTreeCount + ' (accuracy = ' + smallestAdequateModel.accuracy.toFixed(4) + ')');
  }
}


// ----------------------------------------------------------------------------
// SECTION 10: FINAL RANDOM FOREST TRAINING
// ----------------------------------------------------------------------------
var finalTreeCount = CONFIG.HYPERPARAMETER_SEARCH.autoSelectOptimalTrees
    ? recommendedTreeCount
    : CONFIG.RANDOM_FOREST.numberOfTrees;

var rfClassificationParams = {
  numberOfTrees: finalTreeCount,
  variablesPerSplit: CONFIG.RANDOM_FOREST.variablesPerSplit,
  minLeafPopulation: CONFIG.RANDOM_FOREST.minLeafPopulation,
  bagFraction: CONFIG.RANDOM_FOREST.bagFraction,
  seed: CONFIG.RANDOM_FOREST.seed
};

// Classification-mode classifier: outputs the most likely class per pixel.
var rfClassifier = ee.Classifier.smileRandomForest(rfClassificationParams)
    .setOutputMode('CLASSIFICATION')
    .train({
      features: trainingSamples,
      classProperty: CONFIG.CLASS_PROPERTY,
      inputProperties: predictorBandNames
    });

// Probability-mode classifier (same parameters/seed): outputs a probability
// vector per pixel, one value per class, in CLASS_IDS order.
var rfProbabilityClassifier = ee.Classifier.smileRandomForest(rfClassificationParams)
    .setOutputMode('MULTIPROBABILITY')
    .train({
      features: trainingSamples,
      classProperty: CONFIG.CLASS_PROPERTY,
      inputProperties: predictorBandNames
    });

print('Final Random Forest trained with ' + finalTreeCount + ' trees (bagFraction=' +
    CONFIG.RANDOM_FOREST.bagFraction + ', seed=' + CONFIG.RANDOM_FOREST.seed + ').');


// ----------------------------------------------------------------------------
// SECTION 11: CLASSIFICATION, PROBABILITY, CONFIDENCE, AND UNCERTAINTY
// ----------------------------------------------------------------------------
var lulcClassified = predictorStack.classify(rfClassifier).rename('lulc_class');

var classProbabilityArray = predictorStack.classify(rfProbabilityClassifier); // 1-band array image
var classProbabilityImage = classProbabilityArray
    .arrayFlatten([CONFIG.CLASS_NAMES.map(function(name, i) { return 'prob_' + CONFIG.CLASS_IDS[i]; })]);

// Confidence: probability of the winning (most likely) class.
var confidenceImage = classProbabilityArray.arrayReduce(ee.Reducer.max(), [0])
    .arrayGet([0]).rename('confidence');

// Uncertainty: complement of confidence (1 - top-class probability).
var uncertaintyImage = ee.Image(1).subtract(confidenceImage).rename('uncertainty');


// ----------------------------------------------------------------------------
// SECTION 12: VARIABLE IMPORTANCE
// ----------------------------------------------------------------------------
var variableImportanceResult = VariableImportance.computeVariableImportance(rfClassifier);


// ----------------------------------------------------------------------------
// SECTION 13: ACCURACY ASSESSMENT
// ----------------------------------------------------------------------------
var validationWithPredictions = validationSamples.classify(rfClassifier);
var accuracyAssessmentResult = AccuracyAssessment.assessAccuracy(
    validationWithPredictions, CONFIG.CLASS_PROPERTY, 'classification', CONFIG.CLASS_NAMES, CONFIG.CLASS_IDS);


// ----------------------------------------------------------------------------
// SECTION 14: AREA STATISTICS
// ----------------------------------------------------------------------------
var areaStatisticsResult = AreaStatistics.computeAreaStatistics(
    lulcClassified, studyArea, CONFIG.CLASS_IDS, CONFIG.CLASS_NAMES, CONFIG.SENTINEL2.scale, CONFIG.SENTINEL2.crs);


// ----------------------------------------------------------------------------
// SECTION 15: VISUALIZATION
// ----------------------------------------------------------------------------
VisualizationModule.renderMap({
  sentinel2Composite: sentinel2Composite,
  lulcClassified: lulcClassified,
  confidenceImage: confidenceImage,
  uncertaintyImage: uncertaintyImage,
  classProbabilityImage: classProbabilityImage,
  trainingSamples: trainingSamples,
  validationSamples: validationSamples,
  validationWithPredictions: validationWithPredictions,
  studyArea: studyArea
});


// ----------------------------------------------------------------------------
// SECTION 16: EXPORTS
// ----------------------------------------------------------------------------
ExportModule.exportAllOutputs({
  lulcClassified: lulcClassified,
  classProbabilityImage: classProbabilityImage,
  confidenceImage: confidenceImage,
  uncertaintyImage: uncertaintyImage,
  studyArea: studyArea,
  trainingSamples: trainingSamples,
  validationSamples: validationSamples,
  variableImportanceResult: variableImportanceResult,
  accuracyAssessmentResult: accuracyAssessmentResult,
  areaStatisticsResult: areaStatisticsResult
});

print('Ladakh LULC Random Forest workflow complete. See the Console, Map layers, ' +
    'and Tasks tab (for exports) for all outputs.');
