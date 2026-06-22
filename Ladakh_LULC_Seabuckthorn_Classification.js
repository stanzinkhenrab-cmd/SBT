// ============================================================================
// LAND USE / LAND COVER CLASSIFICATION OF LADAKH, INDIA
// WITH EMPHASIS ON SEABUCKTHORN (Hippophae rhamnoides) MAPPING
// ============================================================================
// Author: Remote Sensing & GEE Script
// Date: June 2026
// Platform: Google Earth Engine (JavaScript API)
// Satellite: Sentinel-2 Surface Reflectance Harmonized
// Classifier: Random Forest (100 trees)
// Study Area: Upper Indus / Leh Region, Ladakh
// ============================================================================

// ========================== SECTION 1: STUDY AREA ==========================

var roi = ee.Geometry.Polygon([
  [77.3878601, 34.1829314],
  [77.8217575, 34.1829314],
  [77.8217575, 33.8666392],
  [77.3878601, 33.8666392],
  [77.3878601, 34.1829314]
]);

Map.centerObject(roi, 11);
Map.addLayer(roi, {color: 'FF0000'}, 'Study Area ROI', false);

print('========== STUDY AREA ==========');
print('ROI Area (sq km):', roi.area().divide(1e6));

// ========================== SECTION 2: GROUND TRUTH DATA ===================
// Upload the CSV as a GEE Asset (FeatureCollection) with columns:
//   s_no, Class, ClassID, Latitude, Longitude
// Replace the path below with your actual GEE asset path after upload.

var gtAssetPath = 'users/YOUR_USERNAME/GTpoints_ClassID';

// ---------- INLINE GROUND TRUTH GENERATION FROM COORDINATES ----------------
// Since the training data originates from a CSV, we construct the
// FeatureCollection programmatically. For production use, upload the CSV
// as a GEE asset and comment out this section.

// CLASS MAPPING:
// 1 = Seabuckthorn
// 2 = Agricultural Land
// 3 = Natural Vegetation
// 4 = Water Bodies
// 5 = Barren Land
// 6 = Snow/Ice

// Load ground truth from the uploaded GEE asset
var groundTruth = ee.FeatureCollection(gtAssetPath);

// Verify the ground truth data
print('========== GROUND TRUTH DATA ==========');
print('Total ground truth points:', groundTruth.size());
print('First 5 features:', groundTruth.limit(5));

// Count samples per class
var classValues = [1, 2, 3, 4, 5, 6];
var classNames = ['Seabuckthorn', 'Agricultural Land', 'Natural Vegetation',
                  'Water Bodies', 'Barren Land', 'Snow/Ice'];

classValues.forEach(function(classId, index) {
  var count = groundTruth.filter(ee.Filter.eq('ClassID', classId)).size();
  print('Class ' + classId + ' (' + classNames[index] + '):', count);
});

// ========================== SECTION 3: SENTINEL-2 DATA =====================

// SCL-based cloud and shadow masking optimized for Ladakh cold-arid terrain
function maskS2_SCL(image) {
  var scl = image.select('SCL');

  // Retain: Vegetation(4), Bare soil(5), Water(6), Unclassified(7), Snow/Ice(11)
  // Remove: Saturated(1), Dark shadows(2,3), Cloud shadows(3),
  //         Clouds medium(8), Clouds high(9), Cirrus(10)
  var validPixels = scl.eq(4).or(scl.eq(5)).or(scl.eq(6))
                       .or(scl.eq(7)).or(scl.eq(11));

  return image.updateMask(validPixels)
              .divide(10000)
              .copyProperties(image, ['system:time_start']);
}

// Multi-year collection to find the best year
var years = [2023, 2024, 2025];
var bestYear = null;
var bestCount = 0;

print('========== IMAGE AVAILABILITY PER YEAR ==========');

years.forEach(function(year) {
  var col = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
    .filterBounds(roi)
    .filterDate(year + '-06-01', year + '-09-30')
    .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30));
  var count = col.size();
  print('Year ' + year + ' image count:', count);
});

// Use 2024 as primary year (best recent coverage); fallback composites merge years
var s2Collection = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
  .filterBounds(roi)
  .filterDate('2024-06-01', '2024-09-30')
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .map(maskS2_SCL);

// If 2024 has insufficient images, merge with 2023 and 2025
var s2Backup = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
  .filterBounds(roi)
  .filterDate('2023-06-01', '2025-09-30')
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .map(maskS2_SCL);

