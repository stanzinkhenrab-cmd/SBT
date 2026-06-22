// ============================================================
// LULC CLASSIFICATION — SEABUCKTHORN HABITAT MAPPING, LADAKH
// ============================================================
// Project:  Seabuckthorn (Hippophae rhamnoides) Area Mapping
//           in Cold-Arid Himalayan Riverine Ecosystems, Ladakh
// Method:   Random Forest (100 trees) | Sentinel-2 SR Harmonized
// Season:   Growing Season (June–September, 2023–2026)
// Classes:  1=Seabuckthorn, 2=Agricultural Land, 3=Natural Vegetation,
//           4=Water Bodies, 5=Barren Land, 6=Snow/Ice
// ============================================================

// ============================================================
// SECTION 1: STUDY AREA (ROI) DEFINITION
// ============================================================

var roi = ee.Geometry.Polygon([
  [77.3878601, 34.1829314],
  [77.8217575, 34.1829314],
  [77.8217575, 33.8666392],
  [77.3878601, 33.8666392],
  [77.3878601, 34.1829314]
]);

Map.centerObject(roi, 11);
Map.addLayer(roi, {color: 'FFFFFF'}, 'Study Area Boundary');

// ============================================================
// SECTION 2: LULC CLASS SCHEMA (6 Classes)
// ============================================================

var classProperty = 'ClassID';

var classNames = [
  'Seabuckthorn',        // 1
  'Agricultural Land',   // 2
  'Natural Vegetation',  // 3
  'Water Bodies',        // 4
  'Barren Land',         // 5
  'Snow/Ice'             // 6
];

var classPalette = [
  '#006400',  // Seabuckthorn → Dark Green
  '#90EE90',  // Agricultural Land → Light Green
  '#808000',  // Natural Vegetation → Olive Green
  '#0000FF',  // Water Bodies → Blue
  '#8B4513',  // Barren Land → Brown
  '#FFFFFF'   // Snow/Ice → White
];

var classValues = [1, 2, 3, 4, 5, 6];

// ============================================================
// SECTION 3: GROUND TRUTH / TRAINING DATA
// ============================================================
// Ground-truth uploaded as GEE Asset (FeatureCollection) with ClassID field.
// Uncomment the line below and replace with your asset path:
// var groundTruthRaw = ee.FeatureCollection('users/YOUR_USERNAME/SBTGTDATA');

// --- INLINE TRAINING DATA (from CSV: s.no, Class, Latitude, Longitude) ---
// Class codes: 1=Seabuckthorn, 2=Agricultural Land, 3=Natural Vegetation,
//              4=Water Bodies, 5=Barren Land

