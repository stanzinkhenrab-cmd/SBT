# Survey records are irreplaceable field data; keep the classes the database and
# the map library reach reflectively even if shrinking is switched on later.
-keep class com.kvkleh.sbtsurvey.data.db.** { *; }
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Room generates these at build time.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**
