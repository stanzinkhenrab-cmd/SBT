package com.kvkleh.sbtsurvey.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.data.Formats
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import kotlin.math.abs

/**
 * A coordinate plot that needs no tiles, no network and no map library.
 *
 * This is the guaranteed view of the survey positions: it draws every recorded
 * point on a latitude/longitude grid scaled to the data. When map tiles cannot be
 * downloaded — the normal case in the field — the surveyor still sees where the
 * plots sit relative to one another.
 */
@Composable
fun OfflinePlotView(
    surveys: List<SurveyEntity>,
    selected: SurveyEntity?,
    onSelect: (SurveyEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    val bounds = remember(surveys) { PlotBounds.of(surveys) }
    val markerColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow

    Box(modifier = modifier.background(surfaceColor)) {
        if (surveys.isEmpty() || bounds == null) {
            Text(
                text = "No coordinates recorded yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
            return@Box
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(surveys, bounds) {
                    detectTapGestures { tap ->
                        val hit = surveys.minByOrNull { survey ->
                            val point = bounds.toOffset(survey, size.width.toFloat(), size.height.toFloat())
                            abs(point.x - tap.x) + abs(point.y - tap.y)
                        }
                        val hitPoint = hit?.let {
                            bounds.toOffset(it, size.width.toFloat(), size.height.toFloat())
                        }
                        val within = hitPoint != null &&
                            abs(hitPoint.x - tap.x) < 48f &&
                            abs(hitPoint.y - tap.y) < 48f
                        onSelect(if (within) hit else null)
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Reference grid: four lines each way, purely to give a sense of scale.
            val steps = 4
            for (index in 0..steps) {
                val x = w * index / steps
                val y = h * index / steps
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }

            surveys.forEach { survey ->
                val point = bounds.toOffset(survey, w, h)
                val isSelected = survey.id == selected?.id
                drawCircle(
                    color = if (isSelected) selectedColor else markerColor,
                    radius = if (isSelected) 16f else 11f,
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = if (isSelected) 6f else 4f,
                    center = point
                )
            }
        }

        Text(
            text = "Lat ${Formats.coordinate(bounds.minLat)} – ${Formats.coordinate(bounds.maxLat)}  ·  " +
                "Lon ${Formats.coordinate(bounds.minLon)} – ${Formats.coordinate(bounds.maxLon)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(surfaceColor.copy(alpha = 0.9f))
                .padding(8.dp)
        )
    }
}

/** Latitude/longitude extent of the plotted records, padded so points never touch the edge. */
internal data class PlotBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    fun toOffset(survey: SurveyEntity, width: Float, height: Float): Offset {
        val lat = survey.latitude ?: return Offset(width / 2f, height / 2f)
        val lon = survey.longitude ?: return Offset(width / 2f, height / 2f)
        val inset = 0.08f
        val usableW = width * (1 - 2 * inset)
        val usableH = height * (1 - 2 * inset)
        val x = width * inset + usableW * ((lon - minLon) / (maxLon - minLon)).toFloat()
        // Latitude grows northwards, screen y grows downwards.
        val y = height * inset + usableH * (1 - ((lat - minLat) / (maxLat - minLat))).toFloat()
        return Offset(x, y)
    }

    companion object {
        /** Minimum span in degrees, so a single point or a tight cluster still renders. */
        private const val MIN_SPAN = 0.004

        fun of(surveys: List<SurveyEntity>): PlotBounds? {
            val located = surveys.filter { it.hasLocation }
            if (located.isEmpty()) return null
            val lats = located.mapNotNull { it.latitude }
            val lons = located.mapNotNull { it.longitude }
            var minLat = lats.min()
            var maxLat = lats.max()
            var minLon = lons.min()
            var maxLon = lons.max()
            if (maxLat - minLat < MIN_SPAN) {
                val centre = (maxLat + minLat) / 2
                minLat = centre - MIN_SPAN / 2
                maxLat = centre + MIN_SPAN / 2
            }
            if (maxLon - minLon < MIN_SPAN) {
                val centre = (maxLon + minLon) / 2
                minLon = centre - MIN_SPAN / 2
                maxLon = centre + MIN_SPAN / 2
            }
            return PlotBounds(minLat, maxLat, minLon, maxLon)
        }
    }
}
