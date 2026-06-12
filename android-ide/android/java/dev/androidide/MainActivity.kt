// android-ide/android/java/dev/androidide/MainActivity.kt
//
// Application entry point — Kotlin/Jetpack Compose.
//
// Migration note (2026-06-12):
//   Replaces IDEActivity.java (Slint NativeActivity subclass).
//   No JNI, no android_main(), no WebView overlay positioning.
//   The Compose runtime renders the full IDE UI natively;
//   the Monaco WebView is embedded as a Compose AndroidView in EditorPane.kt.

package dev.androidide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.androidide.ui.IdeScreen
import dev.androidide.ui.theme.AndroidIDETheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind system bars (status bar + navigation bar).
        enableEdgeToEdge()
        setContent {
            AndroidIDETheme {
                IdeScreen()
            }
        }
    }
}
