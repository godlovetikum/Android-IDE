// android-ide/android/settings.gradle.kts
//
// Gradle settings for the Android IDE APK project.
// Root project wraps a single :app module that packages the Rust .so.

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

rootProject.name = "AndroidIDE"
include(":app")
