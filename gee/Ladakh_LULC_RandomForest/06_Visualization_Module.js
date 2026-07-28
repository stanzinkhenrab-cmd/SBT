// ============================================================================
// MODULE 06 — VISUALIZATION MODULE
// Scope: Adds every publication-support map layer (classified LULC, false-
//        colour context composite, confidence/uncertainty, per-class
//        probability, validation diagnostics) plus a legend, a north-arrow
//        indicator, and an on-screen scale-bar indicator to the Code Editor
//        Map.
//
// IMPORTANT CARTOGRAPHIC NOTE
//   The Earth Engine Code Editor UI API does not provide a true, print-
//   accurate cartographic scale bar or north arrow widget (unlike a desktop
//   GIS layout view). The widgets built here are functional, on-screen,
//   approximate indicators for interactive use. For a final print/journal
//   figure, import the exported LULC GeoTIFF (see 05_Export_Module.js)
//   into QGIS/ArcGIS/Illustrator and add a precise scale bar, north arrow,
//   and layout grid there — this is standard practice even in fully
//   scripted remote-sensing workflows.
//
// Public API:
//   renderMap(outputs) — adds all layers/widgets to the global Map object.
// ============================================================================

var REPO = 'users/<your_username>/Ladakh_LULC_RandomForest';
var CONFIG = require(REPO + ':00_Config.js');

// ----------------------------------------------------------------------------
// LEGEND
// ----------------------------------------------------------------------------
function buildLegendPanel(classNames, classPalette, title) {
  var legend = ui.Panel({style: {position: 'bottom-right', padding: '8px 15px', backgroundColor: 'rgba(255,255,255,0.9)'}});
  legend.add(ui.Label(title, {fontWeight: 'bold', fontSize: '16px', margin: '0 0 6px 0'}));

  function makeRow(color, name) {
    var colorBox = ui.Label('', {
      backgroundColor: color, padding: '8px', margin: '0 6px 4px 0',
      border: '1px solid #888888'
    });
    var description = ui.Label(name, {margin: '0 0 4px 6px'});
    return ui.Panel({
      widgets: [colorBox, description],
      layout: ui.Panel.Layout.flow('horizontal')
    });
  }

  classNames.forEach(function(name, i) { legend.add(makeRow(classPalette[i], name)); });
  return legend;
}

// ----------------------------------------------------------------------------
// NORTH ARROW AND ON-SCREEN SCALE INDICATOR
// ----------------------------------------------------------------------------
function addNorthArrowWidget() {
  var northArrowPanel = ui.Panel({
    widgets: [
      ui.Label('▲', {fontSize: '20px', margin: '0px', textAlign: 'center', stretch: 'horizontal'}),
      ui.Label('N', {fontWeight: 'bold', fontSize: '14px', margin: '0px', textAlign: 'center', stretch: 'horizontal'})
    ],
    layout: ui.Panel.Layout.flow('vertical'),
    style: {position: 'top-right', padding: '6px', backgroundColor: 'rgba(255,255,255,0.85)'}
  });
  Map.add(northArrowPanel);
}

/** Adds an approximate, zoom-reactive on-screen scale label (see cartographic note above). */
function addScaleIndicatorWidget(referenceLatitudeDegrees) {
  var scaleLabel = ui.Label('', {fontWeight: 'bold', backgroundColor: 'rgba(255,255,255,0.85)', padding: '4px 8px'});
  var scaleBarPanel = ui.Panel({
    widgets: [scaleLabel],
    layout: ui.Panel.Layout.flow('horizontal'),
    style: {position: 'bottom-left'}
  });
  Map.add(scaleBarPanel);

  var referenceBarPixelWidth = 100;

  function refreshScaleLabel() {
    var zoom = Map.getZoom();
    var metersPerPixel = 156543.03392 * Math.cos(referenceLatitudeDegrees * Math.PI / 180) / Math.pow(2, zoom);
    var groundDistanceMeters = metersPerPixel * referenceBarPixelWidth;
    var distanceLabel = groundDistanceMeters >= 1000
        ? (groundDistanceMeters / 1000).toFixed(1) + ' km'
        : groundDistanceMeters.toFixed(0) + ' m';
    scaleLabel.setValue('↔ approx. ' + distanceLabel + ' (' + referenceBarPixelWidth + ' px on screen)');
  }

  Map.onZoomChange(refreshScaleLabel);
  refreshScaleLabel();
}

// ----------------------------------------------------------------------------
// VALIDATION DIAGNOSTIC LAYERS
// ----------------------------------------------------------------------------

