# Publication Documentation

## Title

Machine-Learning-Based Land Use Land Cover Mapping of the Cold-Desert Region
of Ladakh, India, using Sentinel-2 Level-2A Surface Reflectance, Multi-Source
Spectral-Textural-Terrain Predictors, and a Random Forest Classifier in
Google Earth Engine.

## Objectives

1. Produce a reproducible, seven-class LULC map of Ladakh (Water Bodies,
   Snow Cover, Rangeland, Residential, Vegetation, Agriculture, Wetlands) at
   10 m spatial resolution.
2. Assemble a comprehensive predictor stack — spectral bands, vegetation /
   water / snow / urban / bare-soil indices, terrain derivatives, GLCM
   texture, and optional PCA / Tasseled Cap components — appropriate for a
   topographically extreme, semi-arid, high-altitude environment.
3. Train and tune a Random Forest classifier (Breiman, 2001), quantify
   accuracy with a complete, transparent statistical suite (Congalton,
   1991; Stehman, 1997; Olofsson et al., 2014), and report variable
   importance (Belgiu & Dragut, 2016).
4. Generate publication-ready maps, figures, and tabular outputs with a
   fully seeded, reproducible workflow.

## Study Area

Ladakh, in the northernmost part of India, spans a cold-desert,
high-altitude environment (training points in this project range from
approximately 32.77°N–33.33°N and 77.79°E–79.01°E) characterised by
sparse vegetation confined to river valleys and irrigated oases, extensive
bare rangeland and rock, seasonal and perennial snow cover at elevation,
small settlements, and scattered wetlands. This combination of low
vegetation cover, strong topographic control on illumination and land
cover, and spectral similarity between bare soil, rangeland, and residential
rooftops motivates the broad, multi-source predictor stack used here
(spectral + terrain + texture) rather than spectral bands alone.

## Datasets

| Dataset | Earth Engine ID | Role |
|---|---|---|
| Sentinel-2 Level-2A Surface Reflectance | `COPERNICUS/S2_SR_HARMONIZED` | Primary optical imagery |
| Sentinel-2 Cloud Probability (s2cloudless) | `COPERNICUS/S2_CLOUD_PROBABILITY` | Per-pixel cloud masking |
| Copernicus DEM GLO-30 | `COPERNICUS/DEM/GLO30` | Terrain derivatives |
| Field/photo-interpreted training points | `data/Ladakh_LULC_Training_Points.csv` | Training and validation labels |

## Projection

EPSG:4326 (geographic, WGS 84).

## Spatial Resolution

10 m (native Sentinel-2 resolution for the bands used; Copernicus DEM
GLO-30 is resampled onto the same 10 m analysis grid during terrain-feature
computation).

## Temporal Resolution

A single composite per run, built from all Sentinel-2 scenes intersecting
the study area and date range that meet the cloud-cover criterion. The
default date range is a growing-season window (June–September 2025) and is
fully configurable in `00_Config.js` (`TEMPORAL.startDate` /
`TEMPORAL.endDate`).

## Predictor Variables

- **Original bands (10):** Blue, Green, Red, Red Edge 1/2/3, NIR, Narrow
  NIR, SWIR1, SWIR2.
- **Vegetation indices (9):** NDVI, EVI, SAVI, MSAVI, GNDVI, OSAVI, RVI,
  DVI, IPVI.
- **Water indices (5):** NDWI, MNDWI, AWEI, NDMI, LSWI.
- **Snow indices (2):** NDSI, Snow Ratio.
- **Urban indices (3):** NDBI, IBI, UI.
- **Bare-soil indices (3):** BSI, MBSI, BI.
- **Terrain variables (7):** Elevation, Slope, Aspect, Hillshade,
  Curvature, Topographic Position Index, Terrain Ruggedness Index.
- **GLCM texture (24):** Contrast, Entropy, Variance, Homogeneity, ASM,
  Correlation, Dissimilarity, and local Mean, each computed on NIR, Red,
  and SWIR1 (8 metrics x 3 bands).
- **Optional transforms:** Principal Component Analysis (top 3 components
  of the 10 original bands) and Sentinel-2 Tasseled Cap (Brightness,
  Greenness, Wetness; Nedkov, 2017 coefficients).

## Classification Method

Random Forest (Breiman, 2001), implemented as
`ee.Classifier.smileRandomForest` in Google Earth Engine. Random Forest is
widely adopted in remote-sensing land-cover classification for its
robustness to noisy and correlated predictors, resistance to overfitting
via bagging, and built-in variable-importance estimation (Belgiu & Dragut,
2016).

## Machine Learning Parameters

| Parameter | Value |
|---|---|
| Trees | 500 (default; automatically compared against 100/300/700/1000) |
| Variables per split | Automatic (Earth Engine default, ≈√(number of predictors)) |
| Minimum leaf population | 1 (Earth Engine default) |
| Bag fraction | 0.7 |
| Seed | 42 (fixed for full reproducibility) |

