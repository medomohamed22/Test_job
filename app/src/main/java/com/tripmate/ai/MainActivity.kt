package com.tripmate.ai

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripmate.ai.ui.AppViewModel
import com.tripmate.ai.ui.TripMateRoot
import com.tripmate.ai.ui.theme.TripMateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Startup mode detection: keep the splash until preferences & sample data are ready.
        splash.setKeepOnScreenCondition { appViewModel.state.value.loading }
        enableEdgeToEdge()
        setContent {
            val state by appViewModel.state.collectAsStateWithLifecycle()
            TripMateTheme(themeMode = state.prefs.theme, reducedMotion = state.prefs.reducedMotion) {
                if (!state.loading) TripMateRoot(startWithOnboarding = !state.prefs.onboardingDone)
            }
        }
    }
}
