package com.codex.stormy.data.repository.templates

/**
 * Android app template files with GitHub CI for APK builds
 */
object AndroidTemplates {

    val ROOT_BUILD_GRADLE = """
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}
    """.trimIndent()

    val SETTINGS_GRADLE = """
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyApp"
include(":app")
    """.trimIndent()

    val GRADLE_PROPERTIES = """
# Project-wide Gradle settings.
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
    """.trimIndent()

    val APP_BUILD_GRADLE = """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
    """.trimIndent()

    val MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApp"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MyApp">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
    """.trimIndent()

    val MAIN_ACTIVITY = """
package com.example.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        updateCounterDisplay()

        binding.buttonIncrement.setOnClickListener {
            counter++
            updateCounterDisplay()
        }

        binding.buttonDecrement.setOnClickListener {
            counter--
            updateCounterDisplay()
        }

        binding.buttonReset.setOnClickListener {
            counter = 0
            updateCounterDisplay()
        }
    }

    private fun updateCounterDisplay() {
        binding.textCounter.text = counter.toString()
    }
}
    """.trimIndent()

    val ACTIVITY_MAIN_XML = """
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    tools:context=".MainActivity">

    <TextView
        android:id="@+id/textTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="48dp"
        android:text="@string/app_name"
        android:textColor="@color/text_primary"
        android:textSize="32sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

    <TextView
        android:id="@+id/textSubtitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="Welcome to your Android app!"
        android:textColor="@color/text_secondary"
        android:textSize="16sp"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/textTitle" />

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/cardCounter"
        android:layout_width="200dp"
        android:layout_height="200dp"
        app:cardBackgroundColor="@color/surface"
        app:cardCornerRadius="16dp"
        app:cardElevation="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:id="@+id/textCounter"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:text="0"
            android:textColor="@color/primary"
            android:textSize="64sp"
            android:textStyle="bold" />

    </com.google.android.material.card.MaterialCardView>

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp"
        android:orientation="horizontal"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/cardCounter">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonDecrement"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:layout_marginEnd="16dp"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:text="-"
            android:textSize="24sp"
            app:cornerRadius="32dp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonReset"
            android:layout_width="wrap_content"
            android:layout_height="64dp"
            android:layout_marginEnd="16dp"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:text="Reset"
            android:textSize="16sp"
            app:cornerRadius="32dp"
            style="@style/Widget.Material3.Button.OutlinedButton" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonIncrement"
            android:layout_width="64dp"
            android:layout_height="64dp"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:text="+"
            android:textSize="24sp"
            app:cornerRadius="32dp" />

    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
    """.trimIndent()

    val STRINGS_XML = """
<resources>
    <string name="app_name">My App</string>
</resources>
    """.trimIndent()

    val THEMES_XML = """
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.MyApp" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_variant</item>
        <item name="colorOnPrimary">@color/white</item>
        <item name="colorSecondary">@color/secondary</item>
        <item name="colorSecondaryVariant">@color/secondary_variant</item>
        <item name="colorOnSecondary">@color/white</item>
        <item name="android:statusBarColor">@color/background</item>
    </style>
</resources>
    """.trimIndent()

    val COLORS_XML = """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#6200EE</color>
    <color name="primary_variant">#3700B3</color>
    <color name="secondary">#03DAC5</color>
    <color name="secondary_variant">#018786</color>
    <color name="background">#F5F5F5</color>
    <color name="surface">#FFFFFF</color>
    <color name="text_primary">#212121</color>
    <color name="text_secondary">#757575</color>
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
</resources>
    """.trimIndent()

    val GITHUB_WORKFLOW = """
name: Android Build

on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle

    - name: Grant execute permission for gradlew
      run: chmod +x gradlew

    - name: Build Debug APK
      run: ./gradlew assembleDebug

    - name: Build Release APK
      run: ./gradlew assembleRelease

    - name: Upload Debug APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug
        path: app/build/outputs/apk/debug/app-debug.apk

    - name: Upload Release APK
      uses: actions/upload-artifact@v4
      with:
        name: app-release-unsigned
        path: app/build/outputs/apk/release/app-release-unsigned.apk

  # Optional: Create release with APK on tag push
  release:
    needs: build
    runs-on: ubuntu-latest
    if: startsWith(github.ref, 'refs/tags/v')

    steps:
    - name: Download Debug APK
      uses: actions/download-artifact@v4
      with:
        name: app-debug

    - name: Download Release APK
      uses: actions/download-artifact@v4
      with:
        name: app-release-unsigned

    - name: Create Release
      uses: softprops/action-gh-release@v1
      with:
        files: |
          app-debug.apk
          app-release-unsigned.apk
      env:
        GITHUB_TOKEN: ${'$'}{{ secrets.GITHUB_TOKEN }}
    """.trimIndent()

    val README = """
# My Android App

A simple Android application built with Kotlin and Material Design 3.

## Features

- Counter app with increment, decrement, and reset functionality
- Modern Material 3 design
- Dark mode support
- Clean MVVM architecture ready

## Building the App

### Local Build

1. Open the project in Android Studio
2. Sync Gradle files
3. Run the app on an emulator or device

### GitHub CI Build

This project includes a GitHub Actions workflow that automatically builds the APK:

1. **Push to main/master**: Triggers automatic build
2. **Create a tag**: Creates a GitHub release with APK files

To create a release:
```bash
git tag v1.0.0
git push origin v1.0.0
```

### Download APK

- Go to the **Actions** tab in your GitHub repository
- Click on a successful workflow run
- Download the APK from **Artifacts**

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/app/
│   │   └── MainActivity.kt
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml
│   │   └── values/
│   │       ├── strings.xml
│   │       ├── colors.xml
│   │       └── themes.xml
│   └── AndroidManifest.xml
├── build.gradle.kts
└── ...
```

## Requirements

- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34

## License

This project is open source and available under the MIT License.
    """.trimIndent()
}
