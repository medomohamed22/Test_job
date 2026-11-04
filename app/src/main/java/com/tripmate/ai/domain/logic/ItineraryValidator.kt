package com.tripmate.ai.domain.logic

import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TripPlan

enum class IssueSeverity { ERROR, WARNING }

enum class IssueCode {
    EMPTY_TITLE, END_BEFORE_START, NEGATIVE_BUDGET, NO_DAYS, DAY_OUT_OF_RANGE, DAY_WINDOW_INVALID,
    EMPTY_STOP_NAME, DUPLICATE_STOP_ID, IMPOSSIBLE_DURATION, OVERLAPPING_STOPS, STOP_OUTSIDE_DAY,
    MISSING_COORDINATES, INVALID_COORDINATES, NEGATIVE_PRICE, INVALID_GROUP_SIZE, INVALID_CURRENCY,
}

data class ValidationIssue(val code: IssueCode, val severity: IssueSeverity, val dayIndex: Int? = null, val stopId: String? = null)

/**
 * Validates every itinerary before it is saved — including AI-generated ones. Business rules are
 * deterministic so malformed model output can never corrupt saved trips.
 */
object ItineraryValidator {
    const val MAX_STOP_MINUTES = 12 * 60

    fun validate(plan: TripPlan): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        fun err(c: IssueCode, d: Int? = null, s: String? = null) { issues += ValidationIssue(c, IssueSeverity.ERROR, d, s) }
        fun warn(c: IssueCode, d: Int? = null, s: String? = null) { issues += ValidationIssue(c, IssueSeverity.WARNING, d, s) }

        if (plan.title.isBlank()) err(IssueCode.EMPTY_TITLE)
        if (plan.endDate.isBefore(plan.startDate)) err(IssueCode.END_BEFORE_START)
        if (plan.budgetTotal < 0 || plan.budgetTotal.isNaN()) err(IssueCode.NEGATIVE_BUDGET)
        if (plan.groupSize !in 1..50) err(IssueCode.INVALID_GROUP_SIZE)
        if (!plan.currency.matches(Regex("[A-Z]{3}"))) err(IssueCode.INVALID_CURRENCY)
        if (plan.days.isEmpty()) err(IssueCode.NO_DAYS)

        val seen = HashSet<String>()
        plan.days.forEach { day ->
            if (day.date.isBefore(plan.startDate) || day.date.isAfter(plan.endDate)) err(IssueCode.DAY_OUT_OF_RANGE, day.dayIndex)
            if (day.endMinutes <= day.startMinutes || day.startMinutes < 0 || day.endMinutes > 24 * 60) err(IssueCode.DAY_WINDOW_INVALID, day.dayIndex)
            var prevEnd: Int? = null
            day.stops.forEach { s ->
                if (!seen.add(s.id)) err(IssueCode.DUPLICATE_STOP_ID, day.dayIndex, s.id)
                if (s.title.isBlank()) err(IssueCode.EMPTY_STOP_NAME, day.dayIndex, s.id)
                if (s.durationMinutes <= 0 || s.durationMinutes > MAX_STOP_MINUTES) err(IssueCode.IMPOSSIBLE_DURATION, day.dayIndex, s.id)
                if ((s.priceEstimate?.amount ?: 0.0) < 0) err(IssueCode.NEGATIVE_PRICE, day.dayIndex, s.id)
                val lat = s.lat; val lng = s.lng
                if (lat == null || lng == null) warn(IssueCode.MISSING_COORDINATES, day.dayIndex, s.id)
                else if (lat !in -90.0..90.0 || lng !in -180.0..180.0) err(IssueCode.INVALID_COORDINATES, day.dayIndex, s.id)
                if (s.status != StopStatus.SKIPPED) {
                    if (s.startMinutes < 0 || s.endMinutes > 24 * 60 + 6 * 60) err(IssueCode.STOP_OUTSIDE_DAY, day.dayIndex, s.id)
                    else if (s.endMinutes > day.endMinutes) warn(IssueCode.STOP_OUTSIDE_DAY, day.dayIndex, s.id)
                    prevEnd?.let { if (s.startMinutes < it) warn(IssueCode.OVERLAPPING_STOPS, day.dayIndex, s.id) }
                    prevEnd = s.endMinutes
                }
            }
        }
        return issues
    }

    fun hasErrors(plan: TripPlan) = validate(plan).any { it.severity == IssueSeverity.ERROR }
}
