# ==============================================================================
# ARCGIS PRO (ARCPY) SUPERVISED LULC CLASSIFICATION WORKFLOW
# Region: Ladakh/Nubra, India (Cold-Arid Himalayan Riverine Ecosystem)
# Target: 7-Class LULC Mapping (including Seabuckthorn & Agriculture)
# ==============================================================================

import arcpy
from arcpy.sa import *
from arcpy.ia import *
import os

# Check out required extensions
arcpy.CheckOutExtension("Spatial")
arcpy.CheckOutExtension("ImageAnalyst")

# ==============================================================================
# WORKSPACE & DATA INPUTS
# ==============================================================================
# Set your geodatabase workspace (Based on your nubra project folder)
arcpy.env.workspace = r"C:\Users\admin\Documents\ArcGIS\Projects\nubra\nubra.gdb"
arcpy.env.overwriteOutput = True

# Inputs (Matching the exact names inside your nubra.gdb)
s2_raster_path = "S2_Median_Composite_Nubra_tif"
dem_path = "SRTM_Nubra_DEM_tif"
training_points = "Nubra_Training_Points"

# Define output names
output_rf_model = "RF_Model.ecd"
output_classified = "Classified_LULC"
output_refined = "Refined_LULC"

print("Starting Ladakh LULC Workflow...")

# ==============================================================================
# 1. STUDY AREA (ROI)
# ==============================================================================
# Create the ROI polygon geometry
roi_array = arcpy.Array([
    arcpy.Point(77.306500, 34.678861),
    arcpy.Point(77.306500, 34.515667),
    arcpy.Point(77.707361, 34.515667),
    arcpy.Point(77.707361, 34.678861),
    arcpy.Point(77.306500, 34.678861)
])
spatial_ref = arcpy.SpatialReference(4326)  # WGS84
roi_polygon = arcpy.Polygon(roi_array, spatial_ref)

# Persist the ROI as a feature class. arcpy.env.mask requires a path to a
# raster/feature class dataset - it cannot accept a raw in-memory geometry.
roi_fc = os.path.join(arcpy.env.workspace, "ROI_Polygon")
arcpy.management.CopyFeatures(roi_polygon, roi_fc)

# Set analysis environments to restrict processing to the ROI.
# arcpy.env.extent needs an Extent object (not a Polygon), hence ".extent".
# arcpy.env.mask needs a dataset reference, hence the feature class path.
arcpy.env.extent = roi_polygon.extent
arcpy.env.mask = roi_fc

# ==============================================================================
# 2. GROUND TRUTH DATA PREPARATION
# ==============================================================================
print("Preparing Training Data...")
# Buffer the points by 20 meters to capture S2 pixels
training_buffers = "Training_Buffers_20m"
arcpy.analysis.Buffer(training_points, training_buffers, "20 Meters")

# Create Train/Test Split (70/30)
arcpy.management.AddField(training_buffers, "Split_Rand", "DOUBLE")
arcpy.management.CalculateField(training_buffers, "Split_Rand", "arcpy.rand()", "PYTHON3")

train_data = "Training_Data_70"
test_data = "Validation_Data_30"
arcpy.analysis.Select(training_buffers, train_data, "Split_Rand <= 0.7")
arcpy.analysis.Select(training_buffers, test_data, "Split_Rand > 0.7")

# ==============================================================================
# 6. & 7. SPECTRAL PREDICTORS AND INDICES
# ==============================================================================
print("Calculating Spectral Indices...")
s2_img = Raster(s2_raster_path)

# Map bands (Assuming standard S2 band order: 1=B2, 2=B3, 3=B4, 4=B5, 5=B6, 6=B7, 7=B8, 8=B11, 9=B12)
b2 = Raster(s2_raster_path + "/Band_1")  # Blue
b3 = Raster(s2_raster_path + "/Band_2")  # Green
b4 = Raster(s2_raster_path + "/Band_3")  # Red
b5 = Raster(s2_raster_path + "/Band_4")  # Red Edge 1
b8 = Raster(s2_raster_path + "/Band_7")  # NIR
b11 = Raster(s2_raster_path + "/Band_8")  # SWIR 1
b12 = Raster(s2_raster_path + "/Band_9")  # SWIR 2

# Calculate Core Indices (Using ArcPy map algebra)
ndvi = Float(b8 - b4) / Float(b8 + b4)
ndwi = Float(b3 - b8) / Float(b3 + b8)
mndwi = Float(b3 - b11) / Float(b3 + b11)
ndsi = Float(b3 - b11) / Float(b3 + b11)
ndbi = Float(b11 - b8) / Float(b11 + b8)
savi = ((Float(b8 - b4) / Float(b8 + b4 + 0.5)) * 1.5)