/** Styles a point FeatureCollection by its numeric class property using CLASS_PALETTE. */
function styleByClass(featureCollection) {
  var paletteList = ee.List(CONFIG.CLASS_PALETTE);
  var styled = featureCollection.map(function(feature) {
    var classId = feature.getNumber(CONFIG.CLASS_PROPERTY);
    var color = paletteList.get(classId);
    return feature.set('style', {color: color, pointSize: 5, pointShape: 'circle', width: 1});
  });
  return styled.style({styleProperty: 'style'});
}

/** Splits classified validation points into correct/misclassified subsets and styles them green/red. */
function buildCorrectnessLayers(validationWithPredictions) {
  var withCorrectness = validationWithPredictions.map(function(feature) {
    var isCorrect = ee.Number(feature.get(CONFIG.CLASS_PROPERTY)).eq(feature.get('classification'));
    return feature.set('is_correct', isCorrect);
  });

  var correct = withCorrectness.filter(ee.Filter.eq('is_correct', 1))
      .map(function(f) { return f.set('style', {color: '#00CC00', pointSize: 5, pointShape: 'circle'}); })
      .style({styleProperty: 'style'});

  var misclassified = withCorrectness.filter(ee.Filter.eq('is_correct', 0))
      .map(function(f) { return f.set('style', {color: '#FF00FF', pointSize: 6, pointShape: 'diamond'}); })
      .style({styleProperty: 'style'});

  return {correct: correct, misclassified: misclassified};
}

// ----------------------------------------------------------------------------
// MAIN ENTRY POINT
// ----------------------------------------------------------------------------

/**
 * Adds every map layer and cartographic widget for the project.
 * `outputs` must contain: sentinel2Composite, lulcClassified, confidenceImage,
 * uncertaintyImage, classProbabilityImage, trainingSamples, validationSamples,
 * validationWithPredictions, studyArea.
 */
function renderMap(outputs) {
  // --- Context layers -------------------------------------------------------
  Map.addLayer(outputs.studyArea, {color: '#000000'}, 'Study Area Boundary', false);
  Map.addLayer(outputs.sentinel2Composite, {
    bands: CONFIG.VISUALIZATION.rgbVisBands,
    min: CONFIG.VISUALIZATION.rgbVisMinMax.min,
    max: CONFIG.VISUALIZATION.rgbVisMinMax.max
  }, 'Sentinel-2 False Colour (SWIR1-NIR-Red)', false);

  // --- Classification result --------------------------------------------
  Map.addLayer(outputs.lulcClassified, {
    min: 0, max: CONFIG.NUM_CLASSES - 1, palette: CONFIG.CLASS_PALETTE
  }, 'LULC Classification');

  // --- Probability / confidence / uncertainty ------------------------------
  CONFIG.CLASS_NAMES.forEach(function(name, i) {
    Map.addLayer(outputs.classProbabilityImage.select('prob_' + CONFIG.CLASS_IDS[i]),
        {min: 0, max: 1, palette: CONFIG.VISUALIZATION.confidenceVisPalette},
        'Class Probability - ' + name, false);
  });
  Map.addLayer(outputs.confidenceImage, {min: 0, max: 1, palette: CONFIG.VISUALIZATION.confidenceVisPalette},
      'Prediction Confidence', false);
  Map.addLayer(outputs.uncertaintyImage, {min: 0, max: 1, palette: CONFIG.VISUALIZATION.confidenceVisPalette},
      'Prediction Uncertainty', false);

  // --- Validation / diagnostic layers ---------------------------------------
  Map.addLayer(styleByClass(outputs.trainingSamples), {}, 'Training Points (by class)', false);
  Map.addLayer(styleByClass(outputs.validationSamples), {}, 'Validation Points (by class)', false);

  var correctness = buildCorrectnessLayers(outputs.validationWithPredictions);
  Map.addLayer(correctness.correct, {}, 'Validation - Correctly Classified', false);
  Map.addLayer(correctness.misclassified, {}, 'Validation - Misclassified', false);

  // --- Cartographic widgets -------------------------------------------------
  Map.add(buildLegendPanel(CONFIG.CLASS_NAMES, CONFIG.CLASS_PALETTE, CONFIG.VISUALIZATION.legendTitle));
  addNorthArrowWidget();

  var referenceLatitude = outputs.studyArea.centroid({maxError: 100}).coordinates().get(1).getInfo();
  addScaleIndicatorWidget(referenceLatitude);
}

exports.renderMap = renderMap;
exports.buildLegendPanel = buildLegendPanel;
exports.styleByClass = styleByClass;
exports.buildCorrectnessLayers = buildCorrectnessLayers;