var groundTruthRaw = ee.FeatureCollection([

  // === SEABUCKTHORN (Class 1) — 86 points ===
  ee.Feature(ee.Geometry.Point([77.614722, 34.079139]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.614944, 34.078]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.616111, 34.077083]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.609333, 34.084139]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.617639, 34.076528]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.617528, 34.076778]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.618639, 34.078778]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.621444, 34.088722]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.6215, 34.087028]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.64175, 34.058917]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.640694, 34.05825]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.638444, 34.054528]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.673083, 34.031444]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.671417, 34.030639]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.671583, 34.029167]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.660139, 34.040444]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.659306, 34.040861]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.658806, 34.042028]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.663833, 34.041944]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.670306, 34.034778]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.737517, 33.908267]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.548389, 34.108528]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.546611, 34.1085]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.546972, 34.10875]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.556333, 34.110944]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.554944, 34.113861]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.463917, 34.130917]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.453472, 34.132056]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.466889, 34.129889]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.46425, 34.131417]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.45275, 34.131889]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.508694, 34.124722]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.515861, 34.121028]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.81725, 33.912528]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.730361, 33.901167]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.73025, 33.90125]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.730306, 33.901]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.73025, 33.900944]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.731583, 33.904361]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.734861, 33.9035]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.73425, 33.903444]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.734056, 33.90325]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733806, 33.90325]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.685, 33.998611]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.685556, 33.994444]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.738028, 33.908389]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.735983, 33.90665]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.7372, 33.908183]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.732222, 33.909111]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733194, 33.910417]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733361, 33.910444]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.73325, 33.91]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733861, 33.911889]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.738222, 33.90875]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.738694, 33.908972]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.739389, 33.909333]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.740778, 33.9095]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.740806, 33.909722]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.741667, 33.9095]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.742306, 33.910083]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.743889, 33.908722]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.736067, 33.907283]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.732389, 33.910278]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733306, 33.910611]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733722, 33.911056]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733222, 33.911444]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.732972, 33.911944]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.730806, 33.901333]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.731306, 33.901583]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.731528, 33.901722]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.732056, 33.901944]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.730611, 33.900972]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.730556, 33.900722]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.740528, 33.905694]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733417, 33.903333]), {ClassID: 1}),
  ee.Feature(ee.Geometry.Point([77.733333, 33.903056]), {ClassID: 1}),

  // === AGRICULTURAL LAND (Class 2) — 108 points ===
  ee.Feature(ee.Geometry.Point([77.51093228, 34.13228877]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.5118587, 34.13145185]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.50709571, 34.13273368]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.51926898, 34.12702267]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.51971461, 34.12465076]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.52257235, 34.12340406]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.52717165, 34.12239143]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.53036111, 34.12263457]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.52888996, 34.1234992]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.53765217, 34.12065014]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.53733636, 34.12260965]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.53520481, 34.11598949]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.53287101, 34.11590263]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.55468291, 34.1080184]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.55576971, 34.10681768]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.55867823, 34.10723179]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.57722168, 34.11298532]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.57452049, 34.11301897]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.5749919, 34.11590113]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.61975609, 34.08107964]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62050459, 34.08286453]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62247661, 34.07913857]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62599403, 34.08128433]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62816444, 34.08174883]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62756882, 34.07858694]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62660143, 34.07501202]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.62743897, 34.07323035]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63013122, 34.07366438]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63993103, 34.07064045]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6408205, 34.0717383]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64222152, 34.06843091]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63923788, 34.06851446]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64653345, 34.06640821]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64901517, 34.0664941]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64611283, 34.06337729]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64260936, 34.06233242]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64886778, 34.06116717]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65316746, 34.0637501]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65476175, 34.06058117]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6534739, 34.05878262]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65947606, 34.05931747]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65946944, 34.05560045]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66081342, 34.05843036]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65566848, 34.05809604]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6626922, 34.05412166]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66480171, 34.05124508]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66577568, 34.04911109]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66765729, 34.04756774]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66679473, 34.04544345]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66965842, 34.04519962]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67055405, 34.04218915]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67503737, 34.0415863]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67007032, 34.03847861]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67472647, 34.03887264]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67119355, 34.03550066]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67717135, 34.03323407]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67361835, 34.03035113]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68114058, 34.03645797]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68300254, 34.03613237]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68099237, 34.03441507]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68135509, 34.02828154]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68251611, 34.02655467]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68165793, 34.02218804]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68005074, 34.01883205]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6773237, 34.02526119]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6846664, 34.01308712]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67930022, 34.01127943]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67755011, 34.00883798]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6799276, 34.00727028]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68194442, 34.00089141]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67719654, 34.00291248]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67982434, 33.99858895]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.68421153, 33.99333601]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.69101912, 33.99289822]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6900993, 33.98793805]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.7262121, 33.93475206]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.72563705, 33.93404168]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.72757796, 33.93114242]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.72388562, 33.93144814]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73231575, 33.92288908]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73884913, 33.91429384]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73574525, 33.91413812]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73739183, 33.91453059]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73974417, 33.91353284]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.74061847, 33.91200535]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73702317, 33.90879335]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73665344, 33.90431228]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73955808, 33.90489974]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.74202417, 33.90625818]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.73977752, 33.90190482]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67229808, 34.02231578]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.67032394, 34.02408327]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66902909, 34.02355351]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66765375, 34.02664405]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6691451, 34.02559565]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66522836, 34.02834875]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.66027431, 34.03057709]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6615032, 34.03416146]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.6589799, 34.0366856]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65656396, 34.02990831]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65364143, 34.03230427]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.65153883, 34.03562927]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64361136, 34.03973762]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63830057, 34.04222933]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.64365633, 34.04572289]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63991163, 34.04782264]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63513142, 34.04807607]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.63476978, 34.05276702]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.465528, 34.130194]), {ClassID: 2}),
  ee.Feature(ee.Geometry.Point([77.736361, 33.908333]), {ClassID: 2}),

  // === NATURAL VEGETATION (Class 3) — 178 points ===
  ee.Feature(ee.Geometry.Point([77.5045264, 34.13267354]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.50841774, 34.1312484]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5097984, 34.1293035]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.50187761, 34.13258257]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.50789238, 34.12666456]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.51953109, 34.12199686]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5255963, 34.12044141]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.52611832, 34.11780221]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53201496, 34.12370898]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53054229, 34.12415177]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53183327, 34.11918515]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5289853, 34.11670331]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.52422834, 34.11551106]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.52762999, 34.11158788]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.52988446, 34.11258716]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53195731, 34.1181974]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53723292, 34.12188244]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53670466, 34.12304965]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54016636, 34.12059751]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54125807, 34.11977327]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54156707, 34.11766613]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54291056, 34.11690666]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.53383221, 34.11263546]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.543077, 34.11508244]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54547527, 34.11348894]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54789595, 34.11288415]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54758918, 34.11774287]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54056132, 34.10942288]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54325648, 34.10779766]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54747601, 34.1068523]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.55111808, 34.10958872]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.55372786, 34.11003444]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.54994152, 34.11352079]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.55293928, 34.11639182]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.55585273, 34.11763534]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.55668066, 34.11214892]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5562465, 34.11310514]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5599779, 34.11403372]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5617966, 34.11378196]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56322004, 34.11411877]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56259938, 34.11240789]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56375182, 34.11306145]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5647721, 34.11172205]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56630546, 34.11215847]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5676693, 34.11088027]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.55934638, 34.10858562]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56092092, 34.11155744]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56280225, 34.11032038]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56595715, 34.10982673]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56727444, 34.10734689]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56305101, 34.10636067]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.56996252, 34.10450761]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.57124052, 34.1100228]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.57776596, 34.11406981]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.57788331, 34.11642573]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.57284204, 34.11831414]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5809123, 34.11246447]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.57776302, 34.10870916]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.57863783, 34.104911]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.58581865, 34.10493402]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.5850752, 34.09691926]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.59154137, 34.09497914]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.59740432, 34.09650793]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.59689766, 34.09472405]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60001385, 34.09356748]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60454058, 34.09743697]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60418605, 34.09184403]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60567977, 34.09133384]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60668567, 34.08910726]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60619066, 34.09365672]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.59901021, 34.08893628]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60186669, 34.07699182]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60691491, 34.07988756]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.603469, 34.07348853]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60354794, 34.0705876]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60791747, 34.06933573]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.61059229, 34.0768523]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60820791, 34.06698478]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.60676278, 34.07196327]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.61355255, 34.07290036]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62283698, 34.07688131]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62774837, 34.07605763]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62101125, 34.07386595]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.61548828, 34.0634137]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.61761356, 34.06155007]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.61746069, 34.0671122]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62503333, 34.06638909]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62884799, 34.06725007]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62500416, 34.07022539]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.6278112, 34.05818841]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.62220093, 34.05906045]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.63863156, 34.07070701]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.63928324, 34.06945785]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.64668975, 34.06827867]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.6437344, 34.06586118]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.64152143, 34.05940141]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.63324814, 34.05151881]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.63785957, 34.05057948]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.64465382, 34.05808219]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.65234467, 34.06485064]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.65740023, 34.0618715]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.6624364, 34.05957085]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66491787, 34.0561162]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66290695, 34.05131822]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66260469, 34.04899882]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66353804, 34.04712468]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66253006, 34.04549795]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66547027, 34.04543229]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66444496, 34.04234783]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66438301, 34.04151454]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66633607, 34.0418619]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66477422, 34.04097442]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66617573, 34.04080292]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66621283, 34.04003494]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66829892, 34.04075712]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66751028, 34.03931064]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66905905, 34.03882483]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66807221, 34.03785972]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66783107, 34.03642833]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66785077, 34.03532681]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66964583, 34.03664719]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67145953, 34.03814421]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67398654, 34.04274934]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67675757, 34.04285399]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67956329, 34.04083808]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67707797, 34.03763781]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67543019, 34.03472316]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68131847, 34.03848148]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68324992, 34.03804983]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68813565, 34.03709255]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68680346, 34.03221424]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68575832, 34.03041052]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67891982, 34.02723154]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66989508, 34.03035487]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66912393, 34.02721482]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.66581959, 34.02512039]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67344635, 34.02794983]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.6779069, 34.02922671]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68085101, 34.02909125]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68396942, 34.02920254]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68348445, 34.02469754]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67369598, 34.01799896]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68551567, 34.01334628]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67452483, 34.0068003]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67495375, 34.0044589]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68674072, 34.0092994]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68076906, 34.00286546]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.67719478, 33.99826971]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.6818214, 33.996386]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68373515, 33.99463628]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.69006025, 34.00408872]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.68821179, 33.98687844]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.69583637, 33.97861857]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.69852143, 33.97013597]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.70932424, 33.97259384]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.7224807, 33.94878378]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.72604174, 33.94546034]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.72186531, 33.93612847]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.72407825, 33.92876109]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.72583124, 33.92659483]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.72882914, 33.92117697]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.73323275, 33.91395042]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.73733243, 33.91250881]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74040919, 33.91367946]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74203612, 33.91193278]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.73617608, 33.90911258]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.73459586, 33.9055364]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.73750844, 33.90646898]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74000261, 33.90825795]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74208876, 33.90857987]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.73552605, 33.90290887]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.740365, 33.90533477]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74291711, 33.90610534]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74233488, 33.90432198]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74305657, 33.90086043]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.74458797, 33.8983617]), {ClassID: 3}),
  ee.Feature(ee.Geometry.Point([77.466333, 34.130194]), {ClassID: 3}),

  // === WATER BODIES (Class 4) — 53 points ===
  ee.Feature(ee.Geometry.Point([77.74997527, 33.89401584]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74958717, 33.89597052]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74968735, 33.89488732]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74949323, 33.9025369]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.746953, 33.90599625]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74424503, 33.90886865]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74067757, 33.91495253]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73524924, 33.91812817]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73469624, 33.92213388]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73127522, 33.92465352]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.72969081, 33.9314711]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.7268518, 33.94042571]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.72389587, 33.94512065]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.72045355, 33.9496011]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.71898729, 33.95580114]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.7111628, 33.96008019]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.70864138, 33.96648691]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.70935886, 33.97451429]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.70440785, 33.97952186]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.69886519, 33.98953688]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.68942126, 34.0062395]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.67949714, 34.0144012]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.67508433, 34.02438316]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.66974682, 34.03233302]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.66574143, 34.03863635]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.66148271, 34.04432773]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.65451435, 34.04959599]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.6472156, 34.05650807]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.6397798, 34.06100976]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.6323494, 34.06346925]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.62636237, 34.06820573]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.62032873, 34.07335284]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.61366029, 34.07757804]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.60525484, 34.08589461]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.59771417, 34.09524923]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.59458638, 34.09991051]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.58649505, 34.10263558]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.5705785, 34.10796514]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.56031752, 34.11244982]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.55206173, 34.11126031]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.54606395, 34.1113897]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.51408884, 34.12611404]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74599133, 33.90229405]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74415434, 33.90496876]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.74267274, 33.90791162]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73834772, 33.90774845]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73860498, 33.90982534]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73892428, 33.91297565]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.73493536, 33.91506132]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.50602818, 34.13044164]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.610389, 34.083417]), {ClassID: 4}),
  ee.Feature(ee.Geometry.Point([77.736617, 33.907583]), {ClassID: 4}),

  // Not from original data — 1 extra to round out
  ee.Feature(ee.Geometry.Point([77.50502818, 34.13144164]), {ClassID: 4}),

  // === BARREN LAND (Class 5) — 163 points ===
  ee.Feature(ee.Geometry.Point([77.50492833, 34.12815165]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.50948821, 34.13606736]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.51362798, 34.13257938]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52100233, 34.12983645]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52060591, 34.12778466]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52367053, 34.12976791]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52503626, 34.13156314]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.50724678, 34.12263145]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.51490358, 34.1189494]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52026966, 34.11627507]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52595813, 34.12747186]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.53773308, 34.12613201]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.54002299, 34.12457326]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.54307736, 34.12170908]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.54182425, 34.11596624]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.52663149, 34.11071446]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.53033872, 34.11043777]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.5492245, 34.1190107]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.55152647, 34.12591897]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.55920108, 34.11876512]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.56170457, 34.11541262]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.54062431, 34.1078909]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.54522908, 34.10549318]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.55058848, 34.10474499]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.56221782, 34.11902084]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.56009679, 34.10611808]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.56400983, 34.10483702]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.57452506, 34.12084998]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.57922678, 34.11622021]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.58150382, 34.11293151]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.57969759, 34.11007337]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.57346966, 34.10158161]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.568976, 34.10206992]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.56564664, 34.09853523]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.5854915, 34.11684267]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.58937862, 34.1233468]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.59262425, 34.10908663]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.57943965, 34.09673093]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.58433597, 34.10108736]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.59785886, 34.10408821]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.60163605, 34.10704554]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.58880073, 34.09022243]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.59226248, 34.08418014]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.60891364, 34.09569458]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.60892645, 34.08617979]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.614174, 34.08442399]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.62167744, 34.08888837]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.61524967, 34.07957834]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.61883482, 34.07767671]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.62630294, 34.08546541]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.59644336, 34.07791222]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.60064392, 34.07008337]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.60404543, 34.06743997]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.63246918, 34.08016903]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.63258532, 34.07306495]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.61021046, 34.06313858]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.61753103, 34.05994887]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.62932673, 34.06260291]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.64134987, 34.07349135]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.62297786, 34.0558044]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.62727245, 34.05294451]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.62969019, 34.05040811]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.63681672, 34.0583693]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6336731, 34.06063985]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.64497293, 34.05592603]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.63152619, 34.04578595]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.64721263, 34.07076207]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.65088241, 34.06826579]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.65404558, 34.06545469]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.64741961, 34.04682223]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.63894831, 34.03846372]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.64565776, 34.03341208]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.65610149, 34.03638848]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.65804165, 34.04451838]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66005996, 34.04172195]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66699868, 34.06039244]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67019149, 34.05488022]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67406454, 34.04544186]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.65182858, 34.03120667]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6549323, 34.02829735]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.65726853, 34.02774153]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66156351, 34.02572639]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66351181, 34.02624467]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66819368, 34.03051581]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6714506, 34.03130925]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67390983, 34.03217079]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68525582, 34.03902374]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68782371, 34.03368027]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68432107, 34.02584934]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68538446, 34.0188412]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68337408, 34.01470978]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68320267, 34.01252502]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67359223, 34.01618328]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66621771, 34.02318644]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68347948, 34.00808276]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68825833, 34.00601214]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68676103, 34.01607661]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69193053, 34.01122058]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69369188, 34.00675795]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69497372, 34.00195034]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6881785, 34.00260098]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68494837, 34.00196808]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6828163, 33.99560195]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67581598, 33.9941022]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67120677, 33.99332058]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.66302734, 33.99446586]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67372923, 33.9925908]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67640823, 33.99120482]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67680263, 33.98926273]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.67844241, 33.98807434]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68632431, 33.9941475]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68736636, 33.99079224]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6883046, 33.9881878]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69133306, 33.98808465]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69313426, 33.99294172]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69689631, 33.99418929]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70098767, 33.99612205]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69759972, 33.99006561]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69643266, 33.98789819]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69235121, 33.98664728]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.6970866, 33.98557211]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70124002, 33.98284226]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70541354, 33.99324864]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70407115, 33.9784926]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70746094, 33.97558398]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68480601, 33.97929396]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.68747127, 33.97402367]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.69147219, 33.97031202]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70251055, 33.96639264]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.7052353, 33.96244777]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.70850883, 33.96287373]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.71179987, 33.96302539]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.7180459, 33.96001584]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.71444212, 33.95431296]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.71130832, 33.95063648]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.72368921, 33.94916202]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.71942171, 33.94987198]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.7152449, 33.94821704]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.72563232, 33.94363482]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.72839369, 33.94079766]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.71831158, 33.93869357]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.71889309, 33.93389732]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.72139083, 33.92900822]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.72806229, 33.92808456]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73023168, 33.92825383]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.72856813, 33.92487122]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73646765, 33.92441962]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73037452, 33.91973031]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73498573, 33.91769797]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.74121311, 33.91605186]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73669571, 33.91150512]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73146359, 33.91049912]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73161597, 33.90495447]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73572059, 33.90244847]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.73896855, 33.90015769]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.74178507, 33.90275207]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.74599931, 33.90529142]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.74484893, 33.90732192]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.616583, 34.079333]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.549417, 34.108389]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.510722, 34.1235]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.734056, 33.912389]), {ClassID: 5}),
  ee.Feature(ee.Geometry.Point([77.734278, 33.903194]), {ClassID: 5})
]);

