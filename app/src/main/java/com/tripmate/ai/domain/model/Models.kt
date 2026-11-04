package com.tripmate.ai.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Pure domain models. No Android dependencies so they are unit-testable on the JVM. */

enum class TripStatus { PLANNED, ACTIVE, COMPLETED }
enum class StopStatus { PENDING, ARRIVED, EXPLORING, DONE, SKIPPED;
    val isFinal get() = this == DONE || this == SKIPPED
    val isInProgress get() = this == ARRIVED || this == EXPLORING
}
enum class StopCategory { SIGHT, MEAL, BREAK, CUSTOM }

/** Where a piece of factual metadata comes from. Shown to the user on every card. */
enum class Verification { VERIFIED_LIVE, UNVERIFIED, SAMPLE }

/** How a travel duration between two stops was obtained. */
enum class TravelSource { NONE, ESTIMATE, ROUTES_API }

enum class Pace(val durationFactor: Double) { RELAXED(1.25), BALANCED(1.0), PACKED(0.8) }
enum class TransportMode(val kmPerHour: Double, val overheadMinutes: Int) {
    WALK(4.5, 0), CAR(22.0, 10), TRANSIT(16.0, 12), TAXI(22.0, 8)
}
enum class WalkingLevel { LIMITED, MODERATE, ACTIVE }
enum class Interest { HISTORY, FOOD, ART, MUSEUMS, NATURE, PHOTOGRAPHY, SHOPPING, FAMILY }
enum class FoodPreference { NONE, VEGETARIAN, HALAL, NO_SPICY }
enum class ExpenseCategory { FOOD, TRANSPORT, TICKETS, SHOPPING, LODGING, OTHER }

@Serializable
data class ChecklistItem(val id: String, val text: String, val done: Boolean = false)

data class Money(val amount: Double, val currency: String)

data class TripStop(
    val id: String,
    val placeId: String? = null,
    val title: String,
    val category: StopCategory = StopCategory.SIGHT,
    val lat: Double? = null,
    val lng: Double? = null,
    /** Planned start, minutes from local midnight. */
    val startMinutes: Int,
    val durationMinutes: Int,
    /** Lower bound the schedule engine may shorten this stop to. */
    val minDurationMinutes: Int = (durationMinutes * 0.6).toInt().coerceAtLeast(10),
    val travelMinutesFromPrev: Int = 0,
    val travelSource: TravelSource = TravelSource.NONE,
    val status: StopStatus = StopStatus.PENDING,
    /** 3 = must-see (never dropped automatically), 2 = normal, 1 = nice-to-have. */
    val priority: Int = 2,
    val notes: String = "",
    val priceEstimate: Money? = null,
    val verification: Verification = Verification.UNVERIFIED,
    /** Typical opening window (minutes of day). Unverified unless [verification] says otherwise. */
    val opensMinutes: Int? = null,
    val closesMinutes: Int? = null,
    val checklist: List<ChecklistItem> = emptyList(),
    val actualArrivalMinutes: Int? = null,
    val actualEndMinutes: Int? = null,
) {
    val endMinutes: Int get() = startMinutes + durationMinutes
    val hasCoordinates: Boolean get() = lat != null && lng != null
}

data class TripDay(
    val dayIndex: Int,
    val date: LocalDate,
    val startMinutes: Int,
    val endMinutes: Int,
    val stops: List<TripStop>,
) {
    val activeStops: List<TripStop> get() = stops.filter { it.status != StopStatus.SKIPPED }
    val currentStop: TripStop? get() = stops.firstOrNull { !it.status.isFinal }
    val completedCount: Int get() = stops.count { it.status == StopStatus.DONE }
    val progress: Float get() = if (stops.isEmpty()) 0f else stops.count { it.status.isFinal } / stops.size.toFloat()
}

data class TripPlan(
    val id: String,
    val title: String,
    val destinationId: String,
    val destinationName: String,
    val locale: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currency: String,
    val budgetTotal: Double,
    val pace: Pace = Pace.BALANCED,
    val transport: TransportMode = TransportMode.TAXI,
    val groupSize: Int = 1,
    val interests: Set<Interest> = emptySet(),
    val walking: WalkingLevel = WalkingLevel.MODERATE,
    val food: FoodPreference = FoodPreference.NONE,
    val status: TripStatus = TripStatus.PLANNED,
    val days: List<TripDay> = emptyList(),
    val isSample: Boolean = false,
    val generatedBy: String = "deterministic",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val dayCount: Int get() = days.size
    val allStops: List<TripStop> get() = days.flatMap { it.stops }
    fun day(index: Int): TripDay? = days.firstOrNull { it.dayIndex == index }
    fun findStop(stopId: String): Pair<TripDay, TripStop>? =
        days.firstNotNullOfOrNull { d -> d.stops.firstOrNull { it.id == stopId }?.let { d to it } }
}

data class Expense(
    val id: String,
    val tripId: String,
    val date: LocalDate,
    val amount: Double,
    val currency: String,
    val category: ExpenseCategory,
    val note: String = "",
    val stopId: String? = null,
)

data class JournalEntry(
    val id: String,
    val tripId: String,
    val createdAt: Long,
    val text: String,
    val photoPath: String? = null,
    val stopId: String? = null,
)

enum class TripEventType { TRIP_STARTED, ARRIVED, EXPLORING, FINISHED, SKIPPED, DELAY_REPORTED, BREAK_TAKEN, PLAN_CHANGED, TRIP_COMPLETED }

data class TripEvent(
    val timestamp: Long,
    val type: TripEventType,
    val stopId: String? = null,
    val metadata: String = "",
)

/* ---------------- Re-planning ---------------- */

enum class ReplanReason { RUNNING_LATE, SKIP_STOP, TAKE_BREAK, CHANGE_PACE, BEHIND_OR_AHEAD, AI_SUGGESTION }

enum class ChangeType { TIME_SHIFTED, SHORTENED, REMOVED, ADDED, UNCHANGED }

data class StopChange(
    val stopId: String,
    val title: String,
    val type: ChangeType,
    val oldStart: Int?,
    val newStart: Int?,
    val oldDuration: Int?,
    val newDuration: Int?,
)

enum class WarningCode { DAY_OVERRUN, MAY_CLOSE_BEFORE_VISIT, MEAL_SHIFTED_LATE, NOTHING_TO_CHANGE }

data class ReplanWarning(val code: WarningCode, val stopId: String? = null, val minutes: Int? = null)

data class ReplanProposal(
    val tripId: String,
    val dayIndex: Int,
    val reason: ReplanReason,
    val reasonDetail: Int? = null,
    val before: TripDay,
    val after: TripDay,
    val changes: List<StopChange>,
    val warnings: List<ReplanWarning>,
    val explanation: String? = null,
    val requiresConfirmation: Boolean = true,
) {
    val hasChanges: Boolean get() = changes.any { it.type != ChangeType.UNCHANGED }
}

/* ---------------- AI provenance ---------------- */

enum class AiRoute { ON_DEVICE, CLOUD, OFFLINE_CONTENT, DEMO, UNAVAILABLE }

enum class OnDeviceAvailability { UNKNOWN, AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, NOT_CONFIGURED, ERROR }

data class AiExecutionInfo(
    val task: String,
    val route: AiRoute,
    val durationMs: Long? = null,
    val availability: OnDeviceAvailability = OnDeviceAvailability.UNKNOWN,
    val fallbackReason: String? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)

data class AiResult(val text: String, val info: AiExecutionInfo)
