package com.tripmate.ai.ui.screens.guide

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.ai.AiInferenceCoordinator
import com.tripmate.ai.ai.AiPrompts
import com.tripmate.ai.ai.AiTask
import com.tripmate.ai.ai.GuideQuestion
import com.tripmate.ai.ai.GuideService
import com.tripmate.ai.ai.GuideStyle
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.repo.newId
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.model.AiResult
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.ArtMotif
import com.tripmate.ai.domain.model.ChecklistItem
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.ui.components.AiBadge
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.DestinationArt
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.LoadingState
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.VerificationBadge
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuideUi(val style: GuideStyle = GuideStyle.SHORT, val result: AiResult? = null, val loading: Boolean = false, val answerTitle: Int? = null, val photo: Bitmap? = null)

@HiltViewModel
class StopDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TripRepository,
    private val guide: GuideService,
    private val ai: AiInferenceCoordinator,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {
    private val tripId: String = checkNotNull(savedState["tripId"])
    private val stopId: String = checkNotNull(savedState["stopId"])
    val trip: StateFlow<TripPlan?> = repo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _ui = MutableStateFlow(GuideUi())
    val ui: StateFlow<GuideUi> = _ui.asStateFlow()
    val consent = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private suspend fun stop(): Pair<TripPlan, TripStop>? = repo.getTrip(tripId)?.let { p -> p.findStop(stopId)?.let { p to it.second } }
    private val ar get() = repo.isArabic()

    fun loadGuide(style: GuideStyle) = viewModelScope.launch {
        val (plan, s) = stop() ?: return@launch
        _ui.update { it.copy(style = style, loading = true, answerTitle = null) }
        val r = guide.guide(s.placeId, s.title, plan.destinationName, style, ar, prefs.current().cloudAiConsent)
        _ui.update { it.copy(result = r, loading = false) }
    }

    fun ask(q: GuideQuestion, title: Int) = viewModelScope.launch {
        val (plan, s) = stop() ?: return@launch
        _ui.update { it.copy(loading = true, answerTitle = title) }
        val r = guide.question(s.placeId, s.title, plan.destinationName, q, ar, prefs.current().cloudAiConsent)
        _ui.update { it.copy(result = r, loading = false) }
    }

    fun askFree(text: String) = viewModelScope.launch {
        val (plan, s) = stop() ?: return@launch
        _ui.update { it.copy(loading = true, answerTitle = R.string.guide_your_question) }
        val r = guide.ask(s.title, plan.destinationName, text, ar, prefs.current().cloudAiConsent)
        _ui.update { it.copy(result = r, loading = false) }
    }

    /** On-device preferred: summarises the curated/cached passage currently shown. */
    fun summarizeShown() = viewModelScope.launch {
        val text = _ui.value.result?.text?.takeIf { it.isNotBlank() } ?: return@launch
        _ui.update { it.copy(loading = true, answerTitle = R.string.guide_summary) }
        val r = guide.summarize(text, ar)
        _ui.update { it.copy(result = if (r.text.isBlank()) r.copy(text = text) else r, loading = false) }
    }

    fun explainPhoto(bitmap: Bitmap) = viewModelScope.launch {
        val s = stop()?.second
        _ui.update { it.copy(loading = true, answerTitle = R.string.guide_photo_result, photo = bitmap) }
        val r = guide.explainPhoto(bitmap, s?.title, ar)
        _ui.update { it.copy(result = r, loading = false) }
    }

    fun grantConsent() = viewModelScope.launch { prefs.setCloudConsent(true) }

    private fun editStop(block: (TripStop) -> TripStop) = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val (day, _) = plan.findStop(stopId) ?: return@launch
        repo.updateDay(tripId, day.copy(stops = day.stops.map { if (it.id == stopId) block(it) else it }))
    }
    fun toggleItem(id: String) = editStop { s -> s.copy(checklist = s.checklist.map { if (it.id == id) it.copy(done = !it.done) else it }) }
    fun addItem(text: String) = editStop { s -> s.copy(checklist = s.checklist + ChecklistItem(newId(), text.trim().take(120))) }
    fun removeItem(id: String) = editStop { s -> s.copy(checklist = s.checklist.filterNot { it.id == id }) }
    fun saveNotes(text: String) = editStop { it.copy(notes = text.take(2000)) }

    val aiNote = MutableStateFlow<AiResult?>(null)
    val aiBusy = MutableStateFlow(false)

    /** On-device preferred: suggests checklist lines; never replaces existing items. */
    fun suggestChecklist() = viewModelScope.launch {
        val (_, s) = stop() ?: return@launch
        aiBusy.value = true
        val r = ai.generate(if (prefs.current().cloudAiConsent) AiTask.NEXT_STOP_CHECKLIST else AiTask.GUIDE_LOCAL_ONLY,
            AiPrompts.checklist(s.title, com.tripmate.ai.domain.logic.TimeMath.hhmm(s.startMinutes), ar))
        aiBusy.value = false
        aiNote.value = r
        if (r.info.route == AiRoute.ON_DEVICE || r.info.route == AiRoute.CLOUD) {
            val lines = r.text.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.length in 2..120 }.take(5)
            editStop { st -> st.copy(checklist = st.checklist + lines.map { ChecklistItem(newId(), it) }) }
        }
    }

    /** On-device preferred: rewrites the user's note; result shown for approval, not auto-saved. */
    fun polishNote(text: String) = viewModelScope.launch {
        aiBusy.value = true
        val r = ai.generate(if (prefs.current().cloudAiConsent) AiTask.PARAPHRASE_NOTE else AiTask.GUIDE_LOCAL_ONLY, AiPrompts.paraphraseNote(text, ar))
        aiBusy.value = false
        aiNote.value = r
    }
}

