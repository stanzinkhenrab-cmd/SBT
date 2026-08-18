package com.kvkleh.sbtsurvey.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** Supplies tiles to the composer; backed either by the live cache or a prefetched set. */
fun interface TileSource {
    fun tile(layer: BaseMapLayer, zoom: Int, x: Int, y: Int): Bitmap?
}

/** Everything needed to draw one map sheet. */
data class MapSheet(
    val surveys: List<SurveyEntity>,
    val layer: BaseMapLayer,
    val bounds: GeoBounds,
    val decorated: Boolean = true,
    val labelMarkers: Boolean = true,
    val detailBoost: Int = 0,
    val highlightId: Long? = null,
    val title: String = "Seabuckthorn Field Survey – Ladakh",
    val subtitle: String = "Krishi Vigyan Kendra – Leh, Ladakh · MIDH-SBM"
)

/**
 * Draws the survey map onto any [Canvas].
 *
 * The same code paints the interactive screen, the PDF page and the exported raster, so
 * what a surveyor sees is what gets printed. Because it draws to a plain canvas, text and
 * symbols stay vector in the PDF while only the imagery is raster.
 */
object MapComposer {

    /**
     * Computes the layout for a sheet without drawing it, so an exporter can work out
     * which tiles it needs before rendering.
     */
    fun projectionFor(width: Float, height: Float, sheet: MapSheet): MapProjection {
        val unit = min(width, height) / 100f

        val titleBand = if (sheet.decorated) height * 0.105f else 0f
        val footerBand = if (sheet.decorated) height * 0.080f else 0f
        val outer = if (sheet.decorated) unit * 2.4f else 0f
        val gutter = if (sheet.decorated) unit * 4.4f else 0f

        val mapLeft = outer + gutter
        val mapTop = titleBand + gutter * 0.55f
        val mapRight = width - outer - gutter
        val mapBottom = height - footerBand - gutter * 0.9f

        return MapProjection.fit(
            bounds = sheet.bounds,
            left = mapLeft,
            top = mapTop,
            width = (mapRight - mapLeft).coerceAtLeast(1f),
            height = (mapBottom - mapTop).coerceAtLeast(1f),
            maxLevel = sheet.layer.maxZoom,
            detailBoost = sheet.detailBoost
        )
    }

    /**
     * Paints a complete sheet and returns the projection used, which the GeoTIFF export
     * needs in order to georeference the result.
     */
    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        sheet: MapSheet,
        tiles: TileSource
    ): MapProjection {
        val unit = min(width, height) / 100f
        val titleBand = if (sheet.decorated) height * 0.105f else 0f
        val footerBand = if (sheet.decorated) height * 0.080f else 0f

        val projection = projectionFor(width, height, sheet)
        val painter = SheetPainter()
        val frame = RectF(projection.left, projection.top, projection.right, projection.bottom)

        if (sheet.decorated) painter.fillPaper(canvas, width, height)

        canvas.save()
        canvas.clipRect(frame)
        painter.drawImagery(canvas, projection, sheet, tiles)
        painter.drawGraticule(canvas, projection, sheet)
        painter.drawMarkers(canvas, projection, sheet, unit)
        canvas.restore()

        if (sheet.decorated) {
            painter.drawNeatline(canvas, frame, unit)
            painter.drawCoordinateLabels(canvas, projection, frame, unit)
            painter.drawNorthArrow(canvas, frame, unit)
            painter.drawScaleBar(canvas, projection, frame, unit, sheet)
            painter.drawLegend(canvas, frame, unit, sheet)
            painter.drawTitleBlock(canvas, width, titleBand, unit, sheet)
            painter.drawFooter(canvas, width, height, footerBand, unit, sheet)
        }

        return projection
    }
}


private const val PAPER = Color.WHITE
private const val INK = 0xFF1A1D19.toInt()
private const val MUTED = 0xFF5C6157.toInt()
private const val NEATLINE = 0xFF2B2F29.toInt()
private const val MARKER = 0xFFE86A0C.toInt()
private const val MARKER_EDGE = 0xFF23140A.toInt()
private const val HIGHLIGHT = 0xFF1B6B3A.toInt()
private const val GRID_LIGHT = 0x33000000
private const val GRID_DARK = 0x59FFFFFF
private const val EMPTY_MAP = 0xFFEDF1EA.toInt()

