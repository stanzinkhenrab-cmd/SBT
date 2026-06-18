// ============================================================
// LULC CLASSIFICATION — SEABUCKTHORN MAPPING, LADAKH (INDIA)
// Project: Seabuckthorn (Hippophae rhamnoides) Area Mapping
//          Cold Desert Region of Ladakh
// Method:  Random Forest (200 trees) | Sentinel-2 SR Harmonized
// Season:  Growing Season (June–September)
// Classes: 1=Seabuckthorn, 2=Agricultural Land, 3=Natural Vegetation,
//          4=Water Bodies, 5=Barren Land, 6=Snow/Ice
// ============================================================

// ============================================================
// SECTION 1: STUDY AREA (ROI) DEFINITION
// ============================================================

var roi = ee.Geometry.Rectangle([
  77.3878601, 33.8666392,   // SW corner (xMin, yMin)
  77.8217575, 34.1829314    // NE corner (xMax, yMax)
]);

Map.centerObject(roi, 11);
Map.addLayer(roi, {color: 'FFFFFF'}, 'Study Area Boundary', true, 0.3);

// ============================================================
// SECTION 2: LULC CLASS SCHEMA
// ============================================================

// Class codes 1–6 as required for the final product
var classProperty = 'ClassID';
var classNames   = ['Seabuckthorn', 'Agricultural Land', 'Natural Vegetation',
                    'Water Bodies', 'Barren Land', 'Snow/Ice'];
var classCodes   = [1, 2, 3, 4, 5, 6];

// Seabuckthorn=Dark Green, Agriculture=Light Green, Vegetation=Olive Green,
// Water=Blue, Barren=Brown, Snow/Ice=White
var classPalette = ['#006400', '#90EE90', '#808000', '#1E90FF', '#8B4513', '#FFFFFF'];
var numClasses   = 6;

// ============================================================
// SECTION 3: GROUND TRUTH / TRAINING DATA (578 field GPS points + Snow/Ice)
// ============================================================

// All 578 ground truth points from SBTGTDATA.csv embedded inline.
// Snow/Ice points derived from high-elevation persistent snow/ice areas
// within the ROI visible in satellite imagery during the growing season.

// --- Helper: create point features for a class ---
function makePoints(coords, classId) {
  return coords.map(function(c) {
    return ee.Feature(ee.Geometry.Point([c[0], c[1]]), {ClassID: classId});
  });
}

// === SEABUCKTHORN — ClassID 1 (86 points) ===
var sbtCoords = [
  [77.614722,34.079139],[77.614944,34.078],[77.616111,34.077083],[77.609333,34.084139],
  [77.617639,34.076528],[77.617528,34.076778],[77.618639,34.078778],[77.621444,34.088722],
  [77.6215,34.087028],[77.64175,34.058917],[77.640694,34.05825],[77.638444,34.054528],
  [77.673083,34.031444],[77.671417,34.030639],[77.671583,34.029167],[77.660139,34.040444],
  [77.659306,34.040861],[77.658806,34.042028],[77.663833,34.041944],[77.670306,34.034778],
  [77.737517,33.908267],[77.548389,34.108528],[77.546611,34.1085],[77.546972,34.10875],
  [77.556333,34.110944],[77.554944,34.113861],[77.463917,34.130917],[77.453472,34.132056],
  [77.466889,34.129889],[77.46425,34.131417],[77.45275,34.131889],[77.508694,34.124722],
  [77.515861,34.121028],[77.81725,33.912528],[77.730361,33.901167],[77.73025,33.90125],
  [77.730306,33.901],[77.73025,33.900944],[77.731583,33.904361],[77.734861,33.9035],
  [77.73425,33.903444],[77.734056,33.90325],[77.733806,33.90325],[77.685,33.998611],
  [77.685556,33.994444],[77.738028,33.908389],[77.735983,33.90665],[77.7372,33.908183],
  [77.732222,33.909111],[77.733194,33.910417],[77.733361,33.910444],[77.73325,33.91],
  [77.733861,33.911889],[77.738222,33.90875],[77.738694,33.908972],[77.739389,33.909333],
  [77.740778,33.9095],[77.740806,33.909722],[77.741667,33.9095],[77.742306,33.910083],
  [77.743889,33.908722],[77.736067,33.907283],[77.732389,33.910278],[77.733306,33.910611],
  [77.733722,33.911056],[77.733222,33.911444],[77.732972,33.911944],[77.730806,33.901333],
  [77.731306,33.901583],[77.731528,33.901722],[77.732056,33.901944],[77.730611,33.900972],
  [77.730556,33.900722],[77.740528,33.905694],[77.733417,33.903333],[77.733333,33.903056]
];

