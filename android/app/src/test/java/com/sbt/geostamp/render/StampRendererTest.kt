package com.sbt.geostamp.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.sbt.geostamp.map.StaticMap
import com.sbt.geostamp.model.StampContent
import com.sbt.geostamp.model.StampOptions
import com.sbt.geostamp.model.Template
import com.sbt.geostamp.qr.QrCodes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Renders every template with Robolectric's native (Skia-backed) graphics and writes the
 * results to `build/reports/stamp-samples` so the layouts can be eyeballed after a run.
 *
 * Point it at a real photo with `-Dgeostamp.sampleImage=/path/to/photo.jpg`; otherwise a
 * synthetic gradient stands in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StampRendererTest {

    @Before
    fun pinTimeZone() {
        // Keeps the rendered date deterministic wherever the suite runs.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    private val content = StampContent(
        title = "Sumoor, Ladakh, India",
        addressLine = "Sumoor, Nubra Valley, Ladakh 194401, India",
        latitude = 34.998600,
        longitude = 77.383358,
        altitudeMeters = 3132.0,
        timeMillis = fixedTime(),
        note = "Seabuckthorn survey plot 12"
    )

    @Test
    fun `every template renders and keeps the photo width`() {
        val photo = samplePhoto()
        val outputDir = File("build/reports/stamp-samples").apply { mkdirs() }

        Template.entries.forEach { template ->
            val options = StampOptions(template = template)
            val qr = QrCodes.encode(content.mapsUrl()!!, 256)
            assertNotNull("QR encoding failed for $template", qr)

            val thumbnail = mapThumbnail(StampRenderer.suggestedThumbnailSize(photo, options))
            val stamped = StampRenderer.render(photo, content, options, thumbnail, qr)

            assertEquals(photo.width, stamped.width)
            if (template == Template.POLAROID) {
                assertTrue(
                    "Polaroid should extend the canvas downwards",
                    stamped.height > photo.height
                )
            } else {
                assertEquals(photo.height, stamped.height)
                assertTrue(
                    "$template drew nothing over the photo",
                    differsNearBottom(photo, stamped)
                )
            }

            FileOutputStream(File(outputDir, "${template.name.lowercase()}.png")).use { out ->
                stamped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            stamped.recycle()
        }
    }

    @Test
    fun `coordinates format in both decimal and dms`() {
        assertEquals("Lat 34.998600, Long 77.383358", content.formattedCoordinates(dms = false))
        assertEquals("34°59'55.0\"N  77°23'00.1\"E", content.formattedCoordinates(dms = true))
    }

    @Test
    fun `stamp survives a missing location`() {
        val photo = samplePhoto()
        val blank = content.copy(latitude = null, longitude = null, altitudeMeters = null)
        val stamped = StampRenderer.render(photo, blank, StampOptions(), null, null)
        assertEquals(photo.width, stamped.width)
    }

    // -------------------------------------------------------------- fixtures

    private fun samplePhoto(): Bitmap {
        System.getProperty("geostamp.sampleImage")?.let { path ->
            BitmapFactory.decodeFile(path)?.let { return it }
        }
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, 1600f,
            Color.rgb(120, 150, 190), Color.rgb(210, 200, 170), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, 1200f, 1600f, paint)
        paint.shader = null
        paint.color = Color.rgb(90, 105, 80)
        canvas.drawCircle(600f, 900f, 320f, paint)
        return bitmap
    }

    /**
     * A flat stand-in by default so the test stays offline. Set `-Dgeostamp.liveMap=true`
     * to pull real OpenStreetMap tiles when you want a true-to-life sample.
     */
    private fun mapThumbnail(sizePx: Int): Bitmap {
        if (System.getProperty("geostamp.liveMap") == "true") {
            val map = StaticMap(File("build/tmp/map-cache").apply { mkdirs() })
            runBlocking {
                map.thumbnail(content.latitude!!, content.longitude!!, sizePx, sizePx, 13)
            }.let { return it }
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(232, 224, 216))
        return bitmap
    }

    /** The stamp lives in the bottom band, so a difference there proves it was drawn. */
    private fun differsNearBottom(before: Bitmap, after: Bitmap): Boolean {
        val y = (before.height * 0.95f).toInt()
        for (x in 0 until before.width step 7) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) return true
        }
        return false
    }

    private fun fixedTime(): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
        calendar.set(2026, Calendar.AUGUST, 29, 10, 34, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