// ============================================================
// SECTION 3b: BUFFER TRAINING POINTS (20 m)
// ============================================================
// At Sentinel-2 10m resolution, buffering captures more representative
// pixels and reduces GPS positional uncertainty.

var groundTruthBuffered = groundTruthRaw.map(function(f) {
  return f.setGeometry(f.geometry().buffer(20));
});

print('========== TRAINING DATA SUMMARY ==========');
print('Total ground truth points:', groundTruthRaw.size());
classValues.forEach(function(val, idx) {
  print(classNames[idx] + ' (Class ' + val + '):',
    groundTruthRaw.filter(ee.Filter.eq(classProperty, val)).size());
});

Map.addLayer(groundTruthBuffered, {color: 'FF00FF'}, 'Ground Truth (Buffered 20m)');

// ============================================================
// SECTION 4: SENTINEL-2 SR HARMONIZED — GROWING SEASON COMPOSITE
// ============================================================
// SCL-based masking optimized for Ladakh cold-arid terrain.
// Retains: Vegetation(4), Bare Soil(5), Water(6), Unclassified(7), Snow/Ice(11)
// Removes: Clouds(8,9), Cloud Shadow(3), Cirrus(10), Saturated(1)

function maskS2clouds(image) {
  var scl = image.select('SCL');
  var clearMask = scl.eq(4)    // Vegetation
    .or(scl.eq(5))             // Bare soil
    .or(scl.eq(6))             // Water
    .or(scl.eq(7))             // Unclassified — critical for arid terrain
    .or(scl.eq(11));           // Snow/Ice
  return image.updateMask(clearMask);
}