private const val MAX_LABELLED_MARKERS = 45

/** Graticule intervals in degrees: 1°, 30', 15', 10', 5', 2', 1', 30", 15", 10", 5", 2". */
private val GRATICULE_STEPS = doubleArrayOf(
    1.0, 0.5, 0.25, 1.0 / 6, 1.0 / 12, 1.0 / 30, 1.0 / 60,
    1.0 / 120, 1.0 / 240, 1.0 / 360, 1.0 / 720, 1.0 / 1800
)

/**
 * One rendering pass.
 *
 * A fresh instance per draw is deliberate — [Paint] is mutable, and an export rendering
 * on a background thread must not share brushes with the screen redrawing on the main
 * thread.
 */
private class SheetPainter {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Paper background for a decorated sheet. */
    fun fillPaper(canvas: Canvas, width: Float, height: Float) {
        fill.color = PAPER
        canvas.drawRect(0f, 0f, width, height, fill)
    }

    // --- Map content ---------------------------------------------------------

    fun drawImagery(
        canvas: Canvas,
        projection: MapProjection,
        sheet: MapSheet,
        tiles: TileSource
    ) {
        fill.color = if (sheet.layer.usesNetwork && sheet.layer.darkImagery) {
            0xFF11150F.toInt()
        } else {
            EMPTY_MAP
        }
        canvas.drawRect(projection.left, projection.top, projection.right, projection.bottom, fill)

        if (!sheet.layer.usesNetwork) return

        val tileSize = projection.tileSize
        val firstX = floor(projection.originTileX).toInt()
        val lastX = ceil(projection.originTileX + projection.width / tileSize).toInt()
        val firstY = floor(projection.originTileY).toInt()
        val lastY = ceil(projection.originTileY + projection.height / tileSize).toInt()

        val source = Rect()
        val destination = RectF()

        for (x in firstX..lastX) {
            for (y in firstY..lastY) {
                if (!MapMath.isValidTileY(y, projection.level)) continue
                val bitmap = tiles.tile(
                    sheet.layer,
                    projection.level,
                    MapMath.normaliseTileX(x, projection.level),
                    y
                ) ?: continue

                val left = projection.left + ((x - projection.originTileX) * tileSize).toFloat()
                val top = projection.top + ((y - projection.originTileY) * tileSize).toFloat()
                source.set(0, 0, bitmap.width, bitmap.height)
                // Half a pixel of overlap hides seams between neighbouring tiles.
                destination.set(
                    left,
                    top,
                    left + tileSize.toFloat() + 0.5f,
                    top + tileSize.toFloat() + 0.5f
                )
                canvas.drawBitmap(bitmap, source, destination, imagePaint)
            }
        }
    }

    /** Faint coordinate grid across the map face. */
    fun drawGraticule(canvas: Canvas, projection: MapProjection, sheet: MapSheet) {
        val onDark = sheet.layer.usesNetwork && sheet.layer.darkImagery
        stroke.color = if (onDark) GRID_DARK else GRID_LIGHT
        stroke.strokeWidth = min(projection.width, projection.height) / 900f

        val step = graticuleStep(projection)
        forEachGraticule(projection, step, vertical = true) { longitude ->
            val x = projection.xOf(longitude)
            canvas.drawLine(x, projection.top, x, projection.bottom, stroke)
        }
        forEachGraticule(projection, step, vertical = false) { latitude ->
            val y = projection.yOf(latitude)
            canvas.drawLine(projection.left, y, projection.right, y, stroke)
        }
    }

