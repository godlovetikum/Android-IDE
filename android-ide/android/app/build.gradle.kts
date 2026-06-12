// android-ide/android/app/build.gradle.kts
//
// Android application module.
//
// Stack: Kotlin 1.9.22 + Jetpack Compose BOM 2024.02.00 + Material3
//
// Source layout (relative to android/app/):
//   ../java/dev/androidide/   — Kotlin source files (all .kt)
//   ../assets/editor/         — Monaco editor HTML + JS assets
//   src/main/AndroidManifest.xml — Application manifest
//   src/main/res/                — Launcher icon resources
//
// Migration note (2026-06-12):
//   Migrated from Slint/Rust + JNI to Kotlin/Jetpack Compose.
//   Removed: NDK ABI filters, jniLibs source set, no-op dependencies block.
//   Added: Kotlin plugin, Compose build feature, Material3 + ViewModel deps.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.androidide"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.androidide"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-alpha"
    }

    sourceSets {
        named("main") {
            // Kotlin source files live at android/java/ — one level above app/.
            // Path: app/ -> ../java = android/java/
            // The Kotlin compiler picks up .kt files in java.srcDirs() by convention.
            java.srcDirs("../java")
            // Monaco editor assets at android/assets/.
            // Path: app/ -> ../assets = android/assets/
            assets.srcDirs("../assets")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // Compose requires opt-in for some experimental APIs.
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }

    buildFeatures {
        // Enable Jetpack Compose code generation.
        compose = true
    }

    composeOptions {
        // Compose compiler extension version must match Kotlin version.
        // Kotlin 1.9.22 → Compose Compiler 1.5.8
        // Reference: https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // Debug signing for CI. Add a production keystore via GitHub Secrets
            // before shipping to the Play Store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // Suppress intentional debug-signed release warning during CI.
        disable += "SigningRelease"
    }
}

dependencies {
    // ── Jetpack Compose BOM ────────────────────────────────────────────────
    // The BOM pins all Compose library versions together.
    // https://developer.android.com/jetpack/compose/setup#bom-version-mapping
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Material Design 3 — dark theme, navigation drawer, top app bar, tabs
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ── Activity ───────────────────────────────────────────────────────────
    // ComponentActivity.setContent {} + rememberLauncherForActivityResult
    implementation("androidx.activity:activity-compose:1.8.2")

    // ── Lifecycle / ViewModel ──────────────────────────────────────────────
    // viewModel() Compose integration + StateFlow.collectAsState()
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // ── Kotlin coroutines ──────────────────────────────────────────────────
    // viewModelScope, Dispatchers.IO for SAF operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── Debug tooling ──────────────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
