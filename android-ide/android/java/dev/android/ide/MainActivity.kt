package dev.android.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import dev.android.ide.app.AppShell
import dev.android.ide.app.AppShellViewModel

class MainActivity : ComponentActivity() {
    val appShellViewModel: AppShellViewModel by lazy {
        ViewModelProvider(this)[AppShellViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!appShellViewModel.back()) finish()
            }
        })
        setContent {
            AppShell(viewModel = appShellViewModel, onExit = ::finish)
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
