package com.kvkleh.sbtsurvey.ui.map

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.ui.components.DetailRow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Saved survey positions on a map.
 *
 * Two views are available: an OpenStreetMap tile map, which caches every tile it
 * has ever loaded and therefore keeps working offline once an area has been seen,
 * and a plain coordinate plot that never needs tiles at all. If tiles cannot be
 * fetched the surveyor switches view instead of losing the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyMapScreen(
    onOpenSurvey: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SurveyMapViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Survey Map", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${state.points.size} plotted" +
                                if (state.withoutLocation > 0) {
                                    " · ${state.withoutLocation} without coordinates"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setUseTileMap(!state.useTileMap) }) {
                        Icon(
                            Icons.Filled.Layers,
                            contentDescription = if (state.useTileMap) {
                                "Switch to offline coordinate view"
                            } else {
                                "Switch to map view"
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.useTileMap) {
                TileMap(
                    surveys = state.points,
                    onSelect = viewModel::select,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                OfflinePlotView(
                    surveys = state.points,
                    selected = state.selected,
                    onSelect = viewModel::select,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (state.points.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp)
                ) {
                    Text(
                        text = "No survey has coordinates yet.\nRecord a GPS position on the " +
                            "survey form and the plot will appear here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            Text(
                text = if (state.useTileMap) {
                    "Map tiles need internet the first time; they are then cached on the device."
                } else {
                    "Offline coordinate view – no tiles required."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(6.dp)
            )

            state.selected?.let { survey ->
                SurveyCallout(
                    survey = survey,
                    onOpen = { onOpenSurvey(survey.id) },
                    onClose = { viewModel.select(null) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun TileMap(
    surveys: List<SurveyEntity>,
    onSelect: (SurveyEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(GeoPoint(LEH_LATITUDE, LEH_LONGITUDE))
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.overlays.clear()
            surveys.forEach { survey ->
                val latitude = survey.latitude ?: return@forEach
                val longitude = survey.longitude ?: return@forEach
                val marker = Marker(view)
                marker.position = GeoPoint(latitude, longitude)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = survey.surveyId
                marker.subDescription = survey.village
                marker.setOnMarkerClickListener { _, _ ->
                    onSelect(survey)
                    true
                }
                view.overlays.add(marker)
            }
            val located = surveys.filter { it.hasLocation }
            if (located.isNotEmpty()) {
                val latitudes = located.mapNotNull { it.latitude }
                val longitudes = located.mapNotNull { it.longitude }
                val box = BoundingBox(
                    latitudes.max() + BOX_PADDING,
                    longitudes.max() + BOX_PADDING,
                    latitudes.min() - BOX_PADDING,
                    longitudes.min() - BOX_PADDING
                )
                view.post { runCatching { view.zoomToBoundingBox(box, false) } }
            }
            view.invalidate()
        }
    )
}

@Composable
private fun SurveyCallout(
    survey: SurveyEntity,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = survey.surveyId,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }
            DetailRow("Village", survey.village)
            DetailRow("Survey date", Formats.dateTime(survey.createdAt))
            DetailRow("Maturity stage", survey.maturityStage.orEmpty())
            DetailRow("Latitude", Formats.coordinate(survey.latitude))
            DetailRow("Longitude", Formats.coordinate(survey.longitude))
            if (survey.altitude != null) {
                DetailRow("Altitude", "${Formats.metres(survey.altitude)} m")
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpen) { Text("Open survey") }
            }
        }
    }
}

/**
 * osmdroid keeps its cache under the app's own storage, so the map needs no
 * external-storage permission and its tiles are removed with the app.
 */
private fun configureOsmdroid(context: Context) {
    val configuration = Configuration.getInstance()
    configuration.load(
        context,
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    )
    configuration.userAgentValue = context.packageName
    val base = File(context.filesDir, "osmdroid")
    if (!base.exists()) base.mkdirs()
    configuration.osmdroidBasePath = base
    val tiles = File(base, "tiles")
    if (!tiles.exists()) tiles.mkdirs()
    configuration.osmdroidTileCache = tiles
}

private const val LEH_LATITUDE = 34.1526
private const val LEH_LONGITUDE = 77.5771
private const val DEFAULT_ZOOM = 10.0
private const val BOX_PADDING = 0.01
