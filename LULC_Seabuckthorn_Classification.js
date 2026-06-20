// ============================================================
// LULC CLASSIFICATION — SEABUCKTHORN AREA MAPPING, LADAKH
// Project: Seabuckthorn Area Mapping and Nutritional Profiling
//          under the Cold Desert Region of Ladakh
// Method:  Random Forest (100 trees) | Sentinel-2 SR
// Season:  Growing Season (June–September)
// Classes: Water Bodies (0), Vegetation (1), Barren Land (2),
//          Seabuckthorn (3)
// Note:    Agricultural Field merged into Vegetation class
// ============================================================
// FIXES FOR LADAKH TOPOGRAPHY:
//   1. 20m buffer polygons around GPS points for better sampling
//   2. MNDWI replaces NDWI for water (avoids snow/ice confusion)
//   3. Red Edge bands (B5, B6, B7) separate SBT from agriculture
//   4. Elevation mask: SBT only 2800–4000m, Water below 4500m
//   5. Slope filter: SBT only on gentle slopes (<25 degrees)
//   6. Class-balanced sampling prevents minority over-prediction
//   7. NDVI gate on Seabuckthorn (must be green vegetation)
//   8. BSI for better barren land separation in arid terrain
// ============================================================

// ============================================================
// SECTION 1: STUDY AREA (ROI) DEFINITION
// ============================================================

var roi = ee.Geometry.Rectangle([
  77.3878601, 33.8666392,
  77.8217575, 34.1829314
]);

Map.centerObject(roi, 11);
Map.addLayer(roi, {color: 'FFFFFF'}, 'Study Area Boundary');

// ============================================================
// SECTION 2: LULC CLASS SCHEMA (4 CLASSES)
// ============================================================

// Agricultural Field is merged into Vegetation for Ladakh context
// 0 = Water Bodies | 1 = Vegetation | 2 = Barren Land | 3 = Seabuckthorn
var classProperty = 'class_int';
var classNames  = ['Water Bodies', 'Vegetation', 'Barren Land', 'Seabuckthorn'];
var classPalette = ['#1E90FF', '#228B22', '#D2B48C', '#FF0000'];
var numClasses  = 4;

// ============================================================
// SECTION 3: GROUND TRUTH / TRAINING DATA (578 GPS POINTS)
// ============================================================

