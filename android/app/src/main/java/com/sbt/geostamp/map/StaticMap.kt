package com.sbt.geostamp.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Builds the little map thumbnail shown inside the stamp by stitching OpenStreetMap raster
 * tiles together and drawing a pin at the centre.
 *
 * Tiles are cached on disk, and every failure path falls back to a drawn placeholder so the
 * stamp still renders when the phone is offline.
 */
class StaticMap(private val cacheDir: File) {

    /** Override to point at your own tile server; see README for OSM's usage policy. */
    var tileUrlTemplate: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    /** Sent with every tile request — OSM rejects requests without an identifying agent. */
    var userAgent: String = "GeoStampCamera/1.0 (Android; open-source photo stamper)"

    suspend fun thumbnail(
        latitude: Double,
        longitude: Double,
        widthPx: Int,
        heightPx: Int,
        zoom: Int = 14
    ): Bitmap = withContext(Dispatchers.IO) {
        val stitched = runCatching { stitch(latitude, longitude, widthPx, heightPx, zoom) }.getOrNull()
        val canvasBitmap = stitched ?: placeholder(widthPx, heightPx)
        drawPin(canvasBitmap)
        canvasBitmap
    }

    private suspend fun stitch(
        latitude: Double,
        longitude: Double,
        widthPx: Int,
        heightPx: Int,
        zoom: Int
    ): Bitmap? = coroutineScope {
        val scale = 2.0.pow(zoom)
        val centreX = (longitude + 180.0) / 360.0 * scale * TILE_SIZE
        val centreY = (1.0 - asinh(tan(latitude * PI / 180.0)) / PI) / 2.0 * scale * TILE_SIZE

        val left = centreX - widthPx / 2.0
        val top = centreY - heightPx / 2.0
        val firstTileX = floor(left / TILE_SIZE).toInt()
        val firstTileY = floor(top / TILE_SIZE).toInt()
        val lastTileX = floor((left + widthPx) / TILE_SIZE).toInt()
        val lastTileY = floor((top + heightPx) / TILE_SIZE).toInt()
        val tileCount = 2.0.pow(zoom).toInt()

        val requests = buildList {
            for (tileX in firstTileX..lastTileX) {
                for (tileY in firstTileY..lastTileY) {
                    if (tileY < 0 || tileY >= tileCount) continue
                    add(tileX to tileY)
                }
            }
        }
        if (requests.isEmpty()) return@coroutineScope null

        val tiles = requests.map { (tileX, tileY) ->
            val wrappedX = ((tileX % tileCount) + tileCount) % tileCount
            async { Triple(tileX, tileY, loadTile(zoom, wrappedX, tileY)) }
        }.map { it.await() }

        if (tiles.none { it.third != null }) return@coroutineScope null

        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(LAND_COLOR)
        tiles.forEach { (tileX, tileY, bitmap) ->
            if (bitmap == null) return@forEach
            val offsetX = (tileX * TILE_SIZE - left).toFloat()
            val offsetY = (tileY * TILE_SIZE - top).toFloat()
            canvas.drawBitmap(bitmap, offsetX, offsetY, null)
            bitmap.recycle()
        }
        output
    }

    private fun loadTile(zoom: Int, x: Int, y: Int): Bitmap? {
        val cached = File(tileCacheDir(), "${zoom}_${x}_${y}.png")
        if (cached.exists() && cached.length() > 0L) {
            BitmapFactory.decodeFile(cached.absolutePath)?.let { return it }
        }

        val url = tileUrlTemplate
            .replace("{z}", zoom.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())

        val bytes = runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: return null

        runCatching { cached.writeBytes(bytes) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun tileCacheDir(): File =
        File(cacheDir, "map-tiles").apply { if (!exists()) mkdirs() }

    /** A believable "map-ish" backdrop for when tiles cannot be fetched. */
    private fun placeholder(widthPx: Int, heightPx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
            LAND_COLOR, 0xFFD8E4C8.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)

        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = 0x33000000
        paint.strokeWidth = widthPx * 0.006f
        val step = widthPx / 5f
        var offset = -heightPx.toFloat()
        while (offset < widthPx + heightPx) {
            canvas.drawLine(offset, 0f, offset + heightPx, heightPx.toFloat(), paint)
            offset += step
        }

        paint.color = 0x552E7D32
        paint.strokeWidth = widthPx * 0.05f
        canvas.drawLine(0f, heightPx * 0.62f, widthPx.toFloat(), heightPx * 0.38f, paint)
        return bitmap
    }

    /** Red map pin plus the translucent heading cone, centred on the thumbnail. */
    private fun drawPin(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val centreX = width / 2f
        val centreY = height / 2f
        val pinHeight = height * 0.42f
        val headRadius = pinHeight * 0.30f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Heading cone.
        paint.color = 0x553D5AFE
        val cone = Path().apply {
            moveTo(centreX, centreY)
            lineTo(centreX - width * 0.22f, centreY + height * 0.34f)
            lineTo(centreX + width * 0.22f, centreY + height * 0.34f)
            close()
        }
        canvas.drawPath(cone, paint)

        val headCentreY = centreY - pinHeight * 0.34f
        val pin = Path().apply {
            addCircle(centreX, headCentreY, headRadius, Path.Direction.CW)
            moveTo(centreX - headRadius * 0.72f, headCentreY + headRadius * 0.70f)
            lineTo(centreX, centreY + pinHeight * 0.52f)
            lineTo(centreX + headRadius * 0.72f, headCentreY + headRadius * 0.70f)
            close()
        }

        paint.color = 0x40000000
        canvas.drawOval(
            RectF(
                centreX - headRadius * 0.8f,
                centreY + pinHeight * 0.44f,
                centreX + headRadius * 0.8f,
                centreY + pinHeight * 0.62f
            ),
            paint
        )

        paint.color = PIN_COLOR
        canvas.drawPath(pin, paint)
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = headRadius * 0.22f
        canvas.drawPath(pin, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centreX, headCentreY, headRadius * 0.34f, paint)
    }

    private companion object {
        const val TILE_SIZE = 256
        const val LAND_COLOR = 0xFFE8E0D8.toInt()
        const val PIN_COLOR = 0xFFE53935.toInt()
    }
}
