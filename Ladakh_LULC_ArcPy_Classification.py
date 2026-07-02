# ==============================================================================
# ARCGIS PRO (ARCPY) SUPERVISED LULC CLASSIFICATION WORKFLOW
# Region: Nubra Valley, Ladakh, India (Cold-Arid Himalayan Riverine Ecosystem)
# Classes (from nubra_lulc_training_points.csv):
#   1 = Water | 2 = Vegetation | 3 = Seabuck Thorn | 4 = Sand
#   5 = Snow  | 6 = Agricultural Field | 7 = Barren Land
# ==============================================================================

import arcpy
from arcpy.sa import *
import os

# Check out required extensions
arcpy.CheckOutExtension("Spatial")
arcpy.CheckOutExtension("ImageAnalyst")

# ==============================================================================
# WORKSPACE & DATA INPUTS  (EDIT THESE to match your project)
# ==============================================================================
# Auto-detects the geodatabase of the currently open ArcGIS Pro project.
# Replace with an explicit path if running outside Pro (e.g. r"C:\...\nubra.gdb").
aprx = arcpy.mp.ArcGISProject("CURRENT")
arcpy.env.workspace = aprx.defaultGeodatabase
arcpy.env.overwriteOutput = True

# Rasters already loaded in your map (from the Contents pane)
dem_path = "SRTM_Nubra_DEM.tif"
s2_raster_path = "S2_Median_Composite_Nubra.tif"

# Raw ground-truth CSV (Class, Latitude, Longitude columns)
training_csv = r"C:\Users\admin\Documents\ArcGIS\Projects\nubra\nubra_lulc_training_points.csv"

# Output names
training_points_raw = "Training_Points_WGS84"
training_points = "Training_Points"
output_rf_model = "RF_Model.ecd"
output_classified = "Classified_LULC"
output_refined = "Refined_LULC"

# Class name -> numeric ClassID used to train the classifier
CLASS_MAP = {
    "Water": 1,
    "Vegetation": 2,
    "Seabuck Thorn": 3,
    "Sand": 4,
    "Snow": 5,
    "Agricultural Field": 6,
    "Barren Land": 7,
}

print("Starting Nubra LULC Workflow...")

# ==============================================================================
# 1. STUDY AREA
# ==============================================================================
# The DEM and Sentinel-2 composite are already clipped to the Nubra study area,
# so processing extent/mask/snap can be read straight from the DEM instead of
# building a separate ROI polygon (env.extent/env.mask only accept dataset
# paths or Extent objects, never a raw in-memory geometry).
arcpy.env.extent = dem_path
arcpy.env.mask = dem_path
arcpy.env.snapRaster = dem_path
arcpy.env.cellSize = dem_path

# ==============================================================================
# 2. GROUND TRUTH DATA PREPARATION
# ==============================================================================
print("Preparing Training Data...")

# Build points from the CSV (lat/long in WGS84) ...
arcpy.management.XYTableToPoint(
    training_csv, training_points_raw, "Longitude", "Latitude", None,
    arcpy.SpatialReference(4326)
)

# ... then reproject to match the imagery so distances/pixels line up correctly.
s2_sr = arcpy.Describe(s2_raster_path).spatialReference
arcpy.management.Project(training_points_raw, training_points, s2_sr)

# Convert the text "Class" field to a numeric "ClassID" field for the classifier
arcpy.management.AddField(training_points, "ClassID", "SHORT")
class_code_block = f"""
class_map = {CLASS_MAP!r}
def get_class_id(cls):
    return class_map.get(cls, 0)
"""
arcpy.management.CalculateField(
    training_points, "ClassID", "get_class_id(!Class!)", "PYTHON3", class_code_block
)

# Create Train/Test Split (70/30)
arcpy.management.AddField(training_points, "Split_Rand", "DOUBLE")
arcpy.management.CalculateField(
    training_points, "Split_Rand", "random.random()", "PYTHON3", "import random"
)

train_data = "Training_Data_70"
test_data = "Validation_Data_30"
arcpy.analysis.Select(training_points, train_data, "Split_Rand <= 0.7")
arcpy.analysis.Select(training_points, test_data, "Split_Rand > 0.7")

# ==============================================================================
# 3. SPECTRAL PREDICTORS AND INDICES
# ==============================================================================
print("Calculating Spectral Indices...")

# Band mapping for the 7-band Nubra composite (named after their source S2 band)
b3 = Raster(s2_raster_path + "/Band_2")   # Green (B3)
b4 = Raster(s2_raster_path + "/Band_3")   # Red   (B4)
b8 = Raster(s2_raster_path + "/Band_7")   # NIR   (B8)
b11 = Raster(s2_raster_path + "/Band_8")  # SWIR1 (B11)

ndvi = Float(b8 - b4) / Float(b8 + b4)
mndwi = Float(b3 - b11) / Float(b3 + b11)     # Water index
ndsi = Float(b3 - b11) / Float(b3 + b11)      # Snow index (same bands, different use/threshold)
savi = (Float(b8 - b4) / Float(b8 + b4 + 0.5)) * 1.5

