# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.codex.stormy.**$$serializer { *; }
-keepclassmembers class com.codex.stormy.** {
    *** Companion;
}
-keepclasseswithmembers class com.codex.stormy.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.codex.stormy.data.local.entity.** { *; }

# Keep data models
-keep class com.codex.stormy.data.model.** { *; }
-keep class com.codex.stormy.domain.model.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep crash handler
-keep class com.codex.stormy.crash.** { *; }