var groundTruthRaw = ee.FeatureCollection([
  // === AGRICULTURAL FIELD → mapped to Vegetation (class 1) ===
  ee.Feature(ee.Geometry.Point([77.51093228, 34.13228877]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5118587, 34.13145185]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.50709571, 34.13273368]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.51926898, 34.12702267]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.51971461, 34.12465076]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52257235, 34.12340406]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52717165, 34.12239143]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53036111, 34.12263457]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52888996, 34.1234992]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53765217, 34.12065014]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53733636, 34.12260965]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53520481, 34.11598949]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53287101, 34.11590263]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55468291, 34.1080184]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55576971, 34.10681768]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55867823, 34.10723179]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57722168, 34.11298532]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57452049, 34.11301897]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5749919, 34.11590113]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.61975609, 34.08107964]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62050459, 34.08286453]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62247661, 34.07913857]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62599403, 34.08128433]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62816444, 34.08174883]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62756882, 34.07858694]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62660143, 34.07501202]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62743897, 34.07323035]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63013122, 34.07366438]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63993103, 34.07064045]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6408205, 34.0717383]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64222152, 34.06843091]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63923788, 34.06851446]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64653345, 34.06640821]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64901517, 34.0664941]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64611283, 34.06337729]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64260936, 34.06233242]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64886778, 34.06116717]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65316746, 34.0637501]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65476175, 34.06058117]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6534739, 34.05878262]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65947606, 34.05931747]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65946944, 34.05560045]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66081342, 34.05843036]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65566848, 34.05809604]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6626922, 34.05412166]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66480171, 34.05124508]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66577568, 34.04911109]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66765729, 34.04756774]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66679473, 34.04544345]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66965842, 34.04519962]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67055405, 34.04218915]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67503737, 34.0415863]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67007032, 34.03847861]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67472647, 34.03887264]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67119355, 34.03550066]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67717135, 34.03323407]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67361835, 34.03035113]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68114058, 34.03645797]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68300254, 34.03613237]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68099237, 34.03441507]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68135509, 34.02828154]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68251611, 34.02655467]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68165793, 34.02218804]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68005074, 34.01883205]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6773237, 34.02526119]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6846664, 34.01308712]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67930022, 34.01127943]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67755011, 34.00883798]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6799276, 34.00727028]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68194442, 34.00089141]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67719654, 34.00291248]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67982434, 33.99858895]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68421153, 33.99333601]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.69101912, 33.99289822]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6900993, 33.98793805]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.7262121, 33.93475206]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72563705, 33.93404168]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72757796, 33.93114242]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72388562, 33.93144814]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73231575, 33.92288908]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73884913, 33.91429384]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73574525, 33.91413812]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73739183, 33.91453059]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73974417, 33.91353284]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74061847, 33.91200535]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73702317, 33.90879335]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73665344, 33.90431228]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73955808, 33.90489974]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74202417, 33.90625818]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73977752, 33.90190482]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67229808, 34.02231578]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67032394, 34.02408327]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66902909, 34.02355351]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66765375, 34.02664405]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6691451, 34.02559565]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66522836, 34.02834875]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66027431, 34.03057709]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6615032, 34.03416146]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6589799, 34.0366856]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65656396, 34.02990831]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65364143, 34.03230427]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65153883, 34.03562927]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64361136, 34.03973762]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63830057, 34.04222933]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64365633, 34.04572289]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63991163, 34.04782264]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63513142, 34.04807607]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63476978, 34.05276702]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.465528, 34.130194]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.736361, 33.908333]), {class_str: 'Vegetation'}),

  // === BARREN LAND (163 points) ===
  ee.Feature(ee.Geometry.Point([77.50492833, 34.12815165]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.50948821, 34.13606736]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.51362798, 34.13257938]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52100233, 34.12983645]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52060591, 34.12778466]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52367053, 34.12976791]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52503626, 34.13156314]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.50724678, 34.12263145]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.51490358, 34.1189494]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52026966, 34.11627507]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52595813, 34.12747186]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.53773308, 34.12613201]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.54002299, 34.12457326]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.54307736, 34.12170908]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.54182425, 34.11596624]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.52663149, 34.11071446]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.53033872, 34.11043777]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.5492245, 34.1190107]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.55152647, 34.12591897]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.55920108, 34.11876512]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.56170457, 34.11541262]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.54062431, 34.1078909]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.54522908, 34.10549318]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.55058848, 34.10474499]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.56221782, 34.11902084]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.56009679, 34.10611808]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.56400983, 34.10483702]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.57452506, 34.12084998]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.57922678, 34.11622021]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.58150382, 34.11293151]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.57969759, 34.11007337]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.57346966, 34.10158161]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.568976, 34.10206992]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.56564664, 34.09853523]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.5854915, 34.11684267]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.58937862, 34.1233468]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.59262425, 34.10908663]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.57943965, 34.09673093]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.58433597, 34.10108736]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.59785886, 34.10408821]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.60163605, 34.10704554]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.58880073, 34.09022243]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.59226248, 34.08418014]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.60891364, 34.09569458]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.60892645, 34.08617979]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.614174, 34.08442399]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.62167744, 34.08888837]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.61524967, 34.07957834]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.61883482, 34.07767671]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.62630294, 34.08546541]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.59644336, 34.07791222]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.60064392, 34.07008337]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.60404543, 34.06743997]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.63246918, 34.08016903]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.63258532, 34.07306495]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.61021046, 34.06313858]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.61753103, 34.05994887]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.62932673, 34.06260291]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.64134987, 34.07349135]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.62297786, 34.0558044]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.62727245, 34.05294451]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.62969019, 34.05040811]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.63681672, 34.0583693]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6336731, 34.06063985]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.64497293, 34.05592603]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.63152619, 34.04578595]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.64721263, 34.07076207]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.65088241, 34.06826579]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.65404558, 34.06545469]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.64741961, 34.04682223]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.63894831, 34.03846372]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.64565776, 34.03341208]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.65610149, 34.03638848]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.65804165, 34.04451838]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66005996, 34.04172195]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66699868, 34.06039244]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67019149, 34.05488022]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67406454, 34.04544186]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.65182858, 34.03120667]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6549323, 34.02829735]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.65726853, 34.02774153]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66156351, 34.02572639]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66351181, 34.02624467]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66819368, 34.03051581]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6714506, 34.03130925]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67390983, 34.03217079]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68525582, 34.03902374]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68782371, 34.03368027]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68432107, 34.02584934]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68538446, 34.0188412]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68337408, 34.01470978]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68320267, 34.01252502]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67359223, 34.01618328]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66621771, 34.02318644]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68347948, 34.00808276]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68825833, 34.00601214]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68676103, 34.01607661]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69193053, 34.01122058]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69369188, 34.00675795]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69497372, 34.00195034]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6881785, 34.00260098]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68494837, 34.00196808]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6828163, 33.99560195]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67581598, 33.9941022]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67120677, 33.99332058]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.66302734, 33.99446586]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67372923, 33.9925908]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67640823, 33.99120482]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67680263, 33.98926273]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.67844241, 33.98807434]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68632431, 33.9941475]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68736636, 33.99079224]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6883046, 33.9881878]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69133306, 33.98808465]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69313426, 33.99294172]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69689631, 33.99418929]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70098767, 33.99612205]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69759972, 33.99006561]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69643266, 33.98789819]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69235121, 33.98664728]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.6970866, 33.98557211]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70124002, 33.98284226]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70541354, 33.99324864]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70407115, 33.9784926]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70746094, 33.97558398]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68480601, 33.97929396]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.68747127, 33.97402367]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.69147219, 33.97031202]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70251055, 33.96639264]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.7052353, 33.96244777]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.70850883, 33.96287373]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.71179987, 33.96302539]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.7180459, 33.96001584]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.71444212, 33.95431296]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.71130832, 33.95063648]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.72368921, 33.94916202]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.71942171, 33.94987198]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.7152449, 33.94821704]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.72563232, 33.94363482]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.72839369, 33.94079766]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.71831158, 33.93869357]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.71889309, 33.93389732]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.72139083, 33.92900822]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.72806229, 33.92808456]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73023168, 33.92825383]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.72856813, 33.92487122]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73646765, 33.92441962]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73037452, 33.91973031]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73498573, 33.91769797]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.74121311, 33.91605186]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73669571, 33.91150512]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73146359, 33.91049912]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73161597, 33.90495447]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73572059, 33.90244847]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.73896855, 33.90015769]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.74178507, 33.90275207]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.74599931, 33.90529142]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.74484893, 33.90732192]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.616583, 34.079333]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.549417, 34.108389]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.510722, 34.1235]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.734056, 33.912389]), {class_str: 'Barren Land'}),
  ee.Feature(ee.Geometry.Point([77.734278, 33.903194]), {class_str: 'Barren Land'}),

  // === VEGETATION (178 points) ===
  ee.Feature(ee.Geometry.Point([77.5045264, 34.13267354]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.50841774, 34.1312484]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5097984, 34.1293035]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.50187761, 34.13258257]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.50789238, 34.12666456]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.51953109, 34.12199686]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5255963, 34.12044141]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52611832, 34.11780221]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53201496, 34.12370898]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53054229, 34.12415177]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53183327, 34.11918515]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5289853, 34.11670331]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52422834, 34.11551106]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52762999, 34.11158788]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.52988446, 34.11258716]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53195731, 34.1181974]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53723292, 34.12188244]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53670466, 34.12304965]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54016636, 34.12059751]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54125807, 34.11977327]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54156707, 34.11766613]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54291056, 34.11690666]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.53383221, 34.11263546]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.543077, 34.11508244]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54547527, 34.11348894]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54789595, 34.11288415]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54758918, 34.11774287]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54056132, 34.10942288]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54325648, 34.10779766]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54747601, 34.1068523]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55111808, 34.10958872]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55372786, 34.11003444]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.54994152, 34.11352079]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55293928, 34.11639182]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55585273, 34.11763534]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55668066, 34.11214892]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5562465, 34.11310514]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5599779, 34.11403372]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5617966, 34.11378196]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56322004, 34.11411877]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56259938, 34.11240789]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56375182, 34.11306145]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5647721, 34.11172205]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56630546, 34.11215847]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5676693, 34.11088027]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.55934638, 34.10858562]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56092092, 34.11155744]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56280225, 34.11032038]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56595715, 34.10982673]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56727444, 34.10734689]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56305101, 34.10636067]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.56996252, 34.10450761]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57124052, 34.1100228]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57776596, 34.11406981]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57788331, 34.11642573]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57284204, 34.11831414]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5809123, 34.11246447]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57776302, 34.10870916]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.57863783, 34.104911]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.58581865, 34.10493402]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.5850752, 34.09691926]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.59154137, 34.09497914]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.59740432, 34.09650793]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.59689766, 34.09472405]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60001385, 34.09356748]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60454058, 34.09743697]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60418605, 34.09184403]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60567977, 34.09133384]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60668567, 34.08910726]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60619066, 34.09365672]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.59901021, 34.08893628]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60186669, 34.07699182]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60691491, 34.07988756]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.603469, 34.07348853]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60354794, 34.0705876]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60791747, 34.06933573]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.61059229, 34.0768523]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60820791, 34.06698478]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.60676278, 34.07196327]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.61355255, 34.07290036]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62283698, 34.07688131]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62774837, 34.07605763]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62101125, 34.07386595]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.61548828, 34.0634137]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.61761356, 34.06155007]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.61746069, 34.0671122]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62503333, 34.06638909]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62884799, 34.06725007]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62500416, 34.07022539]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6278112, 34.05818841]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.62220093, 34.05906045]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63863156, 34.07070701]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63928324, 34.06945785]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64668975, 34.06827867]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6437344, 34.06586118]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64152143, 34.05940141]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63324814, 34.05151881]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.63785957, 34.05057948]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.64465382, 34.05808219]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65234467, 34.06485064]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.65740023, 34.0618715]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6624364, 34.05957085]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66491787, 34.0561162]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66290695, 34.05131822]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66260469, 34.04899882]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66353804, 34.04712468]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66253006, 34.04549795]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66547027, 34.04543229]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66444496, 34.04234783]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66438301, 34.04151454]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66633607, 34.0418619]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66477422, 34.04097442]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66617573, 34.04080292]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66621283, 34.04003494]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66829892, 34.04075712]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66751028, 34.03931064]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66905905, 34.03882483]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66807221, 34.03785972]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66783107, 34.03642833]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66785077, 34.03532681]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66964583, 34.03664719]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67145953, 34.03814421]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67398654, 34.04274934]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67675757, 34.04285399]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67956329, 34.04083808]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67707797, 34.03763781]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67543019, 34.03472316]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68131847, 34.03848148]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68324992, 34.03804983]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68813565, 34.03709255]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68680346, 34.03221424]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68575832, 34.03041052]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67891982, 34.02723154]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66989508, 34.03035487]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66912393, 34.02721482]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.66581959, 34.02512039]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67344635, 34.02794983]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6779069, 34.02922671]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68085101, 34.02909125]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68396942, 34.02920254]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68348445, 34.02469754]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67369598, 34.01799896]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68551567, 34.01334628]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67452483, 34.0068003]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67495375, 34.0044589]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68674072, 34.0092994]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68076906, 34.00286546]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.67719478, 33.99826971]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.6818214, 33.996386]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68373515, 33.99463628]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.69006025, 34.00408872]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.68821179, 33.98687844]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.69583637, 33.97861857]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.69852143, 33.97013597]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.70932424, 33.97259384]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.7224807, 33.94878378]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72604174, 33.94546034]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72186531, 33.93612847]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72407825, 33.92876109]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72583124, 33.92659483]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.72882914, 33.92117697]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73323275, 33.91395042]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73733243, 33.91250881]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74040919, 33.91367946]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74203612, 33.91193278]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73617608, 33.90911258]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73459586, 33.9055364]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73750844, 33.90646898]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74000261, 33.90825795]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74208876, 33.90857987]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.73552605, 33.90290887]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.740365, 33.90533477]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74291711, 33.90610534]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74233488, 33.90432198]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74305657, 33.90086043]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.74458797, 33.8983617]), {class_str: 'Vegetation'}),
  ee.Feature(ee.Geometry.Point([77.466333, 34.130194]), {class_str: 'Vegetation'}),

  // === WATER BODIES (53 points) ===
  ee.Feature(ee.Geometry.Point([77.74997527, 33.89401584]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74958717, 33.89597052]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74968735, 33.89488732]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74949323, 33.9025369]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.746953, 33.90599625]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74424503, 33.90886865]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74067757, 33.91495253]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73524924, 33.91812817]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73469624, 33.92213388]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73127522, 33.92465352]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.72969081, 33.9314711]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.7268518, 33.94042571]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.72389587, 33.94512065]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.72045355, 33.9496011]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.71898729, 33.95580114]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.7111628, 33.96008019]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.70864138, 33.96648691]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.70935886, 33.97451429]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.70440785, 33.97952186]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.69886519, 33.98953688]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.68942126, 34.0062395]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.67949714, 34.0144012]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.67508433, 34.02438316]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.66974682, 34.03233302]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.66574143, 34.03863635]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.66148271, 34.04432773]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.65451435, 34.04959599]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.6472156, 34.05650807]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.6397798, 34.06100976]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.6323494, 34.06346925]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.62636237, 34.06820573]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.62032873, 34.07335284]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.61366029, 34.07757804]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.60525484, 34.08589461]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.59771417, 34.09524923]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.59458638, 34.09991051]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.58649505, 34.10263558]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.5705785, 34.10796514]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.56031752, 34.11244982]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.55206173, 34.11126031]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.54606395, 34.1113897]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.51408884, 34.12611404]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74599133, 33.90229405]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74415434, 33.90496876]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.74267274, 33.90791162]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73834772, 33.90774845]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73860498, 33.90982534]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73892428, 33.91297565]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.73493536, 33.91506132]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.50602818, 34.13044164]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.610389, 34.083417]), {class_str: 'Water Bodies'}),
  ee.Feature(ee.Geometry.Point([77.736617, 33.907583]), {class_str: 'Water Bodies'}),

  // === SEABUCKTHORN (86 points) ===
  ee.Feature(ee.Geometry.Point([77.614722, 34.079139]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.614944, 34.078]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.616111, 34.077083]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.609333, 34.084139]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.617639, 34.076528]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.617528, 34.076778]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.618639, 34.078778]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.621444, 34.088722]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.6215, 34.087028]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.64175, 34.058917]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.640694, 34.05825]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.638444, 34.054528]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.673083, 34.031444]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.671417, 34.030639]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.671583, 34.029167]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.660139, 34.040444]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.659306, 34.040861]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.658806, 34.042028]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.663833, 34.041944]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.670306, 34.034778]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.737517, 33.908267]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.548389, 34.108528]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.546611, 34.1085]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.546972, 34.10875]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.556333, 34.110944]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.554944, 34.113861]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.463917, 34.130917]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.453472, 34.132056]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.466889, 34.129889]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.46425, 34.131417]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.45275, 34.131889]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.508694, 34.124722]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.515861, 34.121028]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.81725, 33.912528]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.730361, 33.901167]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.73025, 33.90125]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.730306, 33.901]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.73025, 33.900944]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.731583, 33.904361]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.734861, 33.9035]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.73425, 33.903444]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.734056, 33.90325]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733806, 33.90325]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.685, 33.998611]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.685556, 33.994444]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.738028, 33.908389]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.735983, 33.90665]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.7372, 33.908183]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.732222, 33.909111]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733194, 33.910417]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733361, 33.910444]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.73325, 33.91]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733861, 33.911889]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.738222, 33.90875]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.738694, 33.908972]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.739389, 33.909333]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.740778, 33.9095]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.740806, 33.909722]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.741667, 33.9095]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.742306, 33.910083]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.743889, 33.908722]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.736067, 33.907283]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.732389, 33.910278]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733306, 33.910611]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733722, 33.911056]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733222, 33.911444]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.732972, 33.911944]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.730806, 33.901333]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.731306, 33.901583]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.731528, 33.901722]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.732056, 33.901944]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.730611, 33.900972]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.730556, 33.900722]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.740528, 33.905694]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733417, 33.903333]), {class_str: 'Seabuckthorn'}),
  ee.Feature(ee.Geometry.Point([77.733333, 33.903056]), {class_str: 'Seabuckthorn'})
]);