    fun drawMarkers(
        canvas: Canvas,
        projection: MapProjection,
        sheet: MapSheet,
        unit: Float
    ) {
        val size = unit * 1.9f
        val label = sheet.labelMarkers && sheet.surveys.size <= MAX_LABELLED_MARKERS

        sheet.surveys.forEach { survey ->
            val latitude = survey.latitude ?: return@forEach
            val longitude = survey.longitude ?: return@forEach
            val x = projection.xOf(longitude)
            val y = projection.yOf(latitude)
            if (x < projection.left - size || x > projection.right + size) return@forEach
            if (y < projection.top - size || y > projection.bottom + size) return@forEach

            val selected = survey.id == sheet.highlightId
            val scale = if (selected) 1.4f else 1f

            val triangle = Path().apply {
                moveTo(x, y - size * scale)
                lineTo(x + size * 0.92f * scale, y + size * 0.72f * scale)
                lineTo(x - size * 0.92f * scale, y + size * 0.72f * scale)
                close()
            }

            stroke.color = Color.WHITE
            stroke.strokeWidth = unit * 0.42f
            canvas.drawPath(triangle, stroke)

            fill.color = if (selected) HIGHLIGHT else MARKER
            canvas.drawPath(triangle, fill)

            stroke.color = MARKER_EDGE
            stroke.strokeWidth = unit * 0.16f
            canvas.drawPath(triangle, stroke)

            if (label) {
                drawHaloText(
                    canvas = canvas,
                    value = survey.surveyId,
                    x = x,
                    y = y + size * scale + unit * 1.9f,
                    size = unit * 1.45f,
                    align = Paint.Align.CENTER,
                    bold = false
                )
            }
        }
    }

    // --- Decorations ---------------------------------------------------------

    fun drawNeatline(canvas: Canvas, frame: RectF, unit: Float) {
        stroke.color = NEATLINE
        stroke.strokeWidth = unit * 0.28f
        canvas.drawRect(frame, stroke)
    }

    /** Tick marks and degree-minute-second labels on all four sides, as on a map sheet. */
    fun drawCoordinateLabels(
        canvas: Canvas,
        projection: MapProjection,
        frame: RectF,
        unit: Float
    ) {
        val step = graticuleStep(projection)
        val size = unit * 1.55f
        val tick = unit * 0.9f

        stroke.color = NEATLINE
        stroke.strokeWidth = unit * 0.2f

        forEachGraticule(projection, step, vertical = true) { longitude ->
            val x = projection.xOf(longitude)
            if (x < frame.left || x > frame.right) return@forEachGraticule
            canvas.drawLine(x, frame.top, x, frame.top - tick, stroke)
            canvas.drawLine(x, frame.bottom, x, frame.bottom + tick, stroke)
            plainText(canvas, formatDms(longitude, false), x, frame.top - tick * 1.5f, size, Paint.Align.CENTER)
            plainText(canvas, formatDms(longitude, false), x, frame.bottom + tick * 1.5f + size, size, Paint.Align.CENTER)
        }

        forEachGraticule(projection, step, vertical = false) { latitude ->
            val y = projection.yOf(latitude)
            if (y < frame.top || y > frame.bottom) return@forEachGraticule
            canvas.drawLine(frame.left, y, frame.left - tick, y, stroke)
            canvas.drawLine(frame.right, y, frame.right + tick, y, stroke)
            plainText(canvas, formatDms(latitude, true), frame.left - tick * 1.4f, y + size * 0.36f, size, Paint.Align.RIGHT)
            plainText(canvas, formatDms(latitude, true), frame.right + tick * 1.4f, y + size * 0.36f, size, Paint.Align.LEFT)
        }
    }

    fun drawNorthArrow(canvas: Canvas, frame: RectF, unit: Float) {
        val cx = frame.right - unit * 5.5f
        val top = frame.top + unit * 3.2f
        val h = unit * 7.5f
        val w = unit * 3.0f

        // Backing plate keeps the arrow readable over satellite imagery.
        fill.color = 0xE6FFFFFF.toInt()
        canvas.drawRoundRect(
            RectF(cx - w * 1.5f, top - unit * 2.4f, cx + w * 1.5f, top + h + unit * 1.0f),
            unit * 0.8f, unit * 0.8f, fill
        )

        val arrow = Path().apply {
            moveTo(cx, top)
            lineTo(cx + w * 0.62f, top + h)
            lineTo(cx, top + h * 0.74f)
            lineTo(cx - w * 0.62f, top + h)
            close()
        }
        fill.color = INK
        canvas.drawPath(arrow, fill)

        plainText(canvas, "N", cx, top - unit * 0.6f, unit * 2.4f, Paint.Align.CENTER, bold = true)
    }

