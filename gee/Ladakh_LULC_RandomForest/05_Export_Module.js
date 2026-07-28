// ============================================================================
// MODULE 05 — EXPORT MODULE
// Scope: Registers every Export.image.toDrive / Export.table.toDrive task
//        required by the project. Calling exportAllOutputs() queues all
//        tasks; each still needs to be started from the Code Editor's
//        "Tasks" tab (Earth Engine does not auto-run exports, by design,
//        to protect users from unintended large batch jobs).
//
// Outputs registered:
//   Images (GeoTIFF): LULC classification, per-class probability stack,
//                      confidence, uncertainty
//   Tables (CSV):      variable importance, area statistics, accuracy
//                      summary, accuracy per-class, confusion matrix,
//                      training samples, validation samples
// ============================================================================

var REPO = 'users/<your_username>/Ladakh_LULC_RandomForest';
var CONFIG = require(REPO + ':00_Config.js');

/** Shared defaults applied to every image export. */
function exportImageToDrive(image, description, region) {
  Export.image.toDrive({
    image: image,
    description: description,
    folder: CONFIG.EXPORT.driveFolder,
    fileNamePrefix: CONFIG.EXPORT.filePrefix + '_' + description,
    region: region,
    scale: CONFIG.EXPORT.scale,
    crs: CONFIG.EXPORT.crs,
    maxPixels: CONFIG.EXPORT.maxPixels,
    fileFormat: 'GeoTIFF'
  });
}

/** Shared defaults applied to every table (CSV) export. */
function exportTableToDrive(featureCollection, description) {
  Export.table.toDrive({
    collection: featureCollection,
    description: description,
    folder: CONFIG.EXPORT.driveFolder,
    fileNamePrefix: CONFIG.EXPORT.filePrefix + '_' + description,
    fileFormat: 'CSV'
  });
}

/**
 * Queues every export task for the project. `outputs` is the object
 * assembled in 01_Main_Script.js and must contain:
 *   lulcClassified, classProbabilityImage, confidenceImage, uncertaintyImage,
 *   studyArea, trainingSamples, validationSamples, variableImportanceResult,
 *   accuracyAssessmentResult, areaStatisticsResult
 */
function exportAllOutputs(outputs) {
  // --- Raster outputs ---------------------------------------------------
  exportImageToDrive(outputs.lulcClassified.toByte(), 'LULC_Classification', outputs.studyArea);
  exportImageToDrive(outputs.classProbabilityImage.toFloat(), 'Class_Probability', outputs.studyArea);
  exportImageToDrive(outputs.confidenceImage.toFloat(), 'Confidence', outputs.studyArea);
  exportImageToDrive(outputs.uncertaintyImage.toFloat(), 'Uncertainty', outputs.studyArea);

  // --- Tabular outputs ----------------------------------------------------
  exportTableToDrive(outputs.variableImportanceResult.featureCollection, 'Variable_Importance');
  exportTableToDrive(outputs.areaStatisticsResult.featureCollection, 'Area_Statistics');
  exportTableToDrive(outputs.accuracyAssessmentResult.summaryFeatureCollection, 'Accuracy_Statistics_Summary');
  exportTableToDrive(outputs.accuracyAssessmentResult.perClassFeatureCollection, 'Accuracy_Statistics_PerClass');
  exportTableToDrive(outputs.accuracyAssessmentResult.confusionMatrixFeatureCollection, 'Confusion_Matrix');
  exportTableToDrive(outputs.trainingSamples, 'Training_Samples');
  exportTableToDrive(outputs.validationSamples, 'Validation_Samples');

  print('=== EXPORTS QUEUED ===');
  print('11 export tasks were registered (4 rasters + 7 tables). ' +
      'Open the "Tasks" tab in the Code Editor and click "Run" on each to start them.');
}

exports.exportImageToDrive = exportImageToDrive;
exports.exportTableToDrive = exportTableToDrive;
exports.exportAllOutputs = exportAllOutputs;