// ============================================================
// SECTION 3b: CLASS ENCODING AND 20m BUFFER POLYGONS
// ============================================================

// Remap string class names to integer codes (4 classes)
var classLookup = ee.Dictionary({
  'Water Bodies': 0,
  'Vegetation': 1,
  'Barren Land': 2,
  'Seabuckthorn': 3
});

var groundTruth = groundTruthRaw.map(function(f) {
  var className = f.get('class_str');
  var classInt = classLookup.get(className);
  return f.set(classProperty, classInt);
});

// Create 20m buffer polygons around each GPS point for better pixel sampling
// This captures 2x2 pixel neighborhoods at 10m resolution
var groundTruthBuffered = groundTruth.map(function(f) {
  return f.setGeometry(f.geometry().buffer(20));
});

print('========== TRAINING DATA SUMMARY ==========');
print('Total ground truth points:', groundTruth.size());
print('Water Bodies:', groundTruth.filter(ee.Filter.eq(classProperty, 0)).size());
print('Vegetation (incl. Agricultural Field):', groundTruth.filter(ee.Filter.eq(classProperty, 1)).size());
print('Barren Land:', groundTruth.filter(ee.Filter.eq(classProperty, 2)).size());
print('Seabuckthorn:', groundTruth.filter(ee.Filter.eq(classProperty, 3)).size());