// Use the larger collection; prefer 2024-only if sufficient
var imageCount2024 = s2Collection.size();
print('2024 growing season images after filtering:', imageCount2024);

var s2Final = ee.Algorithms.If(
  imageCount2024.gte(10),
  s2Collection,
  s2Backup
);
s2Final = ee.ImageCollection(s2Final);
print('Final collection size:', s2Final.size());

// Generate median composite
var composite = s2Final.median().clip(roi);

// Select and rename spectral bands
var spectralBands = composite.select(
  ['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B11', 'B12']
);

print('========== SENTINEL-2 COMPOSITE ==========');
print('Composite band names:', spectralBands.bandNames());

// ========================== SECTION 4: SPECTRAL INDICES ====================

// Vegetation indices
var ndvi = spectralBands.normalizedDifference(['B8', 'B4']).rename('NDVI');
var savi = spectralBands.expression(
  '1.5 * (NIR - RED) / (NIR + RED + 0.5)', {
    'NIR': spectralBands.select('B8'),
    'RED': spectralBands.select('B4')
  }).rename('SAVI');
var rendvi = spectralBands.normalizedDifference(['B8', 'B5']).rename('RENDVI');
var reci = spectralBands.expression(
  '(NIR / RE1) - 1', {
    'NIR': spectralBands.select('B8'),
    'RE1': spectralBands.select('B5')
  }).rename('RECI');

// Water indices
var ndwi = spectralBands.normalizedDifference(['B3', 'B8']).rename('NDWI');
var mndwi = spectralBands.normalizedDifference(['B3', 'B11']).rename('MNDWI');

// Built-up / bare surface index
var ndbi = spectralBands.normalizedDifference(['B11', 'B8']).rename('NDBI');

// Snow index
var ndsi = spectralBands.normalizedDifference(['B3', 'B11']).rename('NDSI');

print('========== SPECTRAL INDICES COMPUTED ==========');
print('NDVI, SAVI, RENDVI, RECI, NDWI, MNDWI, NDBI, NDSI');

// ========================== SECTION 5: TERRAIN VARIABLES ===================

var srtm = ee.Image('USGS/SRTMGL1_003');
var elevation = srtm.select('elevation').clip(roi).rename('Elevation');
var slope = ee.Terrain.slope(srtm).clip(roi).rename('Slope');
var aspect = ee.Terrain.aspect(srtm).clip(roi).rename('Aspect');

print('========== TERRAIN VARIABLES ==========');
print('DEM: USGS/SRTMGL1_003');
print('Elevation range:', elevation.reduceRegion({
  reducer: ee.Reducer.minMax(), geometry: roi, scale: 30, bestEffort: true
}));

// ========================== SECTION 6: FEATURE STACK =======================

var featureStack = spectralBands
  .addBands(ndvi)
  .addBands(savi)
  .addBands(rendvi)
  .addBands(reci)
  .addBands(ndwi)
  .addBands(mndwi)
  .addBands(ndbi)
  .addBands(ndsi)
  .addBands(elevation)
  .addBands(slope)
  .addBands(aspect);

var predictorBands = featureStack.bandNames();
print('========== FEATURE STACK ==========');
print('Total predictor bands:', predictorBands.size());
print('Band names:', predictorBands);

// ========================== SECTION 7: SNOW/ICE SUPPLEMENTAL SAMPLES =======

// Generate additional Snow/Ice training samples where ground truth is sparse
var snowMask = elevation.gt(5000).and(ndsi.gt(0.40));
var snowSamples = snowMask.selfMask().stratifiedSample({
  numPoints: 50,
  classBand: 'Elevation',
  region: roi,
  scale: 20,
  seed: 42,
  geometries: true
});

// Assign ClassID = 6 (Snow/Ice) to supplemental samples
snowSamples = snowSamples.map(function(f) {
  return f.set('ClassID', 6);
});

print('========== SNOW/ICE SUPPLEMENTAL SAMPLES ==========');
print('Generated supplemental Snow/Ice samples:', snowSamples.size());

// ========================== SECTION 8: TRAINING DATA PREPARATION ===========

// Buffer each ground truth point by 20 meters to capture representative pixels
var bufferedGT = groundTruth.map(function(feature) {
  return feature.buffer(20);
});

// Merge with supplemental Snow/Ice samples (also buffered)
var bufferedSnow = snowSamples.map(function(feature) {
  return feature.buffer(20);
});

