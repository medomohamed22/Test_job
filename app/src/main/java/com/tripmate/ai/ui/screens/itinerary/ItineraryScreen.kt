package com.tripmate.ai.ui.screens.itinerary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.repo.newId
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.logic.IssueSeverity
import com.tripmate.ai.domain.logic.ItineraryGenerator
import com.tripmate.ai.domain.logic.ItineraryValidator
import com.tripmate.ai.domain.logic.ScheduleEngine
import com.tripmate.ai.domain.logic.TimeMath
import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.CatalogPlace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.domain.model.Verification
import com.tripmate.ai.ui.components.AiBadge
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.DestinationArt
import com.tripmate.ai.ui.components.EmptyState
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.LoadingState
import com.tripmate.ai.ui.components.MapPin
import com.tripmate.ai.ui.components.MapPreview
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ItineraryViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: TripRepository) : ViewModel() {
    private val tripId: String = checkNotNull(savedState["tripId"])
    val trip: StateFlow<TripPlan?> = repo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    var loaded = false

    private fun editDay(dayIndex: Int, block: (TripDay, TripPlan) -> TripDay) = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val day = plan.day(dayIndex) ?: return@launch
        val updated = ItineraryGenerator.withEstimatedTravel(block(day, plan), plan.transport)
        repo.updateDay(tripId, updated)
    }

    fun move(dayIndex: Int, stopId: String, delta: Int) = editDay(dayIndex) { d, _ -> ScheduleEngine.move(d, stopId, delta) }
    fun delete(dayIndex: Int, stopId: String) = editDay(dayIndex) { d, _ -> d.copy(stops = d.stops.filterNot { it.id == stopId }) }
    fun update(dayIndex: Int, stop: TripStop) = editDay(dayIndex) { d, _ -> d.copy(stops = d.stops.map { if (it.id == stop.id) stop else it }) }
    fun addPlace(dayIndex: Int, place: CatalogPlace) = editDay(dayIndex) { d, plan ->
        val ar = repo.isArabic()
        val stop = ItineraryGenerator(::newId).placeToStop(place, ar, 0, place.typicalDurationMinutes, repo.generatorLabels())
        d.copy(stops = d.stops + stop)
    }
    fun addCustom(dayIndex: Int, title: String, minutes: Int) = editDay(dayIndex) { d, _ ->
        d.copy(stops = d.stops + TripStop(id = newId(), title = title.trim(), category = StopCategory.CUSTOM, startMinutes = 0,
            durationMinutes = minutes.coerceIn(10, 600), priority = 2, verification = Verification.UNVERIFIED))
    }
    fun rename(title: String) = viewModelScope.launch { repo.getTrip(tripId)?.let { repo.save(it.copy(title = title.trim().take(80))) } }
    fun start(onStarted: () -> Unit) = viewModelScope.launch { repo.setStatus(tripId, TripStatus.ACTIVE); onStarted() }
    fun deleteTrip(done: () -> Unit) = viewModelScope.launch { repo.delete(tripId); done() }
}