A tree-count sensitivity analysis is run automatically (100, 300, 500,
700, 1000 trees), overall accuracy is plotted against tree count, and the
smallest tree count within a configurable tolerance (default 0.005) of the
best observed accuracy is recommended and used for the final model — this
avoids recommending unnecessary model complexity for a marginal accuracy
gain.

## Training Sample Information

700 field/photo-interpreted points across the 7 LULC classes (Water Bodies:
141, Snow Cover: 59, Rangeland: 98, Residential: 101, Vegetation: 99,
Agriculture: 102, Wetlands: 100), each recorded as a `Name`, `latitude`,
`longitude` triplet. The class of each point is derived automatically and
solely from the first character of its `Name` field (e.g., `L12` →
Water Bodies), with no manual per-point editing. Samples are split 70%
training / 30% validation using a fixed random seed (42), so the same
split — and therefore the same trained model and reported accuracy — is
obtained on every re-run.

## Accuracy Assessment

Following Congalton (1991) and Stehman (1997) for standard thematic-
accuracy reporting, and Olofsson et al. (2014) for good-practice area and
accuracy estimation, this project reports:

- Confusion matrix (raw counts)
- Overall Accuracy, Cohen's Kappa
- Producer's Accuracy / User's Accuracy per class (equivalently, Recall /
  Precision)
- Precision, Recall, Sensitivity, Specificity per class
- F1 score per class, plus macro-averaged and support-weighted F1
- Balanced Accuracy (mean per-class recall)
- Matthews Correlation Coefficient (multiclass form, Gorodkin, 2004)
- Intersection-over-Union (IoU) per class

## Expected Outputs

- Classified LULC raster (GeoTIFF)
- Per-class probability raster (GeoTIFF)
- Confidence raster (probability of the winning class) and uncertainty
  raster (1 − confidence)
- Variable importance table and bar chart (top 20 highlighted)
- Per-class area statistics (pixel count, hectares, km², percentage)
- Training and validation sample tables, and validation diagnostic maps
  (correctly classified vs. misclassified points)
- Full accuracy-statistics and confusion-matrix CSVs

## Reproducibility Statement

Every stochastic step in this workflow — the train/validation split and
every Random Forest model trained (including all five models trained
during the tree-count sensitivity analysis) — uses a fixed random seed
(42). Given the same training asset, date range, and configuration,
re-running the workflow reproduces identical classifications and
statistics.

## References

- Breiman, L. (2001). Random forests. *Machine Learning*, 45(1), 5–32.
  https://doi.org/10.1023/A:1010933404324
- Belgiu, M., & Dragut, L. (2016). Random forest in remote sensing: A
  review of applications and future directions. *ISPRS Journal of
  Photogrammetry and Remote Sensing*, 114, 24–31.
  https://doi.org/10.1016/j.isprsjprs.2016.01.011
- Olofsson, P., Foody, G. M., Herold, M., Stehman, S. V., Woodcock, C. E.,
  & Wulder, M. A. (2014). Good practices for estimating area and assessing
  accuracy of land change. *Remote Sensing of Environment*, 148, 42–57.
  https://doi.org/10.1016/j.rse.2014.02.015
- Stehman, S. V. (1997). Selecting and interpreting measures of thematic
  classification accuracy. *Remote Sensing of Environment*, 62(1), 77–89.
  https://doi.org/10.1016/S0034-4257(97)00083-7
- Congalton, R. G. (1991). A review of assessing the accuracy of
  classifications of remotely sensed data. *Remote Sensing of
  Environment*, 37(1), 35–46.
  https://doi.org/10.1016/0034-4257(91)90048-B
- Gorodkin, J. (2004). Comparing two K-category assignments by a
  K-category correlation coefficient. *Computational Biology and
  Chemistry*, 28(5–6), 367–374.
  https://doi.org/10.1016/j.compbiolchem.2004.09.006
- Gorelick, N., Hancher, M., Dixon, M., Ilyushchenko, S., Thau, D., &
  Moore, R. (2017). Google Earth Engine: Planetary-scale geospatial
  analysis for everyone. *Remote Sensing of Environment*, 202, 18–27.
  https://doi.org/10.1016/j.rse.2017.06.031
- Google Earth Engine Developer Documentation.
  https://developers.google.com/earth-engine
- European Space Agency / Copernicus. Sentinel-2 User Guide.
  https://sentinels.copernicus.eu/web/sentinel/user-guides/sentinel-2-msi
- European Space Agency / Copernicus. Copernicus DEM Product Handbook.
  https://spacedata.copernicus.eu/collections/copernicus-digital-elevation-model
- Nedkov, R. (2017). Orthogonal transformation of segmented images from
  the satellite Sentinel-2. *Comptes Rendus de l'Academie Bulgare des
  Sciences*, 70(5), 687–692.