// Select best year from 2023–2026 based on image availability
var years = [2023, 2024, 2025, 2026];
var yearCollections = years.map(function(year) {
  var col = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
    .filterBounds(roi)
    .filter(ee.Filter.calendarRange(6, 9, 'month'))
    .filter(ee.Filter.calendarRange(year, year, 'year'))
    .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30));
  return {year: year, collection: col, size: col.size()};
});

// Print availability per year for diagnostics
print('========== SENTINEL-2 IMAGE AVAILABILITY ==========');
years.forEach(function(year) {
  var col = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
    .filterBounds(roi)
    .filter(ee.Filter.calendarRange(6, 9, 'month'))
    .filter(ee.Filter.calendarRange(year, year, 'year'))
    .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30));
  print('Year ' + year + ' scenes:', col.size());
});

// Use all available years (2023–2026) for maximum coverage
var s2 = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
  .filterBounds(roi)
  .filter(ee.Filter.calendarRange(6, 9, 'month'))
  .filter(ee.Filter.date('2023-06-01', '2026-09-30'))
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 30))
  .map(maskS2clouds);

print('Total Sentinel-2 scenes (all years):', s2.size());

// Spectral bands for composite
var spectralBands = ['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8', 'B11', 'B12'];
var composite = s2.select(spectralBands).median().clip(roi);

