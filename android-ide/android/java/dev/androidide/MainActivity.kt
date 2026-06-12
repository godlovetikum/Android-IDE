// android-ide/android/java/dev/androidide/MainActivity.kt
//
// Application entry point — Kotlin/Jetpack Compose.
//
// AppRoot handles the full navigation shell and applies AndroidIDETheme
// (including dark/light mode) so no theme wrapper is needed here.

package dev.androidide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.androidide.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}