Map.addLayer(groundTruth, {color: 'FF00FF'}, 'Ground Truth Points');
Map.addLayer(groundTruthBuffered, {color: 'FFFF00'}, 'Ground Truth 20m Buffers', false);

// ============================================================
// SECTION 4: SENTINEL-2 CLOUD-FREE COMPOSITE (JUNE–SEPTEMBER)
// ============================================================

function maskS2Clouds(image) {
  var scl = image.select('SCL');
  var clearMask = scl.eq(4)   // Vegetation
    .or(scl.eq(5))            // Bare soil
    .or(scl.eq(6))            // Water
    .or(scl.eq(7))            // Unclassified (critical for arid Ladakh)
    .or(scl.eq(11));          // Snow/Ice
  return image.updateMask(clearMask);
}

var s2 = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
  .filterBounds(roi)
  .filter(ee.Filter.calendarRange(6, 9, 'month'))
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 20))
  .map(maskS2Clouds);

print('========== SENTINEL-2 IMAGERY ==========');
print('Sentinel-2 scenes used:', s2.size());

// Core spectral bands + Red Edge bands for SBT separation
var bands = ['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B11', 'B12'];
var composite = s2.select(bands).median().clip(roi);

Map.addLayer(composite, {
  bands: ['B4', 'B3', 'B2'], min: 200, max: 3000
}, 'True Color Composite');

