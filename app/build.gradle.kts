plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.oro.app"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.oro.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing comes from the environment when present (CI secrets /
    // local publishing) and falls back to the checked-in debug-grade keystore
    // so that open-source contributors can still build a signed release.
    signingConfigs {
        create("release") {
            val envStore = System.getenv("CODEX_KEYSTORE_FILE")
            if (!envStore.isNullOrBlank() && file(envStore).exists()) {
                storeFile = file(envStore)
                storePassword = System.getenv("CODEX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CODEX_KEY_ALIAS")
                keyPassword = System.getenv("CODEX_KEY_PASSWORD")
            } else {
                storeFile = file("../keystore/codex-release.jks")
                storePassword = "codex2024"
                keyAlias = "codex"
                keyPassword = "codex2024"
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
            isPseudoLocalesEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            // Strip Timber/Log.d style chatter and keep the release build quiet.
            isDebuggable = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    lint {
        abortOnError = false
        checkDependencies = true
        warningsAsErrors = false
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }
}


composeCompiler {
    // Emits a report of which composables are skippable/restartable so that
    // recomposition regressions are visible in CI artifacts.
    includeSourceInformation.set(true)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core:common"))
    implementation(project(":core:data"))
    // api-exposed Material/Compose types come through the design system.
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3.window.sizeclass)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)

    implementation(libs.sora.editor)
    implementation(libs.sora.editor.language.textmate)

    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.tasklist)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
