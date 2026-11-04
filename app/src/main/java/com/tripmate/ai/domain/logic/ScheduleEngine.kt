package com.tripmate.ai.domain.logic

import com.tripmate.ai.domain.model.ChangeType
import com.tripmate.ai.domain.model.Pace
import com.tripmate.ai.domain.model.ReplanProposal
import com.tripmate.ai.domain.model.ReplanReason
import com.tripmate.ai.domain.model.ReplanWarning
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopChange
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TravelSource
import com.tripmate.ai.domain.model.TripDay
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.domain.model.WarningCode

/**
 * Deterministic, on-device schedule maths. No AI and no network: every timing decision in
 * Active Trip Mode goes through here so behaviour is predictable, testable and works offline.
 *
 * Nothing in this object mutates persisted data. "propose*" functions return a
 * [ReplanProposal]; the caller saves `proposal.after` only after the user taps Apply.
 */
object ScheduleEngine {

    class IllegalTransition(message: String) : IllegalStateException(message)

    /* ------------------------------------------------------------------ */
    /* Layout                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * Re-computes start times of all PENDING stops sequentially. Finished and in-progress stops
     * keep their (actual) times. [anchor] is the earliest time the first pending stop may start.
     */
    fun reflow(stops: List<TripStop>, dayStart: Int, anchor: Int? = null): List<TripStop> {
        var cursor = dayStart
        var anchorUsed = false
        var hasPrevious = false
        return stops.map { stop ->
            when (stop.status) {
                StopStatus.SKIPPED -> stop
                StopStatus.DONE -> {
                    cursor = stop.actualEndMinutes ?: stop.endMinutes
                    hasPrevious = true
                    stop
                }
                StopStatus.ARRIVED, StopStatus.EXPLORING -> {
                    cursor = (stop.actualArrivalMinutes ?: stop.startMinutes) + stop.durationMinutes
                    hasPrevious = true
                    stop
                }
                StopStatus.PENDING -> {
                    var earliest = cursor + if (hasPrevious) stop.travelMinutesFromPrev else 0
                    if (!anchorUsed && anchor != null) {
                        earliest = maxOf(earliest, anchor)
                        anchorUsed = true
                    }
                    stop.opensMinutes?.let { opens -> if (earliest < opens) earliest = opens }
                    val start = TimeMath.roundUpTo5(earliest)
                    cursor = start + stop.durationMinutes
                    hasPrevious = true
                    stop.copy(startMinutes = start)
                }
            }
        }
    }

    fun lastEnd(stops: List<TripStop>): Int =
        stops.filter { it.status != StopStatus.SKIPPED }.maxOfOrNull {
            if (it.status == StopStatus.DONE) it.actualEndMinutes ?: it.endMinutes
            else (it.actualArrivalMinutes ?: it.startMinutes) + it.durationMinutes
        } ?: 0

    /**
     * Makes pending stops fit before [dayEnd], in this order of preference:
     * 1. shorten flexible sights (never below their minimum),
     * 2. drop nice-to-have sights (priority 1),
     * 3. shorten meals/breaks,
     * 4. drop normal sights (priority 2).
     * Must-see stops (priority 3) and meals are never dropped automatically; if the day still
     * overruns, a warning is produced instead.
     */
    fun fitToDay(stops: List<TripStop>, dayStart: Int, dayEnd: Int, anchor: Int?): Pair<List<TripStop>, List<ReplanWarning>> {
        var current = reflow(stops, dayStart, anchor)
        var overrun = lastEnd(current) - dayEnd
        val isFood = { s: TripStop -> s.category == StopCategory.MEAL || s.category == StopCategory.BREAK }

        fun shrink(filter: (TripStop) -> Boolean) {
            if (overrun <= 0) return
            val order = current.filter { it.status == StopStatus.PENDING && it.durationMinutes > it.minDurationMinutes && filter(it) }
                .sortedWith(compareBy<TripStop> { it.priority }.thenByDescending { it.durationMinutes - it.minDurationMinutes })
                .map { it.id }
            for (id in order) {
                if (overrun <= 0) break
                current = current.map { s ->
                    if (s.id != id) s else {
                        val cut = minOf(s.durationMinutes - s.minDurationMinutes, overrun)
                        val rounded = (cut / 5) * 5
                        s.copy(durationMinutes = s.durationMinutes - if (rounded == 0) cut else rounded)
                    }
                }
                current = reflow(current, dayStart, anchor)
                overrun = lastEnd(current) - dayEnd
            }
        }

        fun drop(maxPriority: Int) {
            while (overrun > 0) {
                val candidate = current.withIndex()
                    .filter { (_, s) -> s.status == StopStatus.PENDING && s.priority <= maxPriority && !isFood(s) }
                    .sortedWith(compareBy<IndexedValue<TripStop>> { it.value.priority }.thenByDescending { it.index })
                    .firstOrNull() ?: return
                current = current.map { if (it.id == candidate.value.id) it.copy(status = StopStatus.SKIPPED) else it }
                current = reflow(current, dayStart, anchor)
                overrun = lastEnd(current) - dayEnd
            }
        }

        shrink { !isFood(it) }
        drop(1)
        shrink(isFood)
        drop(2)

        val warnings = mutableListOf<ReplanWarning>()
        if (overrun > 0) warnings += ReplanWarning(WarningCode.DAY_OVERRUN, minutes = overrun)
        current.filter { it.status == StopStatus.PENDING }.forEach { s ->
            val closes = s.closesMinutes
            if (closes != null && s.endMinutes > closes) {
                warnings += ReplanWarning(WarningCode.MAY_CLOSE_BEFORE_VISIT, s.id, s.endMinutes - closes)
            }
        }
        return current to warnings
    }

