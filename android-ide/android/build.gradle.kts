// android-ide/android/build.gradle.kts
//
// Root-level build file. Only applies plugins; all build logic is in :app.

plugins {
    // Android Gradle Plugin 8.3.2 — matches compileSdk 34 / Gradle 8.x.
    // Do not apply here (apply false); :app applies it.
    id("com.android.application") version "8.3.2" apply false
}
