plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

/**
 * Opt-ins applied to every module.
 *
 * These were previously declared only in :app. Once the code was split into
 * core modules each one needed the same list, and duplicating it per module is
 * exactly how the modules drift apart -- the Material 3 Expressive opt-in in
 * particular has to be present wherever Expressive APIs are touched, which is
 * now more than one place.
 */
val sharedOptIns = listOf(
    "androidx.compose.material3.ExperimentalMaterial3Api",
    "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
    "androidx.compose.foundation.ExperimentalFoundationApi",
    "androidx.compose.animation.ExperimentalAnimationApi",
    "androidx.compose.ui.ExperimentalComposeUiApi",
    "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "kotlinx.serialization.ExperimentalSerializationApi",
)

subprojects {
    // AGP 9's built-in Kotlin registers the `kotlin` extension on Android
    // modules, so this applies once the Android plugin has landed.
    plugins.withId("com.android.base") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension>("kotlin") {
            (this as org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension)
                .compilerOptions {
                    freeCompilerArgs.addAll(sharedOptIns.map { "-opt-in=$it" })
                }
        }
    }
}
