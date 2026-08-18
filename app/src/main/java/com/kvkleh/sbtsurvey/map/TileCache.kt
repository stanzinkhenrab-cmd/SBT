package com.kvkleh.sbtsurvey.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Disk-and-memory cache of raster map tiles, one directory per basemap layer.
 *
 * The survey map is offline-first: markers, coordinates and the scale bar always come
 * from the local database. Imagery is a bonus — tiles already on disk are drawn
 * immediately, and new tiles are fetched only when a network is present.
 *
 * Responses are checked rather than trusted. A tile provider that refuses the request
 * (an HTTP error, or an HTML page in place of an image) is reported through [error] so
 * the map can tell the surveyor what happened, instead of silently drawing a "blocked"
 * placeholder tile as though it were imagery.
 */
class TileCache(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val memory = object : LruCache<String, Bitmap>(MEMORY_TILES) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    private val rootDir: File
        get() = File(context.filesDir, "map_tiles").apply { if (!exists()) mkdirs() }

    private val inFlight = mutableSetOf<String>()
    private val downloadLimit = Semaphore(MAX_PARALLEL_DOWNLOADS)

    /** Bumped whenever a new tile becomes available, so the map can recompose. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Human-readable reason the current layer has no imagery, or null when fine. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var consecutiveFailures = 0

    val isOnline: Boolean
        get() = runCatching {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return@runCatching false
            val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)

    fun clearError() {
        consecutiveFailures = 0
        _error.value = null
    }

    /** Cached tiles only — never blocks and never touches the network. */
    fun peek(layer: BaseMapLayer, zoom: Int, x: Int, y: Int): Bitmap? {
        if (!layer.usesNetwork) return null
        val key = key(layer, zoom, x, y)
        memory.get(key)?.let { return it }

        val file = fileFor(layer, zoom, x, y)
        if (file.exists() && file.length() > 0) {
            val bitmap = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            if (bitmap != null) {
                memory.put(key, bitmap)
                return bitmap
            }
            file.delete()
        }
        return null
    }

    /** Queues a background download for a tile that is not cached yet. */
    fun prefetch(layer: BaseMapLayer, zoom: Int, x: Int, y: Int) {
        if (!layer.usesNetwork || zoom > layer.maxZoom) return
        if (!MapMath.isValidTileY(y, zoom)) return

        val key = key(layer, zoom, x, y)
        synchronized(inFlight) {
            if (key in inFlight || inFlight.size > MAX_QUEUED) return
            inFlight += key
        }
        if (!isOnline) {
            synchronized(inFlight) { inFlight -= key }
            reportOffline()
            return
        }
        scope.launch {
            downloadLimit.withPermit { download(layer, zoom, x, y, key) }
        }
    }

    /**
     * Fetches a tile, waiting for the network if necessary. Used when rendering an
     * export, where a missing tile would leave a hole in the printed map.
     */
    suspend fun fetchBlocking(layer: BaseMapLayer, zoom: Int, x: Int, y: Int): Bitmap? {
        peek(layer, zoom, x, y)?.let { return it }
        if (!layer.usesNetwork || zoom > layer.maxZoom || !MapMath.isValidTileY(y, zoom)) return null
        if (!isOnline) return null

        val key = key(layer, zoom, x, y)
        downloadLimit.withPermit { download(layer, zoom, x, y, key) }
        return peek(layer, zoom, x, y)
    }

    private suspend fun download(
        layer: BaseMapLayer,
        zoom: Int,
        x: Int,
        y: Int,
        key: String
    ) = withContext(Dispatchers.IO) {
        val url = layer.tileUrl(zoom, x, y) ?: return@withContext
        var connection: HttpURLConnection? = null
        try {
            val target = fileFor(layer, zoom, x, y)
            target.parentFile?.mkdirs()
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "image/png,image/jpeg,image/*;q=0.8")
            }

            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()

            if (status != HttpURLConnection.HTTP_OK) {
                recordFailure(layer, "HTTP $status")
                return@withContext
            }
            // A provider that refuses the request often answers 200 with an HTML notice
            // or a placeholder image. Anything that is not an image is not imagery.
            if (!contentType.startsWith("image/")) {
                recordFailure(layer, "the provider returned $contentType instead of imagery")
                return@withContext
            }

            val temp = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }

            if (temp.length() <= 0 || !temp.renameTo(target)) {
                temp.delete()
                recordFailure(layer, "an empty tile was returned")
                return@withContext
            }

            consecutiveFailures = 0
            _error.value = null
            trimDiskCache()
            _version.update { it + 1 }
        } catch (e: Exception) {
            recordFailure(layer, e.message ?: "the network request failed")
        } finally {
            runCatching { connection?.disconnect() }
            synchronized(inFlight) { inFlight -= key }
        }
    }

    /**
     * A single failure is not worth interrupting the surveyor over; a run of them means
     * the layer genuinely is not going to load.
     */
    private fun recordFailure(layer: BaseMapLayer, reason: String) {
        consecutiveFailures++
        if (consecutiveFailures >= FAILURES_BEFORE_REPORTING) {
            _error.value = "${layer.label} imagery could not be loaded ($reason). " +
                "Survey positions are still shown — switch to Offline grid to hide this."
        }
    }

    private fun reportOffline() {
        consecutiveFailures++
        if (consecutiveFailures >= FAILURES_BEFORE_REPORTING) {
            _error.value = "No internet connection, so new map imagery cannot be " +
                "downloaded. Tiles already saved on this device are still shown, and " +
                "survey positions always are."
        }
    }

    private fun trimDiskCache() {
        val files = rootDir.walkTopDown().filter { it.isFile }.toList()
        var total = files.sumOf { it.length() }
        if (total <= MAX_DISK_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (total <= MAX_DISK_BYTES) return
            total -= file.length()
            file.delete()
        }
    }

    /** Removes every cached tile; survey data is untouched. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        memory.evictAll()
        rootDir.deleteRecursively()
        rootDir.mkdirs()
        clearError()
        _version.update { it + 1 }
    }

    fun cacheSizeBytes(): Long =
        runCatching { rootDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
            .getOrDefault(0L)

    private fun fileFor(layer: BaseMapLayer, zoom: Int, x: Int, y: Int) =
        File(rootDir, "${layer.id}/$zoom/$x/$y.tile")

    private fun key(layer: BaseMapLayer, zoom: Int, x: Int, y: Int) = "${layer.id}/$zoom/$x/$y"

    companion object {
        /**
         * A descriptive User-Agent naming the application and its operator, which tile
         * providers require in order to serve a non-browser client.
         */
        private const val USER_AGENT =
            "SeabuckthornFieldSurvey/1.0 (Android; Krishi Vigyan Kendra Leh, Ladakh; " +
                "offline agricultural field survey)"

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val MEMORY_TILES = 120
        private const val MAX_PARALLEL_DOWNLOADS = 4
        private const val MAX_QUEUED = 64
        private const val FAILURES_BEFORE_REPORTING = 4
        private const val MAX_DISK_BYTES = 120L * 1024 * 1024
    }
}
