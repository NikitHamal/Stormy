import java.util.Base64
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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

        // On-device dictation (Whistle). Only ARM: that is what the engine
        // ships for and what phones run; elsewhere dictation falls back to the
        // system recogniser.
        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
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
    // Models from core:data / the JDK are compiled without the Compose
    // compiler, so Compose cannot infer their stability and would treat them
    // as unstable -- every screen taking a Project, GitCommit or List<...>
    // would recompose on every emission. The shared config declares the ones
    // that are immutable in practice. (Strong skipping is the default from
    // Kotlin 2.0.20 on, so it needs no flag here.)
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose-stability.conf")
    )
    // includeSourceInformation is left at the plugin default: forcing it on
    // put per-call-site source metadata into release builds as well. Pass
    // -PcomposeReports=true to get skippability/stability reports instead.
    if (providers.gradleProperty("composeReports").orNull == "true") {
        reportsDestination.set(layout.buildDirectory.dir("compose_compiler"))
        metricsDestination.set(layout.buildDirectory.dir("compose_compiler"))
    }
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

    implementation(libs.a2ui.compose.runtime)
    implementation(libs.a2ui.compose.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)

    // Only the core editor: highlighting is done by SimpleLanguageFactory, so
    // the TextMate language module (and its grammar/regex engine) was dead
    // weight in the APK and in class loading.
    implementation(libs.sora.editor)

    implementation(libs.markwon.core)
    implementation(libs.markwon.html)
    implementation(libs.markwon.linkify)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.tasklist)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// UI screenshots: Robolectric + Roborazzi render the device agent's island and
// screens on the JVM (app/src/test/java/com/oro/app/screenshots), so their look
// can be checked without a phone. They are slow, so the normal unit-test run
// skips them. They run instead of the other app tests with
// -Poro.screenshots=true, and in CI on the ci/shots branch or any branch named
// shots/<anything>: the GitHub connector agents use cannot add a workflow, so
// the existing Android CI renders them there and printScreenshots writes every
// PNG as base64 text into its log (lines prefixed ORO|), the one form that tool
// can read back. PNGs land in app/build/outputs/roborazzi.
// (Line comments on purpose: Kotlin block comments nest, and a glob like
// shots/<two stars> inside one silently comments out the rest of the file.)
val ciBranch = (providers.environmentVariable("GITHUB_REF_NAME").orNull
    ?: providers.environmentVariable("GITHUB_REF").orNull?.removePrefix("refs/heads/")).orEmpty()
val renderScreenshots = providers.gradleProperty("oro.screenshots").orNull == "true" ||
    ciBranch == "ci/shots" || ciBranch.startsWith("shots/")
if (renderScreenshots) logger.lifecycle("Oro: rendering UI screenshots")

val printScreenshots = tasks.register("printScreenshots") {
    val shots = layout.projectDirectory.dir("build/outputs/roborazzi")
    doLast {
        // Every line carries the "ORO|" prefix, so output from tasks running
        // in parallel cannot end up inside an image.
        val files = shots.asFile.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }
        println("ORO|SHOTS ${files.size}")
        files.forEach { f ->
            println("ORO|FILE ${f.name} ${f.length()}")
            Base64.getEncoder().encodeToString(f.readBytes()).chunked(4000).forEach { println("ORO|$it") }
        }
        println("ORO|END")
    }
}

tasks.withType<Test>().configureEach {
    if (renderScreenshots) {
        filter { includeTestsMatching("com.oro.app.screenshots.*") }
        systemProperty("roborazzi.test.record", "true")
        systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        maxHeapSize = "3g"
        // The PNGs are not declared outputs: always render, never from cache.
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
        finalizedBy(printScreenshots)
    } else {
        exclude("com/oro/app/screenshots/**")
    }
}
