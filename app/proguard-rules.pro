# ProGuard / R8 rules for ORB Voice Assistant

# Keep line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep all data classes used in serialization
-keep class com.aistudio.mj.wxyt.domain.ai.** { *; }
-keep class com.aistudio.mj.wxyt.domain.chat.** { *; }
-keep class com.aistudio.mj.wxyt.domain.security.** { *; }
-keep class com.aistudio.mj.wxyt.domain.settings.** { *; }

# Room database entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-dontwarn androidx.compose.**

# Security Crypto
-keep class androidx.security.crypto.** { *; }

# Coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
