package com.tripmate.ai.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.ai.AiInferenceCoordinator
import com.tripmate.ai.ai.AiStatus
import com.tripmate.ai.ai.NetworkMonitor
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.OnDeviceAvailability
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.ui.components.AiBadge
import com.tripmate.ai.ui.components.DestinationArt
import com.tripmate.ai.ui.components.Entrance
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class HomeState(val trips: List<TripPlan> = emptyList(), val online: Boolean = true, val ai: AiStatus? = null) {
    val active: TripPlan? get() = trips.firstOrNull { it.status == TripStatus.ACTIVE }
    val upcoming: TripPlan? get() = trips.firstOrNull { it.status == TripStatus.PLANNED }
}

@HiltViewModel
class HomeViewModel @Inject constructor(repo: TripRepository, network: NetworkMonitor, ai: AiInferenceCoordinator) : ViewModel() {
    val state: StateFlow<HomeState> = combine(repo.observeTrips(), network.online, ai.status) { t, o, a -> HomeState(t, o, a) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

@Composable
fun HomeScreen(
    onPlanTrip: (String?) -> Unit, onOpenTrip: (String) -> Unit, onContinueTrip: (String) -> Unit,
    onExplore: () -> Unit, onSavedTrips: () -> Unit, onSettings: () -> Unit, vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { Hero(state, onPlanTrip = { onPlanTrip(null) }) }
        if (!state.online) item {
            InfoBanner(stringResource(R.string.offline_banner), Modifier.padding(horizontal = 20.dp, vertical = 8.dp), icon = Icons.Filled.WifiOff)
        }
        state.active?.let { trip -> item { Entrance(0) { ActiveTripCard(trip, onContinue = { onContinueTrip(trip.id) }) } } }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(stringResource(R.string.home_quick_actions))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Filled.AddLocationAlt, stringResource(R.string.action_plan_trip), Modifier.weight(1f)) { onPlanTrip(null) }
                    val continueId = (state.active ?: state.upcoming)?.id
                    QuickAction(Icons.Filled.PlayArrow, stringResource(R.string.action_continue_trip), Modifier.weight(1f), enabled = continueId != null) {
                        continueId?.let { if (state.active != null) onContinueTrip(it) else onOpenTrip(it) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Filled.Explore, stringResource(R.string.nav_explore), Modifier.weight(1f), onClick = onExplore)
                    QuickAction(Icons.Filled.Luggage, stringResource(R.string.action_saved_trips), Modifier.weight(1f), onClick = onSavedTrips)
                    QuickAction(Icons.Filled.Settings, stringResource(R.string.nav_settings), Modifier.weight(1f), onClick = onSettings)
                }
            }
        }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionHeader(stringResource(R.string.home_featured)) } }
        item {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(PlaceCatalog.destinations, key = { it.id }) { d ->
                    SoftCard(Modifier.width(250.dp), onClick = { onPlanTrip(d.id) }) {
                        DestinationArt(d.motif, Modifier.fillMaxWidth().height(160.dp), contentDescription = d.name(ar)) {
                            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                Text(d.name(ar), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                                Text(if (ar) d.countryAr else d.countryEn, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                            }
                        }
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(PlaceCatalog.forDestination(d.id).size.let { n -> pluralStringResource(R.plurals.home_places_count, n, n) }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        state.upcoming?.takeIf { state.active == null }?.let { trip ->
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(stringResource(R.string.home_upcoming))
                    TripRow(trip) { onOpenTrip(trip.id) }
                }
            }
        }
    }
}

@Composable
private fun Hero(state: HomeState, onPlanTrip: () -> Unit) {
    val hour = LocalTime.now().hour
    val greeting = stringResource(when (hour) { in 5..11 -> R.string.greeting_morning; in 12..17 -> R.string.greeting_afternoon; else -> R.string.greeting_evening })
    DestinationArt(ArtMotif.PYRAMIDS, Modifier.fillMaxWidth().height(300.dp)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                state.ai?.let { AiBadge(homeRoute(it)) }
            }
            Spacer(Modifier.weight(1f))
            Text(greeting, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f))
            Text(stringResource(R.string.tagline), style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(14.dp))
            Button(onClick = onPlanTrip, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)) {
                Icon(Icons.Filled.AddLocationAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_plan_trip))
            }
        }
    }
}

/** Summarises which AI route is currently possible (not a claim that any call ran). */
private fun homeRoute(s: AiStatus): AiRoute = when {
    !s.firebaseConfigured -> AiRoute.DEMO
    s.online -> AiRoute.CLOUD
    s.onDevice == OnDeviceAvailability.AVAILABLE -> AiRoute.ON_DEVICE
    else -> AiRoute.OFFLINE_CONTENT
}

@Composable
private fun ActiveTripCard(trip: TripPlan, onContinue: () -> Unit) {
    val context = LocalContext.current
    val day = trip.days.firstOrNull { it.date == LocalDate.now() } ?: trip.days.firstOrNull { it.currentStop != null } ?: trip.days.firstOrNull()
    val next = day?.currentStop
    val motif = PlaceCatalog.place(next?.placeId)?.motif ?: ArtMotif.CITY
    SoftCard(Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(), onClick = onContinue) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DestinationArt(motif, Modifier.size(76.dp), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_active_trip), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (next != null) Text(stringResource(R.string.home_next_step, next.title, Formatters.time(context, next.startMinutes)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                else Text(stringResource(R.string.home_day_complete), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { day?.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun TripRow(trip: TripPlan, onClick: () -> Unit) {
    val context = LocalContext.current
    val motif = PlaceCatalog.destination(trip.destinationId)?.motif ?: ArtMotif.CITY
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            DestinationArt(motif, Modifier.size(64.dp), shape = MaterialTheme.shapes.medium)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.date(context, trip.startDate)} · ${pluralStringResource(R.plurals.days_count, trip.dayCount, trip.dayCount)} · ${pluralStringResource(R.plurals.stops_count, trip.allStops.size, trip.allStops.size)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, modifier = modifier.height(88.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
        }
    }
}
