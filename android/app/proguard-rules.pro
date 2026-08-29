# ZXing only needs the core encoder; keep it intact.
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
