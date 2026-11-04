package com.tripmate.ai.ui.screens.active

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.ai.AiInferenceCoordinator
import com.tripmate.ai.ai.AiPrompts
import com.tripmate.ai.ai.AiTask
import com.tripmate.ai.ai.NetworkMonitor
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.data.remote.PlacesRoutesClient
import com.tripmate.ai.data.repo.ProposalHolder
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.repo.newId
import com.tripmate.ai.domain.ai.AiJson
import com.tripmate.ai.domain.ai.PlanIntent
import com.tripmate.ai.domain.logic.Geo
import com.tripmate.ai.domain.logic.ScheduleEngine
import com.tripmate.ai.domain.model.AiResult
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.ReplanProposal
import com.tripmate.ai.domain.model.ReplanReason
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripEventType
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.util.Formatters
import com.tripmate.ai.util.LocationHelper
import com.tripmate.ai.util.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class CheckInPrompt(val stopId: String, val title: String, val distanceMeters: Int?, val located: Boolean)

data class ActiveUiState(
    val nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    val selectedDay: Int? = null,
    val checkIn: CheckInPrompt? = null,
    val locating: Boolean = false,
    val message: Int? = null,
    val messageArg: String? = null,
    val chat: List<ChatMessage> = emptyList(),
    val chatBusy: Boolean = false,
    val changeBusy: Boolean = false,
    val routeRefreshing: Boolean = false,
    val online: Boolean = true,
)

