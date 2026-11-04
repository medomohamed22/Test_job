package com.tripmate.ai.ui.screens.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.data.repo.ProposalHolder
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.domain.model.ChangeType
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.ReplanProposal
import com.tripmate.ai.domain.model.ReplanReason
import com.tripmate.ai.domain.model.StopChange
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripEventType
import com.tripmate.ai.domain.model.WarningCode
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.EmptyState
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.ui.theme.LocalExtendedColors
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProposalViewModel @Inject constructor(private val holder: ProposalHolder, private val repo: TripRepository) : ViewModel() {
    val pending = holder.pending

    /** The ONLY place a re-plan is persisted — after explicit user approval. */
    fun apply(p: ReplanProposal, done: () -> Unit) = viewModelScope.launch {
        repo.updateDay(p.tripId, p.after)
        if (p.reason == ReplanReason.CHANGE_PACE) {
            // Keep the trip's pace consistent with the scaled durations.
            val trip = repo.getTrip(p.tripId)
            val newPace = trip?.let { inferPace(p) }
            if (trip != null && newPace != null) repo.save(trip.copy(pace = newPace))
        }
        repo.logEvent(p.tripId, TripEventType.PLAN_CHANGED, metadata = p.reason.name)
        holder.clear()
        done()
    }
    fun discard(done: () -> Unit) { holder.clear(); done() }

    private fun inferPace(p: ReplanProposal): Pace? {
        val before = p.before.stops.filter { it.status == StopStatus.PENDING }.sumOf { it.durationMinutes }
        val after = p.after.stops.filter { it.status == StopStatus.PENDING }.sumOf { it.durationMinutes }
        return when { after > before -> Pace.RELAXED; after < before -> Pace.PACKED; else -> null }
    }
}

@Composable
fun ProposalScreen(onBack: () -> Unit, vm: ProposalViewModel = hiltViewModel()) {
    val p by vm.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.proposal_title), onBack) },
        bottomBar = {
            p?.takeIf { it.hasChanges }?.let { proposal ->
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.discard(onBack) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_keep_plan)) }
                    Button(onClick = { vm.apply(proposal, onBack) }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.action_apply_changes)) }
                }
            }
        },
    ) { pad ->
        val proposal = p
        if (proposal == null) {
            EmptyState(Icons.Filled.CompareArrows, stringResource(R.string.proposal_none_title), stringResource(R.string.proposal_none_body), Modifier.padding(pad),
                actionLabel = stringResource(R.string.action_back), onAction = onBack)
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                val detail = proposal.reasonDetail?.let { Formatters.duration(context, it) }
                Text(stringResource(proposal.reason.labelRes()) + (detail?.let { " · $it" } ?: ""), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.proposal_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                proposal.explanation?.let { Spacer(Modifier.height(10.dp)); InfoBanner(it) }
                proposal.warnings.forEach { w ->
                    Spacer(Modifier.height(8.dp))
                    val title = w.stopId?.let { id -> proposal.after.stops.firstOrNull { it.id == id }?.title }.orEmpty()
                    val minutes = w.minutes?.let { Formatters.duration(context, it) }.orEmpty()
                    InfoBanner(when (w.code) {
                        WarningCode.DAY_OVERRUN -> stringResource(R.string.warn_overrun, minutes)
                        WarningCode.MAY_CLOSE_BEFORE_VISIT -> stringResource(R.string.warn_closing, title)
                        WarningCode.MEAL_SHIFTED_LATE -> stringResource(R.string.warn_meal_late, title, minutes)
                        WarningCode.NOTHING_TO_CHANGE -> stringResource(R.string.warn_nothing)
                    }, warning = w.code != WarningCode.NOTHING_TO_CHANGE)
                }
                SectionHeader(stringResource(R.string.proposal_changes))
            }
            val changed = proposal.changes.filter { it.type != ChangeType.UNCHANGED }
            items(changed, key = { it.stopId }) { c -> ChangeRow(c); Spacer(Modifier.height(8.dp)) }
            item {
                SectionHeader(stringResource(R.string.proposal_before_after))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ScheduleColumn(stringResource(R.string.proposal_before), proposal.before, Modifier.weight(1f), highlight = false)
                    ScheduleColumn(stringResource(R.string.proposal_after), proposal.after, Modifier.weight(1f), highlight = true)
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.proposal_deterministic_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChangeRow(c: StopChange) {
    val context = LocalContext.current
    val ext = LocalExtendedColors.current
    val (icon, tint, text) = when (c.type) {
        ChangeType.TIME_SHIFTED -> Triple(Icons.Filled.Schedule, MaterialTheme.colorScheme.primary,
            stringResource(R.string.change_shifted, Formatters.time(context, c.oldStart ?: 0), Formatters.time(context, c.newStart ?: 0)))
        ChangeType.SHORTENED -> Triple(Icons.Filled.UnfoldLess, ext.warning,
            stringResource(R.string.change_shortened, Formatters.duration(context, c.oldDuration ?: 0), Formatters.duration(context, c.newDuration ?: 0), Formatters.time(context, c.newStart ?: 0)))
        ChangeType.REMOVED -> Triple(Icons.Filled.RemoveCircle, MaterialTheme.colorScheme.error, stringResource(R.string.change_removed))
        ChangeType.ADDED -> Triple(Icons.Filled.AddCircle, ext.success,
            stringResource(R.string.change_added, Formatters.time(context, c.newStart ?: 0), Formatters.duration(context, c.newDuration ?: 0)))
        ChangeType.UNCHANGED -> Triple(Icons.AutoMirrored.Filled.ArrowForward, MaterialTheme.colorScheme.outline, "")
    }
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(c.title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScheduleColumn(title: String, day: TripDay, modifier: Modifier, highlight: Boolean) {
    val context = LocalContext.current
    SoftCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            day.stops.forEach { s ->
                val skipped = s.status == StopStatus.SKIPPED
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(Formatters.time(context, s.startMinutes), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s.title, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                        textDecoration = if (skipped) TextDecoration.LineThrough else null,
                        color = if (skipped) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(6.dp))
            val end = day.stops.filter { it.status != StopStatus.SKIPPED }.maxOfOrNull { it.endMinutes } ?: day.startMinutes
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ends_at, Formatters.time(context, end)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
