/**
 * ==============================================================================
 * HIGH-ALTITUDE LULC & SEABUCKTHORN MAPPING (LADAKH, INDIA)
 * Optimized for Cold-Arid Himalayan Riverine Ecosystems
 * ==============================================================================
 */

// ==============================================================================
// 1. INITIALIZATION & REGION OF INTEREST (ROI)
// ==============================================================================

// ** USER INPUT REQUIRED HERE **
// Replace with the path to your uploaded Ground Truth FeatureCollection asset.
var gtAssetPath = 'projects/ee-stanzin-soil/assets/GT-points-22-06-26';
var groundTruth = ee.FeatureCollection(gtAssetPath);

// Define the Region of Interest (ROI) using the provided bounding box coordinates
var roi = ee.Geometry.Rectangle([77.3878601, 33.8666392, 77.8217575, 34.1829314]);

Map.centerObject(roi, 11);
Map.addLayer(roi, {color: 'red'}, 'Study Area (ROI)', false);

// ==============================================================================
// 2. TERRAIN PROCESSING (USGS SRTMGL1_003)
// ==============================================================================

// Using USGS SRTM 30m for seamless, gap-free Himalayan coverage
var dem = ee.Image('USGS/SRTMGL1_003').clip(roi);
var elevation = dem.select('elevation');
var slope = ee.Terrain.slope(elevation);
var aspect = ee.Terrain.aspect(elevation);

var terrainStack = ee.Image.cat([
  elevation.rename('Elevation'),
  slope.rename('Slope'),
  aspect.rename('Aspect')
]);

// ==============================================================================
// 3. SENTINEL-2 IMAGE PREPROCESSING & CLOUD MASKING (SCL)
// ==============================================================================

// Function to apply robust cloud masking using the Scene Classification Layer (SCL)
function maskS2cloudsSCL(image) {
  var scl = image.select('SCL');
  // Retain: 4 (Vegetation), 5 (Bare Soil), 6 (Water), 7 (Unclassified), 11 (Snow/Ice)
  // Masks out: Clouds (8, 9, 10), Cloud Shadows (3), Cirrus (10), Saturated/Defective (1, 2)
  var validPixels = scl.eq(4).or(scl.eq(5)).or(scl.eq(6)).or(scl.eq(7)).or(scl.eq(11));

  // Scale surface reflectance values
  var scaled = image.select(['B2','B3','B4','B5','B6','B7','B8','B11','B12'])
                    .multiply(0.0001);

  return scaled.updateMask(validPixels)
               .copyProperties(image, ['system:time_start']);
}

// Fetch growing-season imagery (June-September), 2023-2026, max 30% cloud cover
var s2Collection = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
  .filterBounds(roi)
  .filterDate('2023-06-01', '2026-09-30')
  .filter(ee.Filter.calendarRange(6, 9, 'month'))
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .map(maskS2cloudsSCL);

// Create optimal median composite
var composite = s2Collection.median().clip(roi);

// ==============================================================================
// 4. SPECTRAL INDEX CALCULATION
// ==============================================================================

// Calculate specific vegetation, water, bare-soil, and snow indices
var calcIndices = function(img) {
  var ndvi   = img.normalizedDifference(['B8', 'B4']).rename('NDVI');
  var savi   = img.expression('1.5 * ((B8 - B4) / (B8 + B4 + 0.5))',
               {'B8': img.select('B8'), 'B4': img.select('B4')}).rename('SAVI');
  var rendvi = img.normalizedDifference(['B6', 'B5']).rename('RENDVI');
  var reci   = img.expression('(B8 / B5) - 1',
               {'B8': img.select('B8'), 'B5': img.select('B5')}).rename('RECI');
  var ndwi   = img.normalizedDifference(['B3', 'B8']).rename('NDWI');
  var mndwi  = img.normalizedDifference(['B3', 'B12']).rename('MNDWI');
  var ndbi   = img.normalizedDifference(['B11', 'B8']).rename('NDBI');
  var ndsi   = img.normalizedDifference(['B3', 'B11']).rename('NDSI');

  return img.addBands([ndvi, savi, rendvi, reci, ndwi, mndwi, ndbi, ndsi]);
};

var finalStack = calcIndices(composite).addBands(terrainStack);

// ==============================================================================
// 5. TRAINING DATA PREPARATION & CLASS MAPPING
// ==============================================================================

