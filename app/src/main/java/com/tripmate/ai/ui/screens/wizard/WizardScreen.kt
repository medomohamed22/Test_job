package com.tripmate.ai.ui.screens.wizard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.tripmate.ai.ai.AiInferenceCoordinator
import com.tripmate.ai.ai.AiPrompts
import com.tripmate.ai.ai.AiTask
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.repo.newId
import com.tripmate.ai.data.seed.PlaceCatalog
import com.tripmate.ai.domain.ai.AiJson
import com.tripmate.ai.domain.logic.ItineraryGenerator
import com.tripmate.ai.domain.logic.ItineraryValidator
import com.tripmate.ai.domain.logic.TripRequest
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.FoodPreference
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.TransportMode
import com.tripmate.ai.domain.model.WalkingLevel
import com.tripmate.ai.ui.components.AiBadge
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.LoadingState
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

data class WizardState(
    val step: Int = 0,
    val destinationId: String? = null,
    val customDestination: String = "",
    val startDate: LocalDate = LocalDate.now().plusDays(1),
    val days: Int = 3,
    val hoursPerDay: Int = 8,
    val dayStartHour: Int = 9,
    val budget: String = "",
    val currency: String = "EGP",
    val interests: Set<Interest> = setOf(Interest.HISTORY, Interest.FOOD),
    val pace: Pace = Pace.BALANCED,
    val groupSize: Int = 2,
    val transport: TransportMode = TransportMode.TAXI,
    val walking: WalkingLevel = WalkingLevel.MODERATE,
    val food: FoodPreference = FoodPreference.NONE,
    val useAi: Boolean = false,
    val aiAvailable: Boolean = false,
    val cloudConsent: Boolean = false,
    val generating: Boolean = false,
    val notice: Int? = null,
    val noticeRoute: AiRoute? = null,
) {
    val destinationValid get() = destinationId != null || customDestination.isNotBlank()
    val budgetValue get() = budget.toDoubleOrNull()
}

@HiltViewModel
class WizardViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TripRepository,
    private val prefs: UserPreferencesRepository,
    private val ai: AiInferenceCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(WizardState(destinationId = savedState.get<String>("destinationId")))
    val state: StateFlow<WizardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = prefs.current()
            val s = ai.status.value
            _state.update {
                it.copy(currency = p.currency, interests = p.interests, pace = p.pace, walking = p.walking,
                    budget = (p.dailyBudget * it.days).toLong().toString(), cloudConsent = p.cloudAiConsent,
                    aiAvailable = s.firebaseConfigured && s.online)
            }
        }
    }

    fun update(block: (WizardState) -> WizardState) = _state.update(block)
    fun grantConsent() = viewModelScope.launch { prefs.setCloudConsent(true); _state.update { it.copy(cloudConsent = true, useAi = true) } }

    fun generate(onCreated: (String) -> Unit) {
        val s = _state.value
        if (!s.destinationValid || s.budgetValue == null) return
        _state.update { it.copy(generating = true, notice = null) }
        viewModelScope.launch {
            val ar = repo.isArabic()
            val dest = s.destinationId?.let { PlaceCatalog.destination(it) }
            val request = TripRequest(
                destinationId = dest?.id ?: "custom", destinationName = dest?.name(ar) ?: s.customDestination.trim(),
                startDate = s.startDate, endDate = s.startDate.plusDays(s.days - 1L), dayStartMinutes = s.dayStartHour * 60,
                hoursPerDay = s.hoursPerDay, currency = s.currency, budget = s.budgetValue ?: 0.0, interests = s.interests,
                pace = s.pace, groupSize = s.groupSize, transport = s.transport, walking = s.walking, food = s.food, locale = if (ar) "ar" else "en",
            )
            val generator = ItineraryGenerator(::newId)
            val labels = repo.generatorLabels()
            val baseline = generator.generate(request, PlaceCatalog.places, labels, ar, System.currentTimeMillis())
            var plan = baseline
            var notice: Int? = null
            var route: AiRoute? = null
            if (s.useAi && s.cloudConsent) {
                val result = ai.generate(AiTask.ITINERARY_JSON, AiPrompts.itinerary(request, AiPrompts.placesFor(request.destinationId), ar), json = true)
                route = result.info.route
                val draft = if (result.info.route == AiRoute.CLOUD || result.info.route == AiRoute.ON_DEVICE) AiJson.parseDraft(result.text) else null
                val applied = draft?.let { AiJson.applyDraft(it, baseline, request, PlaceCatalog.places, labels, ar, generator, ::newId) }
                if (applied != null) plan = applied.copy(generatedBy = "cloud-ai")
                else notice = if (draft == null && result.text.isNotBlank()) R.string.wizard_ai_invalid else R.string.wizard_ai_unavailable
            }
            if (ItineraryValidator.hasErrors(plan)) { plan = baseline; notice = R.string.wizard_ai_invalid }
            if (notice != null) plan = plan.copy(generatedBy = "fallback")
            repo.save(plan)
            _state.update { it.copy(generating = false, notice = notice, noticeRoute = route) }
            onCreated(plan.id)
        }
    }
}