Map.addLayer(composite, {
  bands: ['B8', 'B4', 'B3'], min: 200, max: 4000
}, 'False Color (NIR-R-G)', false);

Map.addLayer(composite, {
  bands: ['B12', 'B8', 'B4'], min: 200, max: 4000
}, 'SWIR-NIR-R (Seabuckthorn Enhanced)', false);

// ============================================================
// SECTION 5: SPECTRAL INDICES
// ============================================================

// NDVI
var ndvi = composite.normalizedDifference(['B8', 'B4']).rename('NDVI');

// SAVI (L=0.5 for sparse cold desert canopy)
var savi = composite.expression(
  '((NIR - RED) / (NIR + RED + L)) * (1 + L)', {
    'NIR': composite.select('B8'),
    'RED': composite.select('B4'),
    'L': 0.5
  }).rename('SAVI');

// MNDWI — uses SWIR instead of NIR, far better at separating water from snow/ice/shadow
var mndwi = composite.normalizedDifference(['B3', 'B11']).rename('MNDWI');

// NDWI — kept as supplementary feature
var ndwi = composite.normalizedDifference(['B3', 'B8']).rename('NDWI');

// NDBI — barren/built-up index
var ndbi = composite.normalizedDifference(['B11', 'B8']).rename('NDBI');

