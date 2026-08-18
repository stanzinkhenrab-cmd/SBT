package com.kvkleh.sbtsurvey.map

/**
 * Selectable background imagery for the survey map.
 *
 * Only providers whose terms permit use by a small offline field application are
 * listed, and each carries the attribution its licence requires. Survey markers are
 * always drawn from the local database, so every layer — including [GRID] — works with
 * no connectivity at all.
 */
enum class BaseMapLayer(
    val id: String,
    val label: String,
    val description: String,
    val attribution: String,
    val maxZoom: Int,
    /** True when the imagery is dark, so overlays switch to light colours. */
    val darkImagery: Boolean
) {
    SATELLITE(
        id = "satellite",
        label = "Satellite",
        description = "High-resolution aerial and satellite imagery",
        attribution = "Imagery © Esri, Maxar, Earthstar Geographics",
        maxZoom = 19,
        darkImagery = true
    ),

    TERRAIN(
        id = "terrain",
        label = "Terrain",
        description = "Relief, contours and place names",
        attribution = "© Esri, USGS, NOAA",
        maxZoom = 19,
        darkImagery = false
    ),

    STREET(
        id = "street",
        label = "Street",
        description = "Roads, settlements and tracks",
        attribution = "© OpenStreetMap contributors",
        maxZoom = 19,
        darkImagery = false
    ),

    GRID(
        id = "grid",
        label = "Offline grid",
        description = "Coordinate grid only — no imagery, no data used",
        attribution = "Survey coordinates from this device",
        maxZoom = 19,
        darkImagery = false
    );

    val usesNetwork: Boolean get() = this != GRID

    /**
     * Tile URL for this layer, or null when the layer draws no imagery.
     *
     * Esri's tile services address tiles as `/{level}/{row}/{col}`, which is
     * y before x — the opposite of the XYZ convention OpenStreetMap uses.
     */
    fun tileUrl(zoom: Int, x: Int, y: Int): String? = when (this) {
        SATELLITE ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/" +
                "World_Imagery/MapServer/tile/$zoom/$y/$x"

        TERRAIN ->
            "https://server.arcgisonline.com/ArcGIS/rest/services/" +
                "World_Topo_Map/MapServer/tile/$zoom/$y/$x"

        STREET -> "https://tile.openstreetmap.org/$zoom/$x/$y.png"

        GRID -> null
    }

    companion object {
        val selectable: List<BaseMapLayer> = listOf(SATELLITE, TERRAIN, STREET, GRID)

        fun fromId(id: String?): BaseMapLayer =
            entries.firstOrNull { it.id == id } ?: SATELLITE
    }
}