// Filter out any blank/empty rows that might exist at the bottom of the CSV
var validGT = groundTruth.filter(ee.Filter.notNull(['lat', 'long']));
print('1. Valid rows found in CSV:', validGT.size());

// Convert CSV rows into mapped Point Geometries & Clean up string matching
var formatGT = validGT.map(function(feat) {

  // Explicitly construct the geometry using your CSV's 'long' and 'lat' columns
  var lon = ee.Number(feat.get('long'));
  var lat = ee.Number(feat.get('lat'));
  var pt = ee.Geometry.Point([lon, lat]);

  // Robust String Matching (Removes hidden spaces and standardizes to lowercase)
  var rawClass = ee.String(feat.get('Class'));
  var className = rawClass.trim().toLowerCase();

  var classId = ee.Algorithms.If(className.equals('seabuckthorn'), 1,
                ee.Algorithms.If(className.equals('agricultural land'), 2,
                ee.Algorithms.If(className.equals('natural vegetation'), 3,
                ee.Algorithms.If(className.equals('water bodies'), 4,
                ee.Algorithms.If(className.equals('barren land'), 5, 0)))));

  return ee.Feature(pt).copyProperties(feat)
                       .set('ClassID', classId)
                       .set('CleanedClassName', className);

}).filter(ee.Filter.gt('ClassID', 0));

print('2. GT points successfully matched to a Class ID:', formatGT.size());

// Auto-generate Snow/Ice Training Points (Elevation > 5000m & NDSI > 0.40)
var snowMask = finalStack.select('Elevation').gt(5000)
                 .and(finalStack.select('NDSI').gt(0.40));

var generatedSnowPoints = snowMask.selfMask().sample({
  region: roi,
  scale: 10,
  numPixels: 300,
  geometries: true
}).map(function(feat) {
  return feat.set('ClassID', 6).set('ClassName', 'Snow/Ice');
});

// Auto-generate Built-up Training Points from ESA WorldCover 2021 (10m resolution)
// ESA WorldCover 2021: Class 50 = Built-up
var esaWorldCover = ee.ImageCollection('ESA/WorldCover/v200')
  .first()
  .clip(roi);

var builtupMask = esaWorldCover.eq(50);

// Additional spectral filtering for accuracy: NDBI > 0 and NDVI < 0.2 (non-vegetated built areas)
var builtupRefined = builtupMask
  .and(finalStack.select('NDBI').gt(0))
  .and(finalStack.select('NDVI').lt(0.2));

var generatedBuiltupPoints = builtupRefined.selfMask().sample({
  region: roi,
  scale: 10,
  numPixels: 300,
  geometries: true
}).map(function(feat) {
  return feat.set('ClassID', 7).set('ClassName', 'Built-up');
});

print('3. Generated Snow/Ice points:', generatedSnowPoints.size());
print('4. Generated Built-up points:', generatedBuiltupPoints.size());

// Merge user Ground Truth with the generated Snow/Ice and Built-up points
var mergedTrainingData = formatGT.merge(generatedSnowPoints).merge(generatedBuiltupPoints);

// ==============================================================================
// 6. SAMPLE EXTRACTION & CLASS BALANCING
// ==============================================================================

var predictorBands = ['B2','B3','B4','B5','B6','B7','B8','B11','B12',
                      'NDVI','SAVI','RENDVI','RECI','NDWI','MNDWI','NDBI','NDSI',
                      'Elevation','Slope','Aspect'];

// Extract raster values at training locations and drop Nulls
var sampledData = finalStack.select(predictorBands).sampleRegions({
  collection: mergedTrainingData,
  properties: ['ClassID'],
  scale: 10,
  tileScale: 4
}).filter(ee.Filter.notNull(predictorBands));

// Class Balancing: Find smallest class size and cap all classes
var classCounts = sampledData.aggregate_histogram('ClassID');
print('5. Pixels successfully extracted per ClassID:', classCounts);

// Calculate the minimum count among available classes
var countList = classCounts.values();
var minCount = ee.Number(countList.reduce(ee.Reducer.min()));
print('6. Balancing all classes to sample count:', minCount);

// Apply class balance (now 7 classes including Built-up)
var balancedSample = ee.FeatureCollection(
  ee.List([1, 2, 3, 4, 5, 6, 7]).map(function(classId) {
    return sampledData.filter(ee.Filter.eq('ClassID', classId))
                      .randomColumn('random_sort')
                      .sort('random_sort')
                      .limit(minCount);
  })
).flatten();

