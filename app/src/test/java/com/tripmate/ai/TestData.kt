package com.tripmate.ai

import com.tripmate.ai.domain.logic.GeneratorLabels
import com.tripmate.ai.domain.model.Money
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStop
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

object TestData {
    val labels = GeneratorLabels("Lunch", "Rest", "Water", "Ticket", "Modest")
    private val counter = AtomicInteger()
    fun id(): String = "id-${counter.incrementAndGet()}"
    val date: LocalDate = LocalDate.of(2026, 3, 10)

    fun stop(id: String, start: Int, duration: Int, priority: Int = 2, category: StopCategory = StopCategory.SIGHT, travel: Int = 0, closes: Int? = null) =
        TripStop(id = id, title = "Stop $id", category = category, lat = 30.0, lng = 31.0, startMinutes = start, durationMinutes = duration,
            minDurationMinutes = (duration * 0.6).toInt(), travelMinutesFromPrev = travel, priority = priority, closesMinutes = closes)

    /** 09:00–17:00 day: A(9:00,120,must) B(11:15,90) Lunch(12:45..60) C(14:00,90) D(15:45,60,optional). */
    fun day(): TripDay = TripDay(
        0, date, 9 * 60, 17 * 60,
        listOf(
            stop("A", 540, 120, priority = 3),
            stop("B", 675, 90, travel = 15),
            stop("L", 775, 60, priority = 3, category = StopCategory.MEAL, travel = 10),
            stop("C", 850, 90, travel = 15),
            stop("D", 955, 60, priority = 1, travel = 15),
        ),
    )

    fun plan(days: List<TripDay> = listOf(day())) = TripPlan(
        id = "trip-1", title = "Test", destinationId = "cairo", destinationName = "Cairo", locale = "en",
        startDate = date, endDate = date.plusDays(days.size - 1L), currency = "EGP", budgetTotal = 3000.0, days = days,
    )

    fun withPrice(s: TripStop, amount: Double, currency: String = "EGP") = s.copy(priceEstimate = Money(amount, currency))
}
