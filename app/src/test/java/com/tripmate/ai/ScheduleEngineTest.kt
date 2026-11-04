package com.tripmate.ai

import com.tripmate.ai.domain.logic.ScheduleEngine
import com.tripmate.ai.domain.model.ChangeType
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.ReplanReason
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.WarningCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    @Test fun `reflow lays out pending stops sequentially with travel`() {
        val stops = ScheduleEngine.reflow(TestData.day().stops, 9 * 60)
        assertEquals(540, stops[0].startMinutes)
        assertEquals(540 + 120 + 15, stops[1].startMinutes)
        stops.zipWithNext().forEach { (a, b) -> assertTrue(b.startMinutes >= a.endMinutes + b.travelMinutesFromPrev) }
    }

    @Test fun `reflow keeps finished stops and respects opening time`() {
        val day = TestData.day()
        val done = day.stops.map { if (it.id == "A") it.copy(status = StopStatus.DONE, actualEndMinutes = 600) else it }
            .map { if (it.id == "B") it.copy(opensMinutes = 700) else it }
        val r = ScheduleEngine.reflow(done, day.startMinutes)
        assertEquals(540, r[0].startMinutes) // history untouched
        assertEquals(700, r[1].startMinutes) // waits for opening
    }

    @Test fun `delay of 20 minutes only shifts times`() {
        val day = TestData.day()
        val p = ScheduleEngine.proposeDelay("t", day, 20)
        assertEquals(ReplanReason.RUNNING_LATE, p.reason)
        assertEquals(560, p.after.stops.first { it.id == "A" }.startMinutes)
        assertTrue(p.changes.filter { it.type != ChangeType.UNCHANGED }.all { it.type == ChangeType.TIME_SHIFTED || it.type == ChangeType.SHORTENED })
        assertTrue(p.after.stops.none { it.status == StopStatus.SKIPPED })
    }

    @Test fun `delay of 90 minutes fits the day by shortening or dropping optional stops`() {
        val day = TestData.day()
        val p = ScheduleEngine.proposeDelay("t", day, 90)
        val end = ScheduleEngine.lastEnd(p.after.stops)
        assertTrue("day should end by 17:00 but ends $end", end <= day.endMinutes)
        // Must-see and meal are never dropped
        assertNotEquals(StopStatus.SKIPPED, p.after.stops.first { it.id == "A" }.status)
        assertNotEquals(StopStatus.SKIPPED, p.after.stops.first { it.id == "L" }.status)
        assertTrue(p.changes.any { it.type == ChangeType.SHORTENED || it.type == ChangeType.REMOVED })
        // Durations never go below their minimum
        p.after.stops.forEach { s -> assertTrue(s.durationMinutes >= s.minDurationMinutes) }
    }

    @Test fun `huge delay keeps must-sees and warns about overrun`() {
        val p = ScheduleEngine.proposeDelay("t", TestData.day(), 6 * 60)
        assertTrue(p.warnings.any { it.code == WarningCode.DAY_OVERRUN })
        assertEquals(StopStatus.PENDING, p.after.stops.first { it.id == "A" }.status)
    }

    @Test fun `proposals never mutate the original day`() {
        val day = TestData.day()
        val copy = day.copy()
        ScheduleEngine.proposeDelay("t", day, 90)
        ScheduleEngine.proposeSkip("t", day, "B")
        assertEquals(copy, day)
    }

    @Test fun `skip pulls following stops earlier`() {
        val day = TestData.day()
        val p = ScheduleEngine.proposeSkip("t", day, "B")
        assertEquals(StopStatus.SKIPPED, p.after.stops.first { it.id == "B" }.status)
        assertTrue(p.after.stops.first { it.id == "C" }.startMinutes < day.stops.first { it.id == "C" }.startMinutes)
        assertEquals(ChangeType.REMOVED, p.changes.first { it.stopId == "B" }.type)
    }

    @Test fun `break is inserted before next pending stop`() {
        val day = TestData.day()
        val p = ScheduleEngine.proposeBreak("t", day, 30, 540, "BRK", "Rest")
        val idx = p.after.stops.indexOfFirst { it.id == "BRK" }
        assertEquals(0, idx)
        assertEquals(StopCategory.BREAK, p.after.stops[idx].category)
        assertEquals(ChangeType.ADDED, p.changes.first { it.stopId == "BRK" }.type)
        assertTrue(p.after.stops[1].startMinutes >= 540 + 30)
    }

    @Test fun `pace change scales pending sight durations`() {
        val day = TestData.day()
        val p = ScheduleEngine.proposePace("t", day, Pace.BALANCED, Pace.RELAXED, null)
        val before = day.stops.first { it.id == "B" }.durationMinutes
        assertTrue(p.after.stops.first { it.id == "B" }.durationMinutes >= before || p.after.stops.first { it.id == "B" }.status == StopStatus.SKIPPED)
        assertEquals(60, p.after.stops.first { it.id == "L" }.durationMinutes) // meals untouched
    }

    @Test fun `state transitions follow arrive explore finish`() {
        var day = TestData.day()
        day = ScheduleEngine.markArrived(day, "A", 545)
        assertEquals(StopStatus.ARRIVED, day.stops[0].status)
        assertEquals(545, day.stops[0].actualArrivalMinutes)
        day = ScheduleEngine.markExploring(day, "A", 550)
        assertEquals(StopStatus.EXPLORING, day.stops[0].status)
        day = ScheduleEngine.markFinished(day, "A", 700)
        assertEquals(StopStatus.DONE, day.stops[0].status)
        assertEquals(700, day.stops[0].actualEndMinutes)
        assertEquals("B", day.currentStop?.id)
    }

    @Test(expected = ScheduleEngine.IllegalTransition::class)
    fun `cannot arrive at a second stop while one is in progress`() {
        val day = ScheduleEngine.markArrived(TestData.day(), "A", 545)
        ScheduleEngine.markArrived(day, "B", 560)
    }

    @Test fun `drift reports minutes behind schedule`() {
        val day = ScheduleEngine.markFinished(ScheduleEngine.markArrived(TestData.day(), "A", 540), "A", 720)
        val drift = ScheduleEngine.driftMinutes(day, 720)!!
        assertEquals(720 + 15 - 675, drift)
        val resync = ScheduleEngine.proposeResync("t", day, 720)
        assertTrue(resync.after.stops.first { it.id == "B" }.startMinutes >= 720)
    }

    @Test fun `nothing to change yields a warning and no changes`() {
        val day = TestData.day().let { d -> d.copy(stops = d.stops.map { it.copy(status = StopStatus.DONE, actualEndMinutes = it.endMinutes) }) }
        val p = ScheduleEngine.proposeDelay("t", day, 30)
        assertFalse(p.hasChanges)
        assertTrue(p.warnings.any { it.code == WarningCode.NOTHING_TO_CHANGE })
    }

    @Test fun `closing time warning when visit would end after typical closing`() {
        val d = TestData.day()
        val day = d.copy(stops = d.stops.map { if (it.id == "C") it.copy(closesMinutes = 15 * 60) else it })
        val p = ScheduleEngine.proposeDelay("t", day, 45)
        val c = p.after.stops.first { it.id == "C" }
        if (c.status == StopStatus.PENDING && c.endMinutes > 15 * 60) assertTrue(p.warnings.any { it.code == WarningCode.MAY_CLOSE_BEFORE_VISIT && it.stopId == "C" })
        assertNull(p.explanation)
    }

    @Test fun `move reorders and reflows`() {
        val day = ScheduleEngine.move(TestData.day(), "B", 1)
        assertEquals(listOf("A", "L", "B", "C", "D"), day.stops.map { it.id })
    }
}
