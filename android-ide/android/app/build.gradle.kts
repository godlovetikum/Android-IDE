// android-ide/android/app/build.gradle.kts
//
// Android application module.
//
// Stack: Kotlin 1.9.22 + Jetpack Compose BOM 2024.02.00 + Material3
//
// Source layout (relative to android/app/):
//   ../java/dev/android/ide/   — Kotlin source files (all .kt)
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

// Termux bootstrap archives are generated just before the Android build. The
// repository intentionally does not carry all four architecture archives.
// Select an ABI with -PtermuxAbi=arm64-v8a (or TERMUX_ABI); arm64 is the local
// default for developer builds. The fetch script verifies the pinned checksum.
val termuxAbi = providers.gradleProperty("termuxAbi")
    .orElse(providers.environmentVariable("TERMUX_ABI"))
    .orElse("arm64-v8a")
val prepareTermuxBootstrap = tasks.register<Exec>("prepareTermuxBootstrap") {
    val script = rootProject.projectDir.resolve("../../scripts/fetch-termux-bootstrap.sh").normalize()
    commandLine("bash", script.absolutePath, termuxAbi.get())
}
tasks.named("preBuild").configure { dependsOn(prepareTermuxBootstrap) }

android {
    namespace = "dev.android.ide"
    compileSdk = 34
    ndkVersion = "22.1.7171670"

    defaultConfig {
        applicationId = "dev.android.ide"
        minSdk = 26
        // Termux's writable private runtime requires the Android 10 compatibility
        // behavior: target API 29+ denies execve() from the app home directory.
        // Keep this aligned with the bundled runtime until APK-resident execution
        // is implemented.
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0-alpha"
        // The runtime bootstrap is ABI-specific. Keep the APK aligned with
        // the selected runtime instead of producing a universal artifact.
        ndk {
            abiFilters += termuxAbi.get()
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/termux-pty/Android.mk")
        }
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
            // Use takeIf { isNotBlank() } on every env var so that an unset
            // GitHub Actions secret (which expands to "", not null) is treated
            // as absent and the build falls back to the debug keystore.
            // Without this guard, an empty KEY_PASSWORD causes Gradle to call
            // KeyStore.getKey(alias, charArrayOf()) which throws
            // KeytoolException("Failed to read key … : null") at packageRelease.
            val spwd  = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
            val alias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
            val kpwd  = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }

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
        // This project distributes direct APKs rather than through Google Play.
        // Target API 28 is intentional: the bundled writable Termux runtime
        // depends on Android's pre-29 app-home execution compatibility behavior.
        disable += "ExpiredTargetSdkVersion"
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
    implementation("androidx.compose.runtime:runtime-saveable")
    
    // Material Design 3 — dark theme, navigation drawer, top app bar, tabs
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // Branded language/file icons for the editor tree and search results.
    // The 1.1.1 artifact is Compose Multiplatform/Android compatible.
    implementation("br.com.devsrsouza.compose.icons:simple-icons:1.1.1")
    // GeckoView Stable is the approved embedded browser engine foundation.
    implementation("org.mozilla.geckoview:geckoview:157.0.20260924084938")
    // Browser pull-to-refresh container around GeckoView.
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

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

    // Pinned Termux terminal-view dependency surface. The source is vendored
    // under android/java/com/termux so it is compiled with this app namespace;
    // its JNI PTY library is built below for the selected ABI only.
    implementation("androidx.annotation:annotation:1.7.1")
    // ── Debug tooling ──────────────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