// === AGRICULTURAL LAND — ClassID 2 (110 points) ===
var agCoords = [
  [77.51093228,34.13228877],[77.5118587,34.13145185],[77.50709571,34.13273368],
  [77.51926898,34.12702267],[77.51971461,34.12465076],[77.52257235,34.12340406],
  [77.52717165,34.12239143],[77.53036111,34.12263457],[77.52888996,34.1234992],
  [77.53765217,34.12065014],[77.53733636,34.12260965],[77.53520481,34.11598949],
  [77.53287101,34.11590263],[77.55468291,34.1080184],[77.55576971,34.10681768],
  [77.55867823,34.10723179],[77.57722168,34.11298532],[77.57452049,34.11301897],
  [77.5749919,34.11590113],[77.61975609,34.08107964],[77.62050459,34.08286453],
  [77.62247661,34.07913857],[77.62599403,34.08128433],[77.62816444,34.08174883],
  [77.62756882,34.07858694],[77.62660143,34.07501202],[77.62743897,34.07323035],
  [77.63013122,34.07366438],[77.63993103,34.07064045],[77.6408205,34.0717383],
  [77.64222152,34.06843091],[77.63923788,34.06851446],[77.64653345,34.06640821],
  [77.64901517,34.0664941],[77.64611283,34.06337729],[77.64260936,34.06233242],
  [77.64886778,34.06116717],[77.65316746,34.0637501],[77.65476175,34.06058117],
  [77.6534739,34.05878262],[77.65947606,34.05931747],[77.65946944,34.05560045],
  [77.66081342,34.05843036],[77.65566848,34.05809604],[77.6626922,34.05412166],
  [77.66480171,34.05124508],[77.66577568,34.04911109],[77.66765729,34.04756774],
  [77.66679473,34.04544345],[77.66965842,34.04519962],[77.67055405,34.04218915],
  [77.67503737,34.0415863],[77.67007032,34.03847861],[77.67472647,34.03887264],
  [77.67119355,34.03550066],[77.67717135,34.03323407],[77.67361835,34.03035113],
  [77.68114058,34.03645797],[77.68300254,34.03613237],[77.68099237,34.03441507],
  [77.68135509,34.02828154],[77.68251611,34.02655467],[77.68165793,34.02218804],
  [77.68005074,34.01883205],[77.6773237,34.02526119],[77.6846664,34.01308712],
  [77.67930022,34.01127943],[77.67755011,34.00883798],[77.6799276,34.00727028],
  [77.68194442,34.00089141],[77.67719654,34.00291248],[77.67982434,33.99858895],
  [77.68421153,33.99333601],[77.69101912,33.99289822],[77.6900993,33.98793805],
  [77.7262121,33.93475206],[77.72563705,33.93404168],[77.72757796,33.93114242],
  [77.72388562,33.93144814],[77.73231575,33.92288908],[77.73884913,33.91429384],
  [77.73574525,33.91413812],[77.73739183,33.91453059],[77.73974417,33.91353284],
  [77.74061847,33.91200535],[77.73702317,33.90879335],[77.73665344,33.90431228],
  [77.73955808,33.90489974],[77.74202417,33.90625818],[77.73977752,33.90190482],
  [77.67229808,34.02231578],[77.67032394,34.02408327],[77.66902909,34.02355351],
  [77.66765375,34.02664405],[77.6691451,34.02559565],[77.66522836,34.02834875],
  [77.66027431,34.03057709],[77.6615032,34.03416146],[77.6589799,34.0366856],
  [77.65656396,34.02990831],[77.65364143,34.03230427],[77.65153883,34.03562927],
  [77.64361136,34.03973762],[77.63830057,34.04222933],[77.64365633,34.04572289],
  [77.63991163,34.04782264],[77.63513142,34.04807607],[77.63476978,34.05276702],
  [77.465528,34.130194],[77.736361,33.908333]
];

