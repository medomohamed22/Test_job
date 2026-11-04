package com.tripmate.ai.ui.screens.budget

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.tripmate.ai.domain.logic.BudgetSummary
import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.ExpenseCategory
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.ui.components.BackTopBar
import com.tripmate.ai.ui.components.EmptyState
import com.tripmate.ai.ui.components.InfoBanner
import com.tripmate.ai.ui.components.LoadingState
import com.tripmate.ai.ui.components.SectionHeader
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.StatTile
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.ui.theme.LocalExtendedColors
import com.tripmate.ai.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BudgetUi(val trip: TripPlan, val expenses: List<Expense>, val summary: BudgetSummary)

@HiltViewModel
class BudgetViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: TripRepository) : ViewModel() {
    private val tripId: String = checkNotNull(savedState["tripId"])
    val state: StateFlow<BudgetUi?> = combine(repo.observeTrip(tripId), repo.observeExpenses(tripId)) { t, e ->
        t?.let { BudgetUi(it, e, BudgetCalculator.summarize(it, e, LocalDate.now())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun add(amount: Double, category: ExpenseCategory, note: String, currency: String) = viewModelScope.launch {
        repo.saveExpense(Expense(newId(), tripId, LocalDate.now(), amount, currency, category, note.trim().take(200)))
    }
    fun delete(id: String) = viewModelScope.launch { repo.deleteExpense(id) }
}

@Composable
fun BudgetScreen(tripId: String, onBack: () -> Unit, vm: BudgetViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val state = ui ?: run { LoadingState(stringResource(R.string.loading)); return }
    val context = LocalContext.current
    val s = state.summary
    val ext = LocalExtendedColors.current
    var adding by remember { mutableStateOf(false) }
    fun money(v: Double) = Formatters.money(context, v, s.currency)

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.budget_title), onBack) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_expense)) }) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(stringResource(R.string.budget_spent_of, money(s.spent), money(s.budget)), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { s.fractionUsed.coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = if (s.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (s.isOverBudget) stringResource(R.string.budget_over, money(-s.remaining)) else stringResource(R.string.budget_remaining, money(s.remaining)),
                            style = MaterialTheme.typography.bodyMedium, color = if (s.isOverBudget) MaterialTheme.colorScheme.error else ext.success,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.budget_daily_limit), money(s.dailyLimit), Modifier.weight(1f))
                    StatTile(stringResource(R.string.budget_today), money(s.spentToday), Modifier.weight(1f),
                        accent = if (s.isOverToday) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.budget_left_today), money(s.remainingToday), Modifier.weight(1f))
                    StatTile(stringResource(R.string.budget_planned_est), s.plannedEstimateCurrency?.let { Formatters.money(context, s.plannedEstimates, it) } ?: "—", Modifier.weight(1f),
                        accent = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.height(8.dp))
                InfoBanner(stringResource(R.string.budget_estimate_note))
                if (s.otherCurrencyCount > 0) { Spacer(Modifier.height(8.dp)); InfoBanner(stringResource(R.string.budget_other_currency, s.otherCurrencyCount, s.currency), warning = true) }
                SectionHeader(stringResource(R.string.budget_by_category))
                val max = (s.byCategory.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                s.byCategory.filter { it.value > 0 }.forEach { (cat, v) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(stringResource(cat.labelRes()), Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                        Box(Modifier.weight(1f).height(18.dp).padding(top = 2.dp)) {
                            Box(Modifier.fillMaxWidth((v / max).toFloat()).height(14.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), MaterialTheme.shapes.extraSmall))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(money(v), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (s.byCategory.values.all { it == 0.0 }) Text(stringResource(R.string.budget_no_spend), style = MaterialTheme.typography.bodySmall)
                SectionHeader(stringResource(R.string.budget_expenses))
            }
            if (state.expenses.isEmpty()) item {
                EmptyState(Icons.Filled.Payments, stringResource(R.string.budget_empty_title), stringResource(R.string.budget_empty_body))
            }
            items(state.expenses, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(Formatters.money(context, e.amount, e.currency)) },
                    supportingContent = { Text(listOf(stringResource(e.category.labelRes()), Formatters.date(context, e.date), e.note).filter { it.isNotBlank() }.joinToString(" · ")) },
                    trailingContent = { IconButton(onClick = { vm.delete(e.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete)) } },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        }
    }
    if (adding) AddExpenseDialog(state.trip.currency, onDismiss = { adding = false }) { a, c, n, cur -> vm.add(a, c, n, cur); adding = false }
}

@Composable
fun AddExpenseDialog(defaultCurrency: String, onDismiss: () -> Unit, onSave: (Double, ExpenseCategory, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(defaultCurrency) }
    val value = amount.toDoubleOrNull()
    val valid = value != null && value > 0 && value < 10_000_000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_expense)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text(stringResource(R.string.field_amount)) },
                    singleLine = true, isError = amount.isNotEmpty() && !valid, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { c -> FilterChip(category == c, { category = c }, { Text(stringResource(c.labelRes())) }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Formatters.currencies.forEach { c -> FilterChip(currency == c, { currency = c }, { Text(c) }) }
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text(stringResource(R.string.field_note)) }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(value!!, category, note, currency) }, enabled = valid) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