@Composable
fun StopDetailScreen(tripId: String, stopId: String, onBack: () -> Unit, vm: StopDetailViewModel = hiltViewModel()) {
    val trip by vm.trip.collectAsStateWithLifecycle()
    val plan = trip ?: run { LoadingState(stringResource(R.string.loading)); return }
    val stop = plan.findStop(stopId)?.second ?: run { LoadingState(stringResource(R.string.loading)); return }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val place = PlaceCatalog.place(stop.placeId)
    val motif = place?.motif ?: when (stop.category) { StopCategory.MEAL -> ArtMotif.MEAL; StopCategory.BREAK -> ArtMotif.BREAK; else -> ArtMotif.CITY }

    Scaffold(topBar = { BackTopBar(stop.title, onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            DestinationArt(motif, Modifier.fillMaxWidth().height(150.dp), contentDescription = stop.title) {
                Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(stop.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text("${Formatters.time(context, stop.startMinutes)} · ${Formatters.duration(context, stop.durationMinutes)}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
                }
            }
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.tab_guide, R.string.tab_checklist, R.string.tab_notes).forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(stringResource(t)) }) }
            }
            AnimatedContent(tab, label = "tab") { t ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (t) {
                        0 -> GuideTab(stop, plan, vm)
                        1 -> ChecklistTab(stop, vm)
                        else -> NotesTab(stop, vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideTab(stop: TripStop, plan: TripPlan, vm: StopDetailViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val prefs by vm.consent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var question by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(stop.id) { if (ui.result == null) vm.loadGuide(GuideStyle.SHORT) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = com.tripmate.ai.util.ImageStore(context.applicationContext).decode(uri, 1024)
            pendingPhoto = bmp
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp -> if (bmp != null) pendingPhoto = bmp }

    // Verified vs unverified facts
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.guide_facts), style = MaterialTheme.typography.titleMedium)
            VerificationBadge(stop.verification)
            if (stop.opensMinutes != null && stop.closesMinutes != null)
                Text(stringResource(R.string.hours_typical, Formatters.time(context, stop.opensMinutes), Formatters.time(context, stop.closesMinutes)), style = MaterialTheme.typography.bodySmall)
            stop.priceEstimate?.let { Text(stringResource(R.string.price_estimate, Formatters.money(context, it.amount, it.currency)), style = MaterialTheme.typography.bodySmall) }
            Text(stringResource(R.string.guide_facts_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SectionHeader(stringResource(R.string.guide_tell_me))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(GuideStyle.SHORT to R.string.style_short, GuideStyle.DETAILED to R.string.style_detailed, GuideStyle.KIDS to R.string.style_kids).forEach { (s, l) ->
            FilterChip(ui.style == s && ui.answerTitle == null, { vm.loadGuide(s) }, { Text(stringResource(l)) })
        }
    }
    Spacer(Modifier.height(10.dp))
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
            ui.answerTitle?.let { Text(stringResource(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(6.dp)) }
            ui.photo?.takeIf { ui.answerTitle == R.string.guide_photo_result }?.let {
                Image(it.asImageBitmap(), stringResource(R.string.cd_your_photo), Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(8.dp))
            }
            if (ui.loading) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.guide_thinking))
            } else {
                val r = ui.result
                if (r == null || r.text.isBlank()) Text(stringResource(R.string.guide_unavailable), style = MaterialTheme.typography.bodyMedium)
                else Text(r.text, style = MaterialTheme.typography.bodyLarge)
                r?.let { Spacer(Modifier.height(10.dp)); AiBadge(it.info.route, latencyMs = it.info.durationMs) }
                if (r?.info?.route == AiRoute.CLOUD || r?.info?.route == AiRoute.ON_DEVICE) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ai_generated_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = vm::summarizeShown, enabled = !ui.loading && (ui.result?.text?.length ?: 0) > 120) {
        Icon(Icons.Filled.Summarize, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.guide_summarize_local))
    }
    SectionHeader(stringResource(R.string.guide_quick_questions))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            GuideQuestion.LOOK_FOR to R.string.q_look_for, GuideQuestion.HISTORY to R.string.q_history,
            GuideQuestion.ARCHITECTURE to R.string.q_architecture, GuideQuestion.CUSTOMS to R.string.q_customs, GuideQuestion.TIPS to R.string.q_tips,
        ).forEach { (q, l) -> AssistChip(onClick = { vm.ask(q, l) }, label = { Text(stringResource(l)) }) }
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(question, { question = it.take(300) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.guide_ask_hint)) }, maxLines = 3)
        IconButton(onClick = { vm.askFree(question); question = "" }, enabled = question.isNotBlank() && !ui.loading) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_send)) }
    }
    if (prefs?.cloudAiConsent == false) { Spacer(Modifier.height(8.dp)); InfoBanner(stringResource(R.string.guide_no_consent_note)) }
    SectionHeader(stringResource(R.string.guide_photo))
    Text(stringResource(R.string.guide_photo_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { camera.launch(null) }) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_camera)) }
        OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.action_gallery))
        }
    }
    Spacer(Modifier.height(24.dp))

    pendingPhoto?.let { bmp ->
        AlertDialog(
            onDismissRequest = { pendingPhoto = null },
            icon = { Icon(Icons.Filled.AutoAwesome, null) },
            title = { Text(stringResource(R.string.photo_consent_title)) },
            text = { Text(stringResource(R.string.photo_consent_body)) },
            confirmButton = { Button(onClick = { pendingPhoto = null; vm.grantConsent(); vm.explainPhoto(bmp) }) { Text(stringResource(R.string.photo_consent_send)) } },
            dismissButton = { TextButton(onClick = { pendingPhoto = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ChecklistTab(stop: TripStop, vm: StopDetailViewModel) {
    var newItem by remember { mutableStateOf("") }
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val note by vm.aiNote.collectAsStateWithLifecycle()
    Text(stringResource(R.string.checklist_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    if (stop.checklist.isEmpty()) Text(stringResource(R.string.checklist_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    stop.checklist.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(item.done, { vm.toggleItem(item.id) })
            Text(item.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { vm.removeItem(item.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(newItem, { newItem = it.take(120) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.checklist_add_hint)) }, singleLine = true)
        IconButton(onClick = { vm.addItem(newItem); newItem = "" }, enabled = newItem.isNotBlank()) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = vm::suggestChecklist, enabled = !busy) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null)
        Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.checklist_suggest))
    }
    note?.let { Spacer(Modifier.height(8.dp)); AiBadge(it.info.route, latencyMs = it.info.durationMs)
        if (it.info.route == AiRoute.UNAVAILABLE || it.info.route == AiRoute.OFFLINE_CONTENT || it.info.route == AiRoute.DEMO) {
            Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.ai_local_unavailable), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NotesTab(stop: TripStop, vm: StopDetailViewModel) {
    var text by rememberSaveable(stop.id) { mutableStateOf(stop.notes) }
    val busy by vm.aiBusy.collectAsStateWithLifecycle()
    val note by vm.aiNote.collectAsStateWithLifecycle()
    Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(text, { text = it.take(2000) }, Modifier.fillMaxWidth(), minLines = 5, placeholder = { Text(stringResource(R.string.notes_hint)) })
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { vm.saveNotes(text) }, enabled = text != stop.notes) { Text(stringResource(R.string.action_save)) }
        OutlinedButton(onClick = { vm.polishNote(text) }, enabled = text.isNotBlank() && !busy) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.AutoAwesome, null)
            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.notes_polish))
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.notes_private), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    note?.let { r ->
        Spacer(Modifier.height(12.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                AiBadge(r.info.route, latencyMs = r.info.durationMs)
                Spacer(Modifier.height(8.dp))
                if (r.text.isBlank() || r.info.route !in setOf(AiRoute.ON_DEVICE, AiRoute.CLOUD)) Text(stringResource(R.string.ai_local_unavailable), style = MaterialTheme.typography.bodyMedium)
                else {
                    Text(r.text, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { text = r.text }) { Text(stringResource(R.string.action_use_this)) }
                }
            }
        }
    }
}
