// ============================================================================
// MODULE 02 — ACCURACY ASSESSMENT
// Scope: Confusion matrix construction and the full suite of classification
//        accuracy metrics used in the project (Congalton, 1991; Stehman,
//        1997; Olofsson et al., 2014). All derived per-class and aggregate
//        metrics are computed from a single confusion-matrix fetch so the
//        numbers are guaranteed to be mutually consistent.
//
// Public API:
//   assessAccuracy(validatedFeatures, actualProperty, predictedProperty,
//                   classNames, classIds) -> {
//     confusionMatrix:            ee.ConfusionMatrix (raw EE object, for printing/inspection)
//     confusionMatrixFeatureCollection: ee.FeatureCollection (one feature per row, ready for CSV export)
//     summaryFeatureCollection:   ee.FeatureCollection (single feature, overall metrics)
//     perClassFeatureCollection:  ee.FeatureCollection (one feature per class, per-class metrics)
//     metrics: plain JS object with every computed value (see computeMetricsFromMatrix)
//   }
// ============================================================================

/**
 * Builds an ee.ConfusionMatrix ordered consistently with classIds, then
 * performs one small synchronous fetch of the raw NxN count matrix. All
 * downstream statistics (Kappa, precision/recall/F1, balanced accuracy,
 * specificity, MCC, per-class IoU) are derived from that single fetch so
 * every reported number is mutually consistent. The confusion matrix is
 * intentionally small (NUM_CLASSES x NUM_CLASSES), so this synchronous call
 * is inexpensive and standard practice for final accuracy reporting.
 */
function assessAccuracy(validatedFeatures, actualProperty, predictedProperty, classNames, classIds) {
  var confusionMatrix = validatedFeatures.errorMatrix(actualProperty, predictedProperty, classIds);
  var rawMatrix = confusionMatrix.array().getInfo(); // NUM_CLASSES x NUM_CLASSES nested array

  var metrics = computeMetricsFromMatrix(rawMatrix, classNames);

  return {
    confusionMatrix: confusionMatrix,
    confusionMatrixFeatureCollection: buildConfusionMatrixFeatureCollection(rawMatrix, classNames),
    summaryFeatureCollection: buildSummaryFeatureCollection(metrics),
    perClassFeatureCollection: buildPerClassFeatureCollection(metrics, classNames),
    metrics: metrics
  };
}

/**
 * Computes every accuracy statistic requested by the project specification
 * from a raw NxN confusion-matrix count array (rows = actual/reference
 * class, columns = predicted class), following Congalton (1991) and
 * Stehman (1997) for the standard measures, and Gorodkin (2004) for the
 * multiclass Matthews Correlation Coefficient.
 */
function computeMetricsFromMatrix(matrix, classNames) {
  var numClasses = matrix.length;
  var rowSums = matrix.map(function(row) { return row.reduce(function(a, b) { return a + b; }, 0); });
  var colSums = [];
  for (var c = 0; c < numClasses; c++) {
    var sum = 0;
    for (var r = 0; r < numClasses; r++) { sum += matrix[r][c]; }
    colSums.push(sum);
  }
  var total = rowSums.reduce(function(a, b) { return a + b; }, 0);
  var diagonalSum = 0;
  for (var i = 0; i < numClasses; i++) { diagonalSum += matrix[i][i]; }

  // --- Overall accuracy and Cohen's Kappa -----------------------------------
  var overallAccuracy = safeDivide(diagonalSum, total);
  var chanceAgreement = 0;
  for (var k = 0; k < numClasses; k++) { chanceAgreement += rowSums[k] * colSums[k]; }
  chanceAgreement = safeDivide(chanceAgreement, total * total);
  var kappa = safeDivide(overallAccuracy - chanceAgreement, 1 - chanceAgreement);

  // --- Multiclass Matthews Correlation Coefficient (Gorodkin, 2004) --------
  var sumRowSq = rowSums.reduce(function(a, b) { return a + b * b; }, 0);
  var sumColSq = colSums.reduce(function(a, b) { return a + b * b; }, 0);
  var sumRowColProd = 0;
  for (var m = 0; m < numClasses; m++) { sumRowColProd += rowSums[m] * colSums[m]; }
  var mccNumerator = (total * diagonalSum) - sumRowColProd;
  var mccDenominator = Math.sqrt((total * total - sumColSq) * (total * total - sumRowSq));
  var matthewsCorrelationCoefficient = safeDivide(mccNumerator, mccDenominator);

  // --- Per-class metrics -----------------------------------------------------
  var perClass = [];
  for (var k2 = 0; k2 < numClasses; k2++) {
    var truePositive = matrix[k2][k2];
    var falseNegative = rowSums[k2] - truePositive;   // actual k2, predicted other
    var falsePositive = colSums[k2] - truePositive;   // predicted k2, actual other
    var trueNegative = total - truePositive - falseNegative - falsePositive;

    var precision = safeDivide(truePositive, truePositive + falsePositive);   // User's accuracy
    var recall = safeDivide(truePositive, truePositive + falseNegative);     // Producer's accuracy / Sensitivity
    var specificity = safeDivide(trueNegative, trueNegative + falsePositive);
    var f1 = safeDivide(2 * precision * recall, precision + recall);
    var iou = safeDivide(truePositive, truePositive + falsePositive + falseNegative);

    perClass.push({
      className: classNames[k2],
      support: rowSums[k2],
      truePositive: truePositive,
      falsePositive: falsePositive,
      falseNegative: falseNegative,
      trueNegative: trueNegative,
      producerAccuracy: recall,
      userAccuracy: precision,
      precision: precision,
      recall: recall,
      sensitivity: recall,
      specificity: specificity,
      f1Score: f1,
      iou: iou
    });
  }

  // --- Aggregate precision/recall/F1 ----------------------------------------
  var macroF1 = average(perClass.map(function(c) { return c.f1Score; }));
  var weightedF1 = safeDivide(
      perClass.reduce(function(sum, c) { return sum + c.f1Score * c.support; }, 0), total);
  var balancedAccuracy = average(perClass.map(function(c) { return c.recall; })); // mean of per-class recall
  var macroPrecision = average(perClass.map(function(c) { return c.precision; }));
  var macroRecall = average(perClass.map(function(c) { return c.recall; }));
  var macroSpecificity = average(perClass.map(function(c) { return c.specificity; }));
  var macroSensitivity = macroRecall;

  return {
    numClasses: numClasses,
    totalSamples: total,
    overallAccuracy: overallAccuracy,
    kappa: kappa,
    matthewsCorrelationCoefficient: matthewsCorrelationCoefficient,
    macroPrecision: macroPrecision,
    macroRecall: macroRecall,
    macroF1: macroF1,
    weightedF1: weightedF1,
    balancedAccuracy: balancedAccuracy,
    macroSpecificity: macroSpecificity,
    macroSensitivity: macroSensitivity,
    perClass: perClass,
    rawMatrix: matrix
  };
}

