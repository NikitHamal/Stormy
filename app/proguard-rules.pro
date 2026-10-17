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

# Git data layer: GitHub API models are (de)serialized and JGit callbacks are
# wired by name. The git *UI* package is deliberately not kept wholesale any
# more: it is ~3k lines of composables, and a blanket -keep stopped R8 from
# inlining/optimizing them. Its ViewModels are covered by the rules above and
# its factories are plain code, not reflection.
-keep class com.oro.app.data.git.** { *; }

# WebView bridges (PreviewActivity's inspector interface). Methods annotated
# @JavascriptInterface are only ever called from JavaScript, so R8 sees them as
# unused and would strip or rename them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Release builds: drop verbose/debug logging calls entirely (R8 removes the
# call and the string building feeding it). Warnings and errors are kept.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# MediaPipe LLM Inference (on-device models) uses JNI and protobuf reflection.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**
# Whistle JNI bridge
-keep class com.oro.app.voice.WhistleNative { *; }