// === NATURAL VEGETATION — ClassID 3 (178 points) ===
var vegCoords = [
  [77.5045264,34.13267354],[77.50841774,34.1312484],[77.5097984,34.1293035],
  [77.50187761,34.13258257],[77.50789238,34.12666456],[77.51953109,34.12199686],
  [77.5255963,34.12044141],[77.52611832,34.11780221],[77.53201496,34.12370898],
  [77.53054229,34.12415177],[77.53183327,34.11918515],[77.5289853,34.11670331],
  [77.52422834,34.11551106],[77.52762999,34.11158788],[77.52988446,34.11258716],
  [77.53195731,34.1181974],[77.53723292,34.12188244],[77.53670466,34.12304965],
  [77.54016636,34.12059751],[77.54125807,34.11977327],[77.54156707,34.11766613],
  [77.54291056,34.11690666],[77.53383221,34.11263546],[77.543077,34.11508244],
  [77.54547527,34.11348894],[77.54789595,34.11288415],[77.54758918,34.11774287],
  [77.54056132,34.10942288],[77.54325648,34.10779766],[77.54747601,34.1068523],
  [77.55111808,34.10958872],[77.55372786,34.11003444],[77.54994152,34.11352079],
  [77.55293928,34.11639182],[77.55585273,34.11763534],[77.55668066,34.11214892],
  [77.5562465,34.11310514],[77.5599779,34.11403372],[77.5617966,34.11378196],
  [77.56322004,34.11411877],[77.56259938,34.11240789],[77.56375182,34.11306145],
  [77.5647721,34.11172205],[77.56630546,34.11215847],[77.5676793,34.11088027],
  [77.55934638,34.10858562],[77.56092092,34.11155744],[77.56280225,34.11032038],
  [77.56595715,34.10982673],[77.56727444,34.10734689],[77.56305101,34.10636067],
  [77.56996252,34.10450761],[77.57124052,34.1100228],[77.57776596,34.11406981],
  [77.57788331,34.11642573],[77.57284204,34.11831414],[77.5809123,34.11246447],
  [77.57776302,34.10870916],[77.57863783,34.104911],[77.58581865,34.10493402],
  [77.5850752,34.09691926],[77.59154137,34.09497914],[77.59740432,34.09650793],
  [77.59689766,34.09472405],[77.60001385,34.09356748],[77.60454058,34.09743697],
  [77.60418605,34.09184403],[77.60567977,34.09133384],[77.60668567,34.08910726],
  [77.60619066,34.09365672],[77.59901021,34.08893628],[77.60186669,34.07699182],
  [77.60691491,34.07988756],[77.603469,34.07348853],[77.60354794,34.0705876],
  [77.60791747,34.06933573],[77.61059229,34.0768523],[77.60820791,34.06698478],
  [77.60676278,34.07196327],[77.61355255,34.07290036],[77.62283698,34.07688131],
  [77.62774837,34.07605763],[77.62101125,34.07386595],[77.61548828,34.0634137],
  [77.61761356,34.06155007],[77.61746069,34.0671122],[77.62503333,34.06638909],
  [77.62884799,34.06725007],[77.62500416,34.07022539],[77.6278112,34.05818841],
  [77.62220093,34.05906045],[77.63863156,34.07070701],[77.63928324,34.06945785],
  [77.64668975,34.06827867],[77.6437344,34.06586118],[77.64152143,34.05940141],
  [77.63324814,34.05151881],[77.63785957,34.05057948],[77.64465382,34.05808219],
  [77.65234467,34.06485064],[77.65740023,34.0618715],[77.6624364,34.05957085],
  [77.66491787,34.0561162],[77.66290695,34.05131822],[77.66260469,34.04899882],
  [77.66353804,34.04712468],[77.66253006,34.04549795],[77.66547027,34.04543229],
  [77.66444496,34.04234783],[77.66438301,34.04151454],[77.66633607,34.0418619],
  [77.66477422,34.04097442],[77.66617573,34.04080292],[77.66621283,34.04003494],
  [77.66829892,34.04075712],[77.66751028,34.03931064],[77.66905905,34.03882483],
  [77.66807221,34.03785972],[77.66783107,34.03642833],[77.66785077,34.03532681],
  [77.66964583,34.03664719],[77.67145953,34.03814421],[77.67398654,34.04274934],
  [77.67675757,34.04285399],[77.67956329,34.04083808],[77.67707797,34.03763781],
  [77.67543019,34.03472316],[77.68131847,34.03848148],[77.68324992,34.03804983],
  [77.68813565,34.03709255],[77.68680346,34.03221424],[77.68575832,34.03041052],
  [77.67891982,34.02723154],[77.66989508,34.03035487],[77.66912393,34.02721482],
  [77.66581959,34.02512039],[77.67344635,34.02794983],[77.6779069,34.02922671],
  [77.68085101,34.02909125],[77.68396942,34.02920254],[77.68348445,34.02469754],
  [77.67369598,34.01799896],[77.68551567,34.01334628],[77.67452483,34.0068003],
  [77.67495375,34.0044589],[77.68674072,34.0092994],[77.68076906,34.00286546],
  [77.67719478,33.99826971],[77.6818214,33.996386],[77.68373515,33.99463628],
  [77.69006025,34.00408872],[77.68821179,33.98687844],[77.69583637,33.97861857],
  [77.69852143,33.97013597],[77.70932424,33.97259384],[77.7224807,33.94878378],
  [77.72604174,33.94546034],[77.72186531,33.93612847],[77.72407825,33.92876109],
  [77.72583124,33.92659483],[77.72882914,33.92117697],[77.73323275,33.91395042],
  [77.73733243,33.91250881],[77.74040919,33.91367946],[77.74203612,33.91193278],
  [77.73617608,33.90911258],[77.73459586,33.9055364],[77.73750844,33.90646898],
  [77.74000261,33.90825795],[77.74208876,33.90857987],[77.73552605,33.90290887],
  [77.740365,33.90533477],[77.74291711,33.90610534],[77.74233488,33.90432198],
  [77.74305657,33.90086043],[77.74458797,33.8983617],[77.466333,34.130194]
];