private const val STEPS = 6

@Composable
fun WizardScreen(initialDestination: String?, onBack: () -> Unit, onCreated: (String) -> Unit, vm: WizardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialDestination) { if (initialDestination != null && s.destinationId == null) vm.update { it.copy(destinationId = initialDestination) } }
    var consentDialog by remember { mutableStateOf(false) }
    if (s.generating) { LoadingState(stringResource(if (s.useAi) R.string.wizard_generating_ai else R.string.wizard_generating)); return }

    val canNext = when (s.step) { 0 -> s.destinationValid; 2 -> s.budgetValue != null && (s.budgetValue ?: -1.0) >= 0; else -> true }
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.wizard_title), onBack = { if (s.step > 0) vm.update { it.copy(step = it.step - 1) } else onBack() }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wizard_step_of, s.step + 1, STEPS), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (s.step < STEPS - 1) Button(onClick = { vm.update { it.copy(step = it.step + 1) } }, enabled = canNext) { Text(stringResource(R.string.action_next)) }
                else Button(onClick = { vm.generate(onCreated) }, enabled = s.destinationValid && s.budgetValue != null) {
                    Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.wizard_generate))
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LinearProgressIndicator(progress = { (s.step + 1) / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            AnimatedContent(s.step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "wiz") { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    when (step) {
                        0 -> DestinationStep(s, vm)
                        1 -> DatesStep(s, vm)
                        2 -> BudgetStep(s, vm)
                        3 -> InterestsStep(s, vm)
                        4 -> GroupStep(s, vm)
                        else -> AccessStep(s, vm, onRequestConsent = { consentDialog = true })
                    }
                }
            }
        }
    }
    if (consentDialog) CloudConsentDialog(onAccept = { consentDialog = false; vm.grantConsent() }, onDismiss = { consentDialog = false })
}

@Composable
fun CloudConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AutoAwesome, null) },
        title = { Text(stringResource(R.string.consent_title)) },
        text = { Text(stringResource(R.string.consent_body)) },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.consent_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.consent_decline)) } },
    )
}

@Composable private fun StepTitle(title: Int, subtitle: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(18.dp))
}

@Composable private fun DestinationStep(s: WizardState, vm: WizardViewModel) {
    val ar = LocalConfiguration.current.locales[0].language == "ar"
    StepTitle(R.string.wizard_dest_title, R.string.wizard_dest_sub)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlaceCatalog.destinations.forEach { d ->
            FilterChip(s.destinationId == d.id, { vm.update { it.copy(destinationId = d.id, customDestination = "") } }, { Text(d.name(ar)) })
        }
    }
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        s.customDestination, { v -> vm.update { it.copy(customDestination = v.take(60), destinationId = null) } },
        Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wizard_dest_other)) }, singleLine = true,
    )
    if (s.destinationId == null && s.customDestination.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        InfoBanner(stringResource(R.string.wizard_custom_note), warning = true)
    }
}