var allTrainingRegions = bufferedGT.merge(bufferedSnow);
print('Total training regions (GT + supplemental):', allTrainingRegions.size());

// Extract spectral/terrain values at training locations
var trainingData = featureStack.sampleRegions({
  collection: allTrainingRegions,
  properties: ['ClassID'],
  scale: 10,
  tileScale: 8,
  geometries: true
});

// Remove null-valued samples to prevent classifier failures
var bandList = predictorBands.getInfo();
var validTraining = trainingData.filter(ee.Filter.notNull(bandList));

print('========== TRAINING SAMPLE EXTRACTION ==========');
print('Total extracted samples:', trainingData.size());
print('Valid samples (no nulls):', validTraining.size());

// Count valid samples per class
classValues.forEach(function(classId, index) {
  var count = validTraining.filter(ee.Filter.eq('ClassID', classId)).size();
  print('Valid Class ' + classId + ' (' + classNames[index] + '):', count);
});

// ========================== SECTION 9: CLASS BALANCING =====================

// Determine the minimum class size and cap all classes to that number
var classCounts = classValues.map(function(classId) {
  return validTraining.filter(ee.Filter.eq('ClassID', classId)).size();
});

// Use a server-side approach to find the minimum
var minClassSize = ee.Number(classCounts.reduce(function(prev, curr) {
  return ee.Number(prev).min(ee.Number(curr));
}));

print('========== CLASS BALANCING ==========');
print('Minimum class size (cap):', minClassSize);

// Balance classes by limiting each to the minimum size
var balancedSamples = ee.FeatureCollection(classValues.map(function(classId) {
  return validTraining
    .filter(ee.Filter.eq('ClassID', classId))
    .randomColumn('random', 42)
    .sort('random')
    .limit(minClassSize);
})).flatten();

print('Balanced training samples:', balancedSamples.size());

// ========================== SECTION 10: DATA PARTITIONING ==================

// Add random column for reproducible splitting
var withRandom = balancedSamples.randomColumn('split', 42);
var trainingSplit = withRandom.filter(ee.Filter.lt('split', 0.7));
var validationSplit = withRandom.filter(ee.Filter.gte('split', 0.7));

print('========== DATA PARTITIONING (70/30) ==========');
print('Training samples:', trainingSplit.size());
print('Validation samples:', validationSplit.size());

// ========================== SECTION 11: RANDOM FOREST CLASSIFICATION =======

var rfClassifier = ee.Classifier.smileRandomForest({
  numberOfTrees: 100,
  seed: 42
}).train({
  features: trainingSplit,
  classProperty: 'ClassID',
  inputProperties: bandList
});

print('========== RANDOM FOREST CLASSIFIER ==========');
print('Classifier info:', rfClassifier.explain());

// Classify the feature stack
var classified = featureStack.classify(rfClassifier).clip(roi);

// ========================== SECTION 12: VARIABLE IMPORTANCE ================

var importance = ee.Dictionary(rfClassifier.explain().get('importance'));
print('========== VARIABLE IMPORTANCE ==========');
print('Importance scores:', importance);

// Convert importance to a sorted FeatureCollection for export
var importanceFC = ee.FeatureCollection(importance.keys().map(function(key) {
  return ee.Feature(null, {
    'Variable': key,
    'Importance': importance.getNumber(ee.String(key))
  });
}));

// Sort by importance descending
importanceFC = importanceFC.sort('Importance', false);
print('Ranked variable importance:', importanceFC);

// Variable importance chart
var importanceChart = ui.Chart.feature.byFeature(importanceFC, 'Variable', 'Importance')
  .setChartType('BarChart')
  .setOptions({
    title: 'Random Forest Variable Importance',
    hAxis: {title: 'Importance Score'},
    vAxis: {title: 'Variable'},
    legend: {position: 'none'},
    colors: ['#2E7D32']
  });
print(importanceChart);

// ========================== SECTION 13: ACCURACY ASSESSMENT ================

var validated = validationSplit.classify(rfClassifier);
var confusionMatrix = validated.errorMatrix('ClassID', 'classification');

print('========== ACCURACY ASSESSMENT ==========');
print('Confusion Matrix:', confusionMatrix);
print('Overall Accuracy:', confusionMatrix.accuracy());
print('Kappa Coefficient:', confusionMatrix.kappa());
print('Producer Accuracy:', confusionMatrix.producersAccuracy());
print('User Accuracy:', confusionMatrix.consumersAccuracy());

