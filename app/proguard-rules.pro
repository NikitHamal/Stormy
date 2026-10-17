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

-keep,includedescriptorclasses class com.oro.app.**$$serializer { *; }
-keepclassmembers class com.oro.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.oro.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.oro.app.data.local.entity.** { *; }

# Keep data models
-keep class com.oro.app.data.model.** { *; }
-keep class com.oro.app.domain.model.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep crash handler
-keep class com.oro.app.crash.** { *; }

# JGit - Ignore missing JVM-specific classes that don't exist on Android
-dontwarn java.lang.ProcessHandle
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# JGit - Keep all JGit classes to prevent obfuscation issues with reflection
-keep class org.eclipse.jgit.** { *; }
-keepclassmembers class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# JGit internal classes that use reflection
-keep class org.eclipse.jgit.lib.internal.** { *; }
-keep class org.eclipse.jgit.transport.** { *; }
-keep class org.eclipse.jgit.storage.** { *; }
-keep class org.eclipse.jgit.util.** { *; }
-keep class org.eclipse.jgit.api.** { *; }

# Keep ViewModel classes and their constructors (prevents NoSuchMethodException crashes)
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class com.oro.app.ui.screens.**.ViewModel* { *; }
-keep class com.oro.app.ui.screens.**.*ViewModel { *; }
-keep class com.oro.app.ui.screens.**.*ViewModel$* { *; }

# Keep ViewModelProvider.Factory implementations
-keep class * implements androidx.lifecycle.ViewModelProvider$Factory { *; }

# Keep Git-related classes that may be instantiated via Factory pattern
-keep class com.oro.app.ui.screens.git.** { *; }
-keep class com.oro.app.data.git.** { *; }
