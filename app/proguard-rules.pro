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

# JGit - Ignore missing JVM-specific classes that don't exist on Android
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep ViewModel classes and their constructors (prevents NoSuchMethodException crashes)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.codex.stormy.ui.screens.**.ViewModel* { *; }
-keep class com.codex.stormy.ui.screens.**.*ViewModel { *; }
-keep class com.codex.stormy.ui.screens.**.*ViewModel$* { *; }

# Keep ViewModelProvider.Factory implementations
-keep class * implements androidx.lifecycle.ViewModelProvider$Factory { *; }

# Keep Git-related classes that may be instantiated via Factory pattern
-keep class com.codex.stormy.ui.screens.git.** { *; }
-keep class com.codex.stormy.data.git.** { *; }
