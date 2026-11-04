package com.tripmate.ai

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.tripmate.ai.ai.AppCheckInstaller
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TripMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase is optional. Without google-services.json the app runs in DEMO mode.
        if (BuildConfig.HAS_FIREBASE_CONFIG) {
            runCatching {
                val app = FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this)
                if (app != null) AppCheckInstaller.install()
            }.onFailure { Log.w("TripMate", "Firebase init failed: ${it.javaClass.simpleName}") }
        }
    }
}
