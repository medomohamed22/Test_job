package com.tripmate.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

/** Draws the activity window into a PNG under app/build/screenshots (Robolectric native graphics). */
object Screenshots {
    fun save(scenario: ActivityScenario<MainActivity>?, name: String) {
        runCatching {
            ShadowLooper.idleMainLooper()
            scenario?.onActivity { a ->
                val v = a.window.decorView
                val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
                v.draw(Canvas(bmp))
                val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
                FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }.onFailure { println("screenshot $name failed: $it") }
    }
}
