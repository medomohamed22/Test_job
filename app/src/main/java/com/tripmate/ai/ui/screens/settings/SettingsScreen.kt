package com.tripmate.ai.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.BuildConfig
import com.tripmate.ai.R
import com.tripmate.ai.data.account.AccountRepository
import com.tripmate.ai.data.prefs.ThemeMode
import com.tripmate.ai.data.prefs.UserPreferences
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.domain.model.WalkingLevel
import com.tripmate.ai.ui.components.LargeTitleBar
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.util.DataExporter
import com.tripmate.ai.util.Formatters
import com.tripmate.ai.util.LocaleHelper
import com.tripmate.ai.util.Notifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
    private val repo: TripRepository,
    private val exporter: DataExporter,
    val account: AccountRepository,
    private val notifier: Notifier,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences?> = prefsRepo.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val message = MutableStateFlow<String?>(null)

    fun setLanguage(tag: String) = viewModelScope.launch { prefsRepo.setLanguage(tag); LocaleHelper.apply(tag) }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { prefsRepo.setTheme(t) }
    fun setCurrency(c: String) = viewModelScope.launch { prefsRepo.setCurrency(c) }
    fun setNotifications(on: Boolean) = viewModelScope.launch { prefsRepo.setNotifications(on && notifier.canPost()); if (on) notifier.ensureChannel() }
    fun setReducedMotion(on: Boolean) = viewModelScope.launch { prefsRepo.setReducedMotion(on) }
    fun setConsent(on: Boolean) = viewModelScope.launch { prefsRepo.setCloudConsent(on) }
    fun setWalking(w: WalkingLevel) = viewModelScope.launch { prefsRepo.setWalking(w) }
    fun setDiagnostics(on: Boolean) = viewModelScope.launch { prefsRepo.setDiagnostics(on) }

    fun export(context: Context, uri: android.net.Uri) = viewModelScope.launch {
        val ok = runCatching {
            val json = exporter.exportJson()
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
        }.isSuccess
        message.value = context.getString(if (ok) R.string.export_done else R.string.export_failed)
    }

    fun deleteAll(done: () -> Unit) = viewModelScope.launch { repo.clearAll(); prefsRepo.clearAll(); account.signOut(); done() }

    fun signIn(activity: Activity) = viewModelScope.launch {
        account.signInWithGoogle(activity)
            .onSuccess { message.value = activity.getString(R.string.signed_in_as, it.displayName ?: it.email ?: "") }
            .onFailure { message.value = activity.getString(R.string.sign_in_failed) }
    }
    fun signOut() = account.signOut()
    fun backup(context: Context) = viewModelScope.launch {
        account.backupTrips(repo.allTrips())
            .onSuccess { message.value = context.resources.getQuantityString(R.plurals.backup_done, it, it) }
            .onFailure { message.value = context.getString(R.string.backup_failed) }
    }
}

private fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }

@Composable
fun SettingsScreen(onDiagnostics: () -> Unit, onPrivacy: () -> Unit, onLicenses: () -> Unit, onDataCleared: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val user by vm.account.user.collectAsStateWithLifecycle()
    val msg by vm.message.collectAsStateWithLifecycle()
    val p = prefs ?: return
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(context, it) } }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.setNotifications(granted) }
    LaunchedEffect(msg) { msg?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTitleBar(stringResource(R.string.nav_settings), scroll) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
            item {
                SectionHeader(stringResource(R.string.settings_language))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to R.string.lang_system, "ar" to R.string.lang_arabic, "en" to R.string.lang_english).forEach { (tag, l) ->
                        FilterChip(p.language == tag, { vm.setLanguage(tag) }, { Text(stringResource(l)) })
                    }
                }
                SectionHeader(stringResource(R.string.settings_theme))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (t, l) ->
                        FilterChip(p.theme == t, { vm.setTheme(t) }, { Text(stringResource(l)) })
                    }
                }
                SectionHeader(stringResource(R.string.settings_currency))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Formatters.currencies.forEach { c -> FilterChip(p.currency == c, { vm.setCurrency(c) }, { Text(c) }) }
                }
                Text(stringResource(R.string.settings_currency_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionHeader(stringResource(R.string.settings_accessibility))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WalkingLevel.entries.forEach { w -> FilterChip(p.walking == w, { vm.setWalking(w) }, { Text(stringResource(w.labelRes())) }) }
                }
                ToggleRow(stringResource(R.string.settings_reduced_motion), stringResource(R.string.settings_reduced_motion_sub), p.reducedMotion, vm::setReducedMotion)
                ToggleRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_sub), p.notifications) { on ->
                    if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setNotifications(on)
                }
                SectionHeader(stringResource(R.string.settings_ai))
                ToggleRow(stringResource(R.string.settings_cloud_consent), stringResource(R.string.settings_cloud_consent_sub), p.cloudAiConsent, vm::setConsent)
                NavRow(Icons.Filled.PrivacyTip, stringResource(R.string.privacy_title), onPrivacy)
                if (BuildConfig.SHOW_DIAGNOSTICS || p.diagnosticsEnabled) NavRow(Icons.Filled.Memory, stringResource(R.string.diagnostics_title), onDiagnostics)
                if (!BuildConfig.SHOW_DIAGNOSTICS) ToggleRow(stringResource(R.string.settings_show_diagnostics), null, p.diagnosticsEnabled, vm::setDiagnostics)

                SectionHeader(stringResource(R.string.settings_account))
                if (!vm.account.isAvailable) Text(stringResource(R.string.account_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (user == null) {
                    Text(stringResource(R.string.account_guest), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { context.findActivity()?.let(vm::signIn) }) { Icon(Icons.Filled.AccountCircle, null); Spacer(Modifier.padding(4.dp)); Text(stringResource(R.string.action_sign_in_google)) }
                } else {
                    Text(stringResource(R.string.signed_in_as, user?.displayName ?: user?.email ?: ""), style = MaterialTheme.typography.bodyMedium)
                    NavRow(Icons.Filled.CloudUpload, stringResource(R.string.action_backup)) { vm.backup(context) }
                    NavRow(Icons.Filled.Logout, stringResource(R.string.action_sign_out), vm::signOut)
                }

                SectionHeader(stringResource(R.string.settings_data))
                NavRow(Icons.Filled.Download, stringResource(R.string.action_export)) { exportLauncher.launch("tripmate-export.json") }
                NavRow(Icons.Filled.DeleteForever, stringResource(R.string.action_delete_all)) { confirmDelete = true }
                NavRow(Icons.Filled.Gavel, stringResource(R.string.licenses_title), onLicenses)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.version_line, BuildConfig.VERSION_NAME,
                    stringResource(if (BuildConfig.HAS_FIREBASE_CONFIG) R.string.mode_firebase else R.string.mode_demo),
                    stringResource(if (BuildConfig.MAPS_API_KEY.isNotBlank()) R.string.mode_maps_on else R.string.mode_maps_off)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.delete_all_title)) }, text = { Text(stringResource(R.string.delete_all_body)) },
        confirmButton = { Button(onClick = { confirmDelete = false; vm.deleteAll(onDataCleared) }) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.padding(0.dp),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
