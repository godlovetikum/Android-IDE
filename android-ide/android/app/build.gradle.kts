// android-ide/android/app/build.gradle.kts
//
// Android application module.
//
// Source layout (relative to this file at android/app/):
//   ../../java/dev/androidide/     — Java helper classes (SafBridge, EditorBridge)
//   ../../assets/editor/           — Monaco editor HTML + JS assets
//   src/main/jniLibs/<abi>/        — Compiled Rust .so (placed by cargo-ndk)
//   src/main/AndroidManifest.xml   — Application manifest
//
// The Rust library name is "android_ide_lib" (from [lib] name in Cargo.toml),
// which maps to libandroid_ide_lib.so. NativeActivity finds it via
// android.app.lib_name in AndroidManifest.xml.

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.androidide"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.androidide"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-phase1"

        // Only package the ABIs we build Rust for.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // ---------------------------------------------------------------------------
    // Source sets — reuse the shared java/ and assets/ from the android/ root,
    // so they don't need to be duplicated under app/src/main/.
    // ---------------------------------------------------------------------------
    sourceSets {
        named("main") {
            // Java helper classes live at android/java/, one level above app/.
            java.srcDirs("../../java")
            // Monaco editor assets live at android/assets/.
            assets.srcDirs("../../assets")
            // cargo-ndk places .so files at app/src/main/jniLibs/<abi>/.
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // Use debug signing for CI release builds until a production keystore
            // is configured. Task: add release keystore to GitHub Secrets.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Suppress the "debuggable release" lint warning — intentional for CI.
    lint {
        disable += "SigningRelease"
    }
}

dependencies {
    // No Java/Kotlin library dependencies — the app is driven entirely by the
    // Rust + Slint native layer. SafBridge and EditorBridge only use the
    // Android framework API (already available via compileSdk).
}
