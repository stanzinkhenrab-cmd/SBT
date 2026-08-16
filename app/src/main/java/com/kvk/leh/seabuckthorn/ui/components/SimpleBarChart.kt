package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A minimal, dependency-free bar chart used across the dashboard for maturity/TSS/berry-size
 * distributions and surveys-by-date/village. Kept intentionally simple (no external chart
 * library) since it only needs to render small offline aggregate datasets.
 */
@Composable
fun SimpleBarChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    heightDp: Int = 160
) {
    val maxValue = (data.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val textMeasurer = rememberTextMeasurer()
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(heightDp.dp)) {
            if (data.isEmpty()) return@Canvas
            val barSpacing = 8.dp.toPx()
            val barWidth = (size.width - barSpacing * (data.size - 1)) / data.size
            data.forEachIndexed { index, (_, value) ->
                val barHeight = size.height * (value.toFloat() / maxValue.toFloat())
                val left = index * (barWidth + barSpacing)
                drawRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(left, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
                val text = value.toString()
                val layout = textMeasurer.measure(text, style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp))
                drawText(
                    textLayoutResult = layout,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        left + barWidth / 2 - layout.size.width / 2,
                        size.height - barHeight - layout.size.height - 2.dp.toPx()
                    )
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            data.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
