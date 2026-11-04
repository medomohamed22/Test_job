package com.tripmate.ai.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.tripmate.ai.R
import com.tripmate.ai.ui.navigation.ActiveRoute
import com.tripmate.ai.ui.navigation.BudgetRoute
import com.tripmate.ai.ui.navigation.DiagnosticsRoute
import com.tripmate.ai.ui.navigation.ExploreRoute
import com.tripmate.ai.ui.navigation.HomeRoute
import com.tripmate.ai.ui.navigation.ItineraryRoute
import com.tripmate.ai.ui.navigation.JournalRoute
import com.tripmate.ai.ui.navigation.LicensesRoute
import com.tripmate.ai.ui.navigation.OnboardingRoute
import com.tripmate.ai.ui.navigation.PrivacyRoute
import com.tripmate.ai.ui.navigation.ProposalRoute
import com.tripmate.ai.ui.navigation.SettingsRoute
import com.tripmate.ai.ui.navigation.StopRoute
import com.tripmate.ai.ui.navigation.SummaryRoute
import com.tripmate.ai.ui.navigation.TripsRoute
import com.tripmate.ai.ui.navigation.WizardRoute
import com.tripmate.ai.ui.screens.active.ActiveTripScreen
import com.tripmate.ai.ui.screens.budget.BudgetScreen
import com.tripmate.ai.ui.screens.explore.ExploreScreen
import com.tripmate.ai.ui.screens.guide.StopDetailScreen
import com.tripmate.ai.ui.screens.home.HomeScreen
import com.tripmate.ai.ui.screens.itinerary.ItineraryScreen
import com.tripmate.ai.ui.screens.journal.JournalScreen
import com.tripmate.ai.ui.screens.journal.SummaryScreen
import com.tripmate.ai.ui.screens.onboarding.OnboardingScreen
import com.tripmate.ai.ui.screens.proposal.ProposalScreen
import com.tripmate.ai.ui.screens.settings.DiagnosticsScreen
import com.tripmate.ai.ui.screens.settings.LicensesScreen
import com.tripmate.ai.ui.screens.settings.PrivacyScreen
import com.tripmate.ai.ui.screens.settings.SettingsScreen
import com.tripmate.ai.ui.screens.trips.TripsScreen
import com.tripmate.ai.ui.screens.wizard.WizardScreen
import com.tripmate.ai.ui.theme.LocalReducedMotion
import kotlin.reflect.KClass

private data class TopLevel(val route: Any, val klass: KClass<*>, val icon: ImageVector, val label: Int)

private val topLevel = listOf(
    TopLevel(HomeRoute, HomeRoute::class, Icons.Filled.Home, R.string.nav_home),
    TopLevel(ExploreRoute, ExploreRoute::class, Icons.Filled.Explore, R.string.nav_explore),
    TopLevel(TripsRoute, TripsRoute::class, Icons.Filled.Luggage, R.string.nav_trips),
    TopLevel(SettingsRoute, SettingsRoute::class, Icons.Filled.Settings, R.string.nav_settings),
)

@Composable
fun TripMateRoot(startWithOnboarding: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = topLevel.any { t -> dest?.hierarchy?.any { it.hasRoute(t.klass) } == true }
    val reduced = LocalReducedMotion.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                topLevel.forEach { t ->
                    val selected = dest?.hierarchy?.any { it.hasRoute(t.klass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startWithOnboarding) OnboardingRoute else HomeRoute,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding),
            enterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(180)) },
            popEnterTransition = { if (reduced) EnterTransition.None else fadeIn(tween(220)) },
            popExitTransition = { if (reduced) ExitTransition.None else fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 12 } },
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(onDone = { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } })
            }
            composable<HomeRoute> {
                HomeScreen(
                    onPlanTrip = { nav.navigate(WizardRoute(it)) },
                    onOpenTrip = { nav.navigate(ItineraryRoute(it)) },
                    onContinueTrip = { nav.navigate(ActiveRoute(it)) },
                    onExplore = { nav.navigate(ExploreRoute) { launchSingleTop = true } },
                    onSavedTrips = { nav.navigate(TripsRoute) { launchSingleTop = true } },
                    onSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } },
                )
            }
            composable<ExploreRoute> { ExploreScreen(onPlanTrip = { nav.navigate(WizardRoute(it)) }, onOpenTrip = { nav.navigate(ItineraryRoute(it)) }) }
            composable<TripsRoute> {
                TripsScreen(onOpen = { nav.navigate(ItineraryRoute(it)) }, onContinue = { nav.navigate(ActiveRoute(it)) }, onNew = { nav.navigate(WizardRoute()) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onDiagnostics = { nav.navigate(DiagnosticsRoute) },
                    onPrivacy = { nav.navigate(PrivacyRoute) },
                    onLicenses = { nav.navigate(LicensesRoute) },
                    onDataCleared = { nav.navigate(OnboardingRoute) { popUpTo(0) } },
                )
            }
            composable<WizardRoute> { e ->
                WizardScreen(
                    initialDestination = e.toRoute<WizardRoute>().destinationId,
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate(ItineraryRoute(id)) { popUpTo<WizardRoute> { inclusive = true } } },
                )
            }
            composable<ItineraryRoute> { e ->
                val id = e.toRoute<ItineraryRoute>().tripId
                ItineraryScreen(
                    tripId = id, onBack = { nav.popBackStack() },
                    onStart = { nav.navigate(ActiveRoute(id)) },
                    onStop = { stopId -> nav.navigate(StopRoute(id, stopId)) },
                    onBudget = { nav.navigate(BudgetRoute(id)) }, onJournal = { nav.navigate(JournalRoute(id)) },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable<ActiveRoute> { e ->
                val id = e.toRoute<ActiveRoute>().tripId
                ActiveTripScreen(
                    tripId = id, onBack = { nav.popBackStack() },
                    onProposal = { nav.navigate(ProposalRoute(id)) },
                    onStop = { stopId -> nav.navigate(StopRoute(id, stopId)) },
                    onBudget = { nav.navigate(BudgetRoute(id)) }, onJournal = { nav.navigate(JournalRoute(id)) },
                    onSummary = { nav.navigate(SummaryRoute(id)) },
                    onItinerary = { nav.navigate(ItineraryRoute(id)) },
                )
            }
            composable<StopRoute> { e ->
                val r = e.toRoute<StopRoute>()
                StopDetailScreen(tripId = r.tripId, stopId = r.stopId, onBack = { nav.popBackStack() })
            }
            composable<ProposalRoute> { ProposalScreen(onBack = { nav.popBackStack() }) }
            composable<BudgetRoute> { e -> BudgetScreen(tripId = e.toRoute<BudgetRoute>().tripId, onBack = { nav.popBackStack() }) }
            composable<JournalRoute> { e -> JournalScreen(tripId = e.toRoute<JournalRoute>().tripId, onBack = { nav.popBackStack() }) }
            composable<SummaryRoute> { e -> SummaryScreen(tripId = e.toRoute<SummaryRoute>().tripId, onBack = { nav.popBackStack() }) }
            composable<DiagnosticsRoute> { DiagnosticsScreen(onBack = { nav.popBackStack() }) }
            composable<PrivacyRoute> { PrivacyScreen(onBack = { nav.popBackStack() }) }
            composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
