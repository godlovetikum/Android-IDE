package dev.android.ide

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import dev.android.ide.app.AppShellViewModel
import dev.android.ide.ui.AppRoot

class MainActivity : ComponentActivity() {
    val appShellViewModel: AppShellViewModel by lazy {
        ViewModelProvider(this)[AppShellViewModel::class.java]
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
            AppRoot(viewModel = appShellViewModel, onExit = ::finish)
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
