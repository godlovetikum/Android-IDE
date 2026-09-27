package dev.android.ide

import android.graphics.Color
import android.view.KeyEvent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.ui.AppRoot
import dev.android.ide.viewmodel.IdeViewModel

class MainActivity : ComponentActivity() {
    val appShellViewModel: AppShellViewModel by lazy {
        ViewModelProvider(this)[AppShellViewModel::class.java]
    }
    val ideViewModel: IdeViewModel by lazy {
        ViewModelProvider(this)[IdeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(32, 33, 36)
        window.navigationBarColor = Color.BLACK
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!appShellViewModel.back()) {
                    appShellViewModel.requestExitConfirmation()
                }
            }
        })
        setContent {
            AppRoot(viewModel = appShellViewModel, ideViewModel = ideViewModel, onExit = ::finish)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> ideViewModel.handleVolumeKey(up = true) || super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_VOLUME_DOWN -> ideViewModel.handleVolumeKey(up = false) || super.onKeyDown(keyCode, event)
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStart() {
        super.onStart()
        appShellViewModel.foreground()
    }

    override fun onStop() {
        appShellViewModel.background()
        super.onStop()
    }
}