# Save indices to disk temporarily (to prevent memory overload during RF)
ndvi.save("temp_ndvi")
mndwi.save("temp_mndwi")
savi.save("temp_savi")
ndsi.save("temp_ndsi")

# ==============================================================================
# 8. TERRAIN VARIABLES
# ==============================================================================
print("Generating Terrain Variables...")
dem = Raster(dem_path)
slope = SurfaceParameters(dem, "SLOPE", "DEGREE")
aspect = SurfaceParameters(dem, "ASPECT")
# Simple Terrain Ruggedness (Focal Statistics as proxy)
focal_min = FocalStatistics(dem, NbrRectangle(3, 3, "CELL"), "MINIMUM")
focal_max = FocalStatistics(dem, NbrRectangle(3, 3, "CELL"), "MAXIMUM")
tri = focal_max - focal_min

slope.save("temp_slope")
tri.save("temp_tri")

# ==============================================================================
# 10. FEATURE STACK
# ==============================================================================
print("Building Predictor Stack...")
# Combine raw bands, indices, and terrain into one composite
predictor_stack = "Predictor_Stack"
arcpy.management.CompositeBands(
    [s2_img, "temp_ndvi", "temp_mndwi", "temp_savi", "temp_ndsi", dem_path, "temp_slope", "temp_tri"],
    predictor_stack
)

# ==============================================================================
# 14. RANDOM FOREST CLASSIFICATION
# ==============================================================================
print("Training Random Forest Classifier...")
# Train the model using the 70% split
# "ClassID" matches the numeric class field generated from your CSV script
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
# Classify the raster using the trained model
classified_raster = arcpy.ia.ClassifyRaster(
    in_raster=predictor_stack,
    in_classifier_definition=output_rf_model
)
classified_raster.save(output_classified)

# ==============================================================================
# 26. ECOLOGICAL RULE-BASED REFINEMENT
# ==============================================================================
print("Applying Ecological Refinements...")
# Class mappings: 1=Seabuckthorn, 3=Nat Veg, 4=Water, 5=Barren Land

# Reload layers for Map Algebra
c_ras = Raster(output_classified)
dem_ras = Raster(dem_path)
slope_ras = Raster("temp_slope")
ndvi_ras = Raster("temp_ndvi")
mndwi_ras = Raster("temp_mndwi")

# Rule 1: Seabuckthorn (Class 1) must be Elev 2800-4000m, Slope <= 25, NDVI >= 0.15
sb_mask = (dem_ras >= 2800) & (dem_ras <= 4000) & (slope_ras <= 25) & (ndvi_ras >= 0.15)

# Rule 2: Water (Class 4) must be Elev <= 4500, Slope <= 15, MNDWI >= -0.10
water_mask = (dem_ras <= 4500) & (slope_ras <= 15) & (mndwi_ras >= -0.10)

# Rule 3: Natural Veg (Class 3) must be NDVI >= 0.10
veg_mask = (ndvi_ras >= 0.10)

# Apply nested Con statements to reclassify pixels failing the rules to Barren (5)
refined_1 = Con((c_ras == 1) & (~sb_mask), 5, c_ras)
refined_2 = Con((refined_1 == 4) & (~water_mask), 5, refined_1)
refined_final = Con((refined_2 == 3) & (~veg_mask), 5, refined_2)

refined_final.save(output_refined)
print("Classification Refined and Saved.")

# ==============================================================================
# 24. ACCURACY ASSESSMENT
# ==============================================================================
print("Calculating Accuracy Metrics...")
accuracy_points = "Accuracy_Assessment_Points"

# Generate accuracy points from the 30% validation set
arcpy.ia.CreateAccuracyAssessmentPoints(
    in_class_data=output_refined,
    out_points=accuracy_points,
    target_field="ClassID",
    num_random_points=0,  # 0 means it uses all polygons provided below
    in_validation=test_data
)

# Compute Confusion Matrix
confusion_matrix_table = "Confusion_Matrix"
arcpy.ia.ComputeConfusionMatrix(
    in_accuracy_assessment_points=accuracy_points,
    out_confusion_matrix=confusion_matrix_table
)

print(f"Workflow Complete! Outputs saved in: {arcpy.env.workspace}")
print("Check the generated 'Confusion_Matrix' table for OA, Kappa, and Producer/User accuracy.")

# Clean up temporary rasters to keep your geodatabase tidy
temp_files = ["temp_ndvi", "temp_mndwi", "temp_savi", "temp_ndsi", "temp_slope", "temp_tri"]
for tmp in temp_files:
    if arcpy.Exists(tmp):
        arcpy.management.Delete(tmp)
