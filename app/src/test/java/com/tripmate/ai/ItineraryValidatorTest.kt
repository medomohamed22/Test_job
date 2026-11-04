package com.tripmate.ai

import com.tripmate.ai.domain.logic.IssueCode
import com.tripmate.ai.domain.logic.IssueSeverity
import com.tripmate.ai.domain.logic.ItineraryValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItineraryValidatorTest {
    private fun codes(p: com.tripmate.ai.domain.model.TripPlan) = ItineraryValidator.validate(p).filter { it.severity == IssueSeverity.ERROR }.map { it.code }

    @Test fun `valid plan has no errors`() = assertFalse(ItineraryValidator.hasErrors(TestData.plan()))

    @Test fun `detects negative budget and bad dates`() {
        val p = TestData.plan().copy(budgetTotal = -5.0, endDate = TestData.date.minusDays(1))
        val c = codes(p)
        assertTrue(IssueCode.NEGATIVE_BUDGET in c)
        assertTrue(IssueCode.END_BEFORE_START in c)
    }

    @Test fun `detects duplicate ids, empty names and impossible durations`() {
        val d = TestData.day()
        val bad = d.copy(stops = d.stops + d.stops[0] + d.stops[1].copy(id = "X", title = " ") + d.stops[2].copy(id = "Y", durationMinutes = 0))
        val c = codes(TestData.plan(listOf(bad)))
        assertTrue(IssueCode.DUPLICATE_STOP_ID in c)
        assertTrue(IssueCode.EMPTY_STOP_NAME in c)
        assertTrue(IssueCode.IMPOSSIBLE_DURATION in c)
    }

    @Test fun `missing coordinates is a warning, invalid coordinates an error`() {
        val d = TestData.day()
        val day = d.copy(stops = listOf(d.stops[0].copy(lat = null, lng = null), d.stops[1].copy(lat = 200.0)))
        val issues = ItineraryValidator.validate(TestData.plan(listOf(day)))
        assertTrue(issues.any { it.code == IssueCode.MISSING_COORDINATES && it.severity == IssueSeverity.WARNING })
        assertTrue(issues.any { it.code == IssueCode.INVALID_COORDINATES && it.severity == IssueSeverity.ERROR })
    }

    @Test fun `overlapping stops produce a warning`() {
        val d = TestData.day()
        val day = d.copy(stops = listOf(d.stops[0], d.stops[1].copy(startMinutes = d.stops[0].startMinutes + 10)))
        assertTrue(ItineraryValidator.validate(TestData.plan(listOf(day))).any { it.code == IssueCode.OVERLAPPING_STOPS })
    }

    @Test fun `invalid currency and group size are errors`() {
        val c = codes(TestData.plan().copy(currency = "egp", groupSize = 0))
        assertTrue(IssueCode.INVALID_CURRENCY in c)
        assertTrue(IssueCode.INVALID_GROUP_SIZE in c)
    }
}
