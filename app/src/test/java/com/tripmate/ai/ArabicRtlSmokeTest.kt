package com.tripmate.ai

import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Arabic / RTL smoke test: same app, Arabic locale. Verifies Arabic resources and RTL layout.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ArabicRtlSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun s(id: Int) = ctx.getString(id)

    @Before fun reducedMotion() {
        // Disable decorative/infinite animations so the UI can become idle.
        Settings.Global.putFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    private fun waitText(text: String, timeout: Long = 30_000) {
        try {
            compose.waitUntil(timeout) {
                // Let Room/DataStore results posted to the main looper be delivered (Robolectric paused looper).
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            // Print the UI tree to make CI failures diagnosable.
            println(compose.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().joinToString("\n") { it.config.toString() })
            runCatching { println(compose.onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true)[0].printToString(maxDepth = 40)) }
            throw AssertionError("Text not found: '$text'", e)
        }
    }

    private var scenario: ActivityScenario<MainActivity>? = null
    private fun shot(name: String) = Screenshots.save(scenario, name)

    @Test @Config(qualifiers = "ar-w411dp-h891dp-xxhdpi")
    fun arabicRtlOnboardingAndHome() {
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.use { sc ->
            waitText(s(R.string.onb1_title))
            check(s(R.string.onb1_title) == "خطط لرحلات أذكى") { "Arabic resources not applied" }
            sc.onActivity { a -> check(a.window.decorView.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) { "Layout is not RTL" } }
            shot("ar_1_onboarding")
            compose.onNodeWithText(s(R.string.action_skip)).performClick()
            waitText(s(R.string.prefs_title))
            shot("ar_2_preferences")
            compose.onNodeWithText(s(R.string.action_start_exploring_app)).performScrollTo().performClick()
            waitText(s(R.string.tagline))
            shot("ar_3_home")
            compose.onAllNodesWithText(s(R.string.nav_trips)).onFirst().performClick()
            waitText(s(R.string.sample_trip_title))
            compose.onAllNodesWithText(s(R.string.sample_trip_title)).onFirst().performClick()
            waitText(s(R.string.sample_banner))
            shot("ar_4_itinerary")
        }
    }

}