    /* ------------------------------------------------------------------ */
    /* Proposals                                                            */
    /* ------------------------------------------------------------------ */

    fun proposeDelay(tripId: String, day: TripDay, delayMinutes: Int): ReplanProposal {
        require(delayMinutes in 1..12 * 60) { "Delay must be between 1 minute and 12 hours" }
        val next = day.stops.firstOrNull { it.status == StopStatus.PENDING }
            ?: return emptyProposal(tripId, day, ReplanReason.RUNNING_LATE, delayMinutes)
        val anchor = next.startMinutes + delayMinutes
        return build(tripId, day, ReplanReason.RUNNING_LATE, delayMinutes, day.stops, anchor)
    }

    fun proposeSkip(tripId: String, day: TripDay, stopId: String, nowMinutes: Int? = null): ReplanProposal {
        val target = day.stops.firstOrNull { it.id == stopId } ?: throw IllegalTransition("Unknown stop $stopId")
        if (target.status.isFinal) throw IllegalTransition("Stop already ${target.status}")
        val skipped = day.stops.map { if (it.id == stopId) it.copy(status = StopStatus.SKIPPED) else it }
        // Remaining stops may move earlier, but never before "now".
        return build(tripId, day, ReplanReason.SKIP_STOP, null, skipped, nowMinutes)
    }

    fun proposeBreak(tripId: String, day: TripDay, minutes: Int, nowMinutes: Int?, breakId: String, breakTitle: String): ReplanProposal {
        require(minutes in 5..240) { "Break must be between 5 and 240 minutes" }
        val insertAt = day.stops.indexOfFirst { it.status == StopStatus.PENDING }.let { if (it < 0) day.stops.size else it }
        val inProgress = day.stops.firstOrNull { it.status.isInProgress }
        val start = nowMinutes ?: inProgress?.let { (it.actualArrivalMinutes ?: it.startMinutes) + it.durationMinutes }
            ?: day.stops.getOrNull(insertAt)?.startMinutes ?: day.startMinutes
        val breakStop = TripStop(
            id = breakId, title = breakTitle, category = StopCategory.BREAK,
            startMinutes = start, durationMinutes = minutes, minDurationMinutes = minutes,
            priority = 3, travelSource = TravelSource.NONE,
        )
        val list = day.stops.toMutableList().apply { add(insertAt, breakStop) }
        return build(tripId, day, ReplanReason.TAKE_BREAK, minutes, list, start)
    }

    fun proposePace(tripId: String, day: TripDay, from: Pace, to: Pace, nowMinutes: Int?): ReplanProposal {
        val ratio = to.durationFactor / from.durationFactor
        val scaled = day.stops.map { s ->
            if (s.status == StopStatus.PENDING && s.category == StopCategory.SIGHT) {
                val d = TimeMath.roundUpTo5((s.durationMinutes * ratio).toInt()).coerceAtLeast(15)
                // Slowing down must not be undone by the fitter: keep the longer visits and drop
                // optional stops instead. Speeding up keeps the original lower bound.
                s.copy(durationMinutes = d, minDurationMinutes = if (ratio > 1.0) d else minOf(s.minDurationMinutes, d))
            } else s
        }
        return build(tripId, day, ReplanReason.CHANGE_PACE, null, scaled, nowMinutes)
    }

    /** Re-anchors the remaining stops on the real clock (after finishing early or late). */
    fun proposeResync(tripId: String, day: TripDay, nowMinutes: Int): ReplanProposal =
        build(tripId, day, ReplanReason.BEHIND_OR_AHEAD, null, day.stops, nowMinutes)

    /**
     * Minutes the traveller is behind (+) or ahead (−) of the plan for the next pending stop,
     * or null if there is nothing left.
     */
    fun driftMinutes(day: TripDay, nowMinutes: Int): Int? {
        val inProgress = day.stops.firstOrNull { it.status.isInProgress }
        val next = day.stops.firstOrNull { it.status == StopStatus.PENDING } ?: return null
        val readyAt = if (inProgress != null) {
            maxOf(nowMinutes, (inProgress.actualArrivalMinutes ?: inProgress.startMinutes) + inProgress.durationMinutes)
        } else nowMinutes
        return readyAt + next.travelMinutesFromPrev - next.startMinutes
    }