// ==============================================================================
// 7. DATA PARTITIONING & RANDOM FOREST CLASSIFICATION
// ==============================================================================

// Split: 70% Training / 30% Validation
var withSplit = balancedSample.randomColumn('split_seed', 42);
var trainingSet = withSplit.filter(ee.Filter.lt('split_seed', 0.7));
var validationSet = withSplit.filter(ee.Filter.gte('split_seed', 0.7));

print('Total Training Pixels:', trainingSet.size());
print('Total Validation Pixels:', validationSet.size());

// Train Random Forest
var rfClassifier = ee.Classifier.smileRandomForest({
  numberOfTrees: 100,
  seed: 42
}).train({
  features: trainingSet,
  classProperty: 'ClassID',
  inputProperties: predictorBands
});

// Classify the composite image
var initialClassification = finalStack.select(predictorBands).classify(rfClassifier);

// ==============================================================================
// 8. ECOLOGICAL RULE-BASED POST-CLASSIFICATION REFINEMENT
// ==============================================================================

// Calculate distance to water (rivers/streams/lakes) using MNDWI
var waterMask = finalStack.select('MNDWI').gte(0);
var distToWater = waterMask.fastDistanceTransform(256).multiply(10).rename('DistToWater');

var refinedClassification = initialClassification;

// RULE 1: Seabuckthorn Refinement
// Restrictions: Elev 2800-4000m, Slope <= 25, NDVI >= 0.15, Dist to Water <= 1000m
var sbInvalid = refinedClassification.eq(1).and(
  finalStack.select('Elevation').lt(2800).or(finalStack.select('Elevation').gt(4000))
  .or(finalStack.select('Slope').gt(25))
  .or(finalStack.select('NDVI').lt(0.15))
  .or(distToWater.gt(1000))
);
refinedClassification = refinedClassification.where(sbInvalid, 5); // Default to Barren (5)

// RULE 2: Water Bodies Refinement
// Restrictions: Elev <= 4500m, Slope <= 15, MNDWI >= -0.10
var waterInvalid = refinedClassification.eq(4).and(
  finalStack.select('Elevation').gt(4500)
  .or(finalStack.select('Slope').gt(15))
  .or(finalStack.select('MNDWI').lt(-0.10))
);
refinedClassification = refinedClassification.where(waterInvalid, 5);

// RULE 3: Natural Vegetation Refinement
// Restrictions: NDVI >= 0.10
var vegInvalid = refinedClassification.eq(3).and(
  finalStack.select('NDVI').lt(0.10)
);
refinedClassification = refinedClassification.where(vegInvalid, 5);

// RULE 4: Built-up Refinement
// Restrictions: Elev <= 4200m, Slope <= 20, NDVI < 0.25 (built areas are not heavily vegetated)
var builtupInvalid = refinedClassification.eq(7).and(
  finalStack.select('Elevation').gt(4200)
  .or(finalStack.select('Slope').gt(20))
  .or(finalStack.select('NDVI').gte(0.25))
);
refinedClassification = refinedClassification.where(builtupInvalid, 5); // Default to Barren (5)

// ==============================================================================
// 9. ACCURACY ASSESSMENT & VARIABLE IMPORTANCE
// ==============================================================================

// Validation Phase
var validatedClassified = validationSet.classify(rfClassifier);
var errorMatrix = validatedClassified.errorMatrix('ClassID', 'classification');

var overallAccuracy = errorMatrix.accuracy();
var kappa = errorMatrix.kappa();

print('Validation Error Matrix:', errorMatrix);
print('Overall Accuracy:', overallAccuracy);
print('Kappa Coefficient:', kappa);

// Extract Variable Importance
var dictImportance = rfClassifier.explain().get('importance');
var importanceChart = ui.Chart.feature.byFeature({
  features: ee.FeatureCollection(
    ee.List(ee.Dictionary(dictImportance).keys()).map(function(key) {
      return ee.Feature(null, {'Variable': key, 'Importance': ee.Dictionary(dictImportance).get(key)});
    })
  ),
  xProperty: 'Variable',
  yProperties: ['Importance']
}).setChartType('ColumnChart')
  .setOptions({
    title: 'Random Forest Variable Importance',
    hAxis: {title: 'Predictor Variables', slantedText: true, slantedTextAngle: 45},
    vAxis: {title: 'Importance Score'}
  });

print(importanceChart);

// ==============================================================================
// 10. AREA STATISTICS CALCULATION
// ==============================================================================

