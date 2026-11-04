package com.tripmate.ai.ai

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG builds: App Check debug provider. Register the debug token printed in Logcat in the
 * Firebase console (App Check → Manage debug tokens). Never ship this provider in release.
 */
object AppCheckInstaller {
    fun install() {
        Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
