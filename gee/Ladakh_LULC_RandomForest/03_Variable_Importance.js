// ============================================================================
// MODULE 03 — VARIABLE IMPORTANCE
// Scope: Extracts Random Forest variable (predictor) importance
//        (mean decrease in impurity, as returned by
//        ee.Classifier.explain()), ranks it, builds a bar chart, and
//        highlights the top 20 predictors (Belgiu & Dragut, 2016).
//
// Public API:
//   computeVariableImportance(trainedClassifier) -> {
//     importanceDictionary: ee.Dictionary (raw classifier.explain() output)
//     featureCollection:    ee.FeatureCollection, all variables, ranked descending
//     top20FeatureCollection: ee.FeatureCollection, top 20 variables
//     chart: ui.Chart, bar chart of the top 20 variables
//   }
// ============================================================================

/** Adds a sequential 'rank' property (1 = most important) to an already-sorted FeatureCollection. */
function addRankProperty(sortedFeatureCollection) {
  var count = sortedFeatureCollection.size();
  var featureList = sortedFeatureCollection.toList(count);
  var indices = ee.List.sequence(0, count.subtract(1));
  var ranked = indices.map(function(i) {
    var feature = ee.Feature(featureList.get(i));
    return feature.set('rank', ee.Number(i).add(1));
  });
  return ee.FeatureCollection(ranked);
}

/**
 * Computes, ranks, charts, and prints Random Forest variable importance for
 * an already-trained classifier.
 */
function computeVariableImportance(trainedClassifier) {
  var explanation = trainedClassifier.explain();
  var importanceDictionary = ee.Dictionary(explanation.get('importance'));
  var variableNames = importanceDictionary.keys();

  var importanceFeatures = variableNames.map(function(name) {
    return ee.Feature(null, {
      variable: name,
      importance: importanceDictionary.get(name)
    });
  });

  var sortedFeatureCollection = ee.FeatureCollection(importanceFeatures).sort('importance', false);
  var rankedFeatureCollection = addRankProperty(sortedFeatureCollection);
  var top20FeatureCollection = rankedFeatureCollection.limit(20);

  var chart = ui.Chart.feature.byFeature(top20FeatureCollection, 'variable', 'importance')
      .setChartType('ColumnChart')
      .setOptions({
        title: 'Random Forest Variable Importance — Top 20 Predictors',
        hAxis: {title: 'Predictor Variable', slantedText: true, slantedTextAngle: 60},
        vAxis: {title: 'Importance (Mean Decrease in Impurity)'},
        legend: {position: 'none'},
        colors: ['#228B22']
      });

  print(chart);
  print('Variable importance — full ranked table:', rankedFeatureCollection);
  print('Variable importance — top 20 predictors:', top20FeatureCollection);

  return {
    importanceDictionary: importanceDictionary,
    featureCollection: rankedFeatureCollection,
    top20FeatureCollection: top20FeatureCollection,
    chart: chart
  };
}

exports.computeVariableImportance = computeVariableImportance;
