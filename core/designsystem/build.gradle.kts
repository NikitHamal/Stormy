plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.oro.app.designsystem"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

composeCompiler {
    // Same stability declarations as :app; see compose-stability.conf.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf")
    )
}

dependencies {
    api(project(":core:common"))
    implementation(libs.androidx.core.ktx)
    // Brand animation clocks pause with the screen's lifecycle.
    implementation(libs.androidx.lifecycle.runtime.compose)

    // api, not implementation: consumers theme themselves with these types
    // (ColorScheme, Typography, Shape), so they must be on the compile
    // classpath of anything that depends on the design system.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.foundation)
    api(libs.androidx.material3)
    api(libs.androidx.graphics.shapes)
    // api: design-system components expose ImageVector in their signatures and
    // use the icon set themselves, so consumers need it on the compile path.
    api(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling.preview)

    debugImplementation(libs.androidx.ui.tooling)
}
