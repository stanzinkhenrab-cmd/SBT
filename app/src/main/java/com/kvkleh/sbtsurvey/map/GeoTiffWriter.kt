package com.kvkleh.sbtsurvey.map

import java.io.BufferedOutputStream
import java.io.OutputStream

/**
 * Writes a georeferenced RGB TIFF (GeoTIFF 1.8.2, little-endian, uncompressed).
 *
 * The output opens directly in ArcGIS Pro/Map, QGIS, ERDAS and GDAL with its coordinate
 * system already attached, so an exported survey map can be dropped straight onto an
 * existing GIS project without manual georeferencing.
 *
 * The raster is written in EPSG:3857 (WGS 84 / Pseudo-Mercator) because that is the
 * projection the map is drawn in — pixels are square in that system, which is what makes
 * a simple pixel-scale/tie-point georeference exact rather than approximate.
 *
 * Deliberately dependency-free: pulling a full geospatial stack into a field application
 * would add tens of megabytes for one export format.
 */
object GeoTiffWriter {

    /** WGS 84 / Pseudo-Mercator — the projection used by web basemap tiles. */
    const val EPSG_WEB_MERCATOR = 3857

    // Baseline TIFF tags.
    private const val TAG_IMAGE_WIDTH = 256
    private const val TAG_IMAGE_LENGTH = 257
    private const val TAG_BITS_PER_SAMPLE = 258
    private const val TAG_COMPRESSION = 259
    private const val TAG_PHOTOMETRIC = 262
    private const val TAG_STRIP_OFFSETS = 273
    private const val TAG_SAMPLES_PER_PIXEL = 277
    private const val TAG_ROWS_PER_STRIP = 278
    private const val TAG_STRIP_BYTE_COUNTS = 279
    private const val TAG_PLANAR_CONFIG = 284
    private const val TAG_SOFTWARE = 305

    // GeoTIFF extension tags.
    private const val TAG_MODEL_PIXEL_SCALE = 33550
    private const val TAG_MODEL_TIEPOINT = 33922
    private const val TAG_GEO_KEY_DIRECTORY = 34735
    private const val TAG_GEO_ASCII_PARAMS = 34737

    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_ASCII = 2
    private const val TYPE_DOUBLE = 12

    private const val SOFTWARE = "Seabuckthorn Field Survey - Ladakh (KVK Leh)"