ndvi.save("temp_ndvi")
mndwi.save("temp_mndwi")
ndsi.save("temp_ndsi")
savi.save("temp_savi")

# ==============================================================================
# 4. TERRAIN VARIABLES
# ==============================================================================
print("Generating Terrain Variables...")
dem = Raster(dem_path)
# Slope/Aspect via Spatial Analyst (avoids needing a separate 3D Analyst license,
# which SurfaceParameters requires).
slope = Slope(dem, "DEGREE")
focal_min = FocalStatistics(dem, NbrRectangle(3, 3, "CELL"), "MINIMUM")
focal_max = FocalStatistics(dem, NbrRectangle(3, 3, "CELL"), "MAXIMUM")
tri = focal_max - focal_min  # Terrain ruggedness proxy

slope.save("temp_slope")
tri.save("temp_tri")

# ==============================================================================
# 5. FEATURE STACK
# ==============================================================================
print("Building Predictor Stack...")
predictor_stack = "Predictor_Stack"
arcpy.management.CompositeBands(
    [s2_raster_path, "temp_ndvi", "temp_mndwi", "temp_savi", "temp_ndsi",
     dem_path, "temp_slope", "temp_tri"],
    predictor_stack
)

# ==============================================================================
# 6. RANDOM FOREST CLASSIFICATION
# ==============================================================================
print("Training Random Forest Classifier...")
arcpy.ia.TrainRandomTreesClassifier(
    in_raster=predictor_stack,
    in_training_features=train_data,
    out_classifier_definition=output_rf_model,
    in_class_data_field="ClassID",
    max_num_trees=100,
    max_tree_depth=30,
    max_samples_per_class=150
)

print("Applying Classification...")
classified_raster = arcpy.ia.ClassifyRaster(
    in_raster=predictor_stack,
    in_classifier_definition=output_rf_model
)
classified_raster.save(output_classified)

# ==============================================================================
# 7. ECOLOGICAL RULE-BASED REFINEMENT
# ==============================================================================
# Thresholds below are starting points - tune them against your own scatterplots
# of NDVI/MNDWI/NDSI vs. elevation before relying on the refined output.
print("Applying Ecological Refinements...")

c_ras = Raster(output_classified)
dem_ras = Raster(dem_path)
slope_ras = Raster("temp_slope")
ndvi_ras = Raster("temp_ndvi")
mndwi_ras = Raster("temp_mndwi")
ndsi_ras = Raster("temp_ndsi")

# Seabuckthorn (3): riparian shrub belt, mid-elevation, gentle slope, vegetated
sb_mask = (dem_ras >= 2800) & (dem_ras <= 4000) & (slope_ras <= 25) & (ndvi_ras >= 0.15)

# Water (1): low-lying, flat, wet spectral signature
water_mask = (dem_ras <= 4500) & (slope_ras <= 15) & (mndwi_ras >= -0.10)

# Snow (5): high elevation with a strong snow index
snow_mask = (dem_ras >= 3500) & (ndsi_ras >= 0.30)

# Vegetation (2) / Agricultural Field (6): both require a minimum vegetation signal
veg_mask = (ndvi_ras >= 0.10)

# Pixels failing their class rule fall back to Barren Land (7)
refined_1 = Con((c_ras == 3) & (~sb_mask), 7, c_ras)
refined_2 = Con((refined_1 == 1) & (~water_mask), 7, refined_1)
refined_3 = Con((refined_2 == 5) & (~snow_mask), 7, refined_2)
refined_4 = Con((refined_3 == 2) & (~veg_mask), 7, refined_3)
refined_final = Con((refined_4 == 6) & (~veg_mask), 7, refined_4)

refined_final.save(output_refined)
print("Classification Refined and Saved.")

# ==============================================================================
# 8. ACCURACY ASSESSMENT
# ==============================================================================
print("Calculating Accuracy Metrics...")
accuracy_points = "Accuracy_Assessment_Points"

arcpy.ia.CreateAccuracyAssessmentPoints(
    in_class_data=output_refined,
    out_points=accuracy_points,
    target_field="ClassID",
    num_random_points=0,  # 0 = use all validation features supplied below
    in_validation=test_data
)

confusion_matrix_table = "Confusion_Matrix"
arcpy.ia.ComputeConfusionMatrix(
    in_accuracy_assessment_points=accuracy_points,
    out_confusion_matrix=confusion_matrix_table
)

print(f"Workflow Complete! Outputs saved in: {arcpy.env.workspace}")
print("Check the generated 'Confusion_Matrix' table for OA, Kappa, and Producer/User accuracy.")

# Clean up temporary rasters
temp_files = ["temp_ndvi", "temp_mndwi", "temp_savi", "temp_ndsi", "temp_slope", "temp_tri"]
for tmp in temp_files:
    if arcpy.Exists(tmp):
        arcpy.management.Delete(tmp)