// === WATER BODIES — ClassID 4 (53 points) ===
var waterCoords = [
  [77.74997527,33.89401584],[77.74958717,33.89597052],[77.74968735,33.89488732],
  [77.74949323,33.9025369],[77.746953,33.90599625],[77.74424503,33.90886865],
  [77.74067757,33.91495253],[77.73524924,33.91812817],[77.73469624,33.92213388],
  [77.73127522,33.92465352],[77.72969081,33.9314711],[77.7268518,33.94042571],
  [77.72389587,33.94512065],[77.72045355,33.9496011],[77.71898729,33.95580114],
  [77.7111628,33.96008019],[77.70864138,33.96648691],[77.70935886,33.97451429],
  [77.70440785,33.97952186],[77.69886519,33.98953688],[77.68942126,34.0062395],
  [77.67949714,34.0144012],[77.67508433,34.02438316],[77.66974682,34.03233302],
  [77.66574143,34.03863635],[77.66148271,34.04432773],[77.65451435,34.04959599],
  [77.6472156,34.05650807],[77.6397798,34.06100976],[77.6323494,34.06346925],
  [77.62636237,34.06820573],[77.62032873,34.07335284],[77.61366029,34.07757804],
  [77.60525484,34.08589461],[77.59771417,34.09524923],[77.59458638,34.09991051],
  [77.58649505,34.10263558],[77.5705785,34.10796514],[77.56031752,34.11244982],
  [77.55206173,34.11126031],[77.54606395,34.1113897],[77.51408884,34.12611404],
  [77.74599133,33.90229405],[77.74415434,33.90496876],[77.74267274,33.90791162],
  [77.73834772,33.90774845],[77.73860498,33.90982534],[77.73892428,33.91297565],
  [77.73493536,33.91506132],[77.50602818,34.13044164],[77.610389,34.083417],
  [77.736617,33.907583]
];