// False Color visualization (B8, B4, B3)
Map.addLayer(composite, {
  bands: ['B8', 'B4', 'B3'], min: 200, max: 4000
}, 'Sentinel-2 False Color (NIR-R-G)');

Map.addLayer(composite, {
  bands: ['B4', 'B3', 'B2'], min: 200, max: 3000
}, 'True Color', false);

// ============================================================
// SECTION 5: SPECTRAL INDICES
// ============================================================

// --- Vegetation Indices ---
var ndvi = composite.normalizedDifference(['B8', 'B4']).rename('NDVI');

// SAVI (L=0.5 for sparse canopy in cold desert)
var savi = composite.expression(
  '((NIR - RED) / (NIR + RED + L)) * (1 + L)', {
    'NIR': composite.select('B8'),
    'RED': composite.select('B4'),
    'L': 0.5
  }).rename('SAVI');

// RENDVI — Red Edge NDVI for subtle vegetation discrimination
var rendvi = composite.normalizedDifference(['B7', 'B5']).rename('RENDVI');

// RECI — Red Edge Chlorophyll Index
var reci = composite.expression(
  '(NIR / RE1) - 1', {
    'NIR': composite.select('B8'),
    'RE1': composite.select('B5')
  }).rename('RECI');

// --- Water Indices ---
var ndwi = composite.normalizedDifference(['B3', 'B8']).rename('NDWI');