// Build accuracy statistics as FeatureCollection for export
var overallAccuracy = confusionMatrix.accuracy();
var kappa = confusionMatrix.kappa();
var producerAcc = confusionMatrix.producersAccuracy();
var userAcc = confusionMatrix.consumersAccuracy();

var accuracyFC = ee.FeatureCollection([
  ee.Feature(null, {
    'Metric': 'Overall_Accuracy',
    'Value': overallAccuracy
  }),
  ee.Feature(null, {
    'Metric': 'Kappa_Coefficient',
    'Value': kappa
  })
]);

// Add per-class producer and user accuracy
classValues.forEach(function(classId, index) {
  accuracyFC = accuracyFC.merge(ee.FeatureCollection([
    ee.Feature(null, {
      'Metric': 'Producer_Accuracy_' + classNames[index],
      'Value': producerAcc.toList().flatten().get(index)
    }),
    ee.Feature(null, {
      'Metric': 'User_Accuracy_' + classNames[index],
      'Value': userAcc.toList().flatten().get(index)
    })
  ]));
});

// ========================== SECTION 14: POST-CLASSIFICATION REFINEMENT =====

print('========== ECOLOGICAL POST-CLASSIFICATION REFINEMENT ==========');

// --- River proximity mask for Seabuckthorn ---
// Use MNDWI to identify water channels and buffer them
var waterChannels = mndwi.gt(0.0).selfMask();
var waterDistance = waterChannels.fastDistanceTransform(256, 'pixels')
                                .sqrt().multiply(10); // approximate meters at 10m res

// Seabuckthorn ecological constraints
var sbtElevMask = elevation.gte(2800).and(elevation.lte(4000));
var sbtSlopeMask = slope.lte(25);
var sbtNdviMask = ndvi.gte(0.15);
var sbtRiverMask = waterDistance.lte(2000); // within 2 km of rivers/streams
var sbtEcoMask = sbtElevMask.and(sbtSlopeMask).and(sbtNdviMask).and(sbtRiverMask);

// Water body ecological constraints
var waterElevMask = elevation.lte(4500);
var waterSlopeMask = slope.lte(15);
var waterMndwiMask = mndwi.gte(-0.10);
var waterEcoMask = waterElevMask.and(waterSlopeMask).and(waterMndwiMask);

// Natural vegetation constraint
var vegNdviMask = ndvi.gte(0.10);

// Apply ecological rules
var refinedClassified = classified;

// Seabuckthorn: reclassify ecologically impossible pixels to Barren Land (5)
var sbtPixels = classified.eq(1);
var invalidSbt = sbtPixels.and(sbtEcoMask.not());
refinedClassified = refinedClassified.where(invalidSbt, 5);

// Water: reclassify ecologically impossible pixels to Barren Land (5)
var waterPixels = classified.eq(4);
var invalidWater = waterPixels.and(waterEcoMask.not());
refinedClassified = refinedClassified.where(invalidWater, 5);

// Natural Vegetation: reclassify low-NDVI pixels to Barren Land (5)
var vegPixels = classified.eq(3);
var invalidVeg = vegPixels.and(vegNdviMask.not());
refinedClassified = refinedClassified.where(invalidVeg, 5);

refinedClassified = refinedClassified.rename('LULC').clip(roi);

print('Post-classification refinement applied:');
print('  - Seabuckthorn: Elev 2800-4000m, Slope<=25, NDVI>=0.15, within 2km of rivers');
print('  - Water: Elev<=4500m, Slope<=15, MNDWI>=-0.10');
print('  - Natural Vegetation: NDVI>=0.10');

// ========================== SECTION 15: AREA STATISTICS ====================

var pixelArea = ee.Image.pixelArea().divide(10000); // hectares
var areaImage = pixelArea.addBands(refinedClassified);

var areaStats = areaImage.reduceRegion({
  reducer: ee.Reducer.sum().group({
    groupField: 1,
    groupName: 'ClassID'
  }),
  geometry: roi,
  scale: 10,
  maxPixels: 1e13,
  tileScale: 4,
  bestEffort: true
});

print('========== AREA STATISTICS ==========');
print('Raw area stats:', areaStats);

// Total study area
var totalArea = roi.area().divide(10000); // hectares