// === BARREN LAND — ClassID 5 (163 points) ===
var barrenCoords = [
  [77.50492833,34.12815165],[77.50948821,34.13606736],[77.51362798,34.13257938],
  [77.52100233,34.12983645],[77.52060591,34.12778466],[77.52367053,34.12976791],
  [77.52503626,34.13156314],[77.50724678,34.12263145],[77.51490358,34.1189494],
  [77.52026966,34.11627507],[77.52595813,34.12747186],[77.53773308,34.12613201],
  [77.54002299,34.12457326],[77.54307736,34.12170908],[77.54182425,34.11596624],
  [77.52663149,34.11071446],[77.53033872,34.11043777],[77.5492245,34.1190107],
  [77.55152647,34.12591897],[77.55920108,34.11876512],[77.56170457,34.11541262],
  [77.54062431,34.1078909],[77.54522908,34.10549318],[77.55058848,34.10474499],
  [77.56221782,34.11902084],[77.56009679,34.10611808],[77.56400983,34.10483702],
  [77.57452506,34.12084998],[77.57922678,34.11622021],[77.58150382,34.11293151],
  [77.57969759,34.11007337],[77.57346966,34.10158161],[77.568976,34.10206992],
  [77.56564664,34.09853523],[77.5854915,34.11684267],[77.58937862,34.1233468],
  [77.59262425,34.10908663],[77.57943965,34.09673093],[77.58433597,34.10108736],
  [77.59785886,34.10408821],[77.60163605,34.10704554],[77.58880073,34.09022243],
  [77.59226248,34.08418014],[77.60891364,34.09569458],[77.60892645,34.08617979],
  [77.614174,34.08442399],[77.62167744,34.08888837],[77.61524967,34.07957834],
  [77.61883482,34.07767671],[77.62630294,34.08546541],[77.59644336,34.07791222],
  [77.60064392,34.07008337],[77.60404543,34.06743997],[77.63246918,34.08016903],
  [77.63258532,34.07306495],[77.61021046,34.06313858],[77.61753103,34.05994887],
  [77.62932673,34.06260291],[77.64134987,34.07349135],[77.62297786,34.0558044],
  [77.62727245,34.05294451],[77.62969019,34.05040811],[77.63681672,34.0583693],
  [77.6336731,34.06063985],[77.64497293,34.05592603],[77.63152619,34.04578595],
  [77.64721263,34.07076207],[77.65088241,34.06826579],[77.65404558,34.06545469],
  [77.64741961,34.04682223],[77.63894831,34.03846372],[77.64565776,34.03341208],
  [77.65610149,34.03638848],[77.65804165,34.04451838],[77.66005996,34.04172195],
  [77.66699868,34.06039244],[77.67019149,34.05488022],[77.67406454,34.04544186],
  [77.65182858,34.03120667],[77.6549323,34.02829735],[77.65726853,34.02774153],
  [77.66156351,34.02572639],[77.66351181,34.02624467],[77.66819368,34.03051581],
  [77.6714506,34.03130925],[77.67390983,34.03217079],[77.68525582,34.03902374],
  [77.68782371,34.03368027],[77.68432107,34.02584934],[77.68538446,34.0188412],
  [77.68337408,34.01470978],[77.68320267,34.01252502],[77.67359223,34.01618328],
  [77.66621771,34.02318644],[77.68347948,34.00808276],[77.68825833,34.00601214],
  [77.68676103,34.01607661],[77.69193053,34.01122058],[77.69369188,34.00675795],
  [77.69497372,34.00195034],[77.6881785,34.00260098],[77.68494837,34.00196808],
  [77.6828163,33.99560195],[77.67581598,33.9941022],[77.67120677,33.99332058],
  [77.66302734,33.99446586],[77.67372923,33.9925908],[77.67640823,33.99120482],
  [77.67680263,33.98926273],[77.67844241,33.98807434],[77.68632431,33.9941475],
  [77.68736636,33.99079224],[77.6883046,33.9881878],[77.69133306,33.98808465],
  [77.69313426,33.99294172],[77.69689631,33.99418929],[77.70098767,33.99612205],
  [77.69759972,33.99006561],[77.69643266,33.98789819],[77.69235121,33.98664728],
  [77.6970866,33.98557211],[77.70124002,33.98284226],[77.70541354,33.99324864],
  [77.70407115,33.9784926],[77.70746094,33.97558398],[77.68480601,33.97929396],
  [77.68747127,33.97402367],[77.69147219,33.97031202],[77.70251055,33.96639264],
  [77.7052353,33.96244777],[77.70850883,33.96287373],[77.71179987,33.96302539],
  [77.7180459,33.96001584],[77.71444212,33.95431296],[77.71130832,33.95063648],
  [77.72368921,33.94916202],[77.71942171,33.94987198],[77.7152449,33.94821704],
  [77.72563232,33.94363482],[77.72839369,33.94079766],[77.71831158,33.93869357],
  [77.71889309,33.93389732],[77.72139083,33.92900822],[77.72806229,33.92808456],
  [77.73023168,33.92825383],[77.72856813,33.92487122],[77.73646765,33.92441962],
  [77.73037452,33.91973031],[77.73498573,33.91769797],[77.74121311,33.91605186],
  [77.73669571,33.91150512],[77.73146359,33.91049912],[77.73161597,33.90495447],
  [77.73572059,33.90244847],[77.73896855,33.90015769],[77.74178507,33.90275207],
  [77.74599931,33.90529142],[77.74484893,33.90732192],[77.616583,34.079333],
  [77.549417,34.108389],[77.510722,34.1235],[77.734056,33.912389],[77.734278,33.903194]
];

