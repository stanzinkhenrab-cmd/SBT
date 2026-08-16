package com.kvk.leh.seabuckthorn.domain.model

/**
 * All controlled vocabularies used across the survey form. Each enum stores its [label] for
 * display while [name] (the Kotlin enum constant name) is what gets persisted in Room, so
 * renaming a [label] never breaks stored data.
 */
enum class LandUseType(val label: String) {
    NATURAL_STAND("Natural stand"),
    ORCHARD_BOUNDARY("Orchard boundary"),
    AGRICULTURAL_LAND("Agricultural land"),
    RIVERBANK("Riverbank"),
    WASTELAND("Wasteland"),
    OTHER("Other")
}

enum class OwnershipStatus(val label: String) {
    PRIVATE("Private"),
    COMMUNITY("Community"),
    GOVERNMENT("Government"),
    UNKNOWN("Unknown")
}

enum class GpsStatus(val label: String) {
    CAPTURED("Captured"),
    UNAVAILABLE("Unavailable"),
    MANUAL("Manually entered")
}

enum class ShrubType(val label: String) {
    SOFT_WOOD("Soft wood"),
    HARD_WOOD("Hard wood"),
    MIXED("Mixed"),
    UNKNOWN("Unknown")
}

enum class GrowthForm(val label: String) {
    ERECT("Erect"),
    SEMI_ERECT("Semi-erect"),
    SPREADING("Spreading"),
    PROSTRATE("Prostrate"),
    IRREGULAR("Irregular")
}

enum class RegenerationStatus(val label: String) {
    POOR("Poor"),
    MODERATE("Moderate"),
    GOOD("Good")
}

/** Generic four-point qualitative scale reused for density/incidence style fields. */
enum class Level4(val label: String) {
    NONE("None"),
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High")
}

enum class HealthStatus(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    MODERATE("Moderate"),
    POOR("Poor")
}

enum class EaseLevel(val label: String) {
    DIFFICULT("Difficult"),
    MODERATE("Moderate"),
    EASY("Easy")
}

enum class ColorIntensity(val label: String) {
    LIGHT("Light"),
    MEDIUM("Medium"),
    DARK("Dark")
}

enum class WaterRegime(val label: String) {
    IRRIGATED("Irrigated"),
    RAINFED("Rainfed"),
    NATURAL("Natural (unmanaged)")
}

enum class SoilMoistureClass(val label: String) {
    DRY("Dry"),
    SLIGHTLY_MOIST("Slightly moist"),
    MOIST("Moist"),
    WET("Wet / waterlogged")
}

enum class Aspect(val label: String) {
    NORTH("North"), NORTH_EAST("North-east"), EAST("East"), SOUTH_EAST("South-east"),
    SOUTH("South"), SOUTH_WEST("South-west"), WEST("West"), NORTH_WEST("North-west"),
    FLAT("Flat / no aspect")
}

enum class MaturityStage(val label: String) {
    UNRIPE("Unripe"),
    INTERMEDIATE("Intermediate"),
    RIPE("Ripe"),
    OVERRIPE("Overripe")
}

enum class FruitColor(val label: String) {
    GREEN("Green"),
    GREEN_YELLOW("Green-yellow"),
    YELLOW("Yellow"),
    ORANGE("Orange"),
    ORANGE_RED("Orange-red"),
    RED("Red"),
    DARK_ORANGE_RED("Dark orange/red"),
    OTHER("Other")
}

enum class FruitFirmness(val label: String) {
    VERY_SOFT("Very soft"),
    SOFT("Soft"),
    MODERATE("Moderate"),
    FIRM("Firm"),
    VERY_FIRM("Very firm")
}

enum class FruitShape(val label: String) {
    ROUND("Round"),
    OVAL("Oval"),
    ELLIPSOID("Ellipsoid"),
    OBLONG("Oblong"),
    OTHER("Other")
}

enum class TerrainType(val label: String) {
    FLAT_PLAIN("Flat plain"),
    GENTLE_SLOPE("Gentle slope"),
    STEEP_SLOPE("Steep slope"),
    RIVER_TERRACE("River terrace"),
    ALLUVIAL_FAN("Alluvial fan"),
    ROCKY("Rocky"),
    OTHER("Other")
}

enum class PhotoCategory(val label: String) {
    WHOLE_SHRUB("Whole shrub"),
    STEM_WOOD("Stem/wood"),
    LEAVES("Leaves"),
    UNRIPE_FRUIT("Unripe fruit"),
    INTERMEDIATE_FRUIT("Intermediate fruit"),
    RIPE_FRUIT("Ripe fruit"),
    FRUIT_CLUSTER("Fruit cluster"),
    BERRY_CLOSEUP("Berry close-up"),
    SITE_LOCATION("Site/location"),
    OTHER("Other")
}