// BSI — Bare Soil Index for better barren land detection in arid terrain
var bsi = composite.expression(
  '((SWIR1 + RED) - (NIR + BLUE)) / ((SWIR1 + RED) + (NIR + BLUE))', {
    'SWIR1': composite.select('B11'),
    'RED': composite.select('B4'),
    'NIR': composite.select('B8'),
    'BLUE': composite.select('B2')
  }).rename('BSI');

// Red Edge NDVI — separates Seabuckthorn from agriculture/general vegetation
var reNDVI = composite.normalizedDifference(['B7', 'B5']).rename('RENDVI');

// Red Edge chlorophyll index — sensitive to canopy structure differences
var reCl = composite.expression(
  '(NIR / RE1) - 1', {
    'NIR': composite.select('B7'),
    'RE1': composite.select('B5')
  }).rename('RECI');

Map.addLayer(ndvi, {min: -0.2, max: 0.8, palette: ['brown','yellow','green']}, 'NDVI', false);
Map.addLayer(savi, {min: -0.2, max: 0.6, palette: ['brown','yellow','green']}, 'SAVI', false);
Map.addLayer(mndwi, {min: -0.5, max: 0.5, palette: ['brown','white','blue']}, 'MNDWI', false);
Map.addLayer(ndwi, {min: -0.5, max: 0.5, palette: ['brown','white','blue']}, 'NDWI', false);
Map.addLayer(ndbi, {min: -0.3, max: 0.3, palette: ['green','white','red']}, 'NDBI', false);
Map.addLayer(bsi, {min: -0.3, max: 0.3, palette: ['green','white','brown']}, 'BSI', false);

// ============================================================
// SECTION 6: TOPOGRAPHIC VARIABLES (DEM, SLOPE, ASPECT)
// ============================================================

var dem = ee.ImageCollection('COPERNICUS/DEM/GLO30')
  .filterBounds(roi)
  .select('DEM')
  .mosaic()
  .clip(roi)
  .rename('Elevation');

var terrain = ee.Terrain.products(dem.select('Elevation'));
var slope = terrain.select('slope').rename('Slope');
var aspect = terrain.select('aspect').rename('Aspect');

Map.addLayer(dem, {min: 3000, max: 5500, palette: ['green','yellow','brown','white']}, 'Elevation (DEM)', false);
Map.addLayer(slope, {min: 0, max: 50, palette: ['green','yellow','red']}, 'Slope', false);
Map.addLayer(aspect, {min: 0, max: 360, palette: ['red','yellow','green','cyan','blue','magenta','red']}, 'Aspect', false);

// ============================================================
// SECTION 7: MULTI-BAND INPUT STACK
// ============================================================

var inputImage = composite
  .addBands(ndvi)
  .addBands(savi)
  .addBands(mndwi)
  .addBands(ndwi)
  .addBands(ndbi)
  .addBands(bsi)
  .addBands(reNDVI)
  .addBands(reCl)
  .addBands(dem)
  .addBands(slope)
  .addBands(aspect);

var inputBands = inputImage.bandNames();
print('========== CLASSIFICATION INPUT ==========');
print('Input bands:', inputBands);
print('Total band count:', inputBands.length());

// ============================================================
// SECTION 8: SAMPLE EXTRACTION WITH 20m BUFFER POLYGONS
// ============================================================

// Sample using buffered polygons (20m) for more representative pixel capture
var samples = inputImage.sampleRegions({
  collection: groundTruthBuffered,
  properties: [classProperty],
  scale: 10,
  tileScale: 8,
  geometries: false
});

// Remove samples on masked pixels
samples = samples.filter(ee.Filter.notNull(['B2', 'B3', 'B4', 'B8', 'NDVI', 'Elevation']));

print('Total pixels sampled from buffered polygons:', samples.size());

// 70/30 train-validation split with fixed seed for reproducibility
var samplesWithRandom = samples.randomColumn('random', 42);
var trainingSamples = samplesWithRandom.filter(ee.Filter.lt('random', 0.7));
var validationSamples = samplesWithRandom.filter(ee.Filter.gte('random', 0.7));

// ============================================================
// SECTION 8b: CLASS-BALANCED SAMPLING
// ============================================================