    /**
     * Scale bar in true ground distance.
     *
     * Web-Mercator metres are inflated by 1/cos(latitude); at Ladakh's latitude that is
     * about 21%, so the correction is not optional.
     */
    fun drawScaleBar(
        canvas: Canvas,
        projection: MapProjection,
        frame: RectF,
        unit: Float,
        sheet: MapSheet
    ) {
        val latitude = sheet.bounds.centerLat
        val groundMetresPerPixel = projection.metresPerPixel * cos(Math.toRadians(latitude))
        if (groundMetresPerPixel <= 0) return

        val targetPx = projection.width * 0.26f
        val niceMetres = niceDistance(targetPx * groundMetresPerPixel)
        val barPx = (niceMetres / groundMetresPerPixel).toFloat()
        if (barPx <= 0f || barPx > projection.width) return

        val height = unit * 1.15f
        val right = frame.right - unit * 2.4f
        val left = right - barPx
        val bottom = frame.bottom - unit * 3.4f
        val top = bottom - height

        fill.color = 0xE6FFFFFF.toInt()
        canvas.drawRoundRect(
            RectF(left - unit * 1.2f, top - unit * 2.6f, right + unit * 1.2f, bottom + unit * 1.4f),
            unit * 0.7f, unit * 0.7f, fill
        )

        // Four alternating cells, the classic checker bar.
        val cells = 4
        val cell = barPx / cells
        for (index in 0 until cells) {
            fill.color = if (index % 2 == 0) INK else Color.WHITE
            canvas.drawRect(left + index * cell, top, left + (index + 1) * cell, bottom, fill)
        }
        stroke.color = INK
        stroke.strokeWidth = unit * 0.14f
        canvas.drawRect(left, top, right, bottom, stroke)

        val size = unit * 1.4f
        plainText(canvas, "0", left, top - unit * 0.7f, size, Paint.Align.CENTER)
        plainText(canvas, formatDistance(niceMetres), right, top - unit * 0.7f, size, Paint.Align.CENTER)
    }

    fun drawLegend(canvas: Canvas, frame: RectF, unit: Float, sheet: MapSheet) {
        val located = sheet.surveys.count { it.hasGps }
        val lines = listOf(
            "Survey location  (n = $located)",
            "Basemap: ${sheet.layer.label}",
            "Grid: WGS 84 geographic (EPSG:4326)"
        )

        val size = unit * 1.5f
        val titleSize = unit * 1.9f
        val padding = unit * 1.4f
        val lineHeight = size * 1.55f

        text.textSize = size
        text.typeface = Typeface.DEFAULT
        val widest = lines.maxOf { text.measureText(it) }
        val boxWidth = widest + padding * 2 + unit * 3.2f
        val boxHeight = titleSize + lineHeight * lines.size + padding * 2.1f

        val left = frame.left + unit * 2.4f
        val bottom = frame.bottom - unit * 2.4f
        val top = bottom - boxHeight
        val box = RectF(left, top, left + boxWidth, bottom)

        fill.color = 0xF2FFFFFF.toInt()
        canvas.drawRoundRect(box, unit * 0.8f, unit * 0.8f, fill)
        stroke.color = NEATLINE
        stroke.strokeWidth = unit * 0.16f
        canvas.drawRoundRect(box, unit * 0.8f, unit * 0.8f, stroke)

        var y = top + padding + titleSize * 0.85f
        plainText(canvas, "Legend", left + padding, y, titleSize, Paint.Align.LEFT, bold = true)
        y += lineHeight * 0.85f

        lines.forEachIndexed { index, line ->
            y += lineHeight
            if (index == 0) {
                val glyphX = left + padding + unit * 1.1f
                val glyphY = y - size * 0.35f
                val g = unit * 1.0f
                val triangle = Path().apply {
                    moveTo(glyphX, glyphY - g)
                    lineTo(glyphX + g * 0.92f, glyphY + g * 0.72f)
                    lineTo(glyphX - g * 0.92f, glyphY + g * 0.72f)
                    close()
                }
                fill.color = MARKER
                canvas.drawPath(triangle, fill)
                stroke.color = MARKER_EDGE
                stroke.strokeWidth = unit * 0.13f
                canvas.drawPath(triangle, stroke)
            }
            plainText(canvas, line, left + padding + unit * 3.0f, y, size, Paint.Align.LEFT)
        }
    }

    fun drawTitleBlock(
        canvas: Canvas,
        width: Float,
        band: Float,
        unit: Float,
        sheet: MapSheet
    ) {
        plainText(canvas, sheet.title, width / 2f, band * 0.46f, unit * 3.1f, Paint.Align.CENTER, bold = true)
        plainText(canvas, sheet.subtitle, width / 2f, band * 0.76f, unit * 1.75f, Paint.Align.CENTER, colour = MUTED)
    }

