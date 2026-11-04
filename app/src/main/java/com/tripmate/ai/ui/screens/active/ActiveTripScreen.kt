package com.tripmate.ai.ui.screens.active

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripmate.ai.R
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.logic.ScheduleEngine
import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.ui.components.AiBadge
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.DestinationArt
import com.tripmate.ai.ui.components.EmptyState
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.LoadingState
import com.tripmate.ai.ui.components.MapPin
import com.tripmate.ai.ui.components.MapPreview
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.ui.screens.itinerary.StopTimelineRow
import com.tripmate.ai.ui.theme.LocalExtendedColors
import com.tripmate.ai.util.Formatters

private enum class Sheet { LATE, BREAK, PACE, CHANGE, CHAT }

@Composable
fun ActiveTripScreen(
    tripId: String, onBack: () -> Unit, onProposal: () -> Unit, onStop: (String) -> Unit,
    onBudget: () -> Unit, onJournal: () -> Unit, onSummary: () -> Unit, onItinerary: () -> Unit,
    vm: ActiveTripViewModel = hiltViewModel(),
) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val plan = trip ?: run { LoadingState(stringResource(R.string.loading)); return }
    val context = LocalContext.current
    val day = vm.currentDay(plan)
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var skipTarget by remember { mutableStateOf<TripStop?>(null) }
    var pendingCheckInStop by remember { mutableStateOf<String?>(null) }
    var showLocationRationale by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        pendingCheckInStop?.let { vm.requestCheckIn(it) }
        pendingCheckInStop = null
    }

    LaunchedEffect(ui.message, ui.messageArg) {
        val res = ui.message ?: return@LaunchedEffect
        val text = if (ui.messageArg != null) context.getString(res, ui.messageArg) else context.getString(res)
        val offerReview = res == R.string.msg_behind_offer || res == R.string.msg_ahead_offer
        val result = snackbar.showSnackbar(text, actionLabel = if (offerReview) context.getString(R.string.action_review) else null, withDismissAction = true)
        if (result == SnackbarResult.ActionPerformed && offerReview) onProposal()
        vm.dismissMessage()
    }

    Scaffold(
        topBar = {
            BackTopBar(plan.title, onBack, actions = {
                IconButton(onClick = onItinerary) { Icon(Icons.Filled.ViewTimeline, stringResource(R.string.itinerary_title)) }
                IconButton(onClick = onBudget) { Icon(Icons.Filled.ReceiptLong, stringResource(R.string.budget_title)) }
                IconButton(onClick = onJournal) { Icon(Icons.Filled.Book, stringResource(R.string.journal_title)) }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { sheet = Sheet.CHAT }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.Forum, stringResource(R.string.chat_title))
            }
        },
    ) { pad ->
        if (day == null) {
            EmptyState(Icons.Filled.TravelExplore, stringResource(R.string.empty_day_title), stringResource(R.string.empty_day_body), Modifier.padding(pad))
            return@Scaffold
        }
        val current = day.currentStop
        LazyColumn(Modifier.padding(pad).fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp)) {
            if (!ui.online) item { InfoBanner(stringResource(R.string.offline_active_banner)); Spacer(Modifier.height(10.dp)) }
            if (plan.days.size > 1) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    plan.days.forEach { d ->
                        FilterChip(d.dayIndex == day.dayIndex, { vm.selectDay(d.dayIndex) }, { Text(stringResource(R.string.day_n, d.dayIndex + 1) + " · " + Formatters.dayShort(context, d.date)) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item { DayHeader(plan, day, ui.nowMinutes) }
            item {
                Spacer(Modifier.height(14.dp))
                if (current != null) CurrentStopCard(
                    stop = current, day = day, nowMinutes = ui.nowMinutes, locating = ui.locating,
                    routesConfigured = vm.routesConfigured, routeRefreshing = ui.routeRefreshing,
                    onImHere = {
                        if (current.hasCoordinates && !hasLocationPermission(context)) showLocationRationale = true.also { pendingCheckInStop = current.id }
                        else vm.requestCheckIn(current.id)
                    },
                    onExplore = { vm.startExploring(current.id) },
                    onFinish = { vm.finish(current.id) },
                    onGuide = { onStop(current.id) },
                    onNavigate = { openNavigation(context, current) },
                    onRefreshRoute = vm::refreshTravelTime,
                )
                else DayDoneCard(isLastDay = plan.days.last().dayIndex == day.dayIndex, onSummary = { vm.completeTrip(onSummary) })
            }
            item {
                SectionHeader(stringResource(R.string.active_adjust))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdjustChip(Icons.Filled.HourglassBottom, R.string.action_running_late) { sheet = Sheet.LATE }
                    AdjustChip(Icons.Filled.SkipNext, R.string.action_skip_stop, enabled = current != null) { skipTarget = current }
                    AdjustChip(Icons.Filled.Coffee, R.string.action_take_break) { sheet = Sheet.BREAK }
                    AdjustChip(Icons.Filled.Speed, R.string.action_change_pace) { sheet = Sheet.PACE }
                    AdjustChip(Icons.Filled.EditCalendar, R.string.action_change_plan) { sheet = Sheet.CHANGE }
                    AdjustChip(Icons.Filled.Refresh, R.string.action_resync) { vm.resync(onProposal) }
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.active_adjust_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionHeader(stringResource(R.string.active_map))
                MapPreview(day.stops.filter { it.hasCoordinates && it.status != StopStatus.SKIPPED }.map { MapPin(it.id, it.title, it.lat!!, it.lng!!, it.status == StopStatus.DONE) },
                    height = 180.dp, highlightId = current?.id)
            }
            item { SectionHeader(stringResource(R.string.active_timeline)) }
            itemsIndexed(day.stops, key = { _, s -> s.id }) { i, s ->
                StopTimelineRow(s, i == 0, i == day.stops.lastIndex, highlighted = s.id == current?.id, onClick = { onStop(s.id) })
            }
        }
    }

    // ---- Dialogs & sheets ----
    ui.checkIn?.let { c ->
        val near = c.distanceMeters != null && c.distanceMeters <= 300
        AlertDialog(
            onDismissRequest = vm::cancelCheckIn,
            icon = { Icon(Icons.Filled.MyLocation, null) },
            title = { Text(stringResource(R.string.checkin_title, c.title)) },
            text = {
                Text(when {
                    !c.located -> stringResource(R.string.checkin_no_location)
                    c.distanceMeters == null -> stringResource(R.string.checkin_no_coords)
                    near -> stringResource(R.string.checkin_near, c.distanceMeters)
                    else -> stringResource(R.string.checkin_far, Formatters.number(context, c.distanceMeters / 1000.0))
                })
            },
            confirmButton = { Button(onClick = { vm.confirmArrived(c.stopId) }) { Text(stringResource(R.string.checkin_confirm)) } },
            dismissButton = { TextButton(onClick = vm::cancelCheckIn) { Text(stringResource(R.string.action_not_yet)) } },
        )
    }
    if (showLocationRationale) AlertDialog(
        onDismissRequest = { showLocationRationale = false; pendingCheckInStop?.let { vm.requestCheckIn(it) }; pendingCheckInStop = null },
        icon = { Icon(Icons.Filled.MyLocation, null) },
        title = { Text(stringResource(R.string.location_rationale_title)) },
        text = { Text(stringResource(R.string.location_rationale_body)) },
        confirmButton = {
            Button(onClick = {
                showLocationRationale = false
                permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }) { Text(stringResource(R.string.location_allow)) }
        },
        dismissButton = {
            TextButton(onClick = { showLocationRationale = false; pendingCheckInStop?.let { vm.requestCheckIn(it) }; pendingCheckInStop = null }) {
                Text(stringResource(R.string.location_manual))
            }
        },
    )
    skipTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { skipTarget = null },
            title = { Text(stringResource(R.string.skip_title, s.title)) },
            text = { Text(stringResource(R.string.skip_body)) },
            confirmButton = { Button(onClick = { skipTarget = null; vm.skip(s.id, onProposal) }) { Text(stringResource(R.string.action_preview_changes)) } },
            dismissButton = { TextButton(onClick = { skipTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    when (sheet) {
        Sheet.LATE -> ChoiceSheet(R.string.late_title, R.string.late_sub, listOf(20, 45, 90), { Formatters.duration(context, it) }, onDismiss = { sheet = null }) { m ->
            sheet = null; vm.reportLate(m, onProposal)
        }
        Sheet.BREAK -> ChoiceSheet(R.string.break_title, R.string.break_sub, listOf(15, 30, 45, 60), { Formatters.duration(context, it) }, onDismiss = { sheet = null }) { m ->
            sheet = null; vm.takeBreak(m, onProposal)
        }
        Sheet.PACE -> ChoiceSheet(R.string.pace_title, R.string.pace_sub, Pace.entries.toList(), { context.getString(it.labelRes()) }, onDismiss = { sheet = null }) { p ->
            sheet = null; vm.changePace(p, onProposal)
        }
        Sheet.CHANGE -> ChangePlanSheet(busy = ui.changeBusy, onDismiss = { sheet = null }) { text -> vm.changePlan(text) { sheet = null; onProposal() } }
        Sheet.CHAT -> ChatSheet(ui.chat, ui.chatBusy, onDismiss = { sheet = null }, onSend = vm::sendChat)
        null -> Unit
    }
}

private fun hasLocationPermission(context: Context) =
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun openNavigation(context: Context, stop: TripStop) {
    val uri = if (stop.lat != null && stop.lng != null) "geo:${stop.lat},${stop.lng}?q=${stop.lat},${stop.lng}(${Uri.encode(stop.title)})".toUri()
    else "geo:0,0?q=${Uri.encode(stop.title)}".toUri()
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/maps/search/?api=1&query=${Uri.encode(stop.lat?.let { "$it,${stop.lng}" } ?: stop.title)}".toUri()))
    }
}

@Composable
private fun DayHeader(plan: TripPlan, day: TripDay, now: Int) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val progress by animateFloatAsState(day.progress, label = "progress")
    val drift = ScheduleEngine.driftMinutes(day, now)
    val remaining = (day.endMinutes - now).coerceAtLeast(0)
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.day_of, day.dayIndex + 1, plan.dayCount), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(Formatters.date(context, day.date), style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.time_left_today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Formatters.duration(context, remaining), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(8.dp))
            Row {
                Text(stringResource(R.string.stops_done, day.completedCount, day.stops.count { it.status != StopStatus.SKIPPED }), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                if (drift != null && day.date == java.time.LocalDate.now()) {
                    val (text, color) = when {
                        drift >= 10 -> stringResource(R.string.drift_behind, Formatters.duration(context, drift)) to ext.warning
                        drift <= -10 -> stringResource(R.string.drift_ahead, Formatters.duration(context, -drift)) to ext.success
                        else -> stringResource(R.string.drift_on_time) to ext.success
                    }
                    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        }
    }
}

@Composable
private fun CurrentStopCard(
    stop: TripStop, day: TripDay, nowMinutes: Int, locating: Boolean, routesConfigured: Boolean, routeRefreshing: Boolean,
    onImHere: () -> Unit, onExplore: () -> Unit, onFinish: () -> Unit, onGuide: () -> Unit, onNavigate: () -> Unit, onRefreshRoute: () -> Unit,
) {
    val context = LocalContext.current
    val motif = PlaceCatalog.place(stop.placeId)?.motif ?: when (stop.category) { StopCategory.MEAL -> ArtMotif.MEAL; StopCategory.BREAK -> ArtMotif.BREAK; else -> ArtMotif.CITY }
    SoftCard(Modifier.fillMaxWidth().animateContentSize()) {
        DestinationArt(motif, Modifier.fillMaxWidth().height(170.dp), contentDescription = stop.title) {
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Surface(color = MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(if (stop.status == StopStatus.PENDING) R.string.next_stop else R.string.current_stop),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(stop.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(16.dp)) {
            val startLabel = Formatters.time(context, stop.actualArrivalMinutes ?: stop.startMinutes)
            Text(
                stringResource(R.string.stop_time_line, startLabel, Formatters.duration(context, stop.durationMinutes), stringResource(stop.status.labelRes())),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (stop.status == StopStatus.PENDING && stop.travelMinutesFromPrev > 0) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.travel_line, Formatters.duration(context, stop.travelMinutesFromPrev), stringResource(stop.travelSource.labelRes())),
                        style = MaterialTheme.typography.bodySmall, color = if (stop.travelSource == TravelSource.ROUTES_API) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (routesConfigured) {
                        if (routeRefreshing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else TextButton(onClick = onRefreshRoute) { Text(stringResource(R.string.action_live_time)) }
                    }
                }
            }
            if (stop.status.isInProgress) {
                val elapsed = nowMinutes - (stop.actualArrivalMinutes ?: nowMinutes)
                val left = stop.durationMinutes - elapsed
                Spacer(Modifier.height(6.dp))
                Text(
                    if (left >= 0) stringResource(R.string.time_budget_left, Formatters.duration(context, left)) else stringResource(R.string.time_budget_over, Formatters.duration(context, -left)),
                    style = MaterialTheme.typography.labelLarge, color = if (left >= 0) LocalExtendedColors.current.success else LocalExtendedColors.current.warning,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (stop.status) {
                    StopStatus.PENDING -> {
                        Button(onClick = onImHere, modifier = Modifier.weight(1f).height(52.dp), enabled = !locating) {
                            if (locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.Filled.Flag, null)
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_im_here))
                        }
                        FilledTonalButton(onClick = onExplore, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_start_exploring)) }
                    }
                    StopStatus.ARRIVED -> {
                        Button(onClick = onExplore, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_start_exploring)) }
                        FilledTonalButton(onClick = onFinish, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_finished)) }
                    }
                    StopStatus.EXPLORING -> Button(
                        onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
                    ) { Icon(Icons.Filled.CheckCircle, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_finished)) }
                    else -> Unit
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onGuide, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.MenuBook, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_guide)) }
                OutlinedButton(onClick = onNavigate, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Directions, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_navigate)) }
            }
        }
    }
}

