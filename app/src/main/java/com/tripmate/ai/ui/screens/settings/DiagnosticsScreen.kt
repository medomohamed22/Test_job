package com.tripmate.ai.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.BuildConfig
import com.tripmate.ai.R
import com.tripmate.ai.ai.AiInferenceCoordinator
import com.tripmate.ai.ai.AiTask
import com.tripmate.ai.domain.model.AiResult
import com.tripmate.ai.domain.model.OnDeviceAvailability
import com.tripmate.ai.ui.components.AiBadge
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.labelRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(private val ai: AiInferenceCoordinator) : ViewModel() {
    val status = ai.status
    val history = ai.history
    val testResult = MutableStateFlow<AiResult?>(null)
    val busy = MutableStateFlow(false)

    fun refresh() = viewModelScope.launch { ai.refreshOnDeviceStatus() }
    fun download() = viewModelScope.launch { ai.downloadOnDeviceModel() }

    /** Runs a tiny ONLY_ON_DEVICE prompt and reports the SDK's real inference source and latency. */
    fun testOnDevice() = viewModelScope.launch {
        busy.value = true
        testResult.value = ai.generate(AiTask.ON_DEVICE_SELF_TEST, "Reply with one short friendly sentence welcoming a traveller to Cairo.")
        busy.value = false
    }
    fun testCloud() = viewModelScope.launch {
        busy.value = true
        testResult.value = ai.generate(AiTask.GUIDE_QUESTION, "Reply with one short sentence: what is the Nile?")
        busy.value = false
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit, vm: DiagnosticsViewModel = hiltViewModel()) {
    val s by vm.status.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val test by vm.testResult.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM, LocalConfiguration.current.locales[0])
    Scaffold(topBar = { BackTopBar(stringResource(R.string.diagnostics_title), onBack) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            item {
                InfoBanner(stringResource(R.string.diag_intro))
                Spacer(Modifier.height(12.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KV(stringResource(R.string.diag_firebase), stringResource(if (s.firebaseConfigured) R.string.yes else R.string.diag_no_demo))
                        KV(stringResource(R.string.diag_network), stringResource(if (s.online) R.string.diag_online else R.string.diag_offline))
                        KV(stringResource(R.string.diag_on_device), stringResource(s.onDevice.labelRes()) + (s.downloadProgressBytes?.let { " (${it / 1_000_000} MB)" } ?: ""))
                        KV(stringResource(R.string.diag_cloud_model), s.cloudModel)
                        KV(stringResource(R.string.diag_maps), stringResource(if (BuildConfig.MAPS_API_KEY.isNotBlank()) R.string.yes else R.string.no))
                        s.lastError?.let { KV(stringResource(R.string.diag_last_error), it) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.diag_refresh)) }
                    if (s.onDevice == OnDeviceAvailability.DOWNLOADABLE) Button(onClick = vm::download) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.diag_download)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::testOnDevice, enabled = !busy) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.diag_test_local)) }
                    OutlinedButton(onClick = vm::testCloud, enabled = !busy) { Text(stringResource(R.string.diag_test_cloud)) }
                }
                if (busy) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator(Modifier.size(24.dp)) }
                test?.let { r ->
                    Spacer(Modifier.height(10.dp))
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            AiBadge(r.info.route, latencyMs = r.info.durationMs)
                            Spacer(Modifier.height(6.dp))
                            Text(r.text.ifBlank { stringResource(R.string.diag_no_output) }, style = MaterialTheme.typography.bodyMedium)
                            r.info.fallbackReason?.let { Text(stringResource(R.string.diag_reason, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            r.info.error?.let { Text(stringResource(R.string.diag_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                SectionHeader(stringResource(R.string.diag_history))
                if (history.isEmpty()) Text(stringResource(R.string.diag_history_empty), style = MaterialTheme.typography.bodySmall)
            }
            items(history) { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.task, style = MaterialTheme.typography.labelLarge)
                        Text(listOfNotNull(fmt.format(Date(h.timestamp)), h.fallbackReason, h.error).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AiBadge(h.route, latencyMs = h.durationMs)
                }
            }
        }
    }
}

@Composable
private fun KV(k: String, v: String) {
    Row { Text(k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)); Text(v, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.privacy_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(R.string.privacy_p1, R.string.privacy_p2, R.string.privacy_p3, R.string.privacy_p4, R.string.privacy_p5, R.string.privacy_p6).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.licenses_title), onBack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.licenses_intro), style = MaterialTheme.typography.bodyLarge)
            listOf(
                "AndroidX, Jetpack Compose, Room, DataStore, Navigation, Hilt — Apache License 2.0",
                "Kotlin, kotlinx.coroutines, kotlinx.serialization — Apache License 2.0",
                "OkHttp — Apache License 2.0",
                "Firebase Android SDKs (AI Logic, Auth, Firestore, App Check) — Apache License 2.0; service terms apply",
                "ML Kit GenAI Prompt API (via firebase-ai-ondevice) — ML Kit & Generative AI terms apply",
                "Google Play services (Maps, Location), Maps Compose — Google APIs Terms of Service / Apache License 2.0",
                "Material Symbols / Material Icons — Apache License 2.0",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Text(stringResource(R.string.licenses_content), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