// Build area statistics FeatureCollection
var areaGroups = ee.List(areaStats.get('groups'));
var areaFC = areaGroups.map(function(group) {
  var classId = ee.Dictionary(group).getNumber('ClassID');
  var areaHa = ee.Dictionary(group).getNumber('sum');
  var areaSqKm = areaHa.divide(100);
  var percentage = areaHa.divide(totalArea).multiply(100);

  var className = ee.List(classNames).get(classId.subtract(1).int());

  return ee.Feature(null, {
    'ClassID': classId,
    'ClassName': className,
    'Area_ha': areaHa,
    'Area_sqkm': areaSqKm,
    'Percentage': percentage
  });
});
var areaStatsFC = ee.FeatureCollection(areaFC);
print('Area statistics:', areaStatsFC);

// ========================== SECTION 16: VISUALIZATION ======================

// Color palette for LULC classes
var lulcPalette = [
  '#006400',  // 1: Seabuckthorn - Dark Green
  '#90EE90',  // 2: Agricultural Land - Light Green
  '#808000',  // 3: Natural Vegetation - Olive Green
  '#0000FF',  // 4: Water Bodies - Blue
  '#8B4513',  // 5: Barren Land - Brown
  '#FFFFFF'   // 6: Snow/Ice - White
];

var lulcVis = {min: 1, max: 6, palette: lulcPalette};

// Sentinel-2 False Color Composite (NIR-Red-Green)
Map.addLayer(composite.select(['B8', 'B4', 'B3']),
  {min: 0, max: 0.4}, 'Sentinel-2 False Color (B8-B4-B3)', false);

// NDVI
Map.addLayer(ndvi, {min: -0.1, max: 0.8, palette: ['brown', 'yellow', 'green', 'darkgreen']},
  'NDVI', false);

// DEM
Map.addLayer(elevation, {min: 3000, max: 6000, palette: ['green', 'yellow', 'orange', 'red', 'white']},
  'DEM (SRTM)', false);

// Final classified map
Map.addLayer(refinedClassified, lulcVis, 'LULC Classification (Refined)');

// ========================== SECTION 17: LEGEND =============================

var legend = ui.Panel({
  style: {
    position: 'bottom-left',
    padding: '8px 15px'
  }
});

var legendTitle = ui.Label({
  value: 'LULC Classification',
  style: {fontWeight: 'bold', fontSize: '16px', margin: '0 0 6px 0'}
});
legend.add(legendTitle);

var makeRow = function(color, name) {
  var colorBox = ui.Label({
    style: {
      backgroundColor: color,
      padding: '8px',
      margin: '0 4px 4px 0',
      border: '1px solid black'
    }
  });
  var description = ui.Label({
    value: name,
    style: {margin: '0 0 4px 6px', fontSize: '13px'}
  });
  return ui.Panel({
    widgets: [colorBox, description],
    layout: ui.Panel.Layout.Flow('horizontal')
  });
};

for (var i = 0; i < classNames.length; i++) {
  legend.add(makeRow(lulcPalette[i], classNames[i]));
}

Map.add(legend);

// ========================== SECTION 18: EXPORTS ============================

// Export classified raster
Export.image.toDrive({
  image: refinedClassified.toInt8(),
  description: 'Ladakh_LULC_Classification_2024',
  folder: 'Ladakh_LULC',
  fileNamePrefix: 'Ladakh_LULC_2024',
  region: roi,
  scale: 10,
  crs: 'EPSG:4326',
  maxPixels: 1e13
});

// Export accuracy statistics
Export.table.toDrive({
  collection: accuracyFC,
  description: 'Accuracy_Assessment',
  folder: 'Ladakh_LULC',
  fileNamePrefix: 'Accuracy_Assessment',
  fileFormat: 'CSV'
});

// Export area statistics
Export.table.toDrive({
  collection: areaStatsFC,
  description: 'Area_Statistics',
  folder: 'Ladakh_LULC',
  fileNamePrefix: 'Area_Statistics',
  fileFormat: 'CSV'
});

// Export variable importance
Export.table.toDrive({
  collection: importanceFC,
  description: 'Variable_Importance',
  folder: 'Ladakh_LULC',
  fileNamePrefix: 'Variable_Importance',
  fileFormat: 'CSV'
});

print('========== EXPORTS CONFIGURED ==========');
print('Run the Tasks tab to execute exports to Google Drive folder: Ladakh_LULC');
print('  1. Ladakh_LULC_Classification_2024 (GeoTIFF)');
print('  2. Accuracy_Assessment (CSV)');
print('  3. Area_Statistics (CSV)');
print('  4. Variable_Importance (CSV)');

print('========== SCRIPT COMPLETE ==========');
