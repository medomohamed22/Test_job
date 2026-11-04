package com.tripmate.ai.ui.screens.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tripmate.ai.R
import com.tripmate.ai.data.repo.TripRepository
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.ui.components.EmptyState
import com.tripmate.ai.ui.components.Entrance
import com.tripmate.ai.ui.components.LargeTitleBar
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.ui.screens.home.TripRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripsViewModel @Inject constructor(private val repo: TripRepository) : ViewModel() {
    val trips: StateFlow<List<TripPlan>?> = repo.observeTrips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun addSample() = viewModelScope.launch { repo.save(repo.buildSampleTrip()) }
}

@Composable
fun TripsScreen(onOpen: (String) -> Unit, onContinue: (String) -> Unit, onNew: () -> Unit, vm: TripsViewModel = hiltViewModel()) {
    val trips by vm.trips.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { LargeTitleBar(stringResource(R.string.trips_title), scroll) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.action_plan_trip)) }) },
    ) { pad ->
        val list = trips ?: return@Scaffold
        if (list.isEmpty()) {
            Column(Modifier.padding(pad)) {
                EmptyState(Icons.Filled.Luggage, stringResource(R.string.trips_empty_title), stringResource(R.string.trips_empty_body), actionLabel = stringResource(R.string.trips_add_sample), onAction = vm::addSample)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp)) {
            TripStatus.entries.forEach { status ->
                val group = list.filter { it.status == status }
                if (group.isNotEmpty()) {
                    item(key = "h_$status") { SectionHeader(stringResource(status.labelRes())) }
                    items(group, key = { it.id }) { t ->
                        Entrance {
                            Column {
                                TripRow(t) { if (t.status == TripStatus.ACTIVE) onContinue(t.id) else onOpen(t.id) }
                                if (t.status == TripStatus.ACTIVE) TextButton(onClick = { onOpen(t.id) }) { Text(stringResource(R.string.action_view_itinerary)) }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
            item { TextButton(onClick = vm::addSample) { Text(stringResource(R.string.trips_add_sample), style = MaterialTheme.typography.labelLarge) } }
        }
    }
}
