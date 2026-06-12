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
//
// APK signing:
//   Debug:   auto-generated Android SDK debug keystore — always available.
//   Release: reads four GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD,
//            KEY_ALIAS, KEY_PASSWORD). When any secret is absent (local dev,
//            fork PRs), falls back to the debug keystore automatically.
//   See the signingConfigs block below for setup instructions.

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

    // ── APK Signing ─────────────────────────────────────────────────────────
    //
    // Release signing setup (one-time, per project):
    //
    //   1. Generate a release keystore:
    //        keytool -genkeypair -v \
    //          -keystore release.keystore \
    //          -alias android-ide-release \
    //          -keyalg RSA -keysize 4096 -validity 10000 \
    //          -storepass <storePassword> -keypass <keyPassword> \
    //          -dname "CN=Android IDE, O=YourOrg, C=US"
    //
    //   2. Base64-encode the keystore file (no line wrapping):
    //        base64 -w 0 release.keystore > release.keystore.b64
    //        # macOS: base64 -i release.keystore -o release.keystore.b64
    //
    //   3. Add four GitHub repository secrets (Settings → Secrets → Actions):
    //        KEYSTORE_BASE64     — contents of release.keystore.b64
    //        KEYSTORE_PASSWORD   — storePassword used in step 1
    //        KEY_ALIAS           — android-ide-release (or whatever alias you used)
    //        KEY_PASSWORD        — keyPassword used in step 1
    //
    // When the four secrets are present, assembleRelease produces a
    // production-signed APK. When they are absent (fork CI, local dev),
    // the release build automatically falls back to the debug keystore so
    // the pipeline does not fail.
    signingConfigs {
        getByName("debug") {
            // Standard Android SDK debug keystore — created automatically on
            // first build. Values are fixed by Android convention; do not change.
            storeFile     = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias      = "androiddebugkey"
            keyPassword   = "android"
        }

        create("release") {
            val spwd  = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val kpwd  = System.getenv("KEY_PASSWORD")
        
            val releaseKeystore = file("${rootDir}/release.keystore")
        
            if (
                releaseKeystore.exists() &&
                spwd != null &&
                alias != null &&
                kpwd != null
            ) {
                storeFile = releaseKeystore
                storePassword = spwd
                keyAlias = alias
                keyPassword = kpwd
            } else {
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
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
            signingConfig  = signingConfigs.getByName("debug")
            isDebuggable   = true
            isMinifyEnabled = false
        }
        release {
            signingConfig  = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    lint {
        // SigningRelease warns when a release build uses the debug keystore.
        // Suppressed here because the debug-keystore fallback in signingConfigs
        // is intentional (fires on fork PRs and local dev without secrets).
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
