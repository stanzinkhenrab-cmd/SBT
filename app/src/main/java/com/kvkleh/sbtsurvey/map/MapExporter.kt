package com.kvkleh.sbtsurvey.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

enum class MapExportFormat(
    val label: String,
    val description: String,
    val extension: String,
    val mimeType: String
) {
    PDF(
        label = "Map sheet (PDF)",
        description = "Print-ready A4 layout with legend, scale bar and grid",
        extension = "pdf",
        mimeType = "application/pdf"
    ),
    GEOTIFF(
        label = "GeoTIFF (GIS)",
        description = "Georeferenced raster for ArcGIS, QGIS and ERDAS (EPSG:3857)",
        extension = "tif",
        mimeType = "image/tiff"
    ),
    JPEG(
        label = "Image (JPG)",
        description = "High-resolution picture for reports and messaging",
        extension = "jpg",
        mimeType = "image/jpeg"
    ),
    PNG(
        label = "Image (PNG)",
        description = "Lossless picture, best for printing and slides",
        extension = "png",
        mimeType = "image/png"
    )
}

data class MapExportResult(
    val file: File,
    val format: MapExportFormat,
    val markerCount: Int,
    val widthPx: Int,
    val heightPx: Int,
    /** Present for GeoTIFF: ground resolution in metres per pixel. */
    val metresPerPixel: Double? = null
) {
    val sizeLabel: String
        get() {
            val bytes = file.length()
            return when {
                bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
                else -> String.format(Locale.US, "%.0f kB", bytes / 1024.0)
            }
        }
}

/**
 * Renders the survey map to a shareable file.
 *
 * PDF, JPG and PNG carry the full cartographic layout — title, legend, north arrow,
 * scale bar and coordinate grid — because they are meant to be read by a person. The
 * GeoTIFF deliberately contains the map face only, with no decoration, because every
 * pixel in it has to correspond to a real ground position for GIS software to use it.
 */