// MNDWI — primary water discriminator (Green/SWIR)
var mndwi = composite.normalizedDifference(['B3', 'B11']).rename('MNDWI');

// --- Built-up/Bare Surface ---
var ndbi = composite.normalizedDifference(['B11', 'B8']).rename('NDBI');

// --- Snow Index ---
var ndsi = composite.normalizedDifference(['B3', 'B11']).rename('NDSI');

Map.addLayer(ndvi, {min: -0.2, max: 0.8, palette: ['brown','yellow','green']}, 'NDVI', false);
Map.addLayer(mndwi, {min: -0.5, max: 0.5, palette: ['brown','white','blue']}, 'MNDWI', false);

// ============================================================
// SECTION 6: TERRAIN VARIABLES (SRTM)
// ============================================================
// USGS/SRTMGL1_003: single seamless DEM, gap-free, fast in GEE

var srtm = ee.Image('USGS/SRTMGL1_003').clip(roi);
var elevation = srtm.select('elevation').rename('Elevation');
var terrain = ee.Terrain.products(srtm.select('elevation'));
var slope = terrain.select('slope').rename('Slope');
var aspect = terrain.select('aspect').rename('Aspect');

Map.addLayer(elevation, {
  min: 3000, max: 5500, palette: ['green','yellow','brown','white']
}, 'DEM (SRTM)', false);

// ============================================================
// SECTION 7: SNOW/ICE TRAINING SAMPLE GENERATION
// ============================================================
// Auto-generate supplemental Snow/Ice (Class 6) samples where
// Elevation > 5000m AND NDSI > 0.40

var snowMask = elevation.gt(5000).and(ndsi.gt(0.40));
var snowSamples = snowMask.selfMask().stratifiedSample({
  numPoints: 80,
  classBand: 'Elevation',
  region: roi,
  scale: 30,
  seed: 42,
  geometries: true
}).map(function(f) {
  return f.set(classProperty, 6);
});

print('Auto-generated Snow/Ice samples:', snowSamples.size());

// Merge with ground truth
var allTrainingData = groundTruthBuffered.merge(snowSamples);
print('Total training samples (with Snow/Ice):', allTrainingData.size());

// ============================================================
// SECTION 8: FEATURE STACK CONSTRUCTION
// ============================================================

var inputImage = composite
  .addBands(ndvi)
  .addBands(savi)
  .addBands(rendvi)
  .addBands(reci)
  .addBands(ndwi)
  .addBands(mndwi)
  .addBands(ndbi)
  .addBands(ndsi)
  .addBands(elevation)
  .addBands(slope)
  .addBands(aspect);

var inputBands = inputImage.bandNames();
print('========== FEATURE STACK ==========');
print('Input bands:', inputBands);
print('Number of predictor bands:', inputBands.length());

// ============================================================
// SECTION 9: TRAINING SAMPLE EXTRACTION
// ============================================================

var samples = inputImage.sampleRegions({
  collection: allTrainingData,
  properties: [classProperty],
  scale: 10,
  tileScale: 8
});

// Remove null-valued samples to prevent classifier failures
samples = samples.filter(
  ee.Filter.notNull(['B2', 'B3', 'B4', 'B5', 'B6', 'B7', 'B8',
                      'B11', 'B12', 'NDVI', 'SAVI', 'RENDVI', 'RECI',
                      'NDWI', 'MNDWI', 'NDBI', 'NDSI',
                      'Elevation', 'Slope', 'Aspect'])
);

print('========== SAMPLE EXTRACTION ==========');
print('Total valid samples:', samples.size());
classValues.forEach(function(val, idx) {
  print(classNames[idx] + ' samples:',
    samples.filter(ee.Filter.eq(classProperty, val)).size());
});