data class ChatMessage(val fromUser: Boolean, val text: String, val route: AiRoute? = null)

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TripRepository,
    private val proposals: ProposalHolder,
    private val ai: AiInferenceCoordinator,
    private val location: LocationHelper,
    private val routes: PlacesRoutesClient,
    private val notifier: Notifier,
    private val prefs: UserPreferencesRepository,
    network: NetworkMonitor,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val tripId: String = checkNotNull(savedState["tripId"])
    val trip: StateFlow<TripPlan?> = repo.observeTrip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val _ui = MutableStateFlow(ActiveUiState(online = network.onlineNow()))
    val ui: StateFlow<ActiveUiState> = _ui.asStateFlow()
    val routesConfigured get() = routes.isConfigured

    init {
        viewModelScope.launch { while (isActive) { _ui.update { it.copy(nowMinutes = nowMinutes()) }; delay(30_000) } }
        viewModelScope.launch { network.online.collect { o -> _ui.update { it.copy(online = o) } } }
    }

    private fun nowMinutes() = LocalTime.now().let { it.hour * 60 + it.minute }

    /** Today's day if the trip covers today, otherwise the first day with remaining stops. */
    fun currentDay(plan: TripPlan): TripDay? {
        _ui.value.selectedDay?.let { sel -> plan.day(sel)?.let { return it } }
        return plan.days.firstOrNull { it.date == LocalDate.now() }
            ?: plan.days.firstOrNull { it.currentStop != null } ?: plan.days.lastOrNull()
    }

    fun selectDay(index: Int) = _ui.update { it.copy(selectedDay = index) }
    fun dismissMessage() = _ui.update { it.copy(message = null, messageArg = null) }
    private fun message(res: Int, arg: String? = null) = _ui.update { it.copy(message = res, messageArg = arg) }

    private fun mutateDay(block: (TripDay) -> TripDay, event: TripEventType, stopId: String?) = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val day = currentDay(plan) ?: return@launch
        try {
            val updated = block(day)
            repo.updateDay(tripId, updated)
            repo.logEvent(tripId, event, stopId)
            if (event == TripEventType.FINISHED) afterFinish(plan, updated)
        } catch (e: ScheduleEngine.IllegalTransition) {
            message(R.string.msg_finish_current_first)
        }
    }

    // ---- Check-in: GPS is only a hint; the user always confirms. ----
    fun requestCheckIn(stopId: String) = viewModelScope.launch {
        val plan = trip.value ?: return@launch
        val stop = plan.findStop(stopId)?.second ?: return@launch
        if (!location.hasPermission()) { _ui.update { it.copy(checkIn = CheckInPrompt(stop.id, stop.title, null, false)) }; return@launch }
        _ui.update { it.copy(locating = true) }
        val here = location.currentLocation()
        val dist = if (here != null && stop.lat != null && stop.lng != null) (Geo.distanceKm(here.lat, here.lng, stop.lat, stop.lng) * 1000).toInt() else null
        _ui.update { it.copy(locating = false, checkIn = CheckInPrompt(stop.id, stop.title, dist, here != null)) }
    }
    fun cancelCheckIn() = _ui.update { it.copy(checkIn = null) }
    fun confirmArrived(stopId: String) {
        _ui.update { it.copy(checkIn = null) }
        mutateDay({ ScheduleEngine.markArrived(it, stopId, nowMinutes()) }, TripEventType.ARRIVED, stopId)
    }
    fun startExploring(stopId: String) = mutateDay({ ScheduleEngine.markExploring(it, stopId, nowMinutes()) }, TripEventType.EXPLORING, stopId)
    fun finish(stopId: String) = mutateDay({ ScheduleEngine.markFinished(it, stopId, nowMinutes()) }, TripEventType.FINISHED, stopId)

    private suspend fun afterFinish(plan: TripPlan, day: TripDay) {
        val next = day.stops.firstOrNull { it.status == StopStatus.PENDING }
        if (next != null && prefs.current().notifications) notifier.nextStop(next.title, Formatters.time(context, next.startMinutes))
        val drift = ScheduleEngine.driftMinutes(day, nowMinutes())
        if (drift != null && kotlin.math.abs(drift) >= 15) {
            // Offer (never apply) a re-sync when the real clock has drifted from the plan.
            proposals.set(ScheduleEngine.proposeResync(plan.id, day, nowMinutes()))
            message(if (drift > 0) R.string.msg_behind_offer else R.string.msg_ahead_offer, Formatters.duration(context, kotlin.math.abs(drift)))
        }
        if (day.stops.all { it.status.isFinal } && plan.days.last().dayIndex == day.dayIndex) message(R.string.msg_trip_last_stop_done)
    }

    // ---- Proposals (deterministic; saved only after Apply) ----
    private fun propose(build: (TripPlan, TripDay) -> ReplanProposal, onReady: () -> Unit) = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val day = currentDay(plan) ?: return@launch
        runCatching { build(plan, day) }.onSuccess { proposals.set(it); onReady() }.onFailure { message(R.string.msg_cannot_change) }
    }
    fun reportLate(minutes: Int, onReady: () -> Unit) = propose({ p, d -> ScheduleEngine.proposeDelay(p.id, d, minutes) }, onReady)
    fun skip(stopId: String, onReady: () -> Unit) = propose({ p, d -> ScheduleEngine.proposeSkip(p.id, d, stopId, nowMinutes()) }, onReady)
    fun takeBreak(minutes: Int, onReady: () -> Unit) =
        propose({ p, d -> ScheduleEngine.proposeBreak(p.id, d, minutes, nowMinutes().coerceIn(d.startMinutes, d.endMinutes), newId(), context.getString(R.string.stop_rest_break)) }, onReady)
    fun changePace(pace: Pace, onReady: () -> Unit) = propose({ p, d -> ScheduleEngine.proposePace(p.id, d, p.pace, pace, null) }, onReady)
    fun resync(onReady: () -> Unit) = propose({ p, d -> ScheduleEngine.proposeResync(p.id, d, nowMinutes()) }, onReady)

    /**
     * "Change plan" in natural language: AI only *interprets* the request into an action;
     * the deterministic engine computes the times. Offline, a keyword parser is used instead.
     */
    fun changePlan(text: String, onReady: () -> Unit) = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val day = currentDay(plan) ?: return@launch
        _ui.update { it.copy(changeBusy = true) }
        val consent = prefs.current().cloudAiConsent
        var explanation: String? = null
        var route = AiRoute.OFFLINE_CONTENT
        val intent: PlanIntent = if (consent) {
            val r = ai.generate(AiTask.ADJUST_INTENT_JSON, AiPrompts.adjustIntent(day, text), json = true)
            val parsed = if (r.info.route == AiRoute.CLOUD || r.info.route == AiRoute.ON_DEVICE) AiJson.parseIntent(r.text) else null
            if (parsed != null) { explanation = parsed.explanation?.take(300); route = r.info.route; AiJson.toPlanIntent(parsed, day) }
            else AiJson.keywordIntent(text, day)
        } else AiJson.keywordIntent(text, day)
        _ui.update { it.copy(changeBusy = false) }
        val proposal = runCatching {
            when (intent) {
                is PlanIntent.Delay -> ScheduleEngine.proposeDelay(plan.id, day, intent.minutes)
                is PlanIntent.Skip -> ScheduleEngine.proposeSkip(plan.id, day, intent.stopId, nowMinutes())
                is PlanIntent.Break -> ScheduleEngine.proposeBreak(plan.id, day, intent.minutes, nowMinutes().coerceIn(day.startMinutes, day.endMinutes), newId(), context.getString(R.string.stop_rest_break))
                is PlanIntent.ChangePace -> ScheduleEngine.proposePace(plan.id, day, plan.pace, intent.pace, null)
                PlanIntent.Unknown -> null
            }
        }.getOrNull()
        if (proposal == null) { message(R.string.msg_change_not_understood); return@launch }
        proposals.set(proposal.copy(reason = if (route != AiRoute.OFFLINE_CONTENT) ReplanReason.AI_SUGGESTION else proposal.reason,
            explanation = explanation?.let { "[${context.getString(if (route == AiRoute.ON_DEVICE) R.string.route_on_device else R.string.route_cloud)}] $it" }))
        onReady()
    }

    // ---- Live travel time (Routes API) ----
    fun refreshTravelTime() = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val day = currentDay(plan) ?: return@launch
        val nextIdx = day.stops.indexOfFirst { it.status == StopStatus.PENDING }
        val prev = day.stops.take(nextIdx.coerceAtLeast(0)).lastOrNull { it.status != StopStatus.SKIPPED && it.hasCoordinates }
        val next = day.stops.getOrNull(nextIdx)
        if (prev == null || next == null || !next.hasCoordinates) { message(R.string.msg_route_need_coords); return@launch }
        _ui.update { it.copy(routeRefreshing = true) }
        routes.computeRoute(prev.lat!!, prev.lng!!, next.lat!!, next.lng!!, plan.transport)
            .onSuccess { r ->
                repo.updateDay(tripId, day.copy(stops = day.stops.map { if (it.id == next.id) it.copy(travelMinutesFromPrev = r.durationMinutes, travelSource = TravelSource.ROUTES_API) else it }))
                message(R.string.msg_route_updated, Formatters.duration(context, r.durationMinutes))
            }
            .onFailure { message(R.string.msg_route_failed) }
        _ui.update { it.copy(routeRefreshing = false) }
    }

    // ---- Scoped chat ----
    fun sendChat(text: String) = viewModelScope.launch {
        val plan = repo.getTrip(tripId) ?: return@launch
        val day = currentDay(plan)
        _ui.update { it.copy(chat = it.chat + ChatMessage(true, text), chatBusy = true) }
        val consent = prefs.current().cloudAiConsent
        val result: AiResult = ai.generate(
            if (consent) AiTask.TRIP_CHAT else AiTask.GUIDE_LOCAL_ONLY,
            AiPrompts.tripChat(plan, day, text, repo.isArabic()),
        ) { context.getString(R.string.chat_offline_reply) }
        _ui.update { it.copy(chat = it.chat + ChatMessage(false, result.text.ifBlank { context.getString(R.string.chat_offline_reply) }, result.info.route), chatBusy = false) }
    }

    fun completeTrip(onDone: () -> Unit) = viewModelScope.launch { repo.setStatus(tripId, TripStatus.COMPLETED); onDone() }
}