    private fun build(
        tripId: String,
        before: TripDay,
        reason: ReplanReason,
        detail: Int?,
        stops: List<TripStop>,
        anchor: Int?,
    ): ReplanProposal {
        val (fitted, warnings) = fitToDay(stops, before.startMinutes, before.endMinutes, anchor)
        val after = before.copy(stops = fitted)
        val changes = diff(before, after)
        val allWarnings = warnings.toMutableList()
        mealWarnings(before, after, allWarnings)
        if (changes.none { it.type != ChangeType.UNCHANGED }) allWarnings += ReplanWarning(WarningCode.NOTHING_TO_CHANGE)
        return ReplanProposal(tripId, before.dayIndex, reason, detail, before, after, changes, allWarnings)
    }

    private fun emptyProposal(tripId: String, day: TripDay, reason: ReplanReason, detail: Int?) =
        ReplanProposal(tripId, day.dayIndex, reason, detail, day, day, emptyList(), listOf(ReplanWarning(WarningCode.NOTHING_TO_CHANGE)))

    private fun mealWarnings(before: TripDay, after: TripDay, out: MutableList<ReplanWarning>) {
        val old = before.stops.associateBy { it.id }
        after.stops.filter { it.category == StopCategory.MEAL && it.status == StopStatus.PENDING }.forEach { meal ->
            val o = old[meal.id] ?: return@forEach
            if (meal.startMinutes - o.startMinutes >= 60) out += ReplanWarning(WarningCode.MEAL_SHIFTED_LATE, meal.id, meal.startMinutes - o.startMinutes)
        }
    }

    fun diff(before: TripDay, after: TripDay): List<StopChange> {
        val old = before.stops.associateBy { it.id }
        return after.stops.mapNotNull { s ->
            val o = old[s.id]
            when {
                o == null -> StopChange(s.id, s.title, ChangeType.ADDED, null, s.startMinutes, null, s.durationMinutes)
                o.status.isFinal -> null // history is never changed
                s.status == StopStatus.SKIPPED -> StopChange(s.id, s.title, ChangeType.REMOVED, o.startMinutes, null, o.durationMinutes, null)
                s.durationMinutes != o.durationMinutes ->
                    StopChange(s.id, s.title, ChangeType.SHORTENED, o.startMinutes, s.startMinutes, o.durationMinutes, s.durationMinutes)
                s.startMinutes != o.startMinutes ->
                    StopChange(s.id, s.title, ChangeType.TIME_SHIFTED, o.startMinutes, s.startMinutes, o.durationMinutes, s.durationMinutes)
                else -> StopChange(s.id, s.title, ChangeType.UNCHANGED, o.startMinutes, s.startMinutes, o.durationMinutes, s.durationMinutes)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* User-confirmed state transitions                                     */
    /* ------------------------------------------------------------------ */

    fun markArrived(day: TripDay, stopId: String, nowMinutes: Int): TripDay = transition(day, stopId) { s ->
        if (s.status != StopStatus.PENDING) throw IllegalTransition("Can only arrive at a pending stop")
        if (day.stops.any { it.status.isInProgress }) throw IllegalTransition("Finish the current stop first")
        s.copy(status = StopStatus.ARRIVED, actualArrivalMinutes = nowMinutes)
    }

    fun markExploring(day: TripDay, stopId: String, nowMinutes: Int): TripDay = transition(day, stopId) { s ->
        when (s.status) {
            StopStatus.ARRIVED -> s.copy(status = StopStatus.EXPLORING)
            StopStatus.PENDING -> if (day.stops.any { it.status.isInProgress }) throw IllegalTransition("Finish the current stop first")
                else s.copy(status = StopStatus.EXPLORING, actualArrivalMinutes = nowMinutes)
            else -> throw IllegalTransition("Cannot start exploring from ${s.status}")
        }
    }

    fun markFinished(day: TripDay, stopId: String, nowMinutes: Int): TripDay = transition(day, stopId) { s ->
        if (s.status.isFinal) throw IllegalTransition("Stop already finished")
        val arrival = s.actualArrivalMinutes ?: nowMinutes
        s.copy(status = StopStatus.DONE, actualArrivalMinutes = arrival, actualEndMinutes = maxOf(nowMinutes, arrival))
    }

    private fun transition(day: TripDay, stopId: String, block: (TripStop) -> TripStop): TripDay {
        if (day.stops.none { it.id == stopId }) throw IllegalTransition("Unknown stop $stopId")
        return day.copy(stops = day.stops.map { if (it.id == stopId) block(it) else it })
    }

    /* ------------------------------------------------------------------ */
    /* Editing helpers (itinerary editor)                                   */
    /* ------------------------------------------------------------------ */

    fun move(day: TripDay, stopId: String, delta: Int): TripDay {
        val idx = day.stops.indexOfFirst { it.id == stopId }
        val target = idx + delta
        if (idx < 0 || target !in day.stops.indices) return day
        val list = day.stops.toMutableList()
        val item = list.removeAt(idx)
        list.add(target, item)
        return day.copy(stops = reflow(list, day.startMinutes))
    }
}
