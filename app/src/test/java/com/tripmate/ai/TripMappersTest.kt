package com.tripmate.ai

import com.tripmate.ai.data.db.ExpenseEntity
import com.tripmate.ai.data.db.TripWithChildren
import com.tripmate.ai.data.mapper.TripMappers
import com.tripmate.ai.domain.model.ChecklistItem
import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.ExpenseCategory
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.domain.model.Verification
import org.junit.Assert.assertEquals
import org.junit.Test

class TripMappersTest {
    @Test fun `trip round-trips through Room entities`() {
        val d = TestData.day()
        val day = d.copy(stops = d.stops.mapIndexed { i, s ->
            if (i == 0) TestData.withPrice(s, 700.0).copy(status = StopStatus.DONE, actualArrivalMinutes = 545, actualEndMinutes = 660,
                checklist = listOf(ChecklistItem("c1", "Water", true), ChecklistItem("c2", "Ticket")), verification = Verification.SAMPLE, notes = "Great\nview")
            else s
        })
        val plan = TestData.plan(listOf(day, TestData.day().copy(dayIndex = 1, date = TestData.date.plusDays(1), stops = TestData.day().stops.map { it.copy(id = it.id + "2") })))
            .copy(status = TripStatus.ACTIVE, pace = Pace.PACKED, interests = setOf(Interest.HISTORY, Interest.FOOD), createdAt = 1, updatedAt = 2)

        val e = TripMappers.toEntities(plan)
        assertEquals(10, e.stops.size)
        // Shuffle to prove ordering comes from position/dayIndex, not insertion order.
        val restored = TripMappers.toDomain(TripWithChildren(e.trip, e.days.reversed(), e.stops.shuffled(java.util.Random(4))))
        assertEquals(plan, restored)
    }

    @Test fun `unknown enum values fall back safely`() {
        val e = TripMappers.toEntities(TestData.plan())
        val corrupted = e.copy(trip = e.trip.copy(pace = "WARP", status = "??", interests = "HISTORY,NOPE"), stops = e.stops.map { it.copy(status = "BAD", checklistJson = "{not json") })
        val p = TripMappers.toDomain(TripWithChildren(corrupted.trip, corrupted.days, corrupted.stops))
        assertEquals(Pace.BALANCED, p.pace)
        assertEquals(TripStatus.PLANNED, p.status)
        assertEquals(setOf(Interest.HISTORY), p.interests)
        assertEquals(StopStatus.PENDING, p.allStops.first().status)
        assertEquals(emptyList<ChecklistItem>(), p.allStops.first().checklist)
    }

    @Test fun `expense mapping round-trips`() {
        val ex = Expense("e1", "trip-1", TestData.date, 12.5, "EGP", ExpenseCategory.TRANSPORT, "taxi", "A")
        assertEquals(ex, TripMappers.toDomain(TripMappers.toEntity(ex)))
        assertEquals(ExpenseCategory.OTHER, TripMappers.toDomain(ExpenseEntity("e", "t", 0, 1.0, "EGP", "???", "", null)).category)
    }
}