class MapExporter(
    private val context: Context,
    private val tileCache: TileCache
) {

    private val exportDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun export(
        format: MapExportFormat,
        surveys: List<SurveyEntity>,
        layer: BaseMapLayer,
        bounds: GeoBounds
    ): MapExportResult = withContext(Dispatchers.IO) {
        val located = surveys.filter { it.hasGps }
        check(located.isNotEmpty()) {
            "No survey has GPS coordinates yet, so there is nothing to map."
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val name = "SBT_Survey_Map_$stamp.${format.extension}"
        val target = File(exportDir, name)

        val result = when (format) {
            MapExportFormat.PDF -> renderPdf(target, located, layer, bounds)
            MapExportFormat.GEOTIFF -> renderGeoTiff(target, located, layer, bounds)
            MapExportFormat.JPEG -> renderRaster(target, located, layer, bounds, format)
            MapExportFormat.PNG -> renderRaster(target, located, layer, bounds, format)
        }

        check(target.exists() && target.length() > 0) {
            "The map file could not be written. Please try again."
        }
        result
    }

    // --- PDF -----------------------------------------------------------------

    /**
     * A4 landscape at 72 pt/inch.
     *
     * Drawing in points keeps text, symbols and the neatline as vector objects, so the
     * sheet stays crisp at any print size; only the imagery is raster, and the detail
     * boost pulls that from two zoom levels deeper to keep it near 300 dpi.
     */
    private suspend fun renderPdf(
        target: File,
        surveys: List<SurveyEntity>,
        layer: BaseMapLayer,
        bounds: GeoBounds
    ): MapExportResult {
        val widthPt = 842
        val heightPt = 595

        val sheet = MapSheet(
            surveys = surveys,
            layer = layer,
            bounds = bounds,
            decorated = true,
            detailBoost = PDF_DETAIL_BOOST
        )
        val tiles = prefetch(widthPt.toFloat(), heightPt.toFloat(), sheet)

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(widthPt, heightPt, 1).create()
            val page = document.startPage(pageInfo)
            MapComposer.draw(page.canvas, widthPt.toFloat(), heightPt.toFloat(), sheet, tiles)
            document.finishPage(page)
            target.outputStream().use { document.writeTo(it) }
        } finally {
            document.close()
            tiles.recycle()
        }

        return MapExportResult(target, MapExportFormat.PDF, surveys.size, widthPt, heightPt)
    }

    // --- Raster --------------------------------------------------------------

    private suspend fun renderRaster(
        target: File,
        surveys: List<SurveyEntity>,
        layer: BaseMapLayer,
        bounds: GeoBounds,
        format: MapExportFormat
    ): MapExportResult {
        val sheet = MapSheet(surveys = surveys, layer = layer, bounds = bounds, decorated = true)

        var width = RASTER_WIDTH
        var height = RASTER_HEIGHT
        var bitmap = allocate(width, height)
        if (bitmap == null) {
            // Fall back to a smaller sheet rather than failing on a low-memory device.
            width = RASTER_WIDTH_FALLBACK
            height = RASTER_HEIGHT_FALLBACK
            bitmap = allocate(width, height)
                ?: error("Not enough memory to render the map. Close other apps and try again.")
        }

        val tiles = prefetch(width.toFloat(), height.toFloat(), sheet)
        try {
            MapComposer.draw(Canvas(bitmap), width.toFloat(), height.toFloat(), sheet, tiles)
            target.outputStream().use { output ->
                if (format == MapExportFormat.PNG) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                }
            }
        } finally {
            tiles.recycle()
            bitmap.recycle()
        }

        return MapExportResult(target, format, surveys.size, width, height)
    }

    // --- GeoTIFF -------------------------------------------------------------

    private suspend fun renderGeoTiff(
        target: File,
        surveys: List<SurveyEntity>,
        layer: BaseMapLayer,
        bounds: GeoBounds
    ): MapExportResult {
        // No decoration: in a georeferenced raster every pixel must be a ground position,
        // so a legend or title drawn into the image would be meaningless in GIS.
        val sheet = MapSheet(
            surveys = surveys,
            layer = layer,
            bounds = bounds,
            decorated = false,
            labelMarkers = false
        )

        var size = GEOTIFF_SIZE
        var bitmap = allocate(size, size)
        if (bitmap == null) {
            size = GEOTIFF_SIZE_FALLBACK
            bitmap = allocate(size, size)
                ?: error("Not enough memory to render the map. Close other apps and try again.")
        }

        val tiles = prefetch(size.toFloat(), size.toFloat(), sheet)
        val projection: MapProjection
        try {
            projection = MapComposer.draw(Canvas(bitmap), size.toFloat(), size.toFloat(), sheet, tiles)

            val pixels = IntArray(size * size)
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

            target.outputStream().use { output ->
                GeoTiffWriter.write(
                    output = output,
                    pixels = pixels,
                    width = size,
                    height = size,
                    originX = projection.webMercatorLeft,
                    originY = projection.webMercatorTop,
                    pixelSize = projection.metresPerPixel
                )
            }
        } finally {
            tiles.recycle()
            bitmap.recycle()
        }

        return MapExportResult(
            file = target,
            format = MapExportFormat.GEOTIFF,
            markerCount = surveys.size,
            widthPx = size,
            heightPx = size,
            metresPerPixel = projection.metresPerPixel
        )
    }

    // --- Tiles ---------------------------------------------------------------

    /**
     * Collects every tile the sheet needs before drawing starts.
     *
     * Exports must not have holes, so tiles missing from the cache are fetched and waited
     * for. Offline, whatever is already cached is used and the rest is simply absent.
     */
    private suspend fun prefetch(width: Float, height: Float, sheet: MapSheet): PrefetchedTiles {
        val projection = MapComposer.projectionFor(width, height, sheet)
        val cached = HashMap<String, Bitmap>()
        if (!sheet.layer.usesNetwork) return PrefetchedTiles(cached)

        val tileSize = projection.tileSize
        val firstX = floor(projection.originTileX).toInt()
        val lastX = ceil(projection.originTileX + projection.width / tileSize).toInt()
        val firstY = floor(projection.originTileY).toInt()
        val lastY = ceil(projection.originTileY + projection.height / tileSize).toInt()

        var fetched = 0
        for (x in firstX..lastX) {
            for (y in firstY..lastY) {
                if (!MapMath.isValidTileY(y, projection.level)) continue
                if (fetched >= MAX_EXPORT_TILES) break
                val normalisedX = MapMath.normaliseTileX(x, projection.level)
                val bitmap = tileCache.fetchBlocking(sheet.layer, projection.level, normalisedX, y)
                if (bitmap != null) {
                    cached["${projection.level}/$normalisedX/$y"] = bitmap
                    fetched++
                }
            }
        }
        return PrefetchedTiles(cached)
    }

    /**
     * Tiles held for the duration of one render.
     *
     * The bitmaps belong to the shared cache, so [recycle] only drops the references.
     */
    private class PrefetchedTiles(private val tiles: Map<String, Bitmap>) : TileSource {
        override fun tile(layer: BaseMapLayer, zoom: Int, x: Int, y: Int): Bitmap? =
            tiles["$zoom/$x/$y"]?.takeIf { !it.isRecycled }

        fun recycle() = Unit
    }

    private fun allocate(width: Int, height: Int): Bitmap? = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        null
    }

    companion object {
        /** A4 landscape at roughly 210 dpi. */
        private const val RASTER_WIDTH = 2480
        private const val RASTER_HEIGHT = 1754
        private const val RASTER_WIDTH_FALLBACK = 1754
        private const val RASTER_HEIGHT_FALLBACK = 1240

        private const val GEOTIFF_SIZE = 2000
        private const val GEOTIFF_SIZE_FALLBACK = 1200

        /** Two extra zoom levels lift PDF imagery from ~72 dpi to roughly 290 dpi. */
        private const val PDF_DETAIL_BOOST = 2

        private const val MAX_EXPORT_TILES = 400

    }
}