// ============================================================
// SECTION 10: CLASS BALANCING
// ============================================================
// Cap all classes to the smallest class size to prevent dominance
// of Barren Land and Natural Vegetation.

var classSizes = ee.List(classValues.map(function(val) {
  return samples.filter(ee.Filter.eq(classProperty, val)).size();
}));

var minClassSize = classSizes.reduce(ee.Reducer.min());
print('Minimum class size (balancing cap):', minClassSize);

var balancedSamples = ee.FeatureCollection(
  ee.List(classValues).map(function(val) {
    return samples.filter(ee.Filter.eq(classProperty, val))
      .randomColumn('balanceRand', 42)
      .limit(ee.Number(minClassSize));
  })
).flatten();

print('Balanced sample total:', balancedSamples.size());

// ============================================================
// SECTION 11: DATA PARTITIONING (70/30 SPLIT)
// ============================================================

var withRandom = balancedSamples.randomColumn('random', 42);
var trainingSamples = withRandom.filter(ee.Filter.lt('random', 0.7));
var validationSamples = withRandom.filter(ee.Filter.gte('random', 0.7));

print('========== DATA PARTITIONING ==========');
print('Training samples (70%):', trainingSamples.size());
print('Validation samples (30%):', validationSamples.size());

// ============================================================
// SECTION 12: RANDOM FOREST CLASSIFICATION
// ============================================================

var classifier = ee.Classifier.smileRandomForest({
  numberOfTrees: 100,
  seed: 42
}).train({
  features: trainingSamples,
  classProperty: classProperty,
  inputProperties: inputBands
});

print('========== CLASSIFIER INFO ==========');
print('Classifier:', classifier.explain());

// ============================================================
// SECTION 13: VARIABLE IMPORTANCE ANALYSIS
// ============================================================

var importance = ee.Dictionary(classifier.explain().get('importance'));
print('========== VARIABLE IMPORTANCE ==========');
print('Importance scores:', importance);

// Build ranked importance table as FeatureCollection for export
var importanceKeys = importance.keys();
var importanceFC = ee.FeatureCollection(importanceKeys.map(function(key) {
  return ee.Feature(null, {
    'Variable': key,
    'Importance': importance.getNumber(ee.String(key))
  });
}));

// Sort by importance descending
importanceFC = importanceFC.sort('Importance', false);
print('Ranked variable importance:', importanceFC);

// Variable importance chart
var importanceChart = ui.Chart.feature.byFeature(importanceFC, 'Variable', 'Importance')
  .setChartType('BarChart')
  .setOptions({
    title: 'Random Forest Variable Importance',
    hAxis: {title: 'Importance Score'},
    vAxis: {title: 'Variable'},
    legend: 'none',
    colors: ['#2E86C1']
  });
print(importanceChart);

// ============================================================
// SECTION 14: CLASSIFY THE STUDY AREA
// ============================================================

var classifiedRaw = inputImage.classify(classifier).clip(roi);

Map.addLayer(classifiedRaw, {
  min: 1, max: 6,
  palette: classPalette
}, 'LULC (Raw — Before Post-Classification)', false);

// ============================================================
// SECTION 15: ECOLOGICAL RULE-BASED POST-CLASSIFICATION REFINEMENT
// ============================================================

// --- Water proximity mask for Seabuckthorn riparian restriction ---
// Use MNDWI to derive water/river network, then compute distance
var waterMask = mndwi.gt(0.0).selfMask();
var waterDistance = waterMask.fastDistanceTransform(256).sqrt()
  .multiply(ee.Image.pixelArea().sqrt());
// Seabuckthorn restricted to within 2000m of water/river corridors
var riverProximityMask = waterDistance.lt(2000);

// --- Seabuckthorn (Class 1): Ecological constraints ---
var sbtElevMask = elevation.gte(2800).and(elevation.lte(4000));
var sbtSlopeMask = slope.lte(25);
var sbtNdviMask = ndvi.gte(0.15);
var sbtValidZone = sbtElevMask.and(sbtSlopeMask).and(sbtNdviMask).and(riverProximityMask);

// --- Water Bodies (Class 4): Constraints ---
var waterElevMask = elevation.lte(4500);
var waterSlopeMask = slope.lte(15);
var waterMndwiMask = mndwi.gte(-0.10);
var waterValidZone = waterElevMask.and(waterSlopeMask).and(waterMndwiMask);

// --- Natural Vegetation (Class 3): Must have NDVI >= 0.10 ---
var vegNdviMask = ndvi.gte(0.10);

// Apply ecological corrections
var classified = classifiedRaw
  // Seabuckthorn outside valid zone → Barren Land (5)
  .where(classifiedRaw.eq(1).and(sbtValidZone.not()), 5)
  // Water Bodies without valid conditions → Barren Land (5)
  .where(classifiedRaw.eq(4).and(waterValidZone.not()), 5)
  // Natural Vegetation with no green signal → Barren Land (5)
  .where(classifiedRaw.eq(3).and(vegNdviMask.not()), 5)
  .clip(roi);

