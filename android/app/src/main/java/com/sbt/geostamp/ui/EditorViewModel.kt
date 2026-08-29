package com.sbt.geostamp.ui

import android.app.Application
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbt.geostamp.io.ImageStore
import com.sbt.geostamp.io.PhotoLoader
import com.sbt.geostamp.location.AddressResolver
import com.sbt.geostamp.location.LocationProvider
import com.sbt.geostamp.map.StaticMap
import com.sbt.geostamp.model.StampContent
import com.sbt.geostamp.model.StampOptions
import com.sbt.geostamp.qr.QrCodes
import com.sbt.geostamp.render.StampRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val hasPhoto: Boolean = false,
    val preview: Bitmap? = null,
    val content: StampContent = StampContent(),
    val options: StampOptions = StampOptions(),
    val isBusy: Boolean = false,
    val isLocating: Boolean = false,
    val isSaving: Boolean = false,
    val locationDenied: Boolean = false,
    val message: String? = null,
    val savedUri: Uri? = null,
    val shareUri: Uri? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val locationProvider = LocationProvider(application)
    private val addressResolver = AddressResolver(application)
    private val staticMap = StaticMap(application.cacheDir)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /** Full-resolution source, kept out of the state so Compose never diffs a huge bitmap. */
    private var sourcePhoto: Bitmap? = null

    /** Downscaled copy used for the on-screen preview so edits feel instant. */
    private var previewSource: Bitmap? = null

    private var mapCacheKey: String? = null
    private var mapCache: Bitmap? = null
    private var renderJob: Job? = null

    fun openPhoto(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, message = null, savedUri = null) }
            val bitmap = PhotoLoader.load(getApplication(), uri)
            if (bitmap == null) {
                _state.update { it.copy(isBusy = false, message = "That image could not be opened.") }
                return@launch
            }
            recycleSources()
            sourcePhoto = bitmap
            previewSource = downscale(bitmap, PREVIEW_MAX_EDGE)
            mapCacheKey = null

            val exif = PhotoLoader.readExif(getApplication(), uri)
            _state.update {
                it.copy(
                    hasPhoto = true,
                    isBusy = false,
                    content = it.content.copy(
                        latitude = exif.latitude,
                        longitude = exif.longitude,
                        altitudeMeters = exif.altitudeMeters,
                        timeMillis = exif.timeMillis ?: System.currentTimeMillis()
                    )
                )
            }

            if (exif.hasLocation) {
                describe(exif.latitude!!, exif.longitude!!)
            } else {
                refreshLocation()
            }
            requestRender(immediate = true)
        }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            if (!locationProvider.hasPermission()) {
                _state.update {
                    it.copy(locationDenied = true, message = "Location permission is needed to stamp coordinates.")
                }
                return@launch
            }
            _state.update { it.copy(isLocating = true, locationDenied = false, message = null) }
            val fix: Location? = locationProvider.current()
            if (fix == null) {
                val hint = if (locationProvider.isLocationEnabled()) {
                    "No fix yet — step outside or wait a moment, then tap refresh."
                } else {
                    "Location services are switched off on this device."
                }
                _state.update { it.copy(isLocating = false, message = hint) }
                return@launch
            }

            mapCacheKey = null
            _state.update {
                it.copy(
                    isLocating = false,
                    content = it.content.copy(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        altitudeMeters = fix.altitude.takeIf { alt -> fix.hasAltitude() && alt != 0.0 },
                        accuracyMeters = fix.accuracy.takeIf { _ -> fix.hasAccuracy() },
                        timeMillis = System.currentTimeMillis()
                    )
                )
            }
            describe(fix.latitude, fix.longitude)
            requestRender(immediate = true)
        }
    }

    private suspend fun describe(latitude: Double, longitude: Double) {
        val place = addressResolver.resolve(latitude, longitude)
        _state.update { current ->
            val title = place?.title?.takeIf { it.isNotBlank() }
                ?: current.content.formattedCoordinates(current.options.coordinatesAsDms)
            current.copy(
                content = current.content.copy(
                    title = if (current.content.title.isBlank()) title else current.content.title,
                    addressLine = place?.addressLine ?: current.content.addressLine
                )
            )
        }
    }

    fun updateContent(transform: (StampContent) -> StampContent) {
        _state.update { it.copy(content = transform(it.content)) }
        requestRender()
    }

    fun updateOptions(transform: (StampOptions) -> StampOptions) {
        val before = _state.value.options
        val after = transform(before)
        if (after.mapZoom != before.mapZoom) mapCacheKey = null
        _state.update { it.copy(options = after) }
        requestRender()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearSaved() = _state.update { it.copy(savedUri = null) }

    fun consumeShareUri() = _state.update { it.copy(shareUri = null) }

    fun discardPhoto() {
        renderJob?.cancel()
        recycleSources()
        _state.update {
            EditorState(options = it.options, content = StampContent(note = it.content.note))
        }
    }

    fun save() = finish(share = false)

    fun share() = finish(share = true)

    private fun finish(share: Boolean) {
        val photo = sourcePhoto ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            val stamped = runCatching { compose(photo) }.getOrNull()
            if (stamped == null) {
                _state.update { it.copy(isSaving = false, message = "Rendering failed — try a smaller image.") }
                return@launch
            }
            val result = if (share) {
                ImageStore.shareableUri(getApplication(), stamped)
            } else {
                ImageStore.saveToGallery(getApplication(), stamped)
            }
            stamped.recycle()
            _state.update { current ->
                result.fold(
                    onSuccess = { uri ->
                        if (share) {
                            current.copy(isSaving = false, shareUri = uri)
                        } else {
                            current.copy(
                                isSaving = false,
                                savedUri = uri,
                                message = "Saved to Pictures/GeoStamp"
                            )
                        }
                    },
                    onFailure = { error ->
                        current.copy(
                            isSaving = false,
                            message = error.message ?: "Could not write the image."
                        )
                    }
                )
            }
        }
    }

    private fun requestRender(immediate: Boolean = false) {
        val source = previewSource ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (!immediate) delay(RENDER_DEBOUNCE_MS)
            val rendered = runCatching { compose(source) }.getOrNull() ?: return@launch
            _state.update { it.copy(preview = rendered) }
        }
    }

    /** Builds the map thumbnail and QR for [source], then burns the stamp in. */
    private suspend fun compose(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val snapshot = _state.value
        val content = snapshot.content
        val options = snapshot.options

        val thumbnailSize = StampRenderer.suggestedThumbnailSize(source, options)
        val map = if (options.showMap && content.hasLocation) {
            mapThumbnail(content.latitude!!, content.longitude!!, thumbnailSize, options.mapZoom)
        } else {
            null
        }
        val qr = if (options.showQr) {
            content.mapsUrl()?.let { QrCodes.encode(it, thumbnailSize.coerceAtLeast(128)) }
        } else {
            null
        }

        StampRenderer.render(source, content, options, map, qr)
    }

    private suspend fun mapThumbnail(
        latitude: Double,
        longitude: Double,
        sizePx: Int,
        zoom: Int
    ): Bitmap {
        // Round the key so tiny GPS jitter does not refetch tiles on every re-render.
        val key = "%.4f/%.4f/%d/%d".format(latitude, longitude, sizePx, zoom)
        mapCache?.let { cached -> if (key == mapCacheKey && !cached.isRecycled) return cached }
        val fresh = staticMap.thumbnail(latitude, longitude, sizePx, sizePx, zoom)
        mapCache = fresh
        mapCacheKey = key
        return fresh
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun recycleSources() {
        previewSource?.takeIf { it != sourcePhoto }?.recycle()
        sourcePhoto?.recycle()
        previewSource = null
        sourcePhoto = null
    }

    override fun onCleared() {
        super.onCleared()
        recycleSources()
    }

    private companion object {
        const val PREVIEW_MAX_EDGE = 1280
        const val RENDER_DEBOUNCE_MS = 140L
    }
}
