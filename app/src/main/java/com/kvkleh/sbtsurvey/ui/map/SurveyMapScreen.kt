package com.kvkleh.sbtsurvey.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.export.ShareLauncher
import com.kvkleh.sbtsurvey.map.GeoBounds
import com.kvkleh.sbtsurvey.map.MapComposer
import com.kvkleh.sbtsurvey.map.MapMath
import com.kvkleh.sbtsurvey.map.MapProjection
import com.kvkleh.sbtsurvey.map.MapSheet
import com.kvkleh.sbtsurvey.map.TileSource
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.components.rememberFileSaver
import com.kvkleh.sbtsurvey.ui.surveyMapViewModel
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Leh town — the default view when no survey has coordinates yet. */
private const val DEFAULT_LAT = 34.152588
private const val DEFAULT_LON = 77.577049

/**
 * Offline survey map.
 *
 * Markers, coordinates and the scale bar come entirely from the local database, so the
 * map is usable with no connectivity at all. Background imagery is layered underneath
 * when it is available, and the surveyor chooses which imagery through the layers button.
 *
 * The map face is painted by [MapComposer], the same renderer used for the PDF, image and
 * GeoTIFF exports, so what is on screen is exactly what gets saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMapScreen(onBack: () -> Unit, onOpenSurvey: (Long) -> Unit) {
    val viewModel = surveyMapViewModel()
    val surveys by viewModel.located.collectAsStateWithLifecycle()
    val layer by viewModel.layer.collectAsStateWithLifecycle()
    val tileVersion by viewModel.tileVersion.collectAsStateWithLifecycle()
    val tileError by viewModel.tileError.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tileCache = viewModel.tileCache

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fileSaver = rememberFileSaver { savedName, error ->
        viewModel.reportSaved(savedName, error)
    }

    var zoom by remember { mutableFloatStateOf(13f) }
    var centerLat by remember { mutableStateOf(DEFAULT_LAT) }
    var centerLon by remember { mutableStateOf(DEFAULT_LON) }
    var selected by remember { mutableStateOf<SurveyEntity?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showLayers by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    fun fitToMarkers() {
        val lats = surveys.mapNotNull { it.latitude }
        val lons = surveys.mapNotNull { it.longitude }
        if (lats.isEmpty() || lons.isEmpty()) return
        centerLat = (lats.min() + lats.max()) / 2
        centerLon = (lons.min() + lons.max()) / 2
        val spanLat = max(lats.max() - lats.min(), 0.004)
        val spanLon = max(lons.max() - lons.min(), 0.004)
        zoom = min(log2(360.0 / spanLon), log2(170.0 / spanLat))
            .toFloat()
            .coerceIn(MapMath.MIN_ZOOM, MapMath.MAX_ZOOM - 2f)
    }

    LaunchedEffect(surveys.size) {
        if (surveys.isNotEmpty()) fitToMarkers()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(uiState.pendingSave) {
        uiState.pendingSave?.let { result ->
            fileSaver.save(result.file)
            viewModel.consumeSave()
        }
    }

    LaunchedEffect(uiState.pendingShare) {
        uiState.pendingShare?.let { result ->
            runCatching {
                ShareLauncher.share(
                    context = context,
                    file = result.file,
                    mimeType = result.format.mimeType,
                    body = "Seabuckthorn Field Survey – Ladakh\n" +
                        "Krishi Vigyan Kendra – Leh | MIDH-SBM\n\n" +
                        "Survey map: ${result.markerCount} locations\n" +
                        "File: ${result.file.name} (${result.sizeLabel})"
                )
            }.onFailure { snackbarHostState.showSnackbar("No app available to share this file.") }
            viewModel.consumeShare()
        }
    }

    // The extent currently on screen; also what an export covers.
    val bounds = remember(centerLat, centerLon, zoom, canvasSize) {
        GeoBounds.around(
            centerLat, centerLon, zoom,
            canvasSize.width.toFloat().coerceAtLeast(1f),
            canvasSize.height.toFloat().coerceAtLeast(1f)
        )
    }

    val sheet = remember(surveys, layer, bounds, selected) {
        MapSheet(
            surveys = surveys,
            layer = layer,
            bounds = bounds,
            decorated = false,
            labelMarkers = surveys.size <= 25,
            highlightId = selected?.id
        )
    }

    // Computed in composition (never during drawing) so taps can be matched to markers.
    val projection: MapProjection? = remember(sheet, canvasSize) {
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            null
        } else {
            MapComposer.projectionFor(
                canvasSize.width.toFloat(),
                canvasSize.height.toFloat(),
                sheet
            )
        }
    }

    val hitRadiusPx = with(LocalDensity.current) { 30.dp.toPx() }

    val tileSource = remember(tileCache) {
        TileSource { requestedLayer, z, x, y ->
            val cached = tileCache.peek(requestedLayer, z, x, y)
            if (cached == null) tileCache.prefetch(requestedLayer, z, x, y)
            cached
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Survey Map", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${surveys.size} survey${if (surveys.size == 1) "" else "s"} " +
                                "· ${layer.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val level = MapMath.zoomLevel(zoom)
                            val tileSize = MapMath.scaledTileSize(zoom)
                            val limit = MapMath.tileCount(level).toDouble()
                            val tx = (MapMath.lonToTileX(centerLon, level) - pan.x / tileSize)
                                .coerceIn(0.0, limit)
                            val ty = (MapMath.latToTileY(centerLat, level) - pan.y / tileSize)
                                .coerceIn(0.0, limit)
                            centerLon = MapMath.tileXToLon(tx, level)
                            centerLat = MapMath.tileYToLat(ty, level)

                            if (gestureZoom != 1f) {
                                zoom = (zoom + log2(gestureZoom.toDouble()).toFloat())
                                    .coerceIn(MapMath.MIN_ZOOM, MapMath.MAX_ZOOM)
                            }
                        }
                    }
                    .pointerInput(projection, surveys) {
                        detectTapGestures { tap ->
                            val current = projection ?: return@detectTapGestures
                            selected = surveys
                                .mapNotNull { survey ->
                                    val lat = survey.latitude ?: return@mapNotNull null
                                    val lon = survey.longitude ?: return@mapNotNull null
                                    val point = Offset(current.xOf(lon), current.yOf(lat))
                                    survey to (point - tap).getDistance()
                                }
                                .filter { it.second <= hitRadiusPx }
                                .minByOrNull { it.second }
                                ?.first
                        }
                    }
            ) {
                // Read so the map repaints when a tile download finishes.
                @Suppress("UNUSED_VARIABLE")
                val repaintOnTileArrival = tileVersion

                // The sheet's extent is derived from the measured canvas, so skip the
                // first frame rather than drawing at a placeholder size.
                if (canvasSize.width > 0 && canvasSize.height > 0) {
                    drawIntoCanvas { canvas ->
                        MapComposer.draw(
                            canvas = canvas.nativeCanvas,
                            width = size.width,
                            height = size.height,
                            sheet = sheet,
                            tiles = tileSource
                        )
                    }
                }
            }

            // Right-hand control stack.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { showLayers = true },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = "Choose basemap")
                }
                FilledTonalIconButton(
                    onClick = { showExport = true },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.SaveAlt, contentDescription = "Save map")
                }
                FilledTonalIconButton(
                    onClick = { zoom = (zoom + 1f).coerceAtMost(MapMath.MAX_ZOOM) },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                }
                FilledTonalIconButton(
                    onClick = { zoom = (zoom - 1f).coerceAtLeast(MapMath.MIN_ZOOM) },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                }
                FilledTonalIconButton(
                    onClick = { fitToMarkers() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Fit all surveys")
                }
            }

            // Attribution, as every basemap licence requires.
            Text(
                text = layer.attribution,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )

            tileError?.let { message ->
                TileErrorBanner(
                    message = message,
                    onSwitchLayer = { showLayers = true },
                    onDismiss = viewModel::dismissTileError,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .padding(end = 76.dp)
                )
            }

            if (surveys.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(
                        text = "No survey has GPS coordinates yet.\n" +
                            "Records appear here once a location is captured.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(22.dp)
                    )
                }
            }

            if (uiState.exporting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium
                        )
                        .padding(horizontal = 26.dp, vertical = 20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Rendering map…", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            selected?.let { survey ->
                MarkerDetailCard(
                    survey = survey,
                    onClose = { selected = null },
                    onOpen = { onOpenSurvey(survey.id) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                )
            }
        }
    }

    if (showLayers) {
        BaseMapChooserSheet(
            selected = layer,
            cacheSize = tileCache::cacheSizeBytes,
            onSelect = {
                viewModel.selectLayer(it)
                showLayers = false
            },
            onClearCache = {
                viewModel.clearTileCache()
                showLayers = false
            },
            onDismiss = { showLayers = false }
        )
    }

    if (showExport) {
        MapExportSheet(
            markerCount = surveys.size,
            layerLabel = layer.label,
            onDismiss = { showExport = false },
            onChoose = { format, share ->
                viewModel.exportMap(format, bounds, share)
                showExport = false
            }
        )
    }
}

@Composable
private fun TileErrorBanner(
    message: String,
    onSwitchLayer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row {
                TextButton(onClick = onSwitchLayer) { Text("Change basemap") }
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun MarkerDetailCard(
    survey: SurveyEntity,
    onClose: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = survey.surveyId,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close details")
                }
            }
            MarkerRow("Date", Fmt.date(survey.date))
            MarkerRow("Village", survey.village.ifBlank { "—" })
            MarkerRow("Maturity stage", survey.maturityStage ?: "—")
            MarkerRow("Latitude", Fmt.coordinate(survey.latitude))
            MarkerRow("Longitude", Fmt.coordinate(survey.longitude))
            MarkerRow("Altitude", Fmt.metres(survey.altitude))
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Open full survey record")
            }
        }
    }
}

@Composable
private fun MarkerRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(130.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

private fun log2(value: Double): Double = ln(value) / ln(2.0)