    /**
     * @param pixels ARGB pixels in row-major order, as returned by `Bitmap.getPixels`.
     *               The alpha channel is dropped; the raster is written as RGB.
     * @param originX easting of the *outer* top-left corner of the top-left pixel, metres.
     * @param originY northing of that same corner, metres.
     * @param pixelSize ground size of one pixel in metres (square pixels).
     */
    fun write(
        output: OutputStream,
        pixels: IntArray,
        width: Int,
        height: Int,
        originX: Double,
        originY: Double,
        pixelSize: Double,
        epsgCode: Int = EPSG_WEB_MERCATOR,
        citation: String = "WGS 84 / Pseudo-Mercator"
    ) {
        require(width > 0 && height > 0) { "Raster must not be empty" }
        require(pixels.size >= width * height) { "Pixel buffer is smaller than the raster" }

        val stream = BufferedOutputStream(output, 1 shl 16)

        // The ASCII citation must be NUL-terminated, per the GeoTIFF specification.
        val citationBytes = (citation + "|").toByteArray(Charsets.US_ASCII) + 0
        val softwareBytes = SOFTWARE.toByteArray(Charsets.US_ASCII) + 0

        val entries = 15
        // 8-byte header, then the IFD, then the out-of-line values, then the pixels.
        val ifdOffset = 8L
        val ifdBytes = 2L + entries * 12L + 4L
        var cursor = ifdOffset + ifdBytes

        val bitsPerSampleOffset = cursor; cursor += 3 * 2          // 3 SHORTs
        val pixelScaleOffset = cursor; cursor += 3 * 8             // 3 DOUBLEs
        val tiePointOffset = cursor; cursor += 6 * 8               // 6 DOUBLEs
        val geoKeyOffset = cursor; cursor += GEO_KEY_COUNT * 2     // SHORTs
        val geoAsciiOffset = cursor; cursor += citationBytes.size
        val softwareOffset = cursor; cursor += softwareBytes.size
        if (cursor % 2L != 0L) cursor++                            // word-align the raster
        val rasterOffset = cursor
        val rasterBytes = width.toLong() * height.toLong() * 3L

        // --- Header ---------------------------------------------------------
        stream.write(byteArrayOf(0x49, 0x49))   // "II": little-endian
        stream.writeShort(42)                    // TIFF magic
        stream.writeInt(ifdOffset.toInt())

        // --- Image File Directory, tags in ascending order ------------------
        stream.writeShort(entries)
        stream.writeEntry(TAG_IMAGE_WIDTH, TYPE_LONG, 1, width.toLong())
        stream.writeEntry(TAG_IMAGE_LENGTH, TYPE_LONG, 1, height.toLong())
        stream.writeEntry(TAG_BITS_PER_SAMPLE, TYPE_SHORT, 3, bitsPerSampleOffset)
        stream.writeEntry(TAG_COMPRESSION, TYPE_SHORT, 1, 1L)          // none
        stream.writeEntry(TAG_PHOTOMETRIC, TYPE_SHORT, 1, 2L)          // RGB
        stream.writeEntry(TAG_STRIP_OFFSETS, TYPE_LONG, 1, rasterOffset)
        stream.writeEntry(TAG_SAMPLES_PER_PIXEL, TYPE_SHORT, 1, 3L)
        stream.writeEntry(TAG_ROWS_PER_STRIP, TYPE_LONG, 1, height.toLong())
        stream.writeEntry(TAG_STRIP_BYTE_COUNTS, TYPE_LONG, 1, rasterBytes)
        stream.writeEntry(TAG_PLANAR_CONFIG, TYPE_SHORT, 1, 1L)        // chunky
        stream.writeEntry(TAG_SOFTWARE, TYPE_ASCII, softwareBytes.size, softwareOffset)
        stream.writeEntry(TAG_MODEL_PIXEL_SCALE, TYPE_DOUBLE, 3, pixelScaleOffset)
        stream.writeEntry(TAG_MODEL_TIEPOINT, TYPE_DOUBLE, 6, tiePointOffset)
        stream.writeEntry(TAG_GEO_KEY_DIRECTORY, TYPE_SHORT, GEO_KEY_COUNT, geoKeyOffset)
        stream.writeEntry(TAG_GEO_ASCII_PARAMS, TYPE_ASCII, citationBytes.size, geoAsciiOffset)
        stream.writeInt(0)   // no next IFD

        // --- Out-of-line values --------------------------------------------
        repeat(3) { stream.writeShort(8) }                              // bits per sample

        stream.writeDouble(pixelSize)                                   // ModelPixelScale
        stream.writeDouble(pixelSize)
        stream.writeDouble(0.0)

        stream.writeDouble(0.0)                                         // ModelTiepoint
        stream.writeDouble(0.0)
        stream.writeDouble(0.0)
        stream.writeDouble(originX)
        stream.writeDouble(originY)
        stream.writeDouble(0.0)

        writeGeoKeys(stream, epsgCode, citationBytes.size)
        stream.write(citationBytes)
        stream.write(softwareBytes)
        if ((geoAsciiOffset + citationBytes.size + softwareBytes.size) % 2L != 0L) {
            stream.write(0)
        }

        // --- Raster ---------------------------------------------------------
        val row = ByteArray(width * 3)
        for (y in 0 until height) {
            var index = y * width
            var out = 0
            for (x in 0 until width) {
                val argb = pixels[index++]
                row[out++] = ((argb shr 16) and 0xFF).toByte()
                row[out++] = ((argb shr 8) and 0xFF).toByte()
                row[out++] = (argb and 0xFF).toByte()
            }
            stream.write(row)
        }
        stream.flush()
    }

    /** version, revision, minor revision, key count, then 4 shorts per key. */
    private const val GEO_KEY_COUNT = 4 + 4 * 4

    private fun writeGeoKeys(stream: OutputStream, epsgCode: Int, citationLength: Int) {
        stream.writeShort(1)   // GeoTIFF key directory version
        stream.writeShort(1)   // key revision
        stream.writeShort(0)   // minor revision
        stream.writeShort(4)   // number of keys that follow

        // GTModelTypeGeoKey = ModelTypeProjected
        stream.writeShort(1024); stream.writeShort(0); stream.writeShort(1); stream.writeShort(1)
        // GTRasterTypeGeoKey = RasterPixelIsArea
        stream.writeShort(1025); stream.writeShort(0); stream.writeShort(1); stream.writeShort(1)
        // GTCitationGeoKey -> the ASCII params block
        stream.writeShort(1026)
        stream.writeShort(TAG_GEO_ASCII_PARAMS)
        stream.writeShort(citationLength)
        stream.writeShort(0)
        // ProjectedCSTypeGeoKey = the EPSG code
        stream.writeShort(3072); stream.writeShort(0); stream.writeShort(1); stream.writeShort(epsgCode)
    }

    // --- little-endian primitives ------------------------------------------

    private fun OutputStream.writeShort(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private fun OutputStream.writeInt(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun OutputStream.writeDouble(value: Double) {
        val bits = java.lang.Double.doubleToLongBits(value)
        for (shift in 0 until 8) write(((bits shr (shift * 8)) and 0xFF).toInt())
    }

    private fun OutputStream.writeEntry(tag: Int, type: Int, count: Int, value: Long) {
        writeShort(tag)
        writeShort(type)
        writeInt(count)
        // Values of four bytes or fewer live inline; anything larger is an offset.
        val inlineBytes = when (type) {
            TYPE_SHORT -> count * 2
            TYPE_LONG -> count * 4
            TYPE_ASCII -> count
            TYPE_DOUBLE -> count * 8
            else -> 4
        }
        if (inlineBytes <= 4 && type == TYPE_SHORT) {
            writeShort(value.toInt())
            writeShort(0)
        } else {
            writeInt(value.toInt())
        }
    }
}