function safeDivide(numerator, denominator) {
  return denominator === 0 ? 0 : numerator / denominator;
}

function average(values) {
  return values.length === 0 ? 0 : values.reduce(function(a, b) { return a + b; }, 0) / values.length;
}

/** One ee.Feature per confusion-matrix row, plus a 'Reference' label column, ready for CSV export. */
function buildConfusionMatrixFeatureCollection(matrix, classNames) {
  var rows = matrix.map(function(row, rowIndex) {
    var properties = {Reference: classNames[rowIndex]};
    row.forEach(function(count, colIndex) {
      properties['Predicted_' + classNames[colIndex]] = count;
    });
    return ee.Feature(null, properties);
  });
  return ee.FeatureCollection(rows);
}

/** Single-feature summary of all aggregate (non-per-class) accuracy metrics. */
function buildSummaryFeatureCollection(metrics) {
  return ee.FeatureCollection([ee.Feature(null, {
    total_samples: metrics.totalSamples,
    overall_accuracy: metrics.overallAccuracy,
    kappa: metrics.kappa,
    matthews_correlation_coefficient: metrics.matthewsCorrelationCoefficient,
    macro_precision: metrics.macroPrecision,
    macro_recall: metrics.macroRecall,
    macro_f1: metrics.macroF1,
    weighted_f1: metrics.weightedF1,
    balanced_accuracy: metrics.balancedAccuracy,
    macro_specificity: metrics.macroSpecificity,
    macro_sensitivity: metrics.macroSensitivity
  })]);
}

/** One ee.Feature per class, with every per-class metric, ready for CSV export. */
function buildPerClassFeatureCollection(metrics, classNames) {
  var features = metrics.perClass.map(function(c) {
    return ee.Feature(null, {
      class_name: c.className,
      support: c.support,
      true_positive: c.truePositive,
      false_positive: c.falsePositive,
      false_negative: c.falseNegative,
      true_negative: c.trueNegative,
      producer_accuracy: c.producerAccuracy,
      user_accuracy: c.userAccuracy,
      precision: c.precision,
      recall: c.recall,
      sensitivity: c.sensitivity,
      specificity: c.specificity,
      f1_score: c.f1Score,
      iou: c.iou
    });
  });
  return ee.FeatureCollection(features);
}

/** Prints a human-readable accuracy report to the Console. */
function printAccuracyReport(assessmentResult) {
  var m = assessmentResult.metrics;
  print('=== ACCURACY ASSESSMENT SUMMARY ===');
  print('Total validation samples: ' + m.totalSamples);
  print('Overall Accuracy: ' + m.overallAccuracy.toFixed(4));
  print('Kappa: ' + m.kappa.toFixed(4));
  print('Matthews Correlation Coefficient: ' + m.matthewsCorrelationCoefficient.toFixed(4));
  print('Macro F1: ' + m.macroF1.toFixed(4) + '   Weighted F1: ' + m.weightedF1.toFixed(4));
  print('Balanced Accuracy: ' + m.balancedAccuracy.toFixed(4));
  print('Per-class metrics:', assessmentResult.perClassFeatureCollection);
  print('Confusion matrix (raw):', assessmentResult.confusionMatrix);
}

exports.assessAccuracy = assessAccuracy;
exports.computeMetricsFromMatrix = computeMetricsFromMatrix;
exports.printAccuracyReport = printAccuracyReport;
