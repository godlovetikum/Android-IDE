// android-ide/android/build.gradle.kts
//
// Root-level build file. Only declares plugins; all build logic is in :app.
//
// Migration note (2026-06-12):
//   Added Kotlin Android plugin (1.9.22) to support Kotlin + Jetpack Compose
//   in the :app module. Slint/Rust dependencies removed entirely.

plugins {
    // Android Gradle Plugin 8.3.2 — compatible with Gradle 8.7 and Compose 1.6.x.
    id("com.android.application") version "8.3.2" apply false
    // Kotlin Android plugin — required for .kt source files and Compose compiler.
    // Version 1.9.22 → Compose compiler extension version 1.5.8.
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
