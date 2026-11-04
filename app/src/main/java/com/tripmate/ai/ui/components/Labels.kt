package com.tripmate.ai.ui.components

import androidx.annotation.StringRes
import com.tripmate.ai.R
import com.tripmate.ai.domain.logic.IssueCode
import com.tripmate.ai.domain.model.AiRoute
import com.tripmate.ai.domain.model.ExpenseCategory
import com.tripmate.ai.domain.model.FoodPreference
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.OnDeviceAvailability
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.ReplanReason
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TransportMode
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.domain.model.Verification
import com.tripmate.ai.domain.model.WalkingLevel

@StringRes fun Interest.labelRes() = when (this) {
    Interest.HISTORY -> R.string.interest_history; Interest.FOOD -> R.string.interest_food; Interest.ART -> R.string.interest_art
    Interest.MUSEUMS -> R.string.interest_museums; Interest.NATURE -> R.string.interest_nature; Interest.PHOTOGRAPHY -> R.string.interest_photography
    Interest.SHOPPING -> R.string.interest_shopping; Interest.FAMILY -> R.string.interest_family
}
@StringRes fun Pace.labelRes() = when (this) { Pace.RELAXED -> R.string.pace_relaxed; Pace.BALANCED -> R.string.pace_balanced; Pace.PACKED -> R.string.pace_packed }
@StringRes fun TransportMode.labelRes() = when (this) {
    TransportMode.WALK -> R.string.transport_walk; TransportMode.CAR -> R.string.transport_car
    TransportMode.TRANSIT -> R.string.transport_transit; TransportMode.TAXI -> R.string.transport_taxi
}
@StringRes fun WalkingLevel.labelRes() = when (this) { WalkingLevel.LIMITED -> R.string.walking_limited; WalkingLevel.MODERATE -> R.string.walking_moderate; WalkingLevel.ACTIVE -> R.string.walking_active }
@StringRes fun FoodPreference.labelRes() = when (this) {
    FoodPreference.NONE -> R.string.food_none; FoodPreference.VEGETARIAN -> R.string.food_vegetarian
    FoodPreference.HALAL -> R.string.food_halal; FoodPreference.NO_SPICY -> R.string.food_no_spicy
}
@StringRes fun ExpenseCategory.labelRes() = when (this) {
    ExpenseCategory.FOOD -> R.string.cat_food; ExpenseCategory.TRANSPORT -> R.string.cat_transport; ExpenseCategory.TICKETS -> R.string.cat_tickets
    ExpenseCategory.SHOPPING -> R.string.cat_shopping; ExpenseCategory.LODGING -> R.string.cat_lodging; ExpenseCategory.OTHER -> R.string.cat_other
}
@StringRes fun StopStatus.labelRes() = when (this) {
    StopStatus.PENDING -> R.string.status_pending; StopStatus.ARRIVED -> R.string.status_arrived; StopStatus.EXPLORING -> R.string.status_exploring
    StopStatus.DONE -> R.string.status_done; StopStatus.SKIPPED -> R.string.status_skipped
}
@StringRes fun TripStatus.labelRes() = when (this) { TripStatus.PLANNED -> R.string.trip_planned; TripStatus.ACTIVE -> R.string.trip_active; TripStatus.COMPLETED -> R.string.trip_completed }
@StringRes fun AiRoute.labelRes() = when (this) {
    AiRoute.ON_DEVICE -> R.string.route_on_device; AiRoute.CLOUD -> R.string.route_cloud; AiRoute.OFFLINE_CONTENT -> R.string.route_offline
    AiRoute.DEMO -> R.string.route_demo; AiRoute.UNAVAILABLE -> R.string.route_unavailable
}
@StringRes fun Verification.labelRes() = when (this) { Verification.VERIFIED_LIVE -> R.string.verify_live; Verification.UNVERIFIED -> R.string.verify_unverified; Verification.SAMPLE -> R.string.verify_sample }
@StringRes fun TravelSource.labelRes() = when (this) { TravelSource.NONE -> R.string.travel_none; TravelSource.ESTIMATE -> R.string.travel_estimate; TravelSource.ROUTES_API -> R.string.travel_routes }
@StringRes fun OnDeviceAvailability.labelRes() = when (this) {
    OnDeviceAvailability.UNKNOWN -> R.string.od_unknown; OnDeviceAvailability.AVAILABLE -> R.string.od_available
    OnDeviceAvailability.DOWNLOADABLE -> R.string.od_downloadable; OnDeviceAvailability.DOWNLOADING -> R.string.od_downloading
    OnDeviceAvailability.UNAVAILABLE -> R.string.od_unavailable; OnDeviceAvailability.NOT_CONFIGURED -> R.string.od_not_configured
    OnDeviceAvailability.ERROR -> R.string.od_error
}
@StringRes fun ReplanReason.labelRes() = when (this) {
    ReplanReason.RUNNING_LATE -> R.string.reason_late; ReplanReason.SKIP_STOP -> R.string.reason_skip; ReplanReason.TAKE_BREAK -> R.string.reason_break
    ReplanReason.CHANGE_PACE -> R.string.reason_pace; ReplanReason.BEHIND_OR_AHEAD -> R.string.reason_resync; ReplanReason.AI_SUGGESTION -> R.string.reason_ai
}
@StringRes fun IssueCode.labelRes() = when (this) {
    IssueCode.EMPTY_TITLE -> R.string.issue_empty_title; IssueCode.END_BEFORE_START -> R.string.issue_end_before_start
    IssueCode.NEGATIVE_BUDGET -> R.string.issue_negative_budget; IssueCode.NO_DAYS -> R.string.issue_no_days
    IssueCode.DAY_OUT_OF_RANGE -> R.string.issue_day_range; IssueCode.DAY_WINDOW_INVALID -> R.string.issue_day_window
    IssueCode.EMPTY_STOP_NAME -> R.string.issue_empty_stop; IssueCode.DUPLICATE_STOP_ID -> R.string.issue_duplicate
    IssueCode.IMPOSSIBLE_DURATION -> R.string.issue_duration; IssueCode.OVERLAPPING_STOPS -> R.string.issue_overlap
    IssueCode.STOP_OUTSIDE_DAY -> R.string.issue_outside_day; IssueCode.MISSING_COORDINATES -> R.string.issue_missing_coords
    IssueCode.INVALID_COORDINATES -> R.string.issue_invalid_coords; IssueCode.NEGATIVE_PRICE -> R.string.issue_negative_price
    IssueCode.INVALID_GROUP_SIZE -> R.string.issue_group; IssueCode.INVALID_CURRENCY -> R.string.issue_currency
}
