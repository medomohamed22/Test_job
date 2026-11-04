package com.tripmate.ai.data.mapper

import com.tripmate.ai.data.db.DayEntity
import com.tripmate.ai.data.db.ExpenseEntity
import com.tripmate.ai.data.db.JournalEntity
import com.tripmate.ai.data.db.StopEntity
import com.tripmate.ai.data.db.TripEntity
import com.tripmate.ai.data.db.TripWithChildren
import com.tripmate.ai.domain.model.ChecklistItem
import com.tripmate.ai.domain.model.Expense
import com.tripmate.ai.domain.model.ExpenseCategory
import com.tripmate.ai.domain.model.FoodPreference
import com.tripmate.ai.domain.model.Interest
import com.tripmate.ai.domain.model.JournalEntry
import com.tripmate.ai.domain.model.Money
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TransportMode
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripPlan
import com.tripmate.ai.domain.model.TripStatus
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.domain.model.Verification
import com.tripmate.ai.domain.model.WalkingLevel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Pure mapping functions between Room entities and domain models (unit-tested). */
object TripMappers {
    private val json = Json { ignoreUnknownKeys = true }
    private val checklistSerializer = ListSerializer(ChecklistItem.serializer())

    private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
        runCatching { enumValueOf<E>(value ?: "") }.getOrDefault(default)

    data class TripEntities(val trip: TripEntity, val days: List<DayEntity>, val stops: List<StopEntity>)

    fun toEntities(plan: TripPlan): TripEntities {
        val trip = TripEntity(
            id = plan.id, title = plan.title, destinationId = plan.destinationId, destinationName = plan.destinationName,
            locale = plan.locale, startEpochDay = plan.startDate.toEpochDay(), endEpochDay = plan.endDate.toEpochDay(),
            currency = plan.currency, budget = plan.budgetTotal, pace = plan.pace.name, transport = plan.transport.name,
            groupSize = plan.groupSize, interests = plan.interests.joinToString(",") { it.name }, walking = plan.walking.name,
            food = plan.food.name, status = plan.status.name, isSample = plan.isSample, generatedBy = plan.generatedBy,
            createdAt = plan.createdAt, updatedAt = plan.updatedAt,
        )
        val days = plan.days.map { DayEntity(plan.id, it.dayIndex, it.date.toEpochDay(), it.startMinutes, it.endMinutes) }
        val stops = plan.days.flatMap { day ->
            day.stops.mapIndexed { pos, s ->
                StopEntity(
                    id = s.id, tripId = plan.id, dayIndex = day.dayIndex, position = pos, placeId = s.placeId,
                    title = s.title, category = s.category.name, lat = s.lat, lng = s.lng, startMinutes = s.startMinutes,
                    durationMinutes = s.durationMinutes, minDurationMinutes = s.minDurationMinutes,
                    travelMinutes = s.travelMinutesFromPrev, travelSource = s.travelSource.name, status = s.status.name,
                    priority = s.priority, notes = s.notes, priceAmount = s.priceEstimate?.amount,
                    priceCurrency = s.priceEstimate?.currency, verification = s.verification.name,
                    opensMinutes = s.opensMinutes, closesMinutes = s.closesMinutes,
                    checklistJson = json.encodeToString(checklistSerializer, s.checklist),
                    actualArrivalMinutes = s.actualArrivalMinutes, actualEndMinutes = s.actualEndMinutes,
                )
            }
        }
        return TripEntities(trip, days, stops)
    }

    fun toDomain(row: TripWithChildren): TripPlan {
        val t = row.trip
        val stopsByDay = row.stops.groupBy { it.dayIndex }
        val days = row.days.sortedBy { it.dayIndex }.map { d ->
            TripDay(
                dayIndex = d.dayIndex, date = LocalDate.ofEpochDay(d.epochDay),
                startMinutes = d.startMinutes, endMinutes = d.endMinutes,
                stops = stopsByDay[d.dayIndex].orEmpty().sortedBy { it.position }.map(::stopToDomain),
            )
        }
        return TripPlan(
            id = t.id, title = t.title, destinationId = t.destinationId, destinationName = t.destinationName,
            locale = t.locale, startDate = LocalDate.ofEpochDay(t.startEpochDay), endDate = LocalDate.ofEpochDay(t.endEpochDay),
            currency = t.currency, budgetTotal = t.budget, pace = enumOr(t.pace, Pace.BALANCED),
            transport = enumOr(t.transport, TransportMode.TAXI), groupSize = t.groupSize,
            interests = t.interests.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { Interest.valueOf(it) }.getOrNull() }.toSet(),
            walking = enumOr(t.walking, WalkingLevel.MODERATE), food = enumOr(t.food, FoodPreference.NONE),
            status = enumOr(t.status, TripStatus.PLANNED), days = days, isSample = t.isSample,
            generatedBy = t.generatedBy, createdAt = t.createdAt, updatedAt = t.updatedAt,
        )
    }

    fun stopToDomain(s: StopEntity) = TripStop(
        id = s.id, placeId = s.placeId, title = s.title, category = enumOr(s.category, StopCategory.CUSTOM),
        lat = s.lat, lng = s.lng, startMinutes = s.startMinutes, durationMinutes = s.durationMinutes,
        minDurationMinutes = s.minDurationMinutes, travelMinutesFromPrev = s.travelMinutes,
        travelSource = enumOr(s.travelSource, TravelSource.NONE), status = enumOr(s.status, StopStatus.PENDING),
        priority = s.priority, notes = s.notes,
        priceEstimate = if (s.priceAmount != null && s.priceCurrency != null) Money(s.priceAmount, s.priceCurrency) else null,
        verification = enumOr(s.verification, Verification.UNVERIFIED), opensMinutes = s.opensMinutes, closesMinutes = s.closesMinutes,
        checklist = runCatching { json.decodeFromString(checklistSerializer, s.checklistJson) }.getOrDefault(emptyList()),
        actualArrivalMinutes = s.actualArrivalMinutes, actualEndMinutes = s.actualEndMinutes,
    )

    fun toEntity(e: Expense) = ExpenseEntity(e.id, e.tripId, e.date.toEpochDay(), e.amount, e.currency, e.category.name, e.note, e.stopId)
    fun toDomain(e: ExpenseEntity) = Expense(e.id, e.tripId, LocalDate.ofEpochDay(e.epochDay), e.amount, e.currency, enumOr(e.category, ExpenseCategory.OTHER), e.note, e.stopId)
    fun toEntity(j: JournalEntry) = JournalEntity(j.id, j.tripId, j.createdAt, j.text, j.photoPath, j.stopId)
    fun toDomain(j: JournalEntity) = JournalEntry(j.id, j.tripId, j.createdAt, j.text, j.photoPath, j.stopId)
}