// Find minimum class size to balance training
var waterCount = trainingSamples.filter(ee.Filter.eq(classProperty, 0)).size();
var vegCount = trainingSamples.filter(ee.Filter.eq(classProperty, 1)).size();
var barrenCount = trainingSamples.filter(ee.Filter.eq(classProperty, 2)).size();
var sbtCount = trainingSamples.filter(ee.Filter.eq(classProperty, 3)).size();

print('--- Pre-balance training counts ---');
print('Water Bodies:', waterCount);
print('Vegetation:', vegCount);
print('Barren Land:', barrenCount);
print('Seabuckthorn:', sbtCount);

// Balance: cap each class to the minimum class size (prevents majority dominance)
var minClassSize = waterCount.min(vegCount).min(barrenCount).min(sbtCount);

var balancedTraining = ee.FeatureCollection(
  ee.List([0, 1, 2, 3]).map(function(c) {
    return trainingSamples
      .filter(ee.Filter.eq(classProperty, c))
      .randomColumn('bal_random', 99)
      .sort('bal_random')
      .limit(minClassSize);
  })
).flatten();

print('Balanced training samples per class:', minClassSize);
print('Total balanced training samples:', balancedTraining.size());
print('Validation samples (30%, unbalanced):', validationSamples.size());

// ============================================================
// SECTION 9: RANDOM FOREST CLASSIFIER (100 TREES)
// ============================================================

var classifier = ee.Classifier.smileRandomForest(100)
  .train({
    features: balancedTraining,
    classProperty: classProperty,
    inputProperties: inputBands
  });

var importance = ee.Dictionary(classifier.explain().get('importance'));
print('========== VARIABLE IMPORTANCE ==========');
print('Feature importance:', importance);

// ============================================================
// SECTION 10: CLASSIFY THE STUDY AREA
// ============================================================

var classifiedRaw = inputImage.classify(classifier).clip(roi);

Map.addLayer(classifiedRaw, {
  min: 0, max: 3,
  palette: classPalette
}, 'LULC (Raw — Before Ecological Correction)', false);

// ============================================================
// SECTION 10b: POST-CLASSIFICATION ECOLOGICAL CORRECTIONS
// ============================================================

var elevation = dem.select('Elevation');

// FIX 1: Seabuckthorn only at 2800–4000m, gentle slopes, and must be green
var sbtElevMask = elevation.gte(2800).and(elevation.lte(4000));
var sbtSlopeMask = slope.lte(25);
var sbtNdviMask = ndvi.gte(0.15);
var sbtValidZone = sbtElevMask.and(sbtSlopeMask).and(sbtNdviMask);

// FIX 2: Water Bodies must be below 4500m, gentle slopes, positive MNDWI
var waterSlopeMask = slope.lte(15);
var waterMndwiMask = mndwi.gte(-0.1);
var waterElevMask = elevation.lte(4500);
var waterValidZone = waterSlopeMask.and(waterMndwiMask).and(waterElevMask);

// FIX 3: Vegetation must show greenness
var vegNdviMask = ndvi.gte(0.1);

// Apply corrections
var classified = classifiedRaw
  .where(classifiedRaw.eq(3).and(sbtValidZone.not()), 2)
  .where(classifiedRaw.eq(0).and(waterValidZone.not()), 2)
  .where(classifiedRaw.eq(1).and(vegNdviMask.not()), 2)
  .clip(roi);

Map.addLayer(classified, {
  min: 0, max: 3,
  palette: classPalette
}, 'LULC Classification (Corrected)');

// ============================================================
// SECTION 11: ACCURACY ASSESSMENT
// ============================================================

var validated = validationSamples.classify(classifier);
var confusionMatrix = validated.errorMatrix(classProperty, 'classification');

print('========== ACCURACY ASSESSMENT ==========');
print('Confusion Matrix:', confusionMatrix);
print('Overall Accuracy:', confusionMatrix.accuracy());
print('Kappa Coefficient:', confusionMatrix.kappa());
print('Producers Accuracy (rows):', confusionMatrix.producersAccuracy());
print('Users Accuracy (columns):', confusionMatrix.consumersAccuracy());

// Build exportable accuracy table with per-class metrics
var overallAccuracy = confusionMatrix.accuracy();
var kappa = confusionMatrix.kappa();
var producersAcc = confusionMatrix.producersAccuracy();
var usersAcc = confusionMatrix.consumersAccuracy();

var accuracyFeatures = classNames.map(function(name, index) {
  return ee.Feature(null, {
    'Class': name,
    'Class_Value': index,
    'Producers_Accuracy': ee.List(producersAcc.toList().get(index)).get(0),
    'Users_Accuracy': ee.List(usersAcc.toList().get(index)).get(0)
  });
});

var summaryRow = ee.Feature(null, {
  'Class': 'OVERALL',
  'Class_Value': -1,
  'Producers_Accuracy': overallAccuracy,
  'Users_Accuracy': kappa
});

