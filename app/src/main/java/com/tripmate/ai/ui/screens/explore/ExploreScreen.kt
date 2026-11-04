package com.tripmate.ai.ui.screens.explore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.ai.NetworkMonitor
import com.tripmate.ai.data.db.BookmarkEntity
import com.tripmate.ai.data.remote.PlaceResult
import com.tripmate.ai.data.remote.PlacesRoutesClient
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.repo.newId
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.logic.ItineraryGenerator
import com.tripmate.ai.domain.model.CatalogPlace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.domain.model.Verification
import com.tripmate.ai.ui.components.DestinationArt
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.MapPin
import com.tripmate.ai.ui.components.MapPreview
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.VerificationBadge
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A place shown in Explore: curated catalog entry or a live Google Places result. */
data class ExplorePlace(val key: String, val title: String, val subtitle: String, val lat: Double?, val lng: Double?, val catalog: CatalogPlace?, val live: PlaceResult?)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repo: TripRepository,
    private val places: PlacesRoutesClient,
    network: NetworkMonitor,
) : ViewModel() {
    val bookmarks: StateFlow<List<BookmarkEntity>> = repo.observeBookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trips: StateFlow<List<TripPlan>> = repo.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val online = network.online
    val liveSearchAvailable get() = places.isConfigured
    val liveResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val searching = MutableStateFlow(false)
    val searchError = MutableStateFlow(false)

    fun searchLive(query: String, destinationId: String?) = viewModelScope.launch {
        if (query.isBlank() || !places.isConfigured) return@launch
        searching.value = true; searchError.value = false
        val d = destinationId?.let { PlaceCatalog.destination(it) }
        places.searchText(query, if (repo.isArabic()) "ar" else "en", d?.lat, d?.lng)
            .onSuccess { liveResults.value = it }.onFailure { searchError.value = true; liveResults.value = emptyList() }
        searching.value = false
    }

    fun toggleBookmark(p: ExplorePlace, bookmarked: Boolean) = viewModelScope.launch {
        repo.toggleBookmark(p.key, if (p.live != null) "places" else "catalog", p.title, p.lat, p.lng, p.subtitle, bookmarked)
    }

    /** Adds to the last day of an existing trip; live results are VERIFIED for name/location only. */
    fun addToTrip(p: ExplorePlace, trip: TripPlan) = viewModelScope.launch {
        val plan = repo.getTrip(trip.id) ?: return@launch
        val day = plan.days.lastOrNull { it.currentStop != null } ?: plan.days.lastOrNull() ?: return@launch
        val stop = p.catalog?.let { ItineraryGenerator(::newId).placeToStop(it, repo.isArabic(), 0, it.typicalDurationMinutes, repo.generatorLabels()) }
            ?: TripStop(id = newId(), placeId = p.live?.id, title = p.title, category = StopCategory.CUSTOM, lat = p.lat, lng = p.lng,
                startMinutes = 0, durationMinutes = 60, priority = 2, verification = if (p.live != null) Verification.VERIFIED_LIVE else Verification.UNVERIFIED)
        repo.updateDay(plan.id, ItineraryGenerator.withEstimatedTravel(day.copy(stops = day.stops + stop), plan.transport))
    }
}