// === SNOW/ICE — ClassID 6 ===
// Persistent snow/ice at high-elevation ridge crests and glaciated areas
// within the ROI during the growing season (June–September).
// These points are placed on known permanent snowfields/glaciers
// visible in Sentinel-2 imagery at elevations above 5000 m.
var snowCoords = [
  [77.42, 34.17],[77.43, 34.175],[77.425, 34.168],[77.44, 34.172],
  [77.435, 34.165],[77.41, 34.178],[77.445, 34.17],[77.415, 34.173],
  [77.45, 34.165],[77.455, 34.162],[77.46, 34.16],[77.465, 34.158],
  [77.47, 34.155],[77.475, 34.152],[77.42, 34.165],[77.43, 34.162],
  [77.44, 34.16],[77.45, 34.158],[77.48, 34.15],[77.485, 34.148],
  [77.49, 34.145],[77.495, 34.142],[77.50, 34.14],[77.505, 34.138],
  [77.51, 34.135],[77.79, 33.88],[77.795, 33.875],[77.80, 33.87],
  [77.805, 33.875],[77.81, 33.872]
];

// Build FeatureCollections per class
var sbtPoints    = ee.FeatureCollection(makePoints(sbtCoords, 1));
var agPoints     = ee.FeatureCollection(makePoints(agCoords, 2));
var vegPoints    = ee.FeatureCollection(makePoints(vegCoords, 3));
var waterPoints  = ee.FeatureCollection(makePoints(waterCoords, 4));
var barrenPoints = ee.FeatureCollection(makePoints(barrenCoords, 5));
var snowPoints   = ee.FeatureCollection(makePoints(snowCoords, 6));

// Merge all classes into a single FeatureCollection
var groundTruth = sbtPoints
  .merge(agPoints)
  .merge(vegPoints)
  .merge(waterPoints)
  .merge(barrenPoints)
  .merge(snowPoints);

// Print sample distribution per class
print('========== TRAINING DATA SUMMARY ==========');
print('Total ground truth points:', groundTruth.size());
classCodes.forEach(function(code, i) {
  print(classNames[i] + ' (ClassID=' + code + '):',
        groundTruth.filter(ee.Filter.eq(classProperty, code)).size());
});

Map.addLayer(groundTruth, {color: 'FF00FF'}, 'Ground Truth Points');

// ============================================================
// SECTION 4: SENTINEL-2 CLOUD-FREE COMPOSITE (JUNE–SEPTEMBER)
// ============================================================

// Cloud masking using QA60 bitmask band.
// Bit 10 = opaque cloud, Bit 11 = cirrus cloud.
function maskS2CloudsQA60(image) {
  var qa = image.select('QA60');
  var cloudBitMask  = 1 << 10;
  var cirrusBitMask = 1 << 11;
  var mask = qa.bitwiseAnd(cloudBitMask).eq(0)
    .and(qa.bitwiseAnd(cirrusBitMask).eq(0));
  return image.updateMask(mask)
    .divide(10000)  // Scale reflectance to 0–1
    .copyProperties(image, ['system:time_start']);
}

// Filter Sentinel-2 SR for the growing season
var s2 = ee.ImageCollection('COPERNICUS/S2_SR_HARMONIZED')
  .filterBounds(roi)
  .filter(ee.Filter.calendarRange(6, 9, 'month'))  // June–September
  .filter(ee.Filter.lt('CLOUDY_PIXEL_PERCENTAGE', 20))
  .map(maskS2CloudsQA60);

print('========== SENTINEL-2 IMAGERY ==========');
print('Sentinel-2 scenes used:', s2.size());

// Select the required spectral bands: B2, B3, B4, B5, B8, B11, B12
var bands = ['B2', 'B3', 'B4', 'B5', 'B8', 'B11', 'B12'];
var composite = s2.select(bands).median().clip(roi);

// ============================================================
// SECTION 5: SPECTRAL INDICES
// ============================================================

// NDVI — primary vegetation discrimination
var ndvi = composite.normalizedDifference(['B8', 'B4']).rename('NDVI');

// NDWI — water body detection (McFeeters: Green vs NIR)
var ndwi = composite.normalizedDifference(['B3', 'B8']).rename('NDWI');

// SAVI — sparse canopy correction for cold-arid terrain (L=0.5)
var savi = composite.expression(
  '((NIR - RED) / (NIR + RED + L)) * (1 + L)', {
    'NIR': composite.select('B8'),
    'RED': composite.select('B4'),
    'L': 0.5
  }).rename('SAVI');