var accuracyExport = ee.FeatureCollection(accuracyFeatures)
  .merge(ee.FeatureCollection([summaryRow]));

// ============================================================
// SECTION 12: CLASS AREA CALCULATION (HECTARES & SQ KM)
// ============================================================

var pixelArea = ee.Image.pixelArea().divide(10000); // m² to hectares

var areaByClass = classNames.map(function(name, index) {
  var classMask = classified.eq(ee.Number(index));
  var classArea = pixelArea.updateMask(classMask).reduceRegion({
    reducer: ee.Reducer.sum(),
    geometry: roi,
    scale: 10,
    maxPixels: 1e13,
    tileScale: 4
  });
  var areaHa = ee.Number(classArea.get('area'));
  return ee.Feature(null, {
    'Class': name,
    'Class_Value': index,
    'Area_Hectares': areaHa,
    'Area_SqKm': areaHa.divide(100)
  });
});

var areaTable = ee.FeatureCollection(areaByClass);

print('========== CLASS AREA STATISTICS ==========');
print('Area by LULC class:', areaTable);

// Print formatted area for each class
classNames.forEach(function(name, index) {
  var feat = ee.Feature(areaTable.filter(ee.Filter.eq('Class_Value', index)).first());
  print(name + ':', feat.get('Area_Hectares'), 'ha |', feat.get('Area_SqKm'), 'sq km');
});

// ============================================================
// SECTION 12b: AREA SUMMARY TABLE (PRINTED IN CONSOLE)
// ============================================================

// Compute total area for percentage calculation
var totalArea = pixelArea.reduceRegion({
  reducer: ee.Reducer.sum(),
  geometry: roi,
  scale: 10,
  maxPixels: 1e13,
  tileScale: 4
});
var totalHa = ee.Number(totalArea.get('area'));

var areaWithPercent = classNames.map(function(name, index) {
  var classMask = classified.eq(ee.Number(index));
  var classArea = pixelArea.updateMask(classMask).reduceRegion({
    reducer: ee.Reducer.sum(),
    geometry: roi,
    scale: 10,
    maxPixels: 1e13,
    tileScale: 4
  });
  var areaHa = ee.Number(classArea.get('area'));
  return ee.Feature(null, {
    'Class': name,
    'Class_Value': index,
    'Area_Hectares': areaHa,
    'Area_SqKm': areaHa.divide(100),
    'Percentage': areaHa.divide(totalHa).multiply(100)
  });
});

var areaTableFull = ee.FeatureCollection(areaWithPercent);
print('========== AREA TABLE WITH PERCENTAGES ==========');
print(areaTableFull);

// ============================================================
// SECTION 13: MAP LEGEND
// ============================================================

var legend = ui.Panel({
  style: {
    position: 'bottom-left',
    padding: '8px 12px',
    backgroundColor: 'white'
  }
});

legend.add(ui.Label('LULC Classification — Ladakh', {
  fontWeight: 'bold', fontSize: '14px', margin: '0 0 6px 0'
}));

classNames.forEach(function(name, index) {
  var row = ui.Panel({
    widgets: [
      ui.Label('', {
        backgroundColor: classPalette[index],
        padding: '10px 16px',
        margin: '2px 8px 2px 0',
        border: '1px solid gray'
      }),
      ui.Label(name, {margin: '4px 0', fontSize: '12px'})
    ],
    layout: ui.Panel.Layout.Flow('horizontal')
  });
  legend.add(row);
});

Map.add(legend);

// ============================================================
// SECTION 14: EXPORTS TO GOOGLE DRIVE
// ============================================================

// 14a. Classified LULC raster (GeoTIFF, 10m)
Export.image.toDrive({
  image: classified.toByte(),
  description: 'LULC_Seabuckthorn_Ladakh_Classified',
  folder: 'LULC_Seabuckthorn_Project',
  region: roi,
  scale: 10,
  crs: 'EPSG:4326',
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF'
});

// 14b. Accuracy assessment table (CSV)
Export.table.toDrive({
  collection: accuracyExport,
  description: 'Accuracy_Assessment_Table',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Class', 'Class_Value', 'Producers_Accuracy', 'Users_Accuracy']
});

// 14c. Class area statistics with percentages (CSV)
Export.table.toDrive({
  collection: areaTableFull,
  description: 'Class_Area_Statistics',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Class', 'Class_Value', 'Area_Hectares', 'Area_SqKm', 'Percentage']
});

// 14d. Multi-band input composite for reproducibility
Export.image.toDrive({
  image: inputImage.toFloat(),
  description: 'Input_Composite_Stack',
  folder: 'LULC_Seabuckthorn_Project',
  region: roi,
  scale: 10,
  crs: 'EPSG:4326',
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF'
});

print('============================================');
print('SCRIPT COMPLETE — Go to Tasks tab to export');
print('============================================');
