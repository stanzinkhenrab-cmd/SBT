// ============================================================================
// MODULE 04 — AREA STATISTICS
// Scope: Per-class pixel count and area (hectares, km2, percentage of the
//        mapped study area) computed directly from the classified LULC
//        raster using an exact pixel-area weighting (ee.Image.pixelArea()),
//        which is robust to the EPSG:4326 output projection's varying pixel
//        size with latitude.
//
// Public API:
//   computeAreaStatistics(classifiedImage, region, classIds, classNames,
//                          scale, crs) -> {
//     featureCollection: ee.FeatureCollection, one feature per class with
//       class_id, class_name, pixel_count, area_ha, area_km2, percentage
//     totalAreaHectares: number
//   }
// ============================================================================

/**
 * Computes pixel count and area (m2, via a grouped sum+count reduction
 * over ee.Image.pixelArea()) per class. The grouped result is small
 * (NUM_CLASSES entries), so a single synchronous fetch is used to convert
 * counts into hectares/km2/percentage and to build the export-ready
 * FeatureCollection.
 */
function computeAreaStatistics(classifiedImage, region, classIds, classNames, scale, crs) {
  var areaAndClassImage = ee.Image.pixelArea().rename('area')
      .addBands(classifiedImage.rename('class'));

  var groupedReducer = ee.Reducer.sum()
      .combine({reducer2: ee.Reducer.count(), sharedInputs: true})
      .group({groupField: 1, groupName: 'class'});

  var groupedStats = areaAndClassImage.reduceRegion({
    reducer: groupedReducer,
    geometry: region,
    scale: scale,
    crs: crs,
    maxPixels: 1e13,
    bestEffort: true
  });

  var groupsClient = ee.List(groupedStats.get('groups')).getInfo();

  var statsByClassId = {};
  groupsClient.forEach(function(group) {
    statsByClassId[group.class] = {pixelCount: group.count, areaSquareMeters: group.sum};
  });

  var totalAreaSquareMeters = groupsClient.reduce(function(sum, g) { return sum + g.sum; }, 0);

  var perClassRows = classIds.map(function(classId, index) {
    var stats = statsByClassId[classId] || {pixelCount: 0, areaSquareMeters: 0};
    var areaHectares = stats.areaSquareMeters / 10000;
    var areaSquareKm = stats.areaSquareMeters / 1e6;
    var percentage = totalAreaSquareMeters === 0 ? 0 : (stats.areaSquareMeters / totalAreaSquareMeters) * 100;

    return {
      class_id: classId,
      class_name: classNames[index],
      pixel_count: stats.pixelCount,
      area_ha: areaHectares,
      area_km2: areaSquareKm,
      percentage: percentage
    };
  });

  var featureCollection = ee.FeatureCollection(perClassRows.map(function(row) {
    return ee.Feature(null, row);
  }));

  print('=== AREA STATISTICS (per class) ===', featureCollection);
  print('Total mapped area (hectares): ' + (totalAreaSquareMeters / 10000).toFixed(2));

  return {
    featureCollection: featureCollection,
    perClassRows: perClassRows,
    totalAreaHectares: totalAreaSquareMeters / 10000
  };
}

exports.computeAreaStatistics = computeAreaStatistics;
