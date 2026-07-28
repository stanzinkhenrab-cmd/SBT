# Ladakh LULC Random Forest Classification

A publication-ready Google Earth Engine (JavaScript) project that classifies
Land Use Land Cover (LULC) across Ladakh, India, into seven classes using
Sentinel-2 Level-2A surface reflectance, a broad multi-source predictor
stack, and a Random Forest classifier.

| | |
|---|---|
| Classes | Water Bodies, Snow Cover, Rangeland, Residential, Vegetation, Agriculture, Wetlands |
| Imagery | Sentinel-2 L2A (`COPERNICUS/S2_SR_HARMONIZED`), s2cloudless cloud probability |
| Terrain | Copernicus DEM GLO-30 (`COPERNICUS/DEM/GLO30`) |
| Resolution | 10 m, EPSG:4326 |
| Classifier | Random Forest (`ee.Classifier.smileRandomForest`) |
| Training data | 700 field/photo-interpreted points, `data/Ladakh_LULC_Training_Points.csv` |

See `docs/Publication_Documentation.md` for the full scientific write-up
(objectives, methods, references) and `docs/Workflow_Diagram.md` /
`docs/Methodology_Flowchart.md` for diagrams.

## File map

```
gee/Ladakh_LULC_RandomForest/
  00_Config.js                Parameters and shared utility functions (EDIT THIS FIRST)
  01_Main_Script.js            Orchestrates the full workflow (run this)
  02_Accuracy_Assessment.js    Confusion matrix + full accuracy-metric suite
  03_Variable_Importance.js    Variable importance table + chart
  04_Area_Statistics.js        Per-class pixel/area/percentage statistics
  05_Export_Module.js          All Export.image / Export.table calls
  06_Visualization_Module.js   Map layers, legend, north arrow, scale indicator, validation maps
  docs/
    Workflow_Diagram.md        High-level data/process flow (Mermaid)
    Methodology_Flowchart.md   Step-by-step methodology flowchart (Mermaid)
    Publication_Documentation.md  Full scientific documentation + references
data/
  Ladakh_LULC_Training_Points.csv  700 training/validation points (Name, latitude, longitude)
```

## One-time setup

1. **Create a GEE script repository.** In the Code Editor, open the
   **Scripts** tab → **NEW** → **Repository**, and name it, e.g.,
   `Ladakh_LULC_RandomForest`. This gives you a path such as
   `users/<your_username>/Ladakh_LULC_RandomForest`.
2. **Copy the six `.js` files** from this folder into that repository,
   keeping the same file names (`00_Config.js` … `06_Visualization_Module.js`).
3. **Replace every `<your_username>`** placeholder with your actual GEE
   username/repo owner. It appears:
   - Once in `00_Config.js` (`PROJECT.repositoryPlaceholder`, informational only)
   - Once in `01_Main_Script.js` (`var REPO = ...`)
   - Once in each of `05_Export_Module.js` and `06_Visualization_Module.js` (`var REPO = ...`)
4. **Upload the training data as a Table asset**:
   - **Assets** tab → **NEW** → **Table Upload** → **CSV file** →
     select `data/Ladakh_LULC_Training_Points.csv`.
   - When prompted, set the **Latitude field** to `latitude` and the
     **Longitude field** to `longitude` so Earth Engine builds point
     geometries automatically. (The scripts also rebuild geometry from
     these two columns directly, so this step is a safety net, not a hard
     requirement.)
   - Note the resulting asset ID (e.g.
     `users/<your_username>/Ladakh_LULC_Training_Points`) and paste it into
     `TRAINING.assetId` in `00_Config.js`.
5. **Open `01_Main_Script.js` and click Run.** All console output (charts,
   printed tables, accuracy report), Map layers, and export tasks appear as
   the script executes. Export tasks must be started manually from the
   **Tasks** tab — Earth Engine never auto-runs exports.

## Parameter reference (`00_Config.js`)