@Composable
private fun DayDoneCard(isLastDay: Boolean, onSummary: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.CheckCircle, null, tint = LocalExtendedColors.current.success, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.day_complete_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(if (isLastDay) R.string.day_complete_last else R.string.day_complete_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isLastDay) { Spacer(Modifier.height(12.dp)); Button(onClick = onSummary) { Text(stringResource(R.string.action_finish_trip)) } }
        }
    }
}

@Composable
private fun AdjustChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, enabled = enabled,
        modifier = Modifier.height(44.dp))
}

@Composable
private fun <T> ChoiceSheet(title: Int, subtitle: Int, options: List<T>, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEach { o -> FilledTonalButton(onClick = { onPick(o) }, modifier = Modifier.height(52.dp)) { Text(label(o)) } }
            }
        }
    }
}

@Composable
private fun ChangePlanSheet(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.change_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.change_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(text, { text = it.take(400) }, Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.change_hint)) }, minLines = 2)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { onSubmit(text) }, enabled = text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null)
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_preview_changes))
            }
        }
    }
}

@Composable
private fun ChatSheet(messages: List<ChatMessage>, busy: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.chat_scope_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty()) item { Text(stringResource(R.string.chat_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(messages) { m ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                        Surface(
                            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(0.85f),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(m.text, style = MaterialTheme.typography.bodyMedium)
                                m.route?.let { Spacer(Modifier.height(6.dp)); AiBadge(it) }
                            }
                        }
                    }
                }
                if (busy) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(500) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.chat_hint)) }, maxLines = 3)
                IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send)) }
            }
        }
    }
}