@Composable private fun DatesStep(s: WizardState, vm: WizardViewModel) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    StepTitle(R.string.wizard_dates_title, R.string.wizard_dates_sub)
    OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.wizard_start_date, Formatters.date(context, s.startDate)))
    }
    Spacer(Modifier.height(18.dp))
    Text(pluralStringResource(R.plurals.wizard_days, s.days, s.days), style = MaterialTheme.typography.titleMedium)
    Slider(s.days.toFloat(), { v -> vm.update { it.copy(days = v.toInt()) } }, valueRange = 1f..7f, steps = 5)
    Text(stringResource(R.string.wizard_end_date, Formatters.date(context, s.startDate.plusDays(s.days - 1L))), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(18.dp))
    Text(pluralStringResource(R.plurals.wizard_hours, s.hoursPerDay, s.hoursPerDay), style = MaterialTheme.typography.titleMedium)
    Slider(s.hoursPerDay.toFloat(), { v -> vm.update { it.copy(hoursPerDay = v.toInt()) } }, valueRange = 4f..12f, steps = 7)
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.wizard_day_start), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 8, 9, 10, 11).forEach { h -> FilterChip(s.dayStartHour == h, { vm.update { it.copy(dayStartHour = h) } }, { Text(Formatters.time(context, h * 60)) }) }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = s.startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms -> vm.update { it.copy(startDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) } }
                    picker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable private fun BudgetStep(s: WizardState, vm: WizardViewModel) {
    StepTitle(R.string.wizard_budget_title, R.string.wizard_budget_sub)
    OutlinedTextField(
        s.budget, { v -> vm.update { it.copy(budget = v.filter { c -> c.isDigit() || c == '.' }.take(10)) } }, Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.wizard_total_budget)) }, singleLine = true, isError = s.budgetValue == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = { if (s.budgetValue == null) Text(stringResource(R.string.error_number)) },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Formatters.currencies.forEach { c -> FilterChip(s.currency == c, { vm.update { it.copy(currency = c) } }, { Text(c) }) }
    }
    Spacer(Modifier.height(12.dp))
    InfoBanner(stringResource(R.string.wizard_currency_note))
}

@Composable private fun InterestsStep(s: WizardState, vm: WizardViewModel) {
    StepTitle(R.string.wizard_interests_title, R.string.wizard_interests_sub)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Interest.entries.forEach { i ->
            val on = i in s.interests
            FilterChip(on, { vm.update { it.copy(interests = if (on) it.interests - i else it.interests + i) } }, { Text(stringResource(i.labelRes())) },
                leadingIcon = if (on) { { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) } } else null)
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.prefs_pace), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Pace.entries.forEachIndexed { idx, p ->
            SegmentedButton(s.pace == p, { vm.update { it.copy(pace = p) } }, SegmentedButtonDefaults.itemShape(idx, Pace.entries.size)) { Text(stringResource(p.labelRes())) }
        }
    }
}

@Composable private fun GroupStep(s: WizardState, vm: WizardViewModel) {
    StepTitle(R.string.wizard_group_title, R.string.wizard_group_sub)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { vm.update { it.copy(groupSize = (it.groupSize - 1).coerceAtLeast(1)) } }) { Icon(Icons.Filled.Remove, stringResource(R.string.cd_decrease)) }
        Text(pluralStringResource(R.plurals.wizard_travellers, s.groupSize, s.groupSize), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        FilledTonalIconButton(onClick = { vm.update { it.copy(groupSize = (it.groupSize + 1).coerceAtMost(20)) } }) { Icon(Icons.Filled.Add, stringResource(R.string.cd_increase)) }
    }
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.wizard_transport), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TransportMode.entries.forEach { t -> FilterChip(s.transport == t, { vm.update { it.copy(transport = t) } }, { Text(stringResource(t.labelRes())) }) }
    }
}

@Composable private fun AccessStep(s: WizardState, vm: WizardViewModel, onRequestConsent: () -> Unit) {
    StepTitle(R.string.wizard_access_title, R.string.wizard_access_sub)
    Text(stringResource(R.string.prefs_walking), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        WalkingLevel.entries.forEachIndexed { idx, w ->
            SegmentedButton(s.walking == w, { vm.update { it.copy(walking = w) } }, SegmentedButtonDefaults.itemShape(idx, WalkingLevel.entries.size)) { Text(stringResource(w.labelRes())) }
        }
    }
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.wizard_food), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodPreference.entries.forEach { f -> FilterChip(s.food == f, { vm.update { it.copy(food = f) } }, { Text(stringResource(f.labelRes())) }) }
    }
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.wizard_use_ai), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (s.aiAvailable) R.string.wizard_use_ai_sub else R.string.wizard_ai_not_available), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(s.useAi, { on -> if (on && !s.cloudConsent) onRequestConsent() else vm.update { it.copy(useAi = on) } }, enabled = s.aiAvailable)
    }
    if (!s.aiAvailable) { Spacer(Modifier.height(8.dp)); AiBadge(AiRoute.DEMO) }
    Spacer(Modifier.height(16.dp))
    InfoBanner(stringResource(R.string.wizard_estimates_note))
}