@Composable
fun ItineraryScreen(
    tripId: String, onBack: () -> Unit, onStart: () -> Unit, onStop: (String) -> Unit,
    onBudget: () -> Unit, onJournal: () -> Unit, onDeleted: () -> Unit, vm: ItineraryViewModel = hiltViewModel(),
) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val plan = trip ?: run { LoadingState(stringResource(R.string.loading)); return }
    val context = LocalContext.current
    var dayIndex by rememberSaveable { mutableIntStateOf(0) }
    val day = plan.day(dayIndex) ?: plan.days.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TripStop?>(null) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val issues = remember(plan) { ItineraryValidator.validate(plan) }
    val errors = issues.filter { it.severity == IssueSeverity.ERROR }
    val ar = LocalConfiguration.current.locales[0].language == "ar"

    Scaffold(
        topBar = {
            BackTopBar(plan.title, onBack, actions = {
                IconButton(onClick = onBudget) { Icon(Icons.Filled.ReceiptLong, stringResource(R.string.budget_title)) }
                IconButton(onClick = onJournal) { Icon(Icons.Filled.Book, stringResource(R.string.journal_title)) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_more)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.action_rename)) }, { menu = false; renaming = true }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                        DropdownMenuItem({ Text(stringResource(R.string.action_delete_trip)) }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                    }
                }
            })
        },
        floatingActionButton = {
            if (plan.status != TripStatus.COMPLETED) ExtendedFloatingActionButton(
                onClick = { vm.start(onStart) },
                icon = { Icon(Icons.Filled.PlayArrow, null) },
                text = { Text(stringResource(if (plan.status == TripStatus.ACTIVE) R.string.action_continue_trip else R.string.action_start_trip)) },
                containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary,
                expanded = errors.isEmpty(),
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp)) {
            item {
                DestinationArt(PlaceCatalog.destination(plan.destinationId)?.motif ?: ArtMotif.CITY, Modifier.fillMaxWidth().height(150.dp), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                        Text(plan.destinationName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        Text("${Formatters.date(context, plan.startDate)} – ${Formatters.date(context, plan.endDate)} · ${pluralStringResource(R.plurals.wizard_travellers, plan.groupSize, plan.groupSize)}",
                            style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (plan.generatedBy) {
                        "cloud-ai" -> AiBadge(AiRoute.CLOUD)
                        "sample" -> AiBadge(AiRoute.DEMO)
                        else -> AiBadge(AiRoute.OFFLINE_CONTENT)
                    }
                    Text(stringResource(when (plan.generatedBy) { "cloud-ai" -> R.string.plan_by_ai; "sample" -> R.string.plan_sample; else -> R.string.plan_by_offline }),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (plan.generatedBy == "fallback") { Spacer(Modifier.height(8.dp)); InfoBanner(stringResource(R.string.wizard_ai_fallback_banner), warning = true) }
                if (plan.isSample) { Spacer(Modifier.height(8.dp)); InfoBanner(stringResource(R.string.sample_banner)) }
                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    InfoBanner(stringResource(R.string.validation_errors, errors.joinToString { context.getString(it.code.labelRes()) }), warning = true)
                }
                Spacer(Modifier.height(8.dp))
                InfoBanner(stringResource(R.string.estimates_disclaimer))
                Spacer(Modifier.height(8.dp))
            }
            item {
                PrimaryScrollableTabRow(selectedTabIndex = plan.days.indexOfFirst { it.dayIndex == day?.dayIndex }.coerceAtLeast(0), edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
                    plan.days.forEach { d ->
                        Tab(d.dayIndex == day?.dayIndex, { dayIndex = d.dayIndex }, text = {
                            Text(stringResource(R.string.day_n, d.dayIndex + 1) + " · " + Formatters.dayShort(context, d.date))
                        })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (day != null) {
                item {
                    MapPreview(day.stops.filter { it.hasCoordinates }.map { MapPin(it.id, it.title, it.lat!!, it.lng!!) }, height = 170.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.day_window, Formatters.time(context, day.startMinutes), Formatters.time(context, day.endMinutes)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (day.stops.isEmpty()) item {
                    EmptyState(Icons.Filled.Map, stringResource(R.string.empty_day_title), stringResource(R.string.empty_day_body), actionLabel = stringResource(R.string.action_add_stop), onAction = { adding = true })
                }
                itemsIndexed(day.stops, key = { _, s -> s.id }) { i, stop ->
                    StopTimelineRow(stop, i == 0, i == day.stops.lastIndex, onClick = { onStop(stop.id) }) {
                        Column {
                            IconButton(onClick = { vm.move(day.dayIndex, stop.id, -1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.cd_move_up)) }
                            IconButton(onClick = { vm.move(day.dayIndex, stop.id, 1) }, enabled = i < day.stops.lastIndex) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.cd_move_down)) }
                        }
                        Column {
                            IconButton(onClick = { editing = stop }) { Icon(Icons.Filled.Edit, stringResource(R.string.cd_edit)) }
                            IconButton(onClick = { vm.delete(day.dayIndex, stop.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete)) }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_add_stop))
                    }
                }
            }
        }
    }

    editing?.let { stop -> EditStopDialog(stop, onDismiss = { editing = null }) { updated -> day?.let { vm.update(it.dayIndex, updated) }; editing = null } }
    if (adding && day != null) AddStopSheet(plan, ar, onDismiss = { adding = false },
        onPlace = { vm.addPlace(day.dayIndex, it); adding = false }, onCustom = { t, m -> vm.addCustom(day.dayIndex, t, m); adding = false })
    if (renaming) TextInputDialog(stringResource(R.string.action_rename), plan.title, onDismiss = { renaming = false }) { vm.rename(it); renaming = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_trip_title)) }, text = { Text(stringResource(R.string.delete_trip_body)) },
        confirmButton = { Button(onClick = { confirmDelete = false; vm.deleteTrip(onDeleted) }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditStopDialog(stop: TripStop, onDismiss: () -> Unit, onSave: (TripStop) -> Unit) {
    var title by remember { mutableStateOf(stop.title) }
    var duration by remember { mutableStateOf(stop.durationMinutes.toString()) }
    var notes by remember { mutableStateOf(stop.notes) }
    val d = duration.toIntOrNull()
    val valid = title.isNotBlank() && d != null && d in 5..ItineraryValidator.MAX_STOP_MINUTES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_stop)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, isError = title.isBlank())
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.field_duration_minutes)) },
                    singleLine = true, isError = !valid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.field_notes)) }, minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val dur = TimeMath.roundUpTo5(d!!)
                onSave(stop.copy(title = title.trim(), durationMinutes = dur, minDurationMinutes = minOf(stop.minDurationMinutes, dur), notes = notes.trim()))
            }, enabled = valid) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(80) }, singleLine = true) },
        confirmButton = { Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun AddStopSheet(plan: TripPlan, ar: Boolean, onDismiss: () -> Unit, onPlace: (CatalogPlace) -> Unit, onCustom: (String, Int) -> Unit) {
    val used = plan.allStops.mapNotNull { it.placeId }.toSet()
    val candidates = PlaceCatalog.forDestination(plan.destinationId).filter { it.id !in used }
    var custom by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("60") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            item {
                Text(stringResource(R.string.action_add_stop), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(custom, { custom = it.take(80) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.add_custom_stop)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.field_duration_minutes)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = { onCustom(custom, minutes.toIntOrNull() ?: 60) }, enabled = custom.isNotBlank() && (minutes.toIntOrNull() ?: 0) in 10..600) {
                        Text(stringResource(R.string.action_add))
                    }
                }
                Text(stringResource(R.string.custom_stop_unverified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.add_from_catalog), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(candidates, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text(p.name(ar)) },
                    supportingContent = { Text(p.guide(ar).short, maxLines = 2) },
                    leadingContent = { DestinationArt(p.motif, Modifier.width(56.dp).height(56.dp), shape = MaterialTheme.shapes.small) },
                    trailingContent = { IconButton(onClick = { onPlace(p) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) } },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
