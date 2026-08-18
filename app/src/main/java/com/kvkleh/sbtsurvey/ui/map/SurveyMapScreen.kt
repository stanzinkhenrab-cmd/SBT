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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.map.MapMath
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.surveyMapViewModel
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Leh town — the default view when no survey has coordinates yet. */
private const val DEFAULT_LAT = 34.152588
private const val DEFAULT_LON = 77.577049

/**
 * Offline survey map.
 *
 * Markers, coordinates and the scale bar come entirely from the local database, so the
 * map is fully usable with no connectivity. Background imagery is drawn from the tile
 * cache when tiles are available and quietly omitted when they are not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMapScreen(onBack: () -> Unit, onOpenSurvey: (Long) -> Unit) {
    val viewModel = surveyMapViewModel()
    val surveys by viewModel.located.collectAsStateWithLifecycle()
    val tileVersion by viewModel.tileVersion.collectAsStateWithLifecycle()
    val tileCache = viewModel.tileCache

    var zoom by remember { mutableFloatStateOf(11f) }
    var centerLat by remember { mutableStateOf(DEFAULT_LAT) }
    var centerLon by remember { mutableStateOf(DEFAULT_LON) }
    var selected by remember { mutableStateOf<SurveyEntity?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun fitToMarkers() {
        if (surveys.isEmpty()) return
        val lats = surveys.mapNotNull { it.latitude }
        val lons = surveys.mapNotNull { it.longitude }
        if (lats.isEmpty() || lons.isEmpty()) return
        centerLat = (lats.min() + lats.max()) / 2
        centerLon = (lons.min() + lons.max()) / 2

        val spanLat = max(lats.max() - lats.min(), 0.002)
        val spanLon = max(lons.max() - lons.min(), 0.002)
        // Pick the largest zoom at which the full extent still fits on screen.
        val fitted = min(
            log2(360.0 / spanLon),
            log2(170.0 / spanLat)
        ).toFloat()
        zoom = fitted.coerceIn(MapMath.MIN_ZOOM, MapMath.MAX_ZOOM - 2f)
    }

    LaunchedEffect(surveys.size) {
        if (surveys.isNotEmpty()) fitToMarkers()
    }

    val density = LocalDensity.current
    val markerRadiusPx = with(density) { 11.dp.toPx() }
    val hitRadiusPx = with(density) { 28.dp.toPx() }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Survey Map", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${surveys.size} survey${if (surveys.size == 1) "" else "s"} with coordinates",
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
                .background(backgroundColor)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val level = MapMath.zoomLevel(zoom)
                            val tileSize = MapMath.scaledTileSize(zoom)
                            var tx = MapMath.lonToTileX(centerLon, level) - pan.x / tileSize
                            var ty = MapMath.latToTileY(centerLat, level) - pan.y / tileSize
                            val limit = MapMath.tileCount(level).toDouble()
                            tx = tx.coerceIn(0.0, limit)
                            ty = ty.coerceIn(0.0, limit)
                            centerLon = MapMath.tileXToLon(tx, level)
                            centerLat = MapMath.tileYToLat(ty, level)

                            if (gestureZoom != 1f) {
                                zoom = (zoom + log2(gestureZoom.toDouble()).toFloat())
                                    .coerceIn(MapMath.MIN_ZOOM, MapMath.MAX_ZOOM)
                            }
                        }
                    }
                    .pointerInput(surveys, zoom, centerLat, centerLon, canvasSize) {
                        detectTapGestures { tap ->
                            val hit = surveys.minByOrNull { survey ->
                                val position = project(
                                    survey, zoom, centerLat, centerLon,
                                    canvasSize.width.toFloat(), canvasSize.height.toFloat()
                                ) ?: return@minByOrNull Float.MAX_VALUE
                                (position - tap).getDistance()
                            }
                            val position = hit?.let {
                                project(
                                    it, zoom, centerLat, centerLon,
                                    canvasSize.width.toFloat(), canvasSize.height.toFloat()
                                )
                            }
                            selected = if (position != null &&
                                (position - tap).getDistance() <= hitRadiusPx
                            ) {
                                hit
                            } else {
                                null
                            }
                        }
                    }
            ) {
                val level = MapMath.zoomLevel(zoom)
                val tileSize = MapMath.scaledTileSize(zoom)
                val centerTileX = MapMath.lonToTileX(centerLon, level)
                val centerTileY = MapMath.latToTileY(centerLat, level)

                // Read so the canvas redraws when a tile download finishes.
                @Suppress("UNUSED_VARIABLE")
                val redrawOnTileArrival = tileVersion

                drawTiles(
                    tileCache = tileCache,
                    level = level,
                    tileSize = tileSize,
                    centerTileX = centerTileX,
                    centerTileY = centerTileY,
                    gridColor = gridColor
                )

                surveys.forEach { survey ->
                    val position = projectInScope(
                        survey, level, tileSize, centerTileX, centerTileY, size.width, size.height
                    ) ?: return@forEach
                    if (position.x < -50 || position.y < -50 ||
                        position.x > size.width + 50 || position.y > size.height + 50
                    ) {
                        return@forEach
                    }
                    val isSelected = survey.id == selected?.id
                    drawCircle(
                        color = if (isSelected) secondary else primary,
                        radius = if (isSelected) markerRadiusPx * 1.35f else markerRadiusPx,
                        center = position
                    )
                    drawCircle(
                        color = Color.White,
                        radius = if (isSelected) markerRadiusPx * 1.35f else markerRadiusPx,
                        center = position,
                        style = Stroke(width = 3f)
                    )
                }
            }

            MapControls(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                onZoomIn = { zoom = (zoom + 1f).coerceAtMost(MapMath.MAX_ZOOM) },
                onZoomOut = { zoom = (zoom - 1f).coerceAtLeast(MapMath.MIN_ZOOM) },
                onFit = { fitToMarkers() }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (tileCache.isOnline) {
                        MapMath.metresPerPixel(centerLat, zoom).let {
                            "Scale ≈ ${it.roundToInt()} m/px · ${com.kvkleh.sbtsurvey.map.TileCache.ATTRIBUTION}"
                        }
                    } else {
                        "Offline – survey positions shown on grid · " +
                            "Scale ≈ ${MapMath.metresPerPixel(centerLat, zoom).roundToInt()} m/px"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

            selected?.let { survey ->
                MarkerDetailCard(
                    survey = survey,
                    onClose = { selected = null },
                    onOpen = { onOpenSurvey(survey.id) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

// --- Drawing helpers --------------------------------------------------------

private fun DrawScope.drawTiles(
    tileCache: com.kvkleh.sbtsurvey.map.TileCache,
    level: Int,
    tileSize: Float,
    centerTileX: Double,
    centerTileY: Double,
    gridColor: Color
) {
    val halfWidthTiles = (size.width / 2f) / tileSize
    val halfHeightTiles = (size.height / 2f) / tileSize
    val firstX = floor(centerTileX - halfWidthTiles).toInt()
    val lastX = floor(centerTileX + halfWidthTiles).toInt()
    val firstY = floor(centerTileY - halfHeightTiles).toInt()
    val lastY = floor(centerTileY + halfHeightTiles).toInt()

    for (x in firstX..lastX) {
        for (y in firstY..lastY) {
            if (!MapMath.isValidTileY(y, level)) continue
            val wrappedX = MapMath.normaliseTileX(x, level)
            val left = size.width / 2f + ((x - centerTileX) * tileSize).toFloat()
            val top = size.height / 2f + ((y - centerTileY) * tileSize).toFloat()

            val bitmap = tileCache.peek(level, wrappedX, y)
            if (bitmap != null) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(tileSize.roundToInt() + 1, tileSize.roundToInt() + 1)
                )
            } else {
                tileCache.prefetch(level, wrappedX, y)
                // Graticule cell, so the map still reads as a map when offline.
                drawRect(
                    color = gridColor,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(tileSize, tileSize),
                    style = Stroke(width = 1f)
                )
            }
        }
    }
}

private fun DrawScope.projectInScope(
    survey: SurveyEntity,
    level: Int,
    tileSize: Float,
    centerTileX: Double,
    centerTileY: Double,
    width: Float,
    height: Float
): Offset? {
    val lat = survey.latitude ?: return null
    val lon = survey.longitude ?: return null
    val x = width / 2f + ((MapMath.lonToTileX(lon, level) - centerTileX) * tileSize).toFloat()
    val y = height / 2f + ((MapMath.latToTileY(lat, level) - centerTileY) * tileSize).toFloat()
    return Offset(x, y)
}

/** Same projection as [projectInScope], for hit testing outside a draw scope. */
private fun project(
    survey: SurveyEntity,
    zoom: Float,
    centerLat: Double,
    centerLon: Double,
    width: Float,
    height: Float
): Offset? {
    val lat = survey.latitude ?: return null
    val lon = survey.longitude ?: return null
    val level = MapMath.zoomLevel(zoom)
    val tileSize = MapMath.scaledTileSize(zoom)
    val centerTileX = MapMath.lonToTileX(centerLon, level)
    val centerTileY = MapMath.latToTileY(centerLat, level)
    val x = width / 2f + ((MapMath.lonToTileX(lon, level) - centerTileX) * tileSize).toFloat()
    val y = height / 2f + ((MapMath.latToTileY(lat, level) - centerTileY) * tileSize).toFloat()
    return Offset(x, y)
}

private fun log2(value: Double): Double = ln(value) / ln(2.0)

// --- Overlays ---------------------------------------------------------------

@Composable
private fun MapControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalIconButton(onClick = onZoomIn, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Zoom in")
        }
        FilledTonalIconButton(onClick = onZoomOut, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
        }
        FilledTonalIconButton(onClick = onFit, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Fit all surveys")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        onClick = onOpen
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap this card to open the full record",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
