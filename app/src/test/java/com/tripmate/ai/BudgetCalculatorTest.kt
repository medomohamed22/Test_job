package com.tripmate.ai

import com.tripmate.ai.domain.logic.BudgetCalculator
import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {
    private val today = TestData.date
    private fun e(amount: Double, cat: ExpenseCategory, currency: String = "EGP", daysAgo: Long = 0) =
        Expense(TestData.id(), "trip-1", today.minusDays(daysAgo), amount, currency, cat)

    @Test fun `totals, remaining and categories are accurate`() {
        val plan = TestData.plan().copy(budgetTotal = 3000.0)
        val s = BudgetCalculator.summarize(plan, listOf(e(100.10, ExpenseCategory.FOOD), e(200.20, ExpenseCategory.FOOD), e(500.0, ExpenseCategory.TICKETS)), today)
        assertEquals(800.30, s.spent, 0.001)
        assertEquals(2199.70, s.remaining, 0.001)
        assertEquals(300.30, s.byCategory[ExpenseCategory.FOOD]!!, 0.001)
        assertEquals(500.0, s.byCategory[ExpenseCategory.TICKETS]!!, 0.001)
        assertEquals(0.0, s.byCategory[ExpenseCategory.LODGING]!!, 0.001)
        assertFalse(s.isOverBudget)
    }

    @Test fun `daily limit divides budget across trip days`() {
        val plan = TestData.plan(listOf(TestData.day(), TestData.day().copy(dayIndex = 1, date = today.plusDays(1)), TestData.day().copy(dayIndex = 2, date = today.plusDays(2))))
            .copy(budgetTotal = 3000.0)
        val s = BudgetCalculator.summarize(plan, listOf(e(1200.0, ExpenseCategory.SHOPPING), e(50.0, ExpenseCategory.FOOD, daysAgo = 1)), today)
        assertEquals(3, s.dayCount)
        assertEquals(1000.0, s.dailyLimit, 0.001)
        assertEquals(1200.0, s.spentToday, 0.001)
        assertTrue(s.isOverToday)
        assertEquals(-200.0, s.remainingToday, 0.001)
    }

    @Test fun `other currencies are excluded, never converted`() {
        val s = BudgetCalculator.summarize(TestData.plan(), listOf(e(100.0, ExpenseCategory.FOOD), e(20.0, ExpenseCategory.FOOD, currency = "USD")), today)
        assertEquals(100.0, s.spent, 0.001)
        assertEquals(1, s.otherCurrencyCount)
    }

    @Test fun `over budget and invalid amounts`() {
        val s = BudgetCalculator.summarize(TestData.plan().copy(budgetTotal = 100.0), listOf(e(150.0, ExpenseCategory.OTHER), e(-50.0, ExpenseCategory.OTHER), e(Double.NaN, ExpenseCategory.OTHER)), today)
        assertEquals(150.0, s.spent, 0.001)
        assertTrue(s.isOverBudget)
    }

    @Test fun `planned estimates sum only within one currency and scale by group`() {
        val d = TestData.day()
        val day = d.copy(stops = listOf(TestData.withPrice(d.stops[0], 700.0), TestData.withPrice(d.stops[1], 300.0)))
        val s = BudgetCalculator.summarize(TestData.plan(listOf(day)).copy(groupSize = 2), emptyList(), today)
        assertEquals("EGP", s.plannedEstimateCurrency)
        assertEquals(2000.0, s.plannedEstimates, 0.001)
        val mixed = d.copy(stops = listOf(TestData.withPrice(d.stops[0], 700.0), TestData.withPrice(d.stops[1], 10.0, "USD")))
        assertNull(BudgetCalculator.summarize(TestData.plan(listOf(mixed)), emptyList(), today).plannedEstimateCurrency)
    }
}
