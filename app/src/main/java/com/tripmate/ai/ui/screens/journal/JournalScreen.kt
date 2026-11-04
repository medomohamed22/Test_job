package com.tripmate.ai.ui.screens.journal

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.data.repo.newId
import com.tripmate.ai.domain.logic.BudgetCalculator
import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.JournalEntry
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.EmptyState
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.LoadingState
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.StatTile
import com.tripmate.ai.util.Formatters
import com.tripmate.ai.util.ImageStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class JournalUi(val trip: TripPlan, val entries: List<JournalEntry>, val expenses: List<Expense>)

@HiltViewModel
class JournalViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: TripRepository, val images: ImageStore) : ViewModel() {
    private val tripId: String = checkNotNull(savedState["tripId"])
    val state: StateFlow<JournalUi?> = combine(repo.observeTrip(tripId), repo.observeJournal(tripId), repo.observeExpenses(tripId)) { t, j, e ->
        t?.let { JournalUi(it, j, e) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(text: String, photo: android.net.Uri?) = viewModelScope.launch {
        val path = photo?.let { images.importPhoto(it) }
        repo.saveJournal(JournalEntry(newId(), tripId, System.currentTimeMillis(), text.trim().take(4000), path))
    }
    fun delete(e: JournalEntry) = viewModelScope.launch { repo.deleteJournal(e) }
}

@Composable
private fun Thumb(path: String, images: ImageStore) {
    val bmp by produceState<Bitmap?>(null, path) { value = images.loadThumbnail(path) }
    bmp?.let { Image(it.asImageBitmap(), stringResource(R.string.cd_journal_photo), Modifier.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop) }
}

@Composable
fun JournalScreen(tripId: String, onBack: () -> Unit, vm: JournalViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val state = ui ?: run { LoadingState(stringResource(R.string.loading)); return }
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    val fmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Formatters.locale(context)) }
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.journal_title), onBack) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.journal_add)) }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { InfoBanner(stringResource(R.string.journal_private)) }
            if (state.entries.isEmpty()) item { EmptyState(Icons.Filled.AutoStories, stringResource(R.string.journal_empty_title), stringResource(R.string.journal_empty_body)) }
            items(state.entries, key = { it.id }) { e ->
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(fmt), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.delete(e) }) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete)) }
                        }
                        e.photoPath?.let { Thumb(it, vm.images); Spacer(Modifier.height(8.dp)) }
                        if (e.text.isNotBlank()) Text(e.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    if (adding) AddEntryDialog(onDismiss = { adding = false }) { t, uri -> vm.add(t, uri); adding = false }
}

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onSave: (String, android.net.Uri?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it ?: photo }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.journal_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 4, placeholder = { Text(stringResource(R.string.journal_hint)) })
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (photo == null) R.string.journal_add_photo else R.string.journal_photo_selected))
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(text, photo) }, enabled = text.isNotBlank() || photo != null) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/* ---------------- Trip summary ---------------- */

@Composable
fun SummaryScreen(tripId: String, onBack: () -> Unit, vm: JournalViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val state = ui ?: run { LoadingState(stringResource(R.string.loading)); return }
    val context = LocalContext.current
    val plan = state.trip
    val done = plan.allStops.filter { it.status == StopStatus.DONE && (it.category == StopCategory.SIGHT || it.category == StopCategory.CUSTOM) }
    val summary = BudgetCalculator.summarize(plan, state.expenses, LocalDate.now())
    var includeSpend by remember { mutableStateOf(false) }
    var includeDiary by remember { mutableStateOf(false) }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.summary_title), onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text(plan.title, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.summary_places), Formatters.number(context, done.size), Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_spent), Formatters.money(context, summary.spent, summary.currency), Modifier.weight(1f))
                    StatTile(stringResource(R.string.summary_entries), Formatters.number(context, state.entries.size), Modifier.weight(1f))
                }
                SectionHeader(stringResource(R.string.summary_completed))
                if (done.isEmpty()) Text(stringResource(R.string.summary_none), style = MaterialTheme.typography.bodyMedium)
                done.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 2.dp)) }
                if (state.entries.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.summary_highlights))
                    state.entries.take(3).forEach { e -> if (e.text.isNotBlank()) Text("“${e.text.take(160)}”", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
                }
                SectionHeader(stringResource(R.string.summary_share))
                Text(stringResource(R.string.summary_share_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.summary_include_spend), Modifier.weight(1f)); Switch(includeSpend, { includeSpend = it }) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.summary_include_diary), Modifier.weight(1f)); Switch(includeDiary, { includeDiary = it }) }
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    val text = buildString {
                        appendLine(context.getString(R.string.share_header, plan.title))
                        appendLine(context.resources.getQuantityString(R.plurals.share_places, done.size, done.size))
                        done.forEach { appendLine("• ${it.title}") }
                        if (includeSpend) appendLine(context.getString(R.string.share_spent, Formatters.money(context, summary.spent, summary.currency)))
                        if (includeDiary) state.entries.take(2).filter { it.text.isNotBlank() }.forEach { appendLine("“${it.text.take(140)}”") }
                        append(context.getString(R.string.share_footer))
                    }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.action_share_summary)) }
            }
        }
    }
}
