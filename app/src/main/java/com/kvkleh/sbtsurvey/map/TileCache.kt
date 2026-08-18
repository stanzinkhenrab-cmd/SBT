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
 * Disk-and-memory cache of raster map tiles.
 *
 * The survey map is offline-first: survey markers, coordinates and the scale grid are
 * always drawn from the local database. Background imagery is a bonus — tiles already on
 * disk are used immediately, and new tiles are fetched only when a network happens to be
 * available. With no connectivity the map simply falls back to a plain graticule
 * background; nothing fails and nothing blocks.
 */
class TileCache(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val memory = object : LruCache<String, Bitmap>(MEMORY_TILES) {
        override fun sizeOf(key: String, value: Bitmap) = 1
    }

    private val diskDir: File
        get() = File(context.filesDir, "map_tiles").apply { if (!exists()) mkdirs() }

    private val inFlight = mutableSetOf<String>()
    private val downloadLimit = Semaphore(MAX_PARALLEL_DOWNLOADS)

    /** Bumped whenever a new tile becomes available, so the map can recompose. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    val isOnline: Boolean
        get() = runCatching {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return@runCatching false
            val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)

    /** Cached tiles only — never blocks and never touches the network. */
    fun peek(zoom: Int, x: Int, y: Int): Bitmap? {
        val key = key(zoom, x, y)
        memory.get(key)?.let { return it }

        val file = fileFor(zoom, x, y)
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
    fun prefetch(zoom: Int, x: Int, y: Int) {
        if (!MapMath.isValidTileY(y, zoom)) return
        val key = key(zoom, x, y)
        synchronized(inFlight) {
            if (key in inFlight) return
            if (inFlight.size > MAX_QUEUED) return
            inFlight += key
        }
        if (!isOnline) {
            synchronized(inFlight) { inFlight -= key }
            return
        }
        scope.launch {
            downloadLimit.withPermit { download(zoom, x, y, key) }
        }
    }

    private suspend fun download(zoom: Int, x: Int, y: Int, key: String) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val target = fileFor(zoom, x, y)
            target.parentFile?.mkdirs()
            connection = (URL(tileUrl(zoom, x, y)).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val temp = File(target.parentFile, target.name + ".part")
                connection.inputStream.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                val stored = temp.length() > 0 && temp.renameTo(target)
                if (!stored) {
                    temp.delete()
                } else {
                    trimDiskCache()
                    _version.update { it + 1 }
                }
            }
        } catch (e: Exception) {
            // Offline, DNS failure or an unreachable tile server: the map keeps working
            // without imagery, so there is nothing to report to the surveyor.
        } finally {
            runCatching { connection?.disconnect() }
            synchronized(inFlight) { inFlight -= key }
        }
    }

    private fun trimDiskCache() {
        val files = diskDir.walkTopDown().filter { it.isFile }.toList()
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
        diskDir.deleteRecursively()
        diskDir.mkdirs()
        _version.update { it + 1 }
    }

    fun cacheSizeBytes(): Long =
        runCatching { diskDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)

    private fun fileFor(zoom: Int, x: Int, y: Int) = File(diskDir, "$zoom/$x/$y.png")

    private fun key(zoom: Int, x: Int, y: Int) = "$zoom/$x/$y"

    private fun tileUrl(zoom: Int, x: Int, y: Int) = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

    companion object {
        const val ATTRIBUTION = "Map data © OpenStreetMap contributors"
        private const val USER_AGENT =
            "SeabuckthornFieldSurvey/1.0 (Krishi Vigyan Kendra Leh; offline field survey app)"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val MEMORY_TILES = 96
        private const val MAX_PARALLEL_DOWNLOADS = 4
        private const val MAX_QUEUED = 64
        private const val MAX_DISK_BYTES = 60L * 1024 * 1024
    }
}
