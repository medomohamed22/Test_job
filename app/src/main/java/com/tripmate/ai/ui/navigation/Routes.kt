package com.tripmate.ai.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object HomeRoute
@Serializable data object ExploreRoute
@Serializable data object TripsRoute
@Serializable data object SettingsRoute
@Serializable data class WizardRoute(val destinationId: String? = null)
@Serializable data class ItineraryRoute(val tripId: String)
@Serializable data class ActiveRoute(val tripId: String)
@Serializable data class StopRoute(val tripId: String, val stopId: String)
@Serializable data class ProposalRoute(val tripId: String)
@Serializable data class BudgetRoute(val tripId: String)
@Serializable data class JournalRoute(val tripId: String)
@Serializable data class SummaryRoute(val tripId: String)
@Serializable data object DiagnosticsRoute
@Serializable data object PrivacyRoute
@Serializable data object LicensesRoute
