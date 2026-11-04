package com.tripmate.ai.domain.logic

import com.tripmate.ai.domain.model.CatalogPlace
import com.tripmate.ai.domain.model.ChecklistItem
import com.tripmate.ai.domain.model.FoodPreference
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.Money
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TransportMode
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.domain.model.Verification
import com.tripmate.ai.domain.model.WalkingLevel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class TripRequest(
    val destinationId: String,
    val destinationName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dayStartMinutes: Int = 9 * 60,
    val hoursPerDay: Int = 8,
    val currency: String = "EGP",
    val budget: Double = 0.0,
    val interests: Set<Interest> = emptySet(),
    val pace: Pace = Pace.BALANCED,
    val groupSize: Int = 1,
    val transport: TransportMode = TransportMode.TAXI,
    val walking: WalkingLevel = WalkingLevel.MODERATE,
    val food: FoodPreference = FoodPreference.NONE,
    val locale: String = "en",
)

/** Localised labels the generator needs (keeps the domain free of Android resources). */
data class GeneratorLabels(val lunch: String, val rest: String, val checklistWater: String, val checklistTicket: String, val checklistModest: String)

/**
 * Deterministic offline planner. Always available — used directly in demo/offline mode and as the
 * safety net when AI output is invalid. Only uses curated catalog places.
 */
class ItineraryGenerator(private val idGen: () -> String) {

    fun generate(request: TripRequest, catalog: List<CatalogPlace>, labels: GeneratorLabels, ar: Boolean, now: Long): TripPlan {
        val dayCount = (ChronoUnit.DAYS.between(request.startDate, request.endDate).toInt() + 1).coerceIn(1, 14)
        val dayEnd = (request.dayStartMinutes + request.hoursPerDay * 60).coerceAtMost(23 * 60 + 30)

        val candidates = catalog
            .filter { request.destinationId in it.destinationIds && it.category == StopCategory.SIGHT }
            .filter { request.walking != WalkingLevel.LIMITED || it.walkingIntensity != WalkingLevel.ACTIVE || it.priority == 3 }
            .sortedWith(compareByDescending<CatalogPlace> { score(it, request.interests) }.thenBy { it.id })

        // Group by area so each day stays geographically coherent, highest-scoring areas first.
        val byArea = candidates.groupBy { it.area }.entries
            .sortedByDescending { e -> e.value.maxOf { score(it, request.interests) } }
            .flatMap { it.value }
            .toMutableList()

        val dailyBudgetMinutes = dayEnd - request.dayStartMinutes
        val days = (0 until dayCount).map { index ->
            val stops = mutableListOf<TripStop>()
            var used = 0
            var lunchAdded = false
            var prev: TripStop? = null
            val iterator = byArea.iterator()
            while (iterator.hasNext()) {
                val place = iterator.next()
                val duration = TimeMath.roundUpTo5((place.typicalDurationMinutes * request.pace.durationFactor).toInt())
                val travel = Geo.estimateTravelMinutes(prev?.lat, prev?.lng, place.lat, place.lng, request.transport)
                val cost = duration + if (prev != null) travel else 0
                if (used + cost > dailyBudgetMinutes - 60) continue // leave room for lunch
                stops += placeToStop(place, ar, if (prev != null) travel else 0, duration, labels)
                used += cost
                prev = stops.last()
                iterator.remove()
                if (!lunchAdded && request.dayStartMinutes + used >= 12 * 60 + 30) {
                    stops += mealStop(labels.lunch, prev, request)
                    used += 60
                    lunchAdded = true
                }
                if (request.pace == Pace.RELAXED && stops.count { it.category == StopCategory.SIGHT } == 2) {
                    stops += restStop(labels.rest)
                    used += 30
                }
                if (stops.count { it.category == StopCategory.SIGHT } >= maxSightsPerDay(request.pace)) break
            }
            if (!lunchAdded) stops += mealStop(labels.lunch, prev, request)
            TripDay(index, request.startDate.plusDays(index.toLong()), request.dayStartMinutes, dayEnd,
                ScheduleEngine.reflow(stops, request.dayStartMinutes))
        }

        return TripPlan(
            id = idGen(), title = request.destinationName, destinationId = request.destinationId,
            destinationName = request.destinationName, locale = request.locale,
            startDate = request.startDate, endDate = request.startDate.plusDays(dayCount - 1L),
            currency = request.currency, budgetTotal = request.budget, pace = request.pace,
            transport = request.transport, groupSize = request.groupSize, interests = request.interests,
            walking = request.walking, food = request.food, days = days,
            generatedBy = "deterministic", createdAt = now, updatedAt = now,
        )
    }