| Section | Parameter | Meaning |
|---|---|---|
| PROJECT | `title`, `version`, `author` | Metadata only, no effect on processing |
| STUDY_AREA | `bufferMeters` | Buffer (m) added around the training-point bounding box to define the classification/export extent |
| STUDY_AREA | `fallbackRectangle` | Used only if the training FeatureCollection is empty |
| TEMPORAL | `startDate`, `endDate` | Sentinel-2 compositing window (any calendar range) |
| TEMPORAL | `compositeMethod` | `'median'`, `'mean'`, or `'medoid'` |
| SENTINEL2 | `srCollectionId`, `cloudProbCollectionId` | Source collection IDs |
| SENTINEL2 | `maxCloudCoverPercent` | Scene-level `CLOUDY_PIXEL_PERCENTAGE` filter (default 10) |
| SENTINEL2 | `cloudProbThreshold` | Per-pixel s2cloudless probability (%) above which pixels are masked (default 40) |
| SENTINEL2 | `scale`, `crs` | Output resolution (10 m) and projection (EPSG:4326) |
| CLASS_PREFIX_MAP | `L/S/P/R/V/A/M` | Name-prefix → numeric class ID mapping. **This is the only place to edit if class codes change.** |
| CLASS_NAMES / CLASS_PALETTE | — | Display names and hex colours, in class-ID order |
| TRAINING | `assetId` | GEE Table asset path for the uploaded CSV |
| TRAINING | `nameField`, `latField`, `lonField` | Column names in the uploaded CSV |
| TRAINING | `splitFraction`, `splitSeed` | Train/validation split ratio and fixed seed (reproducibility) |
| TERRAIN | `demCollectionId`, `demBand` | Copernicus DEM GLO-30 source |
| TERRAIN | `tpiRadiusPixels`, `triRadiusPixels` | Neighbourhood radii for TPI / TRI |
| TEXTURE | `sourceBands` | Which bands (`nir`, `red`, `swir1`) get GLCM texture |
| TEXTURE | `glcmSize` | GLCM window radius, in pixels |
| TEXTURE | `quantizationLevels` | Integer levels used to rescale reflectance before GLCM (required by `glcmTexture`) |
| OPTIONAL_TRANSFORMS | `enablePCA`, `pcaNumComponents` | Toggle/size Principal Component Analysis |
| OPTIONAL_TRANSFORMS | `enableTasseledCap` | Toggle the Sentinel-2 Tasseled Cap transform |
| RANDOM_FOREST | `numberOfTrees` | Default tree count if hyperparameter search is disabled |
| RANDOM_FOREST | `variablesPerSplit` | `null` = Earth Engine automatic (~sqrt of predictor count) |
| RANDOM_FOREST | `minLeafPopulation`, `bagFraction`, `seed` | Standard RF hyperparameters |
| HYPERPARAMETER_SEARCH | `enabled`, `treeOptions` | Whether/what tree counts to compare (100/300/500/700/1000) |
| HYPERPARAMETER_SEARCH | `autoSelectOptimalTrees`, `accuracyTolerance` | Auto-picks the smallest tree count within `accuracyTolerance` of the best observed accuracy |
| EXPORT | `driveFolder`, `filePrefix`, `maxPixels`, `scale`, `crs` | Export destination/format defaults |
| VISUALIZATION | `mapCenterZoom`, `legendTitle`, `confidenceVisPalette`, `rgbVisBands`, `rgbVisMinMax` | Map display defaults |

## Expected outputs

- **Console**: overall-accuracy-vs-trees chart, hyperparameter recommendation,
  variable-importance chart and ranked table, full accuracy report
  (confusion matrix, OA, kappa, MCC, precision/recall/F1 macro & weighted,
  balanced accuracy, specificity, per-class IoU), area statistics table.
- **Map**: classified LULC layer, false-colour context composite, per-class
  probability layers, confidence and uncertainty layers, training/validation
  points by class, correctly-classified vs misclassified validation points,
  legend, north-arrow and on-screen scale indicators.
- **Tasks tab** (after manually starting each task): LULC GeoTIFF,
  per-class probability GeoTIFF, confidence GeoTIFF, uncertainty GeoTIFF,
  variable importance CSV, area statistics CSV, accuracy summary CSV,
  per-class accuracy CSV, confusion matrix CSV, training samples CSV,
  validation samples CSV.

## Reproducibility

Every stochastic step is seeded: the train/validation split
(`TRAINING.splitSeed = 42`) and the Random Forest itself
(`RANDOM_FOREST.seed = 42`), including every model trained during the
tree-count hyperparameter search. Re-running the script against the same
training asset and date range reproduces identical results, class
predictions, and reported statistics.

## Known limitations / honest caveats

- The Earth Engine Code Editor UI has no native, print-accurate scale bar
  or north-arrow widget; `06_Visualization_Module.js` provides functional,
  on-screen approximations and the code comments recommend finishing
  cartography (precise scale bar, north arrow, layout) in QGIS/ArcGIS from
  the exported GeoTIFFs, as is standard practice.
- PCA is computed with `bestEffort: true` region statistics for
  practicality over a study area of this size; for a final production run
  you may wish to disable `bestEffort` and instead tile the computation if
  you need exact (non-approximated) covariance statistics.
- Curvature and Terrain Ruggedness Index are computed with standard,
  widely used raster-neighbourhood approximations (a discrete Laplacian
  kernel, and mean absolute elevation difference respectively) rather than
  a dedicated geomorphometric package, since Earth Engine does not ship one
  natively.
