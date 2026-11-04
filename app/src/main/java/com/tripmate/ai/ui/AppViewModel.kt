package com.tripmate.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.ai.AiInferenceCoordinator
import com.tripmate.ai.data.prefs.UserPreferences
import com.tripmate.ai.data.prefs.UserPreferencesRepository
import com.tripmate.ai.data.repo.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppState(val loading: Boolean = true, val prefs: UserPreferences = UserPreferences())

@HiltViewModel
class AppViewModel @Inject constructor(
    prefs: UserPreferencesRepository,
    private val trips: TripRepository,
    private val ai: AiInferenceCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { trips.seedSampleIfNeeded() }
            prefs.preferences.collect { p -> _state.update { it.copy(loading = false, prefs = p) } }
        }
        viewModelScope.launch { runCatching { ai.refreshOnDeviceStatus() } }
    }
}