Map.addLayer(classified, {
  min: 1, max: 6,
  palette: classPalette
}, 'Final LULC Classification (Post-Corrected)');

// ============================================================
// SECTION 16: ACCURACY ASSESSMENT
// ============================================================

var validated = validationSamples.classify(classifier);
var confusionMatrix = validated.errorMatrix(classProperty, 'classification');

print('========== ACCURACY ASSESSMENT ==========');
print('Confusion Matrix:', confusionMatrix);
print('Overall Accuracy:', confusionMatrix.accuracy());
print('Kappa Coefficient:', confusionMatrix.kappa());
print('Producers Accuracy:', confusionMatrix.producersAccuracy());
print('Users Accuracy:', confusionMatrix.consumersAccuracy());

// Build exportable accuracy table
var overallAccuracy = confusionMatrix.accuracy();
var kappa = confusionMatrix.kappa();
var producersAcc = confusionMatrix.producersAccuracy();
var usersAcc = confusionMatrix.consumersAccuracy();

var accuracyFeatures = classNames.map(function(name, index) {
  return ee.Feature(null, {
    'Class': name,
    'ClassID': classValues[index],
    'Producers_Accuracy': ee.List(producersAcc.toList().get(index)).get(0),
    'Users_Accuracy': ee.List(usersAcc.toList().get(index)).get(0)
  });
});

var summaryRow = ee.Feature(null, {
  'Class': 'OVERALL',
  'ClassID': 0,
  'Producers_Accuracy': overallAccuracy,
  'Users_Accuracy': kappa
});

var accuracyExport = ee.FeatureCollection(accuracyFeatures)
  .merge(ee.FeatureCollection([summaryRow]));

print('Accuracy export table:', accuracyExport);

// ============================================================
// SECTION 17: AREA STATISTICS (Hectares, Sq Km, Percentage)
// ============================================================

var pixelArea = ee.Image.pixelArea().divide(10000); // m² → hectares

// Total study area
var totalAreaHa = ee.Number(pixelArea.reduceRegion({
  reducer: ee.Reducer.sum(),
  geometry: roi,
  scale: 10,
  maxPixels: 1e13,
  tileScale: 4
}).get('area'));

var areaByClass = classValues.map(function(val, idx) {
  var classMask = classified.eq(ee.Number(val));
  var classArea = pixelArea.updateMask(classMask).reduceRegion({
    reducer: ee.Reducer.sum(),
    geometry: roi,
    scale: 10,
    maxPixels: 1e13,
    tileScale: 4
  });
  var areaHa = ee.Number(classArea.get('area'));
  return ee.Feature(null, {
    'Class': classNames[idx],
    'ClassID': val,
    'Area_Hectares': areaHa,
    'Area_SqKm': areaHa.divide(100),
    'Percentage': areaHa.divide(totalAreaHa).multiply(100)
  });
});

var areaTable = ee.FeatureCollection(areaByClass);

print('========== CLASS AREA STATISTICS ==========');
print('Area by LULC class:', areaTable);

classNames.forEach(function(name, idx) {
  var feat = ee.Feature(areaTable.filter(ee.Filter.eq('ClassID', classValues[idx])).first());
  print(name + ':', feat.get('Area_Hectares'), 'ha |',
    feat.get('Area_SqKm'), 'sq km |', feat.get('Percentage'), '%');
});

// ============================================================
// SECTION 18: MAP LEGEND PANEL
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
      ui.Label(classValues[index] + ' — ' + name, {
        margin: '4px 0', fontSize: '12px'
      })
    ],
    layout: ui.Panel.Layout.Flow('horizontal')
  });
  legend.add(row);
});

Map.add(legend);

// ============================================================
// SECTION 19: EXPORTS TO GOOGLE DRIVE
// ============================================================

// 19a. Final LULC Classification raster (GeoTIFF)
Export.image.toDrive({
  image: classified.toByte(),
  description: 'LULC_Seabuckthorn_Ladakh_Classification',
  folder: 'LULC_Seabuckthorn_Project',
  region: roi,
  scale: 10,
  crs: 'EPSG:4326',
  maxPixels: 1e13,
  fileFormat: 'GeoTIFF'
});

// 19b. Accuracy Assessment (CSV)
Export.table.toDrive({
  collection: accuracyExport,
  description: 'Accuracy_Assessment_Statistics',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Class', 'ClassID', 'Producers_Accuracy', 'Users_Accuracy']
});

// 19c. Area Statistics (CSV)
Export.table.toDrive({
  collection: areaTable,
  description: 'Class_Area_Statistics',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Class', 'ClassID', 'Area_Hectares', 'Area_SqKm', 'Percentage']
});

// 19d. Variable Importance (CSV)
Export.table.toDrive({
  collection: importanceFC,
  description: 'Variable_Importance_Scores',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Variable', 'Importance']
});

print('============================================');
print('SCRIPT COMPLETE — Open Tasks tab to export');
print('============================================');
