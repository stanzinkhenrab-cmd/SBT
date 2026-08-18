# Keep Room generated implementations
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Keep entity fields (used reflectively by Room type converters / column mapping)
-keepclassmembers class com.kvkleh.sbtsurvey.data.local.** { *; }

# Play services location
-dontwarn com.google.android.gms.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
