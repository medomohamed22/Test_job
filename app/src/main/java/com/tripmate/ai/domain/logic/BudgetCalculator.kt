package com.tripmate.ai.domain.logic

import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.ExpenseCategory
import com.tripmate.ai.domain.model.TripPlan
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class BudgetSummary(
    val currency: String,
    val budget: Double,
    val spent: Double,
    val remaining: Double,
    val dayCount: Int,
    val dailyLimit: Double,
    val spentToday: Double,
    val remainingToday: Double,
    val byCategory: Map<ExpenseCategory, Double>,
    val byDay: Map<LocalDate, Double>,
    /** Expenses logged in a different currency are excluded from totals: no exchange rates are integrated. */
    val otherCurrencyCount: Int,
    val plannedEstimates: Double,
    val plannedEstimateCurrency: String?,
) {
    val isOverBudget: Boolean get() = spent > budget
    val isOverToday: Boolean get() = spentToday > dailyLimit
    val fractionUsed: Float get() = if (budget <= 0) 0f else (spent / budget).toFloat().coerceIn(0f, 1.5f)
}

object BudgetCalculator {
    fun summarize(plan: TripPlan, expenses: List<Expense>, today: LocalDate): BudgetSummary {
        val same = expenses.filter { it.currency == plan.currency && it.amount.isFinite() && it.amount >= 0 }
        val spent = round2(same.sumOf { it.amount })
        val days = (ChronoUnit.DAYS.between(plan.startDate, plan.endDate).toInt() + 1).coerceAtLeast(1)
        val daily = round2(plan.budgetTotal / days)
        val todaySpent = round2(same.filter { it.date == today }.sumOf { it.amount })
        val byCategory = ExpenseCategory.entries.associateWith { c -> round2(same.filter { it.category == c }.sumOf { it.amount }) }
        val byDay = same.groupBy { it.date }.mapValues { (_, v) -> round2(v.sumOf { it.amount }) }.toSortedMap()

        // Planned estimates are only summed when they share one currency — never converted.
        val estimates = plan.allStops.mapNotNull { it.priceEstimate }
        val estCurrency = estimates.map { it.currency }.distinct().singleOrNull()
        val estTotal = if (estCurrency != null) round2(estimates.sumOf { it.amount } * plan.groupSize) else 0.0

        return BudgetSummary(
            currency = plan.currency, budget = plan.budgetTotal, spent = spent,
            remaining = round2(plan.budgetTotal - spent), dayCount = days, dailyLimit = daily,
            spentToday = todaySpent, remainingToday = round2(daily - todaySpent),
            byCategory = byCategory, byDay = byDay,
            otherCurrencyCount = expenses.size - same.size,
            plannedEstimates = estTotal, plannedEstimateCurrency = estCurrency,
        )
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
}