@Composable
fun ExploreScreen(onPlanTrip: (String?) -> Unit, onOpenTrip: (String) -> Unit, vm: ExploreViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val live by vm.liveResults.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searchError by vm.searchError.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf<String?>("cairo") }
    var selected by remember { mutableStateOf<ExplorePlace?>(null) }
    var addTarget by remember { mutableStateOf<ExplorePlace?>(null) }
    val bookmarkedKeys = bookmarks.map { it.key }.toSet()

    val curated = PlaceCatalog.places
        .filter { destination == null || destination in it.destinationIds }
        .filter { query.isBlank() || it.name(ar).contains(query, true) || it.nameEn.contains(query, true) || it.nameAr.contains(query) }
        .map { ExplorePlace(it.id, it.name(ar), it.guide(ar).short, it.lat, it.lng, it, null) }
    val liveItems = live.map { ExplorePlace("places:${it.id}", it.name, it.address.orEmpty(), it.lat, it.lng, null, it) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
        item {
            Text(stringResource(R.string.explore_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                query, { query = it.take(100) }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text(stringResource(R.string.explore_search_hint)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchLive(query, destination) }),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(destination == null, { destination = null }, { Text(stringResource(R.string.explore_all)) })
                PlaceCatalog.destinations.forEach { d -> FilterChip(destination == d.id, { destination = d.id }, { Text(d.name(ar)) }) }
            }
            Spacer(Modifier.height(8.dp))
            when {
                !vm.liveSearchAvailable -> InfoBanner(stringResource(R.string.explore_places_not_configured))
                !online -> InfoBanner(stringResource(R.string.explore_offline), warning = true)
                else -> OutlinedButton(onClick = { vm.searchLive(query, destination) }, enabled = query.isNotBlank() && !searching) {
                    if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Search, null)
                    Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.explore_search_live))
                }
            }
            if (searchError) { Spacer(Modifier.height(8.dp)); InfoBanner(stringResource(R.string.explore_search_failed), warning = true) }
            Spacer(Modifier.height(12.dp))
            MapPreview(curated.filter { it.lat != null }.map { MapPin(it.key, it.title, it.lat!!, it.lng!!) }, height = 180.dp)
        }
        if (liveItems.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.explore_live_results)); Text(stringResource(R.string.powered_by_google), style = MaterialTheme.typography.labelSmall) }
            items(liveItems, key = { it.key }) { p -> PlaceCard(p, p.key in bookmarkedKeys, onOpen = { selected = p }, onBookmark = { vm.toggleBookmark(p, p.key in bookmarkedKeys) }) }
        }
        if (bookmarks.isNotEmpty() && query.isBlank()) {
            item { SectionHeader(stringResource(R.string.explore_saved)) }
            items(bookmarks, key = { "bm_" + it.key }) { b ->
                val cat = PlaceCatalog.place(b.key)
                val p = ExplorePlace(b.key, cat?.name(ar) ?: b.title, cat?.guide(ar)?.short ?: b.address.orEmpty(), b.lat, b.lng, cat, null)
                PlaceCard(p, true, onOpen = { selected = p }, onBookmark = { vm.toggleBookmark(p, true) })
            }
        }
        item { SectionHeader(stringResource(R.string.explore_curated)) }
        if (curated.isEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SearchOff, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.explore_no_results))
            }
        }
        items(curated, key = { "c_" + it.key }) { p -> PlaceCard(p, p.key in bookmarkedKeys, onOpen = { selected = p }, onBookmark = { vm.toggleBookmark(p, p.key in bookmarkedKeys) }) }
    }

    selected?.let { p ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                p.catalog?.let { DestinationArt(it.motif, Modifier.fillMaxWidth().height(150.dp), shape = MaterialTheme.shapes.large) ; Spacer(Modifier.height(12.dp)) }
                Text(p.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                VerificationBadge(if (p.live != null) Verification.VERIFIED_LIVE else Verification.SAMPLE)
                Spacer(Modifier.height(10.dp))
                Text(p.subtitle, style = MaterialTheme.typography.bodyLarge)
                p.catalog?.let { c ->
                    Spacer(Modifier.height(8.dp))
                    if (c.typicalOpens != null && c.typicalCloses != null) Text(stringResource(R.string.hours_typical, Formatters.time(context, c.typicalOpens), Formatters.time(context, c.typicalCloses)), style = MaterialTheme.typography.bodySmall)
                    c.priceEstimate?.let { Text(stringResource(R.string.price_estimate, Formatters.money(context, it, c.priceCurrency)), style = MaterialTheme.typography.bodySmall) }
                    Text(c.interests.joinToString(" · ") { context.getString(it.labelRes()) }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (p.live != null) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.powered_by_google), style = MaterialTheme.typography.labelSmall) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        val uri = if (p.lat != null) "geo:${p.lat},${p.lng}?q=${p.lat},${p.lng}(${Uri.encode(p.title)})".toUri() else "geo:0,0?q=${Uri.encode(p.title)}".toUri()
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }) { Icon(Icons.Filled.Directions, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_open_maps)) }
                    OutlinedButton(onClick = { addTarget = p; selected = null }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_add_to_trip)) }
                }
                p.catalog?.let { c ->
                    TextButton(onClick = { selected = null; onPlanTrip(c.destinationIds.first()) }) { Text(stringResource(R.string.action_plan_here)) }
                }
            }
        }
    }
    addTarget?.let { p ->
        val open = trips.filter { it.status != TripStatus.COMPLETED }
        AlertDialog(
            onDismissRequest = { addTarget = null },
            title = { Text(stringResource(R.string.add_to_which_trip)) },
            text = {
                if (open.isEmpty()) Text(stringResource(R.string.no_open_trips))
                else Column { open.forEach { t -> ListItem(headlineContent = { Text(t.title) }, supportingContent = { Text(t.destinationName) },
                    trailingContent = { IconButton(onClick = { vm.addToTrip(p, t); addTarget = null; onOpenTrip(t.id) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) } }) } }
            },
            confirmButton = { TextButton(onClick = { addTarget = null; onPlanTrip(null) }) { Text(stringResource(R.string.action_plan_trip)) } },
            dismissButton = { TextButton(onClick = { addTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PlaceCard(p: ExplorePlace, bookmarked: Boolean, onOpen: () -> Unit, onBookmark: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onOpen) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            p.catalog?.let { DestinationArt(it.motif, Modifier.size(64.dp), shape = MaterialTheme.shapes.medium); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(p.title, style = MaterialTheme.typography.titleMedium)
                Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            IconButton(onClick = onBookmark) {
                Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, stringResource(if (bookmarked) R.string.cd_remove_bookmark else R.string.cd_bookmark),
                    tint = if (bookmarked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
