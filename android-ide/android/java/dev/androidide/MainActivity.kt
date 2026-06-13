// android-ide/android/java/dev/androidide/MainActivity.kt
//
// Single-Activity host. Enables edge-to-edge, mounts AppRoot, and
// intercepts hardware volume key events on the EDITOR screen so the
// volume buttons move the cursor instead of adjusting system volume.

package dev.androidide

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.androidide.ui.AppRoot
import dev.androidide.viewmodel.IdeViewModel
import dev.androidide.viewmodel.model.AppScreen

class MainActivity : ComponentActivity() {

    val ideViewModel: IdeViewModel by lazy {
        ViewModelProvider(this)[IdeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(ideViewModel = ideViewModel)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (ideViewModel.uiState.value.currentScreen == AppScreen.EDITOR) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    ideViewModel.onVolumeUp()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    ideViewModel.onVolumeDown()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        ideViewModel.saveSession()
    }
}