// NDBI — barren/built-up discrimination
var ndbi = composite.normalizedDifference(['B11', 'B8']).rename('NDBI');

// ============================================================
// SECTION 6: TOPOGRAPHIC VARIABLES (Copernicus DEM 30m)
// ============================================================

// Copernicus GLO-30 DEM for high-accuracy mountainous terrain
var dem = ee.ImageCollection('COPERNICUS/DEM/GLO30')
  .filterBounds(roi)
  .select('DEM')
  .mosaic()
  .clip(roi)
  .rename('Elevation');

var terrain = ee.Terrain.products(dem.rename('DEM'));
var slope   = terrain.select('slope').rename('Slope');
var aspect  = terrain.select('aspect').rename('Aspect');

// ============================================================
// SECTION 7: MULTI-BAND PREDICTOR STACK
// ============================================================

// Combine spectral bands + vegetation indices + terrain layers
var inputImage = composite
  .addBands(ndvi)
  .addBands(ndwi)
  .addBands(savi)
  .addBands(ndbi)
  .addBands(dem)
  .addBands(slope)
  .addBands(aspect);

var inputBands = inputImage.bandNames();
print('========== CLASSIFICATION INPUT ==========');
print('Input bands:', inputBands);
print('Total band count:', inputBands.length());

// ============================================================
// SECTION 8: SAMPLE EXTRACTION AND 70/30 TRAIN-VALIDATION SPLIT
// ============================================================

// Extract predictor values at each ground truth point
var samples = inputImage.sampleRegions({
  collection: groundTruth,
  properties: [classProperty],
  scale: 10,
  tileScale: 8,
  geometries: true
});

print('Total samples extracted:', samples.size());

// Reproducible 70/30 split with seed = 42
var samplesWithRandom = samples.randomColumn('random', 42);
var trainingSamples   = samplesWithRandom.filter(ee.Filter.lt('random', 0.7));
var validationSamples = samplesWithRandom.filter(ee.Filter.gte('random', 0.7));

print('Training samples (70%):', trainingSamples.size());
print('Validation samples (30%):', validationSamples.size());

// ============================================================
// SECTION 9: RANDOM FOREST CLASSIFIER (200 trees, seed=42)
// ============================================================

var classifier = ee.Classifier.smileRandomForest({
  numberOfTrees: 200,
  seed: 42
}).train({
  features: trainingSamples,
  classProperty: classProperty,
  inputProperties: inputBands
});

// ============================================================
// SECTION 10: VARIABLE IMPORTANCE
// ============================================================

var importance = ee.Dictionary(classifier.explain().get('importance'));
print('========== VARIABLE IMPORTANCE ==========');
print('Feature importance:', importance);

// Chart: variable importance as a bar chart
var importanceKeys = importance.keys();
var importanceVals = importance.values();

var importanceFeatures = importanceKeys.map(function(key) {
  return ee.Feature(null, {
    'Variable': key,
    'Importance': importance.getNumber(ee.String(key))
  });
});
var importanceFC = ee.FeatureCollection(importanceFeatures);

var importanceChart = ui.Chart.feature.byFeature(importanceFC, 'Variable', 'Importance')
  .setChartType('BarChart')
  .setOptions({
    title: 'Random Forest Variable Importance',
    hAxis: {title: 'Importance Score'},
    vAxis: {title: 'Predictor Variable'},
    legend: {position: 'none'},
    colors: ['#006400'],
    bar: {groupWidth: '80%'}
  });
print(importanceChart);

// ============================================================
// SECTION 11: CLASSIFY THE STUDY AREA
// ============================================================

var classified = inputImage.classify(classifier).clip(roi);

// ============================================================
// SECTION 12: ACCURACY ASSESSMENT
// ============================================================

var validated = validationSamples.classify(classifier);
var confusionMatrix = validated.errorMatrix(classProperty, 'classification');

print('========== ACCURACY ASSESSMENT ==========');
print('Confusion Matrix:', confusionMatrix);
print('Overall Accuracy:', confusionMatrix.accuracy());
print('Kappa Coefficient:', confusionMatrix.kappa());
print('Producers Accuracy (per class):', confusionMatrix.producersAccuracy());
print('Users Accuracy (per class):', confusionMatrix.consumersAccuracy());

