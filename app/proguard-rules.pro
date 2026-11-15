# Add project-specific ProGuard/R8 keep rules here.

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class com.photo.gallery.data.local.model.** { *; }
-keep interface com.photo.gallery.data.local.dao.** { *; }

# Coil 3 keep rules
-keep class coil3.** { *; }
-dontwarn coil3.**
-keep class * implements coil3.decode.Decoder$Factory { *; }
-keep class * implements coil3.fetch.Fetcher$Factory { *; }

# Media3 ExoPlayer keep rules
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.common.** { *; }
-dontwarn androidx.media3.**

# Biometric keep rules
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