    fun drawFooter(
        canvas: Canvas,
        width: Float,
        height: Float,
        band: Float,
        unit: Float,
        sheet: MapSheet
    ) {
        val size = unit * 1.5f
        val baseline = height - band + size * 1.6f
        val stamp = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

        plainText(canvas, sheet.layer.attribution, unit * 2.6f, baseline, size, Paint.Align.LEFT, colour = MUTED)
        plainText(
            canvas,
            "Prepared by Stanzin Khenrab · Krishi Vigyan Kendra – Leh, Ladakh · $stamp",
            unit * 2.6f,
            baseline + size * 1.5f,
            size,
            Paint.Align.LEFT,
            colour = MUTED
        )
        plainText(
            canvas,
            "${sheet.surveys.count { it.hasGps }} survey locations",
            width - unit * 2.6f,
            baseline,
            size,
            Paint.Align.RIGHT,
            colour = MUTED
        )
    }

    // --- Helpers -------------------------------------------------------------

    private fun plainText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        align: Paint.Align,
        bold: Boolean = false,
        colour: Int = INK
    ) {
        text.textSize = size
        text.textAlign = align
        text.color = colour
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        text.style = Paint.Style.FILL
        canvas.drawText(value, x, y, text)
    }

    /** Text with a white outline, so it stays readable over any imagery. */
    private fun drawHaloText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        align: Paint.Align,
        bold: Boolean
    ) {
        text.textSize = size
        text.textAlign = align
        text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        text.style = Paint.Style.STROKE
        text.strokeWidth = size * 0.32f
        text.color = Color.WHITE
        canvas.drawText(value, x, y, text)

        text.style = Paint.Style.FILL
        text.color = INK
        canvas.drawText(value, x, y, text)
    }

    /** Degree interval giving roughly five divisions across the map. */
    private fun graticuleStep(projection: MapProjection): Double {
        val span = abs(projection.longitudeAt(projection.right) - projection.longitudeAt(projection.left))
        val target = span / 5.0
        return GRATICULE_STEPS.minByOrNull { abs(it - target) } ?: (1.0 / 60.0)
    }

    private inline fun forEachGraticule(
        projection: MapProjection,
        step: Double,
        vertical: Boolean,
        action: (Double) -> Unit
    ) {
        val from: Double
        val to: Double
        if (vertical) {
            from = projection.longitudeAt(projection.left)
            to = projection.longitudeAt(projection.right)
        } else {
            from = projection.latitudeAt(projection.bottom)
            to = projection.latitudeAt(projection.top)
        }
        val low = min(from, to)
        val high = maxOf(from, to)
        var value = ceil(low / step) * step
        var guard = 0
        while (value <= high && guard < 200) {
            action(value)
            value += step
            guard++
        }
    }

    /** 34.152588 -> 34°9'9"N */
    fun formatDms(value: Double, isLatitude: Boolean): String {
        val hemisphere = when {
            isLatitude && value >= 0 -> "N"
            isLatitude -> "S"
            value >= 0 -> "E"
            else -> "W"
        }
        val magnitude = abs(value)
        var degrees = floor(magnitude).toInt()
        var minutes = floor((magnitude - degrees) * 60).toInt()
        var seconds = (((magnitude - degrees) * 60) - minutes) * 60
        var wholeSeconds = seconds.roundToInt()
        if (wholeSeconds >= 60) {
            wholeSeconds -= 60
            minutes++
        }
        if (minutes >= 60) {
            minutes -= 60
            degrees++
        }
        return "$degrees°$minutes'$wholeSeconds\"$hemisphere"
    }

    /** Rounds a distance down to 1, 2 or 5 times a power of ten. */
    private fun niceDistance(metres: Double): Double {
        if (metres <= 0) return 1.0
        val magnitude = Math.pow(10.0, floor(Math.log10(metres)))
        val normalised = metres / magnitude
        val nice = when {
            normalised >= 5 -> 5.0
            normalised >= 2 -> 2.0
            else -> 1.0
        }
        return nice * magnitude
    }

    private fun formatDistance(metres: Double): String = when {
        metres >= 1000 -> {
            val km = metres / 1000.0
            if (km >= 10) "${km.roundToInt()} km" else String.format(Locale.US, "%.1f km", km)
        }
        else -> "${metres.roundToInt()} m"
    }

}