    fun placeToStop(place: CatalogPlace, ar: Boolean, travel: Int, duration: Int, labels: GeneratorLabels): TripStop = TripStop(
        id = idGen(), placeId = place.id, title = place.name(ar), category = place.category,
        lat = place.lat, lng = place.lng, startMinutes = 0, durationMinutes = duration,
        minDurationMinutes = place.minDurationMinutes.coerceAtMost(duration),
        travelMinutesFromPrev = travel, travelSource = if (travel > 0) TravelSource.ESTIMATE else TravelSource.NONE,
        priority = place.priority, priceEstimate = place.priceEstimate?.let { Money(it, place.priceCurrency) },
        verification = Verification.SAMPLE, opensMinutes = place.typicalOpens, closesMinutes = place.typicalCloses,
        checklist = buildList {
            add(ChecklistItem(idGen(), labels.checklistWater))
            if ((place.priceEstimate ?: 0.0) > 0) add(ChecklistItem(idGen(), labels.checklistTicket))
            if (place.motif.name == "MOSQUE" || place.motif.name == "CHURCH") add(ChecklistItem(idGen(), labels.checklistModest))
        },
    )

    private fun mealStop(title: String, near: TripStop?, request: TripRequest) = TripStop(
        id = idGen(), title = title, category = StopCategory.MEAL, lat = near?.lat, lng = near?.lng,
        startMinutes = 0, durationMinutes = 60, minDurationMinutes = 30, travelMinutesFromPrev = 10,
        travelSource = TravelSource.ESTIMATE, priority = 3, verification = Verification.UNVERIFIED,
        opensMinutes = 12 * 60 + 30,
    )

    private fun restStop(title: String) = TripStop(
        id = idGen(), title = title, category = StopCategory.BREAK, startMinutes = 0, durationMinutes = 30,
        minDurationMinutes = 15, priority = 1, verification = Verification.UNVERIFIED,
    )

    private fun maxSightsPerDay(pace: Pace) = when (pace) { Pace.RELAXED -> 3; Pace.BALANCED -> 4; Pace.PACKED -> 5 }

    companion object {
        fun score(place: CatalogPlace, interests: Set<Interest>): Int =
            place.priority * 2 + place.interests.count { it in interests } * 3

        /** Recomputes offline travel estimates after the user reorders or adds stops. */
        fun withEstimatedTravel(day: TripDay, mode: TransportMode): TripDay {
            var prev: TripStop? = null
            val stops = day.stops.map { s ->
                if (s.status == StopStatus.SKIPPED) return@map s
                val updated = if (prev == null) s.copy(travelMinutesFromPrev = 0, travelSource = TravelSource.NONE)
                else if (s.hasCoordinates && prev!!.hasCoordinates) s.copy(
                    travelMinutesFromPrev = Geo.estimateTravelMinutes(prev!!.lat, prev!!.lng, s.lat, s.lng, mode),
                    travelSource = TravelSource.ESTIMATE,
                ) else s.copy(travelMinutesFromPrev = maxOf(s.travelMinutesFromPrev, 15), travelSource = TravelSource.ESTIMATE)
                prev = updated
                updated
            }
            return day.copy(stops = ScheduleEngine.reflow(stops, day.startMinutes))
        }
    }
}