var pixelAreaImage = ee.Image.pixelArea().addBands(refinedClassification);
var classAreasHectares = pixelAreaImage.reduceRegion({
  reducer: ee.Reducer.sum().group({
    groupField: 1,
    groupName: 'ClassID'
  }),
  geometry: roi,
  scale: 10,
  maxPixels: 1e11
});

print('Class Areas (Square Meters):', classAreasHectares);

// ==============================================================================
// 11. VISUALIZATION & LEGEND
// ==============================================================================

// Visualization Parameters
var visS2FC = {bands: ['B8', 'B4', 'B3'], min: 0, max: 0.3};
var visNDVI = {min: -0.2, max: 0.8, palette: ['blue', 'white', 'green']};
var visDEM  = {min: 2500, max: 6000, palette: ['006600', '002200', 'fff700', 'ab7634', 'c4d0ff', 'ffffff']};

var classPalette = [
  '004400', // 1: Seabuckthorn (Dark Green)
  '90EE90', // 2: Agricultural Land (Light Green)
  '808000', // 3: Natural Vegetation (Olive Green)
  '0000FF', // 4: Water Bodies (Blue)
  '8B4513', // 5: Barren Land (Brown)
  'FFFFFF', // 6: Snow/Ice (White)
  'FF0000'  // 7: Built-up (Red)
];

Map.addLayer(dem, visDEM, 'Elevation DEM', false);
Map.addLayer(finalStack, visS2FC, 'Sentinel-2 False Color (B8/B4/B3)', false);
Map.addLayer(finalStack.select('NDVI'), visNDVI, 'NDVI', false);
Map.addLayer(refinedClassification, {min: 1, max: 7, palette: classPalette}, 'Refined LULC Classification');

// Generate Legend Panel
var legend = ui.Panel({
  style: {position: 'bottom-left', padding: '8px 15px', backgroundColor: 'rgba(255, 255, 255, 0.9)'}
});
legend.add(ui.Label({
  value: 'LULC Classes - Ladakh',
  style: {fontWeight: 'bold', fontSize: '16px', margin: '0 0 4px 0'}
}));

var makeRow = function(color, name) {
  var colorBox = ui.Label({
    style: {backgroundColor: '#' + color, padding: '8px', margin: '0 0 4px 0'}
  });
  var description = ui.Label({
    value: name, style: {margin: '0 0 4px 6px'}
  });
  return ui.Panel({widgets: [colorBox, description], layout: ui.Panel.Layout.Flow('horizontal')});
};

legend.add(makeRow('004400', '1 - Seabuckthorn'));
legend.add(makeRow('90EE90', '2 - Agricultural Land'));
legend.add(makeRow('808000', '3 - Natural Vegetation'));
legend.add(makeRow('0000FF', '4 - Water Bodies'));
legend.add(makeRow('8B4513', '5 - Barren Land'));
legend.add(makeRow('FFFFFF', '6 - Snow/Ice'));
legend.add(makeRow('FF0000', '7 - Built-up'));
Map.add(legend);

// ==============================================================================
// 12. EXPORT FUNCTIONS
// ==============================================================================

// 1. Export Classification Image
Export.image.toDrive({
  image: refinedClassification.toByte(),
  description: 'Ladakh_LULC_Seabuckthorn_Map',
  folder: 'GEE_Ladakh_LULC',
  scale: 10,
  region: roi,
  maxPixels: 1e11,
  crs: 'EPSG:4326'
});

// 2. Export Accuracy Statistics CSV
var accuracyFC = ee.FeatureCollection([
  ee.Feature(null, {'Metric': 'Overall Accuracy', 'Value': overallAccuracy}),
  ee.Feature(null, {'Metric': 'Kappa Coefficient', 'Value': kappa})
]);

Export.table.toDrive({
  collection: accuracyFC,
  description: 'Accuracy_Metrics',
  folder: 'GEE_Ladakh_LULC',
  fileFormat: 'CSV'
});

// 3. Export Variable Importance CSV
var importanceFC = ee.FeatureCollection(
  ee.List(ee.Dictionary(dictImportance).keys()).map(function(key) {
    return ee.Feature(null, {
      'Variable': key,
      'Importance': ee.Dictionary(dictImportance).get(key)
    });
  })
);

Export.table.toDrive({
  collection: importanceFC,
  description: 'RF_Variable_Importance',
  folder: 'GEE_Ladakh_LULC',
  fileFormat: 'CSV'
});
