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
 * End-to-end smoke test on the JVM (Robolectric): launches the real app with NO Firebase or Maps
 * configuration (demo mode) and drives the core journey:
 * onboarding → sample trip → start trip → explore → finish → running late → apply proposal.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
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

    @Test fun coreJourneyWorksInDemoMode() {
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.use {
            // Onboarding
            waitText(s(R.string.onb1_title))
            shot("en_1_onboarding")
            compose.onNodeWithText(s(R.string.action_skip)).performClick()
            waitText(s(R.string.prefs_title))
            compose.onNodeWithText(s(R.string.action_start_exploring_app)).performScrollTo().performClick()

            // Home → seeded sample trip
            waitText(s(R.string.tagline))
            shot("en_2_home")
            compose.onAllNodesWithText(s(R.string.nav_trips)).onFirst().performClick()
            waitText(s(R.string.sample_trip_title))
            compose.onAllNodesWithText(s(R.string.sample_trip_title)).onFirst().performClick()

            // Itinerary → start trip
            waitText(s(R.string.sample_banner))
            shot("en_3_itinerary")
            compose.onNodeWithText(s(R.string.action_start_trip), useUnmergedTree = true).performClick()

            // Active trip: explore & finish the first stop
            waitText(s(R.string.next_stop))
            shot("en_4_active_trip")
            compose.onAllNodesWithText(s(R.string.action_start_exploring), useUnmergedTree = true).onFirst().performScrollTo().performClick()
            waitText(s(R.string.action_finished))
            compose.onAllNodesWithText(s(R.string.action_finished), useUnmergedTree = true).onFirst().performScrollTo().performClick()

            // Running late → 90 min → proposal → apply
            compose.onNodeWithText(s(R.string.action_running_late), useUnmergedTree = true).performScrollTo().performClick()
            waitText(s(R.string.late_title))
            compose.onNodeWithText(ctx.getString(R.string.duration_h_m, 1, 30), useUnmergedTree = true).performClick()
            waitText(s(R.string.proposal_sub))
            shot("en_5_proposal")
            compose.onNodeWithText(s(R.string.action_apply_changes), useUnmergedTree = true).performClick()
            waitText(s(R.string.active_adjust))
        }
    }
}