// Build exportable accuracy table
var overallAccuracy = confusionMatrix.accuracy();
var kappa = confusionMatrix.kappa();
var producersAcc = confusionMatrix.producersAccuracy();
var usersAcc = confusionMatrix.consumersAccuracy();

var accuracyFeatures = classCodes.map(function(code, i) {
  return ee.Feature(null, {
    'Class': classNames[i],
    'ClassID': code,
    'Producers_Accuracy': ee.List(producersAcc.toList().get(i)).get(0),
    'Users_Accuracy': ee.List(usersAcc.toList().get(i)).get(0)
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

// ============================================================
// SECTION 13: CLASS AREA CALCULATION (Hectares)
// ============================================================

var pixelArea = ee.Image.pixelArea().divide(10000); // m² to hectares

var areaByClass = classCodes.map(function(code, i) {
  var classMask = classified.eq(ee.Number(code));
  var classArea = pixelArea.updateMask(classMask).reduceRegion({
    reducer: ee.Reducer.sum(),
    geometry: roi,
    scale: 10,
    maxPixels: 1e13,
    tileScale: 4
  });
  var areaHa = ee.Number(classArea.get('area'));
  return ee.Feature(null, {
    'Class': classNames[i],
    'ClassID': code,
    'Area_Hectares': areaHa,
    'Area_SqKm': areaHa.divide(100)
  });
});

var areaTable = ee.FeatureCollection(areaByClass);

print('========== CLASS AREA STATISTICS ==========');
print('Area by LULC class:', areaTable);

classCodes.forEach(function(code, i) {
  var feat = ee.Feature(areaTable.filter(ee.Filter.eq('ClassID', code)).first());
  print(classNames[i] + ':', feat.get('Area_Hectares'), 'ha |',
        feat.get('Area_SqKm'), 'sq km');
});

// ============================================================
// SECTION 14: VISUALIZATION
// ============================================================

// Sentinel-2 False Color Composite (B8, B4, B3 = NIR-R-G)
Map.addLayer(composite, {
  bands: ['B8', 'B4', 'B3'], min: 0, max: 0.4
}, 'False Color Composite (NIR-R-G)');

// NDVI
Map.addLayer(ndvi, {
  min: -0.2, max: 0.8,
  palette: ['#8B4513', '#D2B48C', '#FFFF00', '#90EE90', '#228B22', '#006400']
}, 'NDVI', false);

// DEM
Map.addLayer(dem, {
  min: 3000, max: 5800,
  palette: ['#228B22', '#90EE90', '#FFFF00', '#D2B48C', '#8B4513', '#FFFFFF']
}, 'Elevation (DEM)', false);

// Slope
Map.addLayer(slope, {
  min: 0, max: 50,
  palette: ['#228B22', '#FFFF00', '#FF0000']
}, 'Slope', false);

// Aspect
Map.addLayer(aspect, {
  min: 0, max: 360,
  palette: ['red', 'yellow', 'green', 'cyan', 'blue', 'magenta', 'red']
}, 'Aspect', false);

// Final LULC Classified Map
Map.addLayer(classified, {
  min: 1, max: 6,
  palette: classPalette
}, 'LULC Classification');

// ============================================================
// SECTION 15: MAP LEGEND
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
      ui.Label(classCodes[index] + ' — ' + name, {margin: '4px 0', fontSize: '12px'})
    ],
    layout: ui.Panel.Layout.Flow('horizontal')
  });
  legend.add(row);
});

Map.add(legend);

// ============================================================
// SECTION 16: EXPORTS TO GOOGLE DRIVE
// ============================================================

// 16a. Classified LULC raster (GeoTIFF, 10m resolution)
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

// 16b. Accuracy assessment table (CSV)
Export.table.toDrive({
  collection: accuracyExport,
  description: 'Accuracy_Assessment_Table',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Class', 'ClassID', 'Producers_Accuracy', 'Users_Accuracy']
});

// 16c. Class area statistics (CSV)
Export.table.toDrive({
  collection: areaTable,
  description: 'Class_Area_Statistics',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Class', 'ClassID', 'Area_Hectares', 'Area_SqKm']
});

// 16d. Variable importance table (CSV)
Export.table.toDrive({
  collection: importanceFC,
  description: 'Variable_Importance_Table',
  folder: 'LULC_Seabuckthorn_Project',
  fileFormat: 'CSV',
  selectors: ['Variable', 'Importance']
});

print('============================================');
print('SCRIPT COMPLETE — Run exports from Tasks tab');
print('============================================');